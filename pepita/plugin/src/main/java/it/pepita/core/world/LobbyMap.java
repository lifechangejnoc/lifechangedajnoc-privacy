package it.pepita.core.world;

import it.pepita.core.PepitaCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Mappa esterna per la lobby: un mondo Minecraft in zip messo in plugins/PepitaCore/mappe/ (nome in lobby.mappa).
 * Al primo avvio con uno zip nuovo il vecchio mondo pepita_hub va in una cartella di backup, la mappa viene estratta
 * come cartella pepita_hub nella radice del server e Paper la importa (e aggiorna i blocchi) quando crea il mondo.
 * Nello zip può esserci pepita-lobby.yml con spawn, npc, vuoto-y e blocchi da sostituire.
 */
public final class LobbyMap {
    /** Solo questo serve del mondo: il resto (giocatori, statistiche, altre dimensioni) resta fuori. */
    private static final List<String> KEEP = List.of("level.dat", "region/", "entities/", "poi/");
    private static final String INFO = "pepita-lobby.yml";

    private final PepitaCore plugin;
    private final List<String> pendingReplace = new ArrayList<>();

    public LobbyMap(PepitaCore plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    /** La lobby usa una mappa esterna (installata). */
    public boolean active() {
        return !cfg().getString("lobby.mappa-installata", "").isEmpty();
    }

    private File mapsDir() {
        return new File(plugin.getDataFolder(), "mappe");
    }

    /**
     * La mappa da installare: lo zip (o la cartella già estratta) indicato in lobby.mappa; se non c'è, l'unico zip
     * o l'unica cartella con un level.dat presenti in plugins/PepitaCore/mappe/. null = nessuna mappa.
     */
    private File source() {
        String name = cfg().getString("lobby.mappa", "");
        if (name.isBlank()) return null;
        File dir = mapsDir();
        File f = new File(dir, name);
        if (f.exists()) return f;
        File noExt = new File(dir, name.replaceAll("(?i)\\.zip$", ""));
        if (noExt.isDirectory() && levelDir(noExt.toPath()) != null) return noExt;
        File[] all = dir.listFiles();
        List<File> found = new ArrayList<>();
        if (all != null) for (File c : all)
            if (c.isFile() && c.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".zip") || c.isDirectory() && levelDir(c.toPath()) != null) found.add(c);
        if (found.size() == 1) {
            plugin.getLogger().info("Lobby: \"" + name + "\" non c'è in mappe/, uso \"" + found.getFirst().getName() + "\".");
            return found.getFirst();
        }
        if (found.isEmpty()) {
            if (!active()) plugin.getLogger().info("Lobby: nessuna mappa in " + dir.getPath() + " (uso la lobby del plugin).");
        } else {
            plugin.getLogger().warning("Lobby: in mappe/ ci sono più mappe " + found.stream().map(File::getName).toList()
                    + ": scrivi in config.yml lobby.mappa il nome di quella da usare.");
        }
        return null;
    }

    private static String marker(File f) throws IOException {
        if (f.isFile()) return f.getName() + ":" + f.length() + ":" + f.lastModified();
        Path ld = levelDir(f.toPath());
        return f.getName() + ":" + Files.size(ld.resolve("level.dat")) + ":" + Files.getLastModifiedTime(ld.resolve("level.dat")).toMillis();
    }

    /** Cartella con level.dat dentro una cartella (lei stessa o fino a due livelli sotto). */
    private static Path levelDir(Path dir) {
        if (Files.isRegularFile(dir.resolve("level.dat"))) return dir;
        try (var s = Files.walk(dir, 3)) {
            return s.filter(p -> p.getFileName().toString().equals("level.dat") && Files.isRegularFile(p)).map(Path::getParent)
                    .min(java.util.Comparator.comparingInt(Path::getNameCount)).orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Da chiamare prima di creare il mondo della lobby. Se c'è una mappa nuova, la installa.
     * @param createHub crea (o carica) il mondo pepita_hub come al solito
     */
    public void prepare(Supplier<World> createHub) {
        mapsDir().mkdirs();
        File src = source();
        if (src == null) return;
        String mk;
        try {
            mk = marker(src);
        } catch (IOException ex) {
            plugin.getLogger().severe("Lobby: non riesco a leggere " + src.getName() + ": " + ex.getMessage());
            return;
        }
        if (mk.equals(cfg().getString("lobby.mappa-installata", ""))) {
            plugin.getLogger().info("Lobby: mappa " + src.getName() + " già installata.");
            return;
        }
        plugin.getLogger().info("Nuova mappa per la lobby: " + src.getName() + ". Installazione...");
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path container = Bukkit.getWorldContainer().toPath();
        Path legacy = container.resolve(Layout.W_HUB);
        Path tmp = container.resolve(Layout.W_HUB + "_nuova_" + stamp);
        Path backup = container.resolve(Layout.W_HUB + "_backup_" + stamp);
        Path oldPath = null;
        boolean moved = false;
        try {
            // 1) prima si prepara la mappa in una cartella a parte: il mondo attuale non si tocca finché non è pronta
            YamlConfiguration info;
            int files;
            if (src.isFile()) {
                try (ZipFile zf = new ZipFile(src)) {
                    String root = findRoot(zf);
                    if (root == null) {
                        plugin.getLogger().severe("Lobby: nello zip " + src.getName() + " non c'è un level.dat, non è un mondo Minecraft. Lobby invariata.");
                        return;
                    }
                    files = extract(zf, root, tmp);
                    info = readInfo(zf);
                }
            } else {
                Path ld = levelDir(src.toPath());
                files = copyWorld(ld, tmp);
                Path yml = ld.resolve(INFO);
                info = Files.isRegularFile(yml) ? YamlConfiguration.loadConfiguration(yml.toFile()) : null;
            }
            if (!Files.isRegularFile(tmp.resolve("level.dat")) || !Files.isDirectory(tmp.resolve("region"))) {
                plugin.getLogger().severe("Lobby: la mappa " + src.getName() + " non ha level.dat e region/. Lobby invariata.");
                deleteTree(tmp);
                return;
            }
            // 2) il mondo della lobby attuale (anche appena creato e vuoto) va in backup: Paper non importa sopra file esistenti
            World old = createHub.get();
            oldPath = old.getWorldPath();
            if (!Bukkit.unloadWorld(old, true)) {
                plugin.getLogger().severe("Lobby: impossibile scaricare il mondo pepita_hub, mappa non installata.");
                deleteTree(tmp);
                return;
            }
            if (Files.exists(oldPath)) {
                Files.move(oldPath, backup);
                moved = true;
            }
            if (Files.exists(legacy)) Files.move(legacy, container.resolve(Layout.W_HUB + "_backup_cartella_" + stamp));
            // 3) la mappa diventa la cartella "pepita_hub" (formato classico): Paper la importa quando crea il mondo
            Files.move(tmp, legacy);
            if (info != null) {
                if (info.isList("spawn")) cfg().set("lobby.spawn", info.getDoubleList("spawn"));
                if (info.isList("npc")) cfg().set("lobby.npc", info.getDoubleList("npc"));
                if (info.contains("vuoto-y")) cfg().set("lobby.vuoto-y", info.getInt("vuoto-y"));
                pendingReplace.addAll(info.getStringList("sostituisci"));
            }
            cfg().set("lobby.mappa-installata", mk);
            cfg().set("mondi.costruiti." + Layout.W_HUB, true);
            plugin.saveConfig();
            plugin.getLogger().info("Lobby: mappa pronta (" + files + " file)" + (moved ? ", il vecchio mondo è in " + backup.getFileName() : "")
                    + ". Ora Paper la importa (\"legacy CraftBukkit import\"): può volerci qualche secondo.");
        } catch (Exception ex) {
            plugin.getLogger().severe("Lobby: installazione della mappa non riuscita (" + ex + "). Rimetto la lobby di prima.");
            try {
                deleteTree(tmp);
                if (moved && oldPath != null && !Files.exists(oldPath)) Files.move(backup, oldPath);
            } catch (IOException ex2) {
                plugin.getLogger().severe("Lobby: ripristino non riuscito, il vecchio mondo è in " + backup + ": " + ex2.getMessage());
            }
        }
    }

    private static int copyWorld(Path from, Path to) throws IOException {
        int[] n = {0};
        Files.createDirectories(to);
        for (String k : KEEP) {
            Path s = from.resolve(k.endsWith("/") ? k.substring(0, k.length() - 1) : k);
            if (!Files.exists(s)) continue;
            try (var w = Files.walk(s)) {
                for (Path p : w.toList()) {
                    Path out = to.resolve(from.relativize(p).toString());
                    if (Files.isDirectory(p)) Files.createDirectories(out);
                    else {
                        Files.createDirectories(out.getParent());
                        Files.copy(p, out, StandardCopyOption.REPLACE_EXISTING);
                        n[0]++;
                    }
                }
            }
        }
        return n[0];
    }

    private static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (var w = Files.walk(p)) {
            for (Path q : w.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(q);
        }
    }

    /** Dopo la creazione del mondo: sostituisce i blocchi indicati in pepita-lobby.yml (solo dopo un'installazione). */
    public void afterLoad(World w) {
        int n = 0;
        for (String line : pendingReplace) {
            String[] t = line.trim().split("\\s+", 4);
            if (t.length < 4) continue;
            try {
                w.getBlockAt(Integer.parseInt(t[0]), Integer.parseInt(t[1]), Integer.parseInt(t[2])).setBlockData(Bukkit.createBlockData(t[3]), false);
                n++;
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("pepita-lobby.yml, riga non valida: " + line);
            }
        }
        if (n > 0) plugin.getLogger().info("Lobby: sostituiti " + n + " blocchi.");
        pendingReplace.clear();
    }

    /** Cartella dello zip che contiene level.dat (la più vicina alla radice). */
    private static String findRoot(ZipFile zf) {
        String best = null;
        for (Enumeration<? extends ZipEntry> en = zf.entries(); en.hasMoreElements(); ) {
            String n = en.nextElement().getName().replace('\\', '/');
            if (!n.equals("level.dat") && !n.endsWith("/level.dat")) continue;
            String r = n.substring(0, n.length() - "level.dat".length());
            if (best == null || r.length() < best.length()) best = r;
        }
        return best;
    }

    private static int extract(ZipFile zf, String root, Path target) throws IOException {
        int n = 0;
        Files.createDirectories(target);
        Path base = target.toAbsolutePath().normalize();
        for (Enumeration<? extends ZipEntry> en = zf.entries(); en.hasMoreElements(); ) {
            ZipEntry e = en.nextElement();
            String name = e.getName().replace('\\', '/');
            if (!name.startsWith(root)) continue;
            String rel = name.substring(root.length());
            if (rel.isEmpty() || KEEP.stream().noneMatch(k -> k.endsWith("/") ? rel.startsWith(k) : rel.equals(k))) continue;
            Path out = base.resolve(rel).normalize();
            if (!out.startsWith(base)) continue; // percorsi fuori dalla cartella: ignorati
            if (e.isDirectory()) {
                Files.createDirectories(out);
                continue;
            }
            Files.createDirectories(out.getParent());
            try (InputStream in = zf.getInputStream(e)) {
                Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
            }
            n++;
        }
        return n;
    }

    private static YamlConfiguration readInfo(ZipFile zf) throws IOException {
        ZipEntry best = null;
        for (Enumeration<? extends ZipEntry> en = zf.entries(); en.hasMoreElements(); ) {
            ZipEntry e = en.nextElement();
            String n = e.getName().replace('\\', '/');
            if ((n.equals(INFO) || n.endsWith("/" + INFO)) && (best == null || n.length() < best.getName().length())) best = e;
        }
        if (best == null) return null;
        try (InputStream in = zf.getInputStream(best)) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    // =====================================================================
    //  Punti della lobby (config) con il ripiego sulla lobby costruita dal plugin
    // =====================================================================

    private double[] point(String key, double[] def) {
        List<Double> l = cfg().getDoubleList("lobby." + key);
        if (l.size() < 3) return def;
        return new double[]{l.get(0), l.get(1), l.get(2), l.size() > 3 ? l.get(3) : def[3]};
    }

    public double[] spawn() {
        return point("spawn", Layout.HUB_SPAWN);
    }

    public double[] npc() {
        return point("npc", Layout.HUB_NPC);
    }

    /** Sotto questa altezza si torna allo spawn della lobby. */
    public int voidY() {
        return cfg().getInt("lobby.vuoto-y", 40);
    }

    /** Salva un punto della lobby dalla posizione di un giocatore (comandi /pa lobby). */
    public void setPoint(String key, Location l) {
        double x = Math.floor(l.getX() * 2) / 2, y = Math.floor(l.getY() * 2) / 2, z = Math.floor(l.getZ() * 2) / 2;
        float yaw = Math.round(l.getYaw() / 15f) * 15f;
        cfg().set("lobby." + key, List.of(x, y, z, (double) yaw));
        plugin.saveConfig();
    }
}

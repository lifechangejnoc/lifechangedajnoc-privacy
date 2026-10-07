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

    private File zip() {
        String name = cfg().getString("lobby.mappa", "");
        return name.isBlank() ? null : new File(new File(plugin.getDataFolder(), "mappe"), name);
    }

    private static String marker(File f) {
        return f.getName() + ":" + f.length() + ":" + f.lastModified();
    }

    /**
     * Da chiamare prima di creare il mondo della lobby. Se c'è uno zip nuovo, lo installa.
     * @param createHub crea (o carica) il mondo pepita_hub come al solito
     */
    public void prepare(Supplier<World> createHub) {
        new File(plugin.getDataFolder(), "mappe").mkdirs();
        File z = zip();
        if (z == null || !z.isFile()) return;
        String mk = marker(z);
        if (mk.equals(cfg().getString("lobby.mappa-installata", ""))) return;
        plugin.getLogger().info("Nuova mappa per la lobby: " + z.getName() + ". Installazione...");
        try (ZipFile zf = new ZipFile(z)) {
            String root = findRoot(zf);
            if (root == null) {
                plugin.getLogger().severe("Nello zip " + z.getName() + " non c'è un level.dat: non è un mondo Minecraft. Lobby invariata.");
                return;
            }
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path container = Bukkit.getWorldContainer().toPath();
            // 1) il mondo della lobby attuale (anche appena creato e vuoto) va in backup: Paper non importa sopra file esistenti
            World old = createHub.get();
            Path oldPath = old.getWorldPath();
            if (!Bukkit.unloadWorld(old, true)) {
                plugin.getLogger().severe("Impossibile scaricare il mondo della lobby: mappa non installata.");
                return;
            }
            if (Files.exists(oldPath)) Files.move(oldPath, container.resolve(Layout.W_HUB + "_backup_" + stamp));
            Path legacy = container.resolve(Layout.W_HUB);
            if (Files.exists(legacy)) Files.move(legacy, container.resolve(Layout.W_HUB + "_backup_cartella_" + stamp));
            // 2) estrae la mappa come cartella "pepita_hub" (formato classico): Paper la importa alla creazione del mondo
            int files = extract(zf, root, legacy);
            // 3) punti della lobby e blocchi da sistemare
            YamlConfiguration info = readInfo(zf);
            if (info != null) {
                if (info.isList("spawn")) cfg().set("lobby.spawn", info.getDoubleList("spawn"));
                if (info.isList("npc")) cfg().set("lobby.npc", info.getDoubleList("npc"));
                if (info.contains("vuoto-y")) cfg().set("lobby.vuoto-y", info.getInt("vuoto-y"));
                pendingReplace.addAll(info.getStringList("sostituisci"));
            }
            cfg().set("lobby.mappa-installata", mk);
            cfg().set("mondi.costruiti." + Layout.W_HUB, true);
            plugin.saveConfig();
            plugin.getLogger().info("Mappa della lobby estratta (" + files + " file). Il vecchio mondo è in " + Layout.W_HUB + "_backup_" + stamp
                    + ". Paper ora la importa: può volerci qualche secondo.");
        } catch (IOException ex) {
            plugin.getLogger().severe("Installazione della mappa della lobby non riuscita: " + ex.getMessage());
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

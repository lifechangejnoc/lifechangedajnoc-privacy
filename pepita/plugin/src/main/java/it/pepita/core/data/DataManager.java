package it.pepita.core.data;

import it.pepita.core.PepitaCore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Carica, salva e classifica i dati dei giocatori (un file YAML per giocatore). */
public final class DataManager {
    private final PepitaCore plugin;
    private final File dir;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public record TopEntry(String name, double value, String extra) {}

    private volatile List<TopEntry> topSoldi = List.of();
    private volatile List<TopEntry> topBlocchi = List.of();
    private volatile List<TopEntry> topPrestigio = List.of();

    public DataManager(PepitaCore plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "giocatori");
        if (!dir.exists() && !dir.mkdirs()) plugin.getLogger().warning("Impossibile creare " + dir);
    }

    private File file(UUID u) {
        return new File(dir, u + ".yml");
    }

    public PlayerData get(Player p) {
        PlayerData d = cache.computeIfAbsent(p.getUniqueId(), u -> load(u, p.getName()));
        d.name = p.getName();
        return d;
    }

    public PlayerData cached(UUID u) {
        return cache.get(u);
    }

    public Collection<PlayerData> online() {
        return cache.values();
    }

    public boolean exists(UUID u) {
        return file(u).exists();
    }

    public PlayerData load(UUID u, String name) {
        PlayerData d = new PlayerData(u);
        d.name = name;
        File f = file(u);
        d.newData = !f.exists();
        if (f.exists()) {
            try {
                d.read(YamlConfiguration.loadConfiguration(f));
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Dati corrotti per " + u, e);
            }
        }
        return d;
    }

    /** Dati di un giocatore anche offline (per comandi admin / negozio web). */
    public PlayerData getAny(String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) return get(p);
        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
        if (op == null || !exists(op.getUniqueId())) return null;
        return load(op.getUniqueId(), op.getName() == null ? name : op.getName());
    }

    public void save(PlayerData d, boolean async) {
        final String content = d.write().saveToString();
        final File f = file(d.uuid);
        Runnable r = () -> {
            try {
                File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
                Files.writeString(tmp.toPath(), content, StandardCharsets.UTF_8);
                Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Salvataggio fallito per " + d.name, e);
            }
        };
        if (async && plugin.isEnabled()) Bukkit.getScheduler().runTaskAsynchronously(plugin, r);
        else r.run();
        d.dirty = false;
    }

    /** Salva un giocatore offline modificato e, se online, aggiorna la cache. */
    public void saveAny(PlayerData d) {
        save(d, true);
    }

    public void unload(UUID u) {
        PlayerData d = cache.remove(u);
        if (d != null) save(d, true);
    }

    public void saveAll(boolean async) {
        for (PlayerData d : cache.values()) save(d, async);
    }

    // ---------------- Classifiche ----------------

    public List<TopEntry> topSoldi() { return topSoldi; }
    public List<TopEntry> topBlocchi() { return topBlocchi; }
    public List<TopEntry> topPrestigio() { return topPrestigio; }

    /** Ricalcola le classifiche leggendo i file in asincrono. */
    public void refreshTops() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<UUID, double[]> vals = new HashMap<>();
            Map<UUID, String> names = new HashMap<>();
            File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
            if (files != null) {
                for (File f : files) {
                    try {
                        UUID u = UUID.fromString(f.getName().substring(0, f.getName().length() - 4));
                        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
                        names.put(u, y.getString("nome", "?"));
                        vals.put(u, new double[]{y.getDouble("soldi"), y.getLong("blocchi"),
                                y.getInt("evasioni") * 1_000_000.0 + y.getInt("prestigio") * 100.0 + y.getInt("rank"),
                                y.getInt("evasioni"), y.getInt("prestigio"), y.getInt("rank")});
                    } catch (Exception ignored) {
                    }
                }
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (PlayerData d : cache.values()) {
                    names.put(d.uuid, d.name);
                    vals.put(d.uuid, new double[]{d.soldi, d.blocchi,
                            d.evasioni * 1_000_000.0 + d.prestige * 100.0 + d.rank, d.evasioni, d.prestige, d.rank});
                }
                topSoldi = build(vals, names, 0, v -> "");
                topBlocchi = build(vals, names, 1, v -> "");
                topPrestigio = build(vals, names, 2, v -> "E" + (int) v[3] + " P" + (int) v[4] + " " + (char) ('A' + (int) v[5]));
            });
        });
    }

    private interface Extra { String of(double[] v); }

    private static List<TopEntry> build(Map<UUID, double[]> vals, Map<UUID, String> names, int idx, Extra ex) {
        List<Map.Entry<UUID, double[]>> list = new ArrayList<>(vals.entrySet());
        list.sort(Comparator.comparingDouble((Map.Entry<UUID, double[]> e) -> e.getValue()[idx]).reversed());
        List<TopEntry> out = new ArrayList<>();
        for (int i = 0; i < Math.min(10, list.size()); i++) {
            Map.Entry<UUID, double[]> e = list.get(i);
            out.add(new TopEntry(names.getOrDefault(e.getKey(), "?"), e.getValue()[idx], ex.of(e.getValue())));
        }
        return out;
    }
}

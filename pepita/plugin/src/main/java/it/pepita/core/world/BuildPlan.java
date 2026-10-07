package it.pepita.core.world;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Insieme di blocchi da piazzare. I blocchi sono salvati come stringhe ("minecraft:oak_stairs[facing=north]")
 * così il piano si può costruire anche fuori dal server (anteprime offline) e piazzare poi a lotti.
 */
public final class BuildPlan {
    public static final String AIR = "minecraft:air";
    private final LinkedHashMap<Long, String> blocks = new LinkedHashMap<>();
    private final Map<String, String> intern = new HashMap<>();

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    public static int kx(long k) { return (int) (k >> 38); }
    public static int kz(long k) { return (int) (k << 26 >> 38); }
    public static int ky(long k) { return (int) (k << 52 >> 52); }

    private String in(String s) {
        if (!s.contains(":")) s = "minecraft:" + s;
        return intern.computeIfAbsent(s, v -> v);
    }

    public void set(int x, int y, int z, Material m) {
        blocks.put(key(x, y, z), in(m.getKey().toString()));
    }

    public void set(int x, int y, int z, String blockData) {
        blocks.put(key(x, y, z), in(blockData));
    }

    /** Piazza solo se la posizione è ancora libera nel piano. */
    public void setIfAbsent(int x, int y, int z, String blockData) {
        blocks.putIfAbsent(key(x, y, z), in(blockData));
    }

    public void setIfAbsent(int x, int y, int z, Material m) {
        blocks.putIfAbsent(key(x, y, z), in(m.getKey().toString()));
    }

    /** Forza aria (svuota) nella posizione. */
    public void air(int x, int y, int z) {
        blocks.put(key(x, y, z), AIR);
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, String b) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, b);
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        fill(x1, y1, z1, x2, y2, z2, m.getKey().toString());
    }

    public String get(int x, int y, int z) {
        return blocks.get(key(x, y, z));
    }

    public boolean has(int x, int y, int z) {
        return blocks.containsKey(key(x, y, z));
    }

    /** Toglie la posizione dal piano (non verrà toccata). */
    public void remove(int x, int y, int z) {
        blocks.remove(key(x, y, z));
    }

    public int size() {
        return blocks.size();
    }

    public void forEach(BiConsumer<Long, String> c) {
        blocks.forEach(c);
    }

    /** Esporta il piano in un file di testo "x y z blocco" (per le anteprime). */
    public void export(File f) throws IOException {
        try (BufferedWriter w = new BufferedWriter(new FileWriter(f))) {
            for (Map.Entry<Long, String> e : blocks.entrySet()) {
                long k = e.getKey();
                w.write(kx(k) + " " + ky(k) + " " + kz(k) + " " + e.getValue());
                w.newLine();
            }
        }
    }

    /** Piazza i blocchi a lotti, ordinati per chunk. */
    public void place(Plugin plugin, World w, int perTick, Runnable done) {
        final long[] keys = new long[blocks.size()];
        final String[] datas = new String[blocks.size()];
        int i = 0;
        for (Map.Entry<Long, String> e : blocks.entrySet()) {
            keys[i] = e.getKey();
            datas[i] = e.getValue();
            i++;
        }
        Integer[] order = new Integer[keys.length];
        for (int j = 0; j < order.length; j++) order[j] = j;
        Arrays.sort(order, (a, b) -> Long.compare(chunkKey(keys[a]), chunkKey(keys[b])));
        final Map<String, BlockData> cache = new HashMap<>();
        new BukkitRunnable() {
            int idx = 0;

            @Override
            public void run() {
                int end = Math.min(order.length, idx + perTick);
                for (; idx < end; idx++) {
                    int o = order[idx];
                    long k = keys[o];
                    BlockData bd = cache.computeIfAbsent(datas[o], s -> {
                        try {
                            return Bukkit.createBlockData(s);
                        } catch (IllegalArgumentException ex) {
                            plugin.getLogger().warning("Blocco non valido nel piano: " + s);
                            return Material.STONE.createBlockData();
                        }
                    });
                    w.getBlockAt(kx(k), ky(k), kz(k)).setBlockData(bd, false);
                }
                if (idx >= order.length) {
                    cancel();
                    if (done != null) done.run();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static long chunkKey(long k) {
        return ((long) (kx(k) >> 4) << 32) ^ ((kz(k) >> 4) & 0xFFFFFFFFL);
    }
}

package it.pepita.core.world.build;

import it.pepita.core.mine.Mine;
import it.pepita.core.world.BuildPlan;
import org.bukkit.Material;

import static it.pepita.core.world.build.BuildUtil.noise;

/** Isola e struttura di una miniera (mondo pepita_miniere). */
public final class MineBuild {
    private MineBuild() {}

    record Theme(Material wall, Material wall2, Material rim, Material accent, String rail, Material light) {}

    static Theme theme(String t) {
        return switch (t) {
            case "ardesia" -> new Theme(Material.DEEPSLATE_BRICKS, Material.CRACKED_DEEPSLATE_BRICKS, Material.POLISHED_DEEPSLATE, Material.DEEPSLATE_TILES, "minecraft:deepslate_brick_wall", Material.SEA_LANTERN);
            case "nera" -> new Theme(Material.POLISHED_BLACKSTONE_BRICKS, Material.CRACKED_POLISHED_BLACKSTONE_BRICKS, Material.POLISHED_BLACKSTONE, Material.GILDED_BLACKSTONE, "minecraft:polished_blackstone_brick_wall", Material.SHROOMLIGHT);
            case "prismarino" -> new Theme(Material.PRISMARINE_BRICKS, Material.PRISMARINE, Material.DARK_PRISMARINE, Material.SEA_LANTERN, "minecraft:prismarine_wall", Material.SEA_LANTERN);
            case "end" -> new Theme(Material.END_STONE_BRICKS, Material.END_STONE, Material.PURPUR_BLOCK, Material.PURPUR_PILLAR, "minecraft:end_stone_brick_wall", Material.END_ROD);
            case "prestigio" -> new Theme(Material.PURPUR_BLOCK, Material.PURPUR_PILLAR, Material.POLISHED_BLACKSTONE_BRICKS, Material.AMETHYST_BLOCK, "minecraft:polished_blackstone_brick_wall", Material.SEA_LANTERN);
            case "vip" -> new Theme(Material.GOLD_BLOCK, Material.RAW_GOLD_BLOCK, Material.SMOOTH_QUARTZ, Material.EMERALD_BLOCK, "minecraft:polished_blackstone_wall", Material.SEA_LANTERN);
            case "pvp" -> new Theme(Material.RED_NETHER_BRICKS, Material.NETHER_BRICKS, Material.NETHER_BRICKS, Material.MAGMA_BLOCK, "minecraft:red_nether_brick_wall", Material.SHROOMLIGHT);
            case "evasione" -> new Theme(Material.OBSIDIAN, Material.CRYING_OBSIDIAN, Material.POLISHED_BLACKSTONE, Material.CRYING_OBSIDIAN, "minecraft:blackstone_wall", Material.SOUL_LANTERN);
            default -> new Theme(Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.POLISHED_ANDESITE, Material.CHISELED_STONE_BRICKS, "minecraft:stone_brick_wall", Material.SEA_LANTERN);
        };
    }

    public static void build(BuildPlan p, Mine m) {
        Theme th = theme(m.theme);
        int x1 = m.minX - 1, x2 = m.maxX + 1, z1 = m.minZ - 1, z2 = m.maxZ + 1;
        int bottom = m.minY - 1, top = m.maxY + 1; // top = livello del bordo
        // fondo e pareti
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                p.set(x, bottom, z, th.wall);
                for (int y = bottom; y <= m.maxY; y++) {
                    boolean side = x == x1 || x == x2 || z == z1 || z == z2;
                    if (side) p.set(x, y, z, noise(x, y, z, 31) < 0.8 ? th.wall : th.wall2);
                    else if (y >= m.minY) p.remove(x, y, z);
                }
            }
        // bordo calpestabile
        int ext = 5;
        for (int x = x1 - ext + 1; x <= x2 + ext - 1; x++)
            for (int z = z1 - ext + 1; z <= z2 + ext - 1; z++) {
                boolean inside = x > x1 && x < x2 && z > z1 && z < z2;
                if (inside) continue;
                boolean ring = x == x1 || x == x2 || z == z1 || z == z2;
                p.set(x, top, z, ring ? th.accent : th.rim);
                // isola sotto il bordo
                int dist = Math.min(Math.min(x - (x1 - ext + 1), (x2 + ext - 1) - x), Math.min(z - (z1 - ext + 1), (z2 + ext - 1) - z));
                int depth = 3 + dist * 2 + (int) (noise(x, 0, z, 41) * 3);
                for (int y = top - 1; y >= Math.max(bottom - 8, top - depth); y--) {
                    if (p.has(x, y, z)) continue;
                    double n = noise(x, y, z, 43);
                    p.set(x, y, z, n < 0.55 ? Material.STONE : n < 0.8 ? Material.ANDESITE : n < 0.95 ? Material.TUFF : Material.GOLD_ORE);
                }
            }
        // sotto la miniera
        for (int y = bottom - 1; y >= bottom - 8; y--) {
            int shrink = (bottom - y) * 2;
            for (int x = x1 + shrink; x <= x2 - shrink; x++)
                for (int z = z1 + shrink; z <= z2 - shrink; z++) {
                    double n = noise(x, y, z, 47);
                    p.set(x, y, z, n < 0.6 ? Material.STONE : n < 0.85 ? Material.DEEPSLATE : Material.COAL_ORE);
                }
        }
        // ringhiera e lampioni agli angoli
        int rx1 = x1 - ext + 1, rx2 = x2 + ext - 1, rz1 = z1 - ext + 1, rz2 = z2 + ext - 1;
        for (int x = rx1; x <= rx2; x++)
            for (int z = rz1; z <= rz2; z++) {
                boolean edge = x == rx1 || x == rx2 || z == rz1 || z == rz2;
                if (!edge) continue;
                boolean corner = (x == rx1 || x == rx2) && (z == rz1 || z == rz2);
                p.set(x, top + 1, z, th.rail);
                if (corner) {
                    p.set(x, top + 2, z, th.rail);
                    p.set(x, top + 3, z, th.light == Material.END_ROD ? "minecraft:end_rod[facing=up]" : th.light == Material.SOUL_LANTERN ? "minecraft:soul_lantern[hanging=false]" : th.light.getKey().toString());
                    if (th.light != Material.END_ROD && th.light != Material.SOUL_LANTERN) p.set(x, top + 4, z, "minecraft:lantern[hanging=false]");
                }
            }
        // pedana di arrivo
        int sx = (int) Math.floor(m.spawnX), sz = (int) Math.floor(m.spawnZ);
        for (int x = sx - 1; x <= sx + 1; x++) for (int z = sz - 1; z <= sz + 1; z++) p.set(x, top, z, th.accent);
        p.set(sx, top, sz, Material.GOLD_BLOCK);
        // pilastro dell'insegna sul lato nord
        int lx = (int) Math.floor(m.labelX), lz = (int) Math.floor(m.labelZ);
        for (int y = top + 1; y <= top + 3; y++) p.set(lx, y, lz, th.wall);
        p.set(lx, top + 4, lz, m.icon);
    }
}

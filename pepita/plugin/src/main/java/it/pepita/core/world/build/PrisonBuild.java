package it.pepita.core.world.build;

import it.pepita.core.crate.Crate;
import it.pepita.core.world.BlockFont;
import it.pepita.core.world.BuildPlan;
import it.pepita.core.world.Layout;
import org.bukkit.Material;

import java.util.Random;

import static it.pepita.core.world.build.BuildUtil.noise;

/** Spawn della prigione (mondo "pepita"). */
public final class PrisonBuild {
    private PrisonBuild() {}

    public static void build(BuildPlan p) {
        Random r = new Random(1337);
        final int R = 33;

        // --- Isola galleggiante sotto lo spawn (solo il guscio esterno) ---
        for (int y = 98; y >= 78; y--) {
            double t = (y - 77) / 21.0;
            double rad = 36 * Math.pow(t, 0.55);
            int ri = (int) Math.ceil(rad + 2);
            for (int x = -ri; x <= ri; x++)
                for (int z = -ri; z <= ri; z++) {
                    double d = Math.sqrt(x * x + z * z) + (noise(x, y, z, 7) - 0.5) * 3;
                    if (d > rad) continue;
                    if (d < rad - 2.5 && y < 97) continue; // interno vuoto
                    double n = noise(x, y, z, 11);
                    Material m;
                    if (y >= 96) m = n < 0.5 ? Material.STONE : n < 0.8 ? Material.ANDESITE : Material.COBBLESTONE;
                    else if (y >= 88) m = n < 0.45 ? Material.STONE : n < 0.7 ? Material.TUFF : n < 0.9 ? Material.ANDESITE : Material.DEEPSLATE;
                    else m = n < 0.5 ? Material.DEEPSLATE : n < 0.8 ? Material.TUFF : Material.COBBLED_DEEPSLATE;
                    double o = noise(x, y, z, 23);
                    if (o < 0.035) m = y < 90 ? Material.DEEPSLATE_GOLD_ORE : Material.GOLD_ORE;
                    else if (o < 0.045) m = Material.RAW_GOLD_BLOCK;
                    else if (o < 0.065) m = Material.COAL_ORE;
                    p.set(x, y, z, m);
                }
        }

        // --- Pavimento della piazza (y=99) ---
        for (int x = -R - 3; x <= R + 3; x++)
            for (int z = -R - 3; z <= R + 3; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > R + 2.5) continue;
                Material m;
                boolean path = (Math.abs(x) <= 2 || Math.abs(z) <= 2) && d > 9.5;
                if (d <= 4.5) m = Material.POLISHED_BLACKSTONE_BRICKS;
                else if (d <= 5.5) m = Material.GOLD_BLOCK;
                else if (d <= 8.5) {
                    p.set(x, 98, z, Material.DARK_PRISMARINE);
                    p.set(x, 99, z, Material.WATER);
                    continue;
                } else if (d <= 9.6) m = Material.POLISHED_DEEPSLATE;
                else if (path) {
                    int a = Math.abs(x) <= 2 && Math.abs(z) > 2 ? Math.abs(x) : Math.abs(z) <= 2 && Math.abs(x) > 2 ? Math.abs(z) : 0;
                    m = a == 0 ? Material.GOLD_BLOCK : a == 1 ? Material.POLISHED_ANDESITE : Material.POLISHED_BLACKSTONE_BRICKS;
                } else if (d > 19.5 && d <= 20.6) m = Material.POLISHED_BLACKSTONE_BRICKS;
                else {
                    double n = noise(x, 99, z, 3);
                    m = n < 0.45 ? Material.STONE_BRICKS : n < 0.75 ? Material.POLISHED_ANDESITE : n < 0.9 ? Material.CRACKED_STONE_BRICKS : Material.MOSSY_STONE_BRICKS;
                }
                p.set(x, 99, z, m);
            }

        // --- Muro della prigione ---
        for (int x = -R - 3; x <= R + 3; x++)
            for (int z = -R - 3; z <= R + 3; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d <= R + 0.5 || d > R + 2.5) continue;
                double ang = Math.toDegrees(Math.atan2(z, x));
                boolean window = Math.abs(((ang + 360) % 15) - 7.5) < 2.2;
                for (int y = 99; y <= 106; y++) {
                    double n = noise(x, y, z, 5);
                    Material m = n < 0.6 ? Material.STONE_BRICKS : n < 0.8 ? Material.CRACKED_STONE_BRICKS : Material.MOSSY_STONE_BRICKS;
                    if (y == 106) {
                        if (((x + z) & 1) != 0) continue;
                        m = Material.STONE_BRICKS;
                    }
                    if (window && (y == 102 || y == 103)) {
                        p.set(x, y, z, "minecraft:iron_bars");
                        continue;
                    }
                    if (y == 105) m = Material.CHISELED_STONE_BRICKS;
                    p.set(x, y, z, m);
                }
            }

        // --- Torri di guardia agli angoli ---
        int[][] towers = {{24, 24}, {-24, 24}, {24, -24}, {-24, -24}};
        for (int[] t : towers) {
            for (int x = -6; x <= 6; x++)
                for (int z = -6; z <= 6; z++) {
                    double d = Math.sqrt(x * x + z * z);
                    int wx = t[0] + x, wz = t[1] + z;
                    if (d <= 4.5) {
                        for (int y = 99; y <= 113; y++) {
                            boolean shell = d > 3.3;
                            Material m = shell ? (noise(wx, y, wz, 9) < 0.7 ? Material.DEEPSLATE_BRICKS : Material.CRACKED_DEEPSLATE_BRICKS) : Material.AIR;
                            if (shell && (y == 106 || y == 107) && (Math.abs(x) == 4 && z == 0 || Math.abs(z) == 4 && x == 0))
                                p.set(wx, y, wz, "minecraft:iron_bars");
                            else if (shell) p.set(wx, y, wz, m);
                            else if (y == 99 || y == 113) p.set(wx, y, wz, Material.POLISHED_DEEPSLATE);
                            else p.remove(wx, y, wz);
                        }
                    }
                    if (d <= 5.6) p.set(wx, 114, wz, Material.POLISHED_DEEPSLATE);
                    if (d > 4.6 && d <= 5.6) p.set(wx, 115, wz, "minecraft:deepslate_brick_wall");
                }
            p.set(t[0], 115, t[1], Material.SEA_LANTERN);
            p.set(t[0], 116, t[1], Material.GOLD_BLOCK);
            p.set(t[0], 117, t[1], "minecraft:lightning_rod");
        }

        // --- Monumento: la Pepita Gigante ---
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++)
                for (int y = 100; y <= 102; y++) {
                    boolean corner = Math.abs(x) == 2 && Math.abs(z) == 2;
                    p.set(x, y, z, corner ? Material.CHISELED_POLISHED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS);
                }
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) p.set(x, 103, z, Material.GOLD_BLOCK);
        for (int x = -3; x <= 3; x++)
            for (int z = -3; z <= 3; z++)
                if (Math.abs(x) == 3 || Math.abs(z) == 3) p.set(x, 100, z, "minecraft:polished_blackstone_brick_stairs[facing=" +
                        (Math.abs(x) == 3 && Math.abs(z) == 3 ? (x > 0 ? "west" : "east") : Math.abs(x) == 3 ? (x > 0 ? "west" : "east") : (z > 0 ? "north" : "south")) + "]");
        nugget(p, 0, 108, 0, 4.2, 3.8, 4.2, 101);
        nugget(p, 6, 112, -3, 1.3, 1.2, 1.3, 202);
        nugget(p, -6, 111, 3, 1.4, 1.2, 1.4, 303);
        nugget(p, 2, 113, 6, 1.1, 1.0, 1.1, 404);

        // --- Scritta PEPITA sopra il monumento ---
        String word = "PEPITA";
        int w = BlockFont.width(word);
        int x0 = -w / 2;
        int yTop = 122;
        for (int i = 0; i < word.length(); i++) {
            String[] g = BlockFont.glyph(word.charAt(i));
            for (int row = 0; row < 7; row++)
                for (int col = 0; col < 5; col++)
                    if (g[row].charAt(col) == '#') {
                        // si legge dallo spawn (sud): guardando a nord l'est è a destra
                        int bx = x0 + i * 6 + col;
                        p.set(bx, yTop - row, 1, Material.GOLD_BLOCK);
                        p.set(bx, yTop - row, 0, Material.RAW_GOLD_BLOCK);
                    }
        }

        // --- Portale delle miniere (nord) ---
        int pz = Layout.PRISON_PORTAL_Z;
        for (int x = -4; x <= 4; x++)
            for (int y = 99; y <= 107; y++) {
                boolean frame = Math.abs(x) == 3 || y == 99 || y == 105;
                boolean arch = Math.abs(x) == 4 || y >= 106;
                if (Math.abs(x) <= 2 && y >= 100 && y <= 104) p.set(x, y, pz, "minecraft:nether_portal[axis=x]");
                else if (frame && Math.abs(x) <= 3 && y <= 105) p.set(x, y, pz, Material.CRYING_OBSIDIAN);
                else if (arch) {
                    if (y == 107 && Math.abs(x) == 4) continue;
                    p.set(x, y, pz, y == 107 ? Material.GOLD_BLOCK : Material.POLISHED_BLACKSTONE_BRICKS);
                }
            }
        p.set(0, 107, pz, Material.RAW_GOLD_BLOCK);
        for (int x = -4; x <= 4; x++) p.set(x, 99, pz + 1, Material.POLISHED_BLACKSTONE_BRICKS);

        // --- Casse (est) ---
        Crate[] cs = Crate.values();
        for (int i = 0; i < cs.length; i++) {
            int[] c = Layout.PRISON_CRATES[i];
            String bd = switch (cs[i]) {
                case COMUNE -> "minecraft:chest[facing=west]";
                case RARA -> "minecraft:ender_chest[facing=west]";
                case LEGGENDARIA -> "minecraft:purple_shulker_box[facing=up]";
                case PEPITA -> "minecraft:yellow_shulker_box[facing=up]";
            };
            p.set(c[0], 99, c[2], Material.GOLD_BLOCK);
            p.set(c[0], c[1], c[2], bd);
        }
        for (int z = -9; z <= 9; z++) {
            for (int y = 100; y <= 103; y++) p.set(25, y, z, y == 103 ? Material.GOLD_BLOCK : Material.POLISHED_BLACKSTONE_BRICKS);
            p.set(25, 104, z, (z & 1) == 0 ? "minecraft:polished_blackstone_brick_wall" : "minecraft:air");
            for (int x = 20; x <= 24; x++) p.set(x, 99, z, Material.POLISHED_DEEPSLATE);
        }
        for (int[] c : Layout.PRISON_CRATES) p.set(c[0], 99, c[2], Material.GOLD_BLOCK);
        for (int z : new int[]{-4, 0, 4}) {
            p.set(22, 100, z, "minecraft:polished_blackstone_wall");
            p.set(22, 101, z, "minecraft:lantern[hanging=false]");
        }

        // --- Classifiche (ovest) ---
        Material[] boards = {Material.GOLD_BLOCK, Material.DIAMOND_BLOCK, Material.AMETHYST_BLOCK};
        for (int i = 0; i < Layout.PRISON_BOARDS.length; i++) {
            int[] b = Layout.PRISON_BOARDS[i];
            p.set(b[0], 100, b[2], boards[i]);
            p.set(b[0], 101, b[2], "minecraft:polished_blackstone_wall");
        }
        for (int z = -11; z <= 11; z++) {
            for (int y = 100; y <= 103; y++) p.set(-25, y, z, y == 103 ? Material.GOLD_BLOCK : Material.POLISHED_BLACKSTONE_BRICKS);
            p.set(-25, 104, z, (z & 1) == 0 ? "minecraft:polished_blackstone_brick_wall" : "minecraft:air");
        }

        // --- Banco incantesimi, negozio, giornaliero ---
        p.set(Layout.PRISON_ENCHANT[0], 99, Layout.PRISON_ENCHANT[2], Material.AMETHYST_BLOCK);
        p.set(Layout.PRISON_ENCHANT[0], Layout.PRISON_ENCHANT[1], Layout.PRISON_ENCHANT[2], Material.ENCHANTING_TABLE);
        p.set(Layout.PRISON_SHOP[0], 99, Layout.PRISON_SHOP[2], Material.AMETHYST_BLOCK);
        p.set(Layout.PRISON_SHOP[0], Layout.PRISON_SHOP[1], Layout.PRISON_SHOP[2], Material.EMERALD_BLOCK);
        p.set(Layout.PRISON_DAILY[0], 99, Layout.PRISON_DAILY[2], Material.GOLD_BLOCK);
        p.set(Layout.PRISON_DAILY[0], Layout.PRISON_DAILY[1], Layout.PRISON_DAILY[2], "minecraft:barrel[facing=up]");

        // --- Lampioni ---
        for (int a = 0; a < 360; a += 30) {
            if (a % 90 == 0) continue;
            for (double rad : new double[]{14.5, 27.5}) {
                int x = (int) Math.round(Math.cos(Math.toRadians(a)) * rad);
                int z = (int) Math.round(Math.sin(Math.toRadians(a)) * rad);
                if (Math.abs(x) <= 3 || Math.abs(z) <= 3) continue;
                if (x >= 19 && Math.abs(z) <= 10) continue;
                if (x <= -19 && Math.abs(z) <= 12) continue;
                p.set(x, 100, z, "minecraft:polished_blackstone_wall");
                p.set(x, 101, z, "minecraft:polished_blackstone_wall");
                p.set(x, 102, z, "minecraft:lantern[hanging=false]");
            }
        }

        // --- Alberi di ciliegio nelle aiuole ---
        int[][] trees = {{12, 12}, {-12, 12}, {12, -12}, {-12, -12}};
        for (int[] t : trees) tree(p, t[0], t[1], r);
    }

    static void nugget(BuildPlan p, int cx, int cy, int cz, double rx, double ry, double rz, long seed) {
        int ix = (int) Math.ceil(rx + 1), iy = (int) Math.ceil(ry + 1), iz = (int) Math.ceil(rz + 1);
        for (int x = -ix; x <= ix; x++)
            for (int y = -iy; y <= iy; y++)
                for (int z = -iz; z <= iz; z++) {
                    double v = (x / rx) * (x / rx) + (y / ry) * (y / ry) + (z / rz) * (z / rz);
                    double bump = (noise(x, y, z, seed) - 0.5) * 0.55;
                    if (v > 1 + bump) continue;
                    double n = noise(x, y, z, seed + 1);
                    Material m = n < 0.62 ? Material.RAW_GOLD_BLOCK : Material.GOLD_BLOCK;
                    p.set(cx + x, cy + y, cz + z, m);
                }
    }

    static void tree(BuildPlan p, int tx, int tz, Random r) {
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++) {
                boolean edge = Math.abs(x) == 2 || Math.abs(z) == 2;
                p.set(tx + x, 99, tz + z, edge ? Material.POLISHED_BLACKSTONE_BRICKS : Material.MOSS_BLOCK);
                if (edge) p.set(tx + x, 100, tz + z, "minecraft:polished_blackstone_brick_slab[type=bottom]");
                else if (r.nextInt(3) == 0 && !(x == 0 && z == 0)) p.set(tx + x, 100, tz + z, r.nextBoolean() ? "minecraft:pink_petals[flower_amount=3,facing=north]" : "minecraft:short_grass");
            }
        for (int y = 100; y <= 104; y++) p.set(tx, y, tz, "minecraft:cherry_log[axis=y]");
        String leaves = "minecraft:cherry_leaves[persistent=true]";
        for (int x = -3; x <= 3; x++)
            for (int y = -2; y <= 2; y++)
                for (int z = -3; z <= 3; z++) {
                    double d = x * x / 9.0 + y * y / 4.0 + z * z / 9.0;
                    if (d <= 1.0 + (noise(tx + x, y, tz + z, 77) - 0.5) * 0.4 && !(x == 0 && z == 0 && y < 1))
                        p.set(tx + x, 106 + y, tz + z, leaves);
                }
        p.set(tx, 105, tz, "minecraft:cherry_log[axis=y]");
    }

}

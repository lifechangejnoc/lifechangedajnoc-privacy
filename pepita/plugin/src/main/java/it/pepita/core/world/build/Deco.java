package it.pepita.core.world.build;

import it.pepita.core.world.BuildPlan;

import static it.pepita.core.world.build.BuildUtil.noise;

/** Elementi decorativi riutilizzabili: alberi organici, lampioni, panchine, aiuole, bracieri, colonne. */
public final class Deco {
    private Deco() {}

    public enum TreeType { CHERRY, OAK, AZALEA, BIRCH, CYPRESS, DARK_OAK, OLIVE, SPRUCE, PALE }

    record TreeMats(String log, String wood, String leaves, String leaves2, double leaves2Chance) {}

    static TreeMats mats(TreeType t) {
        return switch (t) {
            case CHERRY -> new TreeMats("cherry_log", "cherry_wood", "cherry_leaves", "cherry_leaves", 0);
            case AZALEA -> new TreeMats("oak_log", "oak_wood", "azalea_leaves", "flowering_azalea_leaves", 0.35);
            case BIRCH -> new TreeMats("birch_log", "birch_wood", "birch_leaves", "birch_leaves", 0);
            case CYPRESS -> new TreeMats("spruce_log", "spruce_wood", "spruce_leaves", "dark_oak_leaves", 0.25);
            case DARK_OAK -> new TreeMats("dark_oak_log", "dark_oak_wood", "dark_oak_leaves", "oak_leaves", 0.2);
            case OLIVE -> new TreeMats("jungle_log", "jungle_wood", "azalea_leaves", "birch_leaves", 0.4);
            case SPRUCE -> new TreeMats("spruce_log", "spruce_wood", "spruce_leaves", "spruce_leaves", 0);
            case PALE -> new TreeMats("pale_oak_log", "pale_oak_wood", "pale_oak_leaves", "pale_oak_leaves", 0);
            default -> new TreeMats("oak_log", "oak_wood", "oak_leaves", "azalea_leaves", 0.12);
        };
    }

    /**
     * Albero organico con la base del tronco in (x, y, z) (y = primo blocco sopra il terreno).
     * size ~ 1 = albero medio (alto 8-10).
     */
    public static void tree(BuildPlan p, int x, int y, int z, TreeType type, double size, long seed) {
        TreeMats m = mats(type);
        if (type == TreeType.CYPRESS) { cypress(p, x, y, z, size, seed, m); return; }
        if (type == TreeType.SPRUCE) { spruce(p, x, y, z, size, seed, m); return; }
        int h = (int) Math.round((type == TreeType.CHERRY ? 5 : type == TreeType.OLIVE ? 3 : 6) * size + noise(x, y, z, seed) * 2);
        double lx = (noise(x, 1, z, seed) - 0.5) * 0.5, lz = (noise(x, 2, z, seed) - 0.5) * 0.5;
        double cx = x, cz = z;
        for (int k = 0; k < h; k++) {
            int bx = (int) Math.round(cx), bz = (int) Math.round(cz);
            p.set(bx, y + k, bz, "minecraft:" + (k == 0 ? m.wood : m.log) + (k == 0 ? "" : "[axis=y]"));
            if (k > 1) { cx += lx; cz += lz; }
        }
        // radici
        if (size >= 0.9)
            for (String f : BuildUtil.CARD) {
                if (noise(x, 3, z, seed + f.hashCode()) < 0.5) continue;
                int[] v = BuildUtil.vec(f);
                p.set(x + v[0], y, z + v[1], "minecraft:" + m.log + "[axis=" + (v[0] != 0 ? "x" : "z") + "]");
            }
        int tx = (int) Math.round(cx), tz = (int) Math.round(cz), ty = y + h - 1;
        int branches = 2 + (int) (noise(x, 4, z, seed) * 3);
        double a0 = noise(x, 5, z, seed) * Math.PI * 2;
        double spread = type == TreeType.CHERRY ? 3.6 : type == TreeType.OLIVE ? 2.6 : 2.4;
        double rise = type == TreeType.CHERRY ? 2.0 : 2.6;
        java.util.List<double[]> ends = new java.util.ArrayList<>();
        for (int b = 0; b < branches; b++) {
            double a = a0 + b * Math.PI * 2 / branches + (noise(b, 6, z, seed) - 0.5) * 0.8;
            double len = spread * size * (0.8 + noise(b, 7, x, seed) * 0.5);
            double ex = tx + Math.cos(a) * len, ez = tz + Math.sin(a) * len, ey = ty + rise * size * (0.7 + noise(b, 8, x, seed) * 0.6);
            int steps = (int) Math.ceil(Math.max(len, ey - ty)) + 1;
            for (int s = 1; s <= steps; s++) {
                double f = s / (double) steps;
                p.set((int) Math.round(tx + (ex - tx) * f), (int) Math.round(ty + (ey - ty) * f), (int) Math.round(tz + (ez - tz) * f), "minecraft:" + m.wood);
            }
            ends.add(new double[]{ex, ey, ez});
        }
        ends.add(new double[]{tx, ty + rise * size, tz});
        double rx = (type == TreeType.CHERRY ? 3.4 : type == TreeType.OLIVE ? 2.8 : 2.7) * size;
        double ry = (type == TreeType.CHERRY ? 1.9 : type == TreeType.OLIVE ? 1.7 : 2.4) * size;
        for (double[] e : ends) blob(p, e[0], e[1] + 0.5, e[2], rx, ry, m, seed, type);
        // petali / foglie a terra
        if (type == TreeType.CHERRY) {
            int r = (int) Math.ceil(rx + 2);
            for (int ax = -r; ax <= r; ax++)
                for (int az = -r; az <= r; az++) {
                    if (ax * ax + az * az > r * r) continue;
                    if (p.has(tx + ax, y, tz + az)) continue;
                    String g = p.get(tx + ax, y - 1, tz + az);
                    if (g == null || !(g.contains("grass_block") || g.contains("moss_block") || g.contains("podzol"))) continue;
                    double n = noise(tx + ax, y, tz + az, seed + 9);
                    if (n < 0.45)
                        p.set(tx + ax, y, tz + az, "minecraft:pink_petals[facing=" + BuildUtil.CARD[(int) (n * 40) & 3] + ",flower_amount=" + (1 + (int) (n * 8) % 4) + "]");
                }
        }
    }

    static void blob(BuildPlan p, double cx, double cy, double cz, double rx, double ry, TreeMats m, long seed, TreeType type) {
        int ix = (int) Math.ceil(rx + 1), iy = (int) Math.ceil(ry + 1);
        for (int ax = -ix; ax <= ix; ax++)
            for (int ay = -iy; ay <= iy; ay++)
                for (int az = -ix; az <= ix; az++) {
                    int bx = (int) Math.round(cx) + ax, by = (int) Math.round(cy) + ay, bz = (int) Math.round(cz) + az;
                    double dx = bx - cx, dy = by - cy, dz = bz - cz;
                    double d = dx * dx / (rx * rx) + dy * dy / (ry * ry) + dz * dz / (rx * rx);
                    double nn = noise(bx, by, bz, seed + 3);
                    if (d > 1 + (nn - 0.5) * 0.55) continue;
                    if (d > 0.55 && nn < 0.12) continue;
                    if (p.has(bx, by, bz) && !p.get(bx, by, bz).contains("leaves")) continue;
                    String l = noise(bx, by, bz, seed + 4) < m.leaves2Chance ? m.leaves2 : m.leaves;
                    p.set(bx, by, bz, "minecraft:" + l + "[persistent=true]");
                    // fronde pendenti sotto la chioma
                    if (dy < -ry * 0.5 && nn > 0.9 && (type == TreeType.CHERRY || type == TreeType.AZALEA || type == TreeType.OLIVE) && !p.has(bx, by - 1, bz))
                        p.set(bx, by - 1, bz, "minecraft:" + l + "[persistent=true]");
                }
    }

    static void cypress(BuildPlan p, int x, int y, int z, double size, long seed, TreeMats m) {
        int h = (int) Math.round(9 * size + noise(x, y, z, seed) * 3);
        for (int k = 0; k < h - 1; k++) p.set(x, y + k, z, "minecraft:" + m.log + "[axis=y]");
        for (int k = 1; k <= h + 1; k++) {
            double t = k / (double) (h + 1);
            double r = (t < 0.35 ? 0.9 + t * 2.2 : 1.7 * Math.pow(1 - (t - 0.35) / 0.65, 0.8)) * size;
            int ri = (int) Math.ceil(r);
            for (int ax = -ri; ax <= ri; ax++)
                for (int az = -ri; az <= ri; az++) {
                    double d = Math.sqrt(ax * ax + az * az) + (noise(x + ax, y + k, z + az, seed) - 0.5) * 0.7;
                    if (d > r + 0.35) continue;
                    if (ax == 0 && az == 0 && k < h - 1) continue;
                    String l = noise(x + ax, y + k, z + az, seed + 1) < m.leaves2Chance ? m.leaves2 : m.leaves;
                    p.set(x + ax, y + k, z + az, "minecraft:" + l + "[persistent=true]");
                }
        }
    }

    static void spruce(BuildPlan p, int x, int y, int z, double size, long seed, TreeMats m) {
        int h = (int) Math.round(10 * size + noise(x, y, z, seed) * 3);
        for (int k = 0; k < h; k++) p.set(x, y + k, z, "minecraft:" + m.log + "[axis=y]");
        for (int k = 2; k <= h + 1; k++) {
            double t = (k - 2) / (double) (h - 1);
            double r = (1 - t) * 3.4 * size * (((h - k) % 3 == 0) ? 1.0 : 0.72) + 0.3;
            int ri = (int) Math.ceil(r);
            for (int ax = -ri; ax <= ri; ax++)
                for (int az = -ri; az <= ri; az++) {
                    double d = Math.sqrt(ax * ax + az * az) + (noise(x + ax, y + k, z + az, seed) - 0.5) * 0.6;
                    if (d > r) continue;
                    if (ax == 0 && az == 0 && k < h) continue;
                    p.set(x + ax, y + k, z + az, "minecraft:" + m.leaves + "[persistent=true]");
                }
        }
    }

    // ----------------------------------------------------------------------------------------

    /** Lampione all'italiana: zoccolo, fusto, braccio con due lanterne appese e lanterna in cima. along = asse del braccio ("x"/"z"). */
    public static void lamp(BuildPlan p, int x, int y, int z, String post, String lantern, int height, boolean arms) {
        lamp(p, x, y, z, post, lantern, height, arms, "x");
    }

    public static void lamp(BuildPlan p, int x, int y, int z, String post, String lantern, int height, boolean arms, String along) {
        p.set(x, y, z, "minecraft:chiseled_polished_blackstone");
        for (int k = 1; k <= height; k++) p.set(x, y + k, z, BuildUtil.mc(post));
        p.set(x, y + height + 1, z, BuildUtil.mc(lantern) + "[hanging=false]");
        if (arms) {
            int ax = along.equals("x") ? 1 : 0, az = 1 - ax;
            for (int s = -1; s <= 1; s += 2) {
                p.set(x + ax * s, y + height, z + az * s, BuildUtil.mc(post));
                p.set(x + ax * s, y + height - 1, z + az * s, BuildUtil.mc(lantern) + "[hanging=true]");
            }
        }
    }

    /** Lampione semplice: muretto + lanterna (+ catena e lanterna appesa se hang). */
    public static void post(BuildPlan p, int x, int y, int z, String post, int h, String top) {
        for (int k = 0; k < h; k++) p.set(x, y + k, z, BuildUtil.mc(post));
        p.set(x, y + h, z, BuildUtil.mc(top));
    }

    /** Panchina di 2 blocchi rivolta verso facing (ci si siede guardando facing). */
    public static void bench(BuildPlan p, int x, int y, int z, String facing, String wood, int len) {
        String along = BuildUtil.cw(facing);
        int[] a = BuildUtil.vec(along);
        // schienale verso l'opposto di facing: scale con facing = opposto (il "dietro" alto)
        for (int i = 0; i < len; i++)
            p.set(x + a[0] * i, y, z + a[1] * i, "minecraft:" + wood + "_stairs[facing=" + BuildUtil.opp(facing) + ",half=bottom,shape=straight]");
        p.set(x - a[0], y, z - a[1], "minecraft:" + wood + "_trapdoor[facing=" + BuildUtil.opp(along) + ",half=bottom,open=true]");
        p.set(x + a[0] * len, y, z + a[1] * len, "minecraft:" + wood + "_trapdoor[facing=" + along + ",half=bottom,open=true]");
    }

    /** Aiuola fiorita (rettangolo) a quota y (terra in y-1). */
    public static void flowers(BuildPlan p, int x1, int z1, int x2, int z2, int y, long seed, String... palette) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                if (p.has(x, y, z)) continue;
                double n = noise(x, y, z, seed);
                if (n < 0.35) p.set(x, y, z, BuildUtil.mc(palette[(int) (noise(x, y, z, seed + 1) * palette.length)]));
                else if (n < 0.55) p.set(x, y, z, "minecraft:short_grass");
            }
    }

    /** Fiore/vegetazione a caso per prato. */
    public static String meadow(double n, double n2) {
        if (n < 0.06) return "minecraft:poppy";
        if (n < 0.10) return "minecraft:cornflower";
        if (n < 0.14) return "minecraft:oxeye_daisy";
        if (n < 0.17) return "minecraft:allium";
        if (n < 0.20) return "minecraft:azure_bluet";
        if (n < 0.23) return "minecraft:dandelion";
        if (n < 0.45) return "minecraft:short_grass";
        if (n < 0.50) return "minecraft:fern";
        if (n < 0.56) return "minecraft:wildflowers[facing=" + BuildUtil.CARD[(int) (n2 * 4) & 3] + ",flower_amount=" + (1 + (int) (n2 * 16) % 4) + "]";
        return null;
    }

    /** Braciere: base, fuoco da campo acceso (anime opzionali), catene. */
    public static void brazier(BuildPlan p, int x, int y, int z, String base, boolean soul) {
        p.set(x, y, z, BuildUtil.mc(base));
        p.set(x, y + 1, z, "minecraft:" + (soul ? "soul_campfire" : "campfire") + "[lit=true,facing=north,signal_fire=false,waterlogged=false]");
    }
}

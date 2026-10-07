package it.pepita.core.world.build;

import it.pepita.core.world.BuildPlan;

import static it.pepita.core.world.build.BuildUtil.fbm;
import static it.pepita.core.world.build.BuildUtil.noise;
import static it.pepita.core.world.build.BuildUtil.vnoise3;

/**
 * Isola sospesa organica: costa irregolare, cima piatta a {@link #top}, sottofondo a cono arrotondato con
 * stratificazioni, stalattiti, rampicanti e radici pendenti. Viene scritto solo il guscio (l'interno resta vuoto).
 */
public final class Island {
    /** Materiale per una posizione. fromTop = blocchi sotto la cima, fromBottom = blocchi sopra il fondo, side = esposto di lato. */
    public interface Mat {
        String at(int x, int y, int z, int fromTop, int fromBottom, boolean side);
    }

    public interface Outline {
        /** Distanza normalizzata dal centro: 0 al centro, 1 sulla costa. */
        double t(int x, int z);
    }

    public final int cx, cz, top;
    public final double radius;
    public double depth;
    public long seed;
    public double rough = 0.17;
    public double lumpy = 0.45;
    public Outline outline;
    public Mat mat = Island::stone;
    /** Primo strato (cima). null = lascia al costruttore. */
    public String surface = "minecraft:grass_block";
    public boolean vines = true, roots = true, stalactites = true, dripstone = true;
    public double stalDensity = 1.0, stalLength = 1.0;
    /** Abbassa di 1 il bordo esterno in modo irregolare (scogliera arrotondata). */
    public boolean edgeDrop = true;
    public String vine = "minecraft:vine";
    public String hanging = "minecraft:hanging_roots";

    private int R;
    private int[] tops, bots;

    public Island(int cx, int cz, int top, double radius, double depth, long seed) {
        this.cx = cx; this.cz = cz; this.top = top; this.radius = radius; this.depth = depth; this.seed = seed;
    }

    public double t(int x, int z) {
        if (outline != null) return outline.t(x, z);
        double dx = x - cx, dz = z - cz;
        double a = Math.atan2(dz, dx);
        double r = Math.sqrt(dx * dx + dz * dz);
        double ca = Math.cos(a), sa = Math.sin(a);
        double k = 1 + rough * ((fbm(ca * 2.2 + 7, sa * 2.2 + 3, seed, 3) - 0.5) * 2.2) + rough * 0.4 * (fbm(ca * 6 + 1, sa * 6 + 9, seed + 5, 2) - 0.5);
        return r / (radius * k);
    }

    public boolean inside(int x, int z) { return t(x, z) < 1; }

    private int idx(int x, int z) { return (x - cx + R) * (2 * R + 1) + (z - cz + R); }

    public int topAt(int x, int z) {
        int dx = x - cx, dz = z - cz;
        if (Math.abs(dx) > R || Math.abs(dz) > R) return Integer.MIN_VALUE;
        return tops[idx(x, z)];
    }

    public int bottomAt(int x, int z) {
        int dx = x - cx, dz = z - cz;
        if (Math.abs(dx) > R || Math.abs(dz) > R) return Integer.MAX_VALUE;
        return bots[idx(x, z)];
    }

    /** Calcola le colonne senza scrivere (per interrogare topAt/bottomAt prima di costruire). */
    public Island prepare() {
        R = (int) Math.ceil(radius * (1 + rough * 1.6)) + 2;
        int n = 2 * R + 1;
        tops = new int[n * n];
        bots = new int[n * n];
        for (int dx = -R; dx <= R; dx++)
            for (int dz = -R; dz <= R; dz++) {
                int x = cx + dx, z = cz + dz;
                int i = idx(x, z);
                double t = t(x, z);
                if (t >= 1) { tops[i] = Integer.MIN_VALUE; bots[i] = Integer.MAX_VALUE; continue; }
                int tp = top;
                if (edgeDrop && t > 0.93 && noise(x, 1, z, seed + 77) < (t - 0.93) * 9) tp = top - 1;
                double prof = 0.7 * Math.pow(Math.max(0, 1 - t), 1.2) + 0.3 * Math.pow(Math.max(0, 1 - t * t), 0.7);
                double lump = 1 - lumpy / 2 + lumpy * fbm(x / 13.0, z / 13.0, seed + 11, 3);
                double d = 2 + depth * prof * lump + (noise(x, 2, z, seed) - 0.5) * 1.5;
                tops[i] = tp;
                bots[i] = (int) Math.round(top - d);
            }
        return this;
    }

    public void build(BuildPlan p) {
        if (tops == null) prepare();
        for (int dx = -R; dx <= R; dx++)
            for (int dz = -R; dz <= R; dz++) {
                int x = cx + dx, z = cz + dz;
                int i = idx(x, z);
                int tp = tops[i], bt = bots[i];
                if (tp == Integer.MIN_VALUE) continue;
                // copertura minima dei vicini (per sapere quali blocchi sono esposti di lato)
                int nTop = Integer.MAX_VALUE, nBot = Integer.MIN_VALUE;
                for (int ax = -1; ax <= 1; ax++)
                    for (int az = -1; az <= 1; az++) {
                        if (ax == 0 && az == 0) continue;
                        int ot = topAt(x + ax, z + az), ob = bottomAt(x + ax, z + az);
                        if (ot == Integer.MIN_VALUE) { nTop = Integer.MIN_VALUE; nBot = Integer.MAX_VALUE; }
                        else if (nTop != Integer.MIN_VALUE) { nTop = Math.min(nTop, ot); nBot = Math.max(nBot, ob); }
                    }
                for (int y = bt; y <= tp; y++) {
                    int fromTop = tp - y, fromBottom = y - bt;
                    boolean side = y > nTop || y < nBot || nTop == Integer.MIN_VALUE;
                    if (!(fromTop <= 3 || fromBottom <= 1 || side)) continue;
                    String m;
                    if (fromTop == 0 && surface != null) m = surface;
                    else if (fromTop == 0) continue;
                    else m = mat.at(x, y, z, fromTop, fromBottom, side);
                    if (m != null) p.set(x, y, z, m);
                }
                // vegetazione pendente sul fianco
                double t = t(x, z);
                if (vines && t > 0.8) {
                    for (String f : BuildUtil.CARD) {
                        int[] v = BuildUtil.vec(f);
                        int ax = x + v[0], az = z + v[1];
                        if (topAt(ax, az) != Integer.MIN_VALUE) continue;
                        double n = noise(ax, 5, az, seed + 31);
                        if (n > 0.42) continue;
                        int len = 1 + (int) (noise(ax, 6, az, seed + 32) * 7);
                        String face = BuildUtil.opp(f);
                        for (int k = 0; k < len; k++) {
                            int y = tp - k;
                            if (y < bt + 1) break;
                            if (p.has(ax, y, az)) break;
                            p.set(ax, y, az, vine.equals("minecraft:vine") ? vine + "[" + face + "=true]" : vine);
                        }
                    }
                }
                if (roots && t > 0.55 && noise(x, 7, z, seed + 41) < 0.18 && !p.has(x, bt - 1, z)) p.set(x, bt - 1, z, hanging);
            }
        if (stalactites) stalactites(p);
    }

    private void stalactites(BuildPlan p) {
        int cell = 6;
        for (int gx = -R; gx <= R; gx += cell)
            for (int gz = -R; gz <= R; gz += cell) {
                int x = cx + gx + (int) (noise(gx, 1, gz, seed + 51) * cell);
                int z = cz + gz + (int) (noise(gx, 2, gz, seed + 52) * cell);
                if (!inside(x, z)) continue;
                double t = t(x, z);
                if (t > 0.88) continue;
                if (noise(gx, 3, gz, seed + 53) > 0.75 * stalDensity) continue;
                double nl = noise(gx, 4, gz, seed + 54);
                double len = (3 + nl * nl * 16) * (1.15 - t) * stalLength + 2;
                double rr = 1.0 + noise(gx, 5, gz, seed + 55) * 2.4 * (1.1 - t);
                int bt = bottomAt(x, z);
                int L = (int) Math.round(len);
                for (int k = 0; k <= L; k++) {
                    double rad = rr * Math.pow(1 - k / (double) (L + 1), 1.3);
                    int y = bt - k;
                    int ri = (int) Math.ceil(rad);
                    boolean placed = false;
                    for (int ax = -ri; ax <= ri; ax++)
                        for (int az = -ri; az <= ri; az++) {
                            double d = Math.sqrt(ax * ax + az * az) + (noise(x + ax, y, z + az, seed + 56) - 0.5) * 0.6;
                            if (d > rad) continue;
                            int bx = x + ax, bz = z + az;
                            if (y >= bottomAt(bx, bz) && topAt(bx, bz) != Integer.MIN_VALUE) continue;
                            String m = mat.at(bx, y, bz, top - y, 0, true);
                            if (m != null) { p.set(bx, y, bz, m); placed = true; }
                        }
                    if (!placed) {
                        if (dripstone && k > 0) {
                            int tip = Math.min(3, L - k + 1);
                            if (p.has(x, y + 1, z)) {
                                String[] th = tip >= 3 ? new String[]{"base", "middle", "frustum", "tip"} : new String[]{"frustum", "tip"};
                                p.set(x, y + 1, z, "minecraft:dripstone_block");
                                for (int j = 0; j < th.length; j++)
                                    p.set(x, y - j, z, "minecraft:pointed_dripstone[thickness=" + th[j] + ",vertical_direction=down,waterlogged=false]");
                            }
                        }
                        break;
                    }
                }
            }
    }

    /** Palette di pietra standard (con oro, è Pepita). */
    public static String stone(int x, int y, int z, int fromTop, int fromBottom, boolean side) {
        double n = noise(x, y, z, 901);
        if (fromTop <= 2) {
            if (side && fromTop >= 1 && n < 0.25) return "minecraft:moss_block";
            return n < 0.6 ? "minecraft:dirt" : n < 0.85 ? "minecraft:coarse_dirt" : "minecraft:rooted_dirt";
        }
        double band = y / 3.2 + vnoise3(x / 9.0, y / 6.0, z / 9.0, 903) * 2.2;
        int b = Math.floorMod((int) Math.floor(band), 5);
        String base;
        if (y < 76) base = n < 0.45 ? "minecraft:deepslate" : n < 0.7 ? "minecraft:cobbled_deepslate" : n < 0.88 ? "minecraft:tuff" : "minecraft:polished_deepslate";
        else base = switch (b) {
            case 0 -> n < 0.7 ? "minecraft:stone" : "minecraft:andesite";
            case 1 -> n < 0.6 ? "minecraft:andesite" : "minecraft:stone";
            case 2 -> n < 0.65 ? "minecraft:tuff" : "minecraft:stone";
            case 3 -> n < 0.7 ? "minecraft:stone" : "minecraft:cobblestone";
            default -> n < 0.5 ? "minecraft:calcite" : "minecraft:diorite";
        };
        if (side && fromTop < 7 && noise(x, y, z, 905) < 0.2) base = "minecraft:mossy_cobblestone";
        double o = noise(x, y, z, 907);
        if (o < 0.022) return y < 76 ? "minecraft:deepslate_gold_ore" : "minecraft:gold_ore";
        if (o < 0.028) return "minecraft:raw_gold_block";
        if (o < 0.045) return y < 76 ? "minecraft:deepslate_coal_ore" : "minecraft:coal_ore";
        if (o < 0.052) return y < 76 ? "minecraft:deepslate_copper_ore" : "minecraft:copper_ore";
        return base;
    }
}

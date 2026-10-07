package it.pepita.core.world.build;

import it.pepita.core.world.BuildPlan;

import java.util.ArrayList;
import java.util.List;

import static it.pepita.core.world.build.BuildUtil.noise;

/**
 * Palazzo all'italiana: intonaco colorato, zoccolo in pietra, portico ad archi sul fronte, cornici marcapiano,
 * finestre con persiane verdi e tendaggi, balconcini, cantonali, tetto a padiglione in coppi.
 */
public final class Palazzo {
    public String wall = "minecraft:yellow_terracotta";
    public String wall2 = "minecraft:yellow_terracotta";
    public String trim = "smooth_sandstone";          // ha _stairs e _slab
    public String trimBlock = "minecraft:smooth_sandstone";
    public String column = "minecraft:smooth_sandstone";
    public String base = "minecraft:polished_andesite";
    public String baseStairs = "polished_andesite";
    public String roofA = "brick", roofB = "granite";  // basi delle scale del tetto
    public String ridge = "minecraft:bricks";
    public String shutter = "minecraft:waxed_oxidized_copper_trapdoor";
    public String curtain = "minecraft:red_wool";
    public String door = "spruce";
    public String porticoFloor = "minecraft:polished_andesite";
    public String lantern = "minecraft:lantern";
    public int groundH = 6;   // altezza piano terra (portico)
    public int upperH = 6;    // altezza di ogni piano superiore (marcapiano compreso)
    public int floors = 1;    // piani superiori
    public boolean flatRoof = false;
    public long seed = 1;

    /** Campate (u iniziale, u finale) del fronte in cui non costruire il portico (porte monumentali). */
    public final List<int[]> skip = new ArrayList<>();

    public int topY() { return groundH + floors * upperH + 2; }

    /**
     * Costruisce il palazzo sul rettangolo [x1..x2] x [z1..z2] con il pavimento a y0 (si cammina a y0+1).
     * front = lato con il portico.
     */
    public void build(BuildPlan p, int x1, int z1, int x2, int z2, int y0, String front, boolean portico) {
        int lx = x2 - x1 + 1, lz = z2 - z1 + 1;
        // quattro lati: per ciascuno un Frame con u=0 all'angolo sinistro (guardando da fuori)
        for (String side : BuildUtil.CARD) {
            Frame f;
            int len;
            switch (side) {
                case "south" -> { f = new Frame(p, x1, y0, z2, "south"); len = lx; }
                case "north" -> { f = new Frame(p, x2, y0, z1, "north"); len = lx; }
                case "west" -> { f = new Frame(p, x1, y0, z1, "west"); len = lz; }
                default -> { f = new Frame(p, x2, y0, z2, "east"); len = lz; }
            }
            int depth = side.equals("north") || side.equals("south") ? lz : lx;
            side(f, len, depth, side.equals(front) && portico, side.equals(front));
        }
        roof(p, x1, z1, x2, z2, y0 + topY());
        if (!flatRoof && chimneys) {
            boolean alongX = lx >= lz;
            int len = alongX ? lx : lz;
            for (int i = 4; i < len - 3; i += 8) {
                for (int s = 0; s < 2; s++) {
                    int x = alongX ? x1 + i + s * 3 : (s == 0 ? x1 + 2 : x2 - 2);
                    int z = alongX ? (s == 0 ? z1 + 2 : z2 - 2) : z1 + i + s * 3;
                    if (x > x2 || z > z2) continue;
                    int ry = y0 + topY() + Math.min(Math.min(x - x1 + 1, x2 + 1 - x), Math.min(z - z1 + 1, z2 + 1 - z));
                    for (int y = ry; y <= ry + 2; y++) p.set(x, y, z, "minecraft:bricks");
                    p.set(x, ry + 2, z, "minecraft:chiseled_stone_bricks");
                    p.set(x, ry + 3, z, "minecraft:stone_brick_slab[type=bottom]");
                }
            }
        }
    }

    public boolean chimneys = true;

    boolean skipped(int u) {
        for (int[] s : skip) if (u >= s[0] && u <= s[1]) return true;
        return false;
    }

    String wallAt(Frame f, int u, int y, int v) {
        double n = noise(f.wx(u, v), f.oy + y, f.wz(u, v), seed);
        return n < 0.8 ? wall : wall2;
    }

    void side(Frame f, int len, int depth, boolean portico, boolean isFront) {
        int top = topY();
        int bays = len;
        for (int u = 0; u < len; u++) {
            boolean corner = u == 0 || u == len - 1;
            boolean sk = isFront && skipped(u);
            // zoccolo
            f.set(u, 0, 0, base);
            f.set(u, 1, 0, base);
            if (!corner && !sk && !portico) f.set(u, 1, 1, "minecraft:" + baseStairs + "_stairs[facing={in},half=bottom]");
            int mod = Math.floorMod(u - 2, 4);   // 0 = pilastro/colonna
            for (int y = 2; y < top; y++) {
                String b;
                if (corner) b = (y % 2 == 0) ? trimBlock : wallAt(f, u, y, 0);   // cantonale
                else b = wallAt(f, u, y, 0);
                f.set(u, y, 0, b);
            }
            if (corner) {
                for (int y = 1; y < top; y++) f.set(u, y, 0, (y % 2 == 0 || y == 1) ? trimBlock : wallAt(f, u, y, 0));
                continue;
            }
            if (sk) continue;
            // ---- piano terra ----
            if (portico && u >= 2 && u <= len - 3) {
                porticoBay(f, u, mod);
            } else {
                groundWindow(f, u, mod);
            }
            // ---- marcapiano e piani superiori ----
            for (int fl = 0; fl < floors; fl++) {
                int yb = groundH + fl * upperH;      // riga del marcapiano
                f.set(u, yb + 1, 0, trimBlock);
                f.set(u, yb + 1, 1, "minecraft:" + trim + "_stairs[facing={in},half=top]");
                upperWindow(f, u, mod, yb + 1, fl, len);
            }
            // cornicione
            f.set(u, top - 1, 0, trimBlock);
            f.set(u, top - 1, 1, "minecraft:" + trim + "_stairs[facing={in},half=top]");
            if (mod == 0) f.set(u, top - 2, 1, "minecraft:" + trim + "_stairs[facing={in},half=top]");
        }
    }

    void porticoBay(Frame f, int u, int mod) {
        int h = groundH;
        // pavimento del portico e soffitto
        for (int v = -3; v <= 0; v++) f.set(u, 0, v, v == 0 ? base : porticoFloor);
        for (int v = -3; v <= -1; v++) {
            for (int y = 1; y < h; y++) f.air(u, y, v);
            f.set(u, h, v, trimBlock);
        }
        if (mod == 0) {
            // colonna
            f.set(u, 1, 0, column);
            for (int y = 2; y < h - 1; y++) f.set(u, y, 0, column.contains("quartz") ? "minecraft:quartz_pillar[axis=y]" : column);
            f.set(u, h - 1, 0, trimBlock);
            f.set(u, h, 0, wallAt(f, u, h, 0));
            f.set(u, 1, 1, "minecraft:" + trim + "_slab[type=bottom]");
        } else {
            for (int y = 1; y < h - 1; y++) f.air(u, y, 0);
            if (mod == 2) f.air(u, h - 1, 0);
            else f.set(u, h - 1, 0, "minecraft:" + trim + "_stairs[facing=" + (mod == 1 ? "{left}" : "{right}") + ",half=top]");
            f.set(u, h, 0, mod == 2 ? trimBlock : wallAt(f, u, h, 0));
            if (mod == 2) f.set(u, h - 1, -1, lantern + "[hanging=true]");
        }
        // parete di fondo del portico (v=-4): botteghe
        f.set(u, 0, -4, base);
        for (int y = 1; y <= h; y++) f.set(u, y, -4, wallAt(f, u, y, -4));
        if (mod == 2) {
            BuildUtil.door(f.p, f.wx(u, -4), f.oy + 1, f.wz(u, -4), door, f.out, "left", false);
            f.set(u, 3, -4, trimBlock);
        } else if (mod == 1 || mod == 3) {
            f.set(u, 2, -4, "minecraft:glass_pane");
            f.set(u, 3, -4, "minecraft:glass_pane");
            f.set(u, 2, -5, curtain);
            f.set(u, 3, -5, curtain);
            f.set(u, 4, -3, "minecraft:" + (mod == 1 ? "green" : "white") + "_wall_banner[facing={out}]");
        }
    }

    void groundWindow(Frame f, int u, int mod) {
        if (mod == 2) {
            for (int y = 2; y <= 4; y++) f.set(u, y, 0, "minecraft:iron_bars");
            for (int y = 2; y <= 4; y++) f.set(u, y, -1, curtain);
            f.set(u, 5, 0, trimBlock);
        } else if (mod == 0) {
            f.set(u, 2, 0, trimBlock);
            f.set(u, 4, 0, trimBlock);
        }
    }

    void upperWindow(Frame f, int u, int mod, int yb, int fl, int len) {
        int y0 = yb + 1;   // prima riga sopra il marcapiano
        int hh = upperH - 1;
        if (mod == 0) {
            // lesena
            for (int y = y0; y < y0 + hh; y++) f.set(u, y, 0, trimBlock);
            return;
        }
        boolean center = Math.abs(u - len / 2) <= 1 && fl == 0;
        if (mod == 2) {
            int wy0 = y0 + 1, wy1 = y0 + hh - 2;
            if (center) wy0 = y0;
            for (int y = wy0; y <= wy1; y++) {
                f.set(u, y, 0, center && y < wy0 + 2 ? "minecraft:glass_pane" : "minecraft:glass_pane");
                f.set(u, y, -1, curtain);
            }
            if (center) BuildUtil.door(f.p, f.wx(u, 0), f.oy + wy0, f.wz(u, 0), "spruce", f.in, "left", false);
            f.set(u, wy1 + 1, 0, trimBlock);
            f.set(u, wy1 + 1, 1, "minecraft:" + trim + "_stairs[facing={in},half=bottom]");
            if (!center) {
                f.set(u, wy0 - 1, 1, "minecraft:" + trim + "_slab[type=top]");
                f.set(u, wy0, 1, potted(f, u, wy0));
            }
        } else {
            int wy0 = y0 + 1, wy1 = y0 + hh - 2;
            if (center) wy0 = y0;
            for (int y = wy0; y <= wy1; y++)
                f.set(u, y, 1, shutter + "[facing={out},half=bottom,open=true]");
        }
        if (center) {
            // balcone sul piano nobile
            f.set(u, y0 - 1, 1, "minecraft:" + trim + "_slab[type=top]");
            f.set(u, y0 - 1, 2, "minecraft:" + trim + "_stairs[facing={in},half=top]");
            f.set(u, y0, 2, "minecraft:iron_bars");
        }
    }

    String potted(Frame f, int u, int y) {
        String[] pots = {"potted_red_tulip", "potted_poppy", "potted_azalea_bush", "potted_flowering_azalea_bush", "potted_cornflower", "potted_pink_tulip", "potted_fern"};
        return "minecraft:" + pots[(int) (noise(f.wx(u, 0), y, f.wz(u, 0), seed + 7) * pots.length)];
    }

    /** Tetto a padiglione in coppi (scale miste) con sporto di 1 blocco. */
    public void roof(BuildPlan p, int x1, int z1, int x2, int z2, int y) {
        if (flatRoof) {
            for (int x = x1; x <= x2; x++)
                for (int z = z1; z <= z2; z++) {
                    boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                    p.set(x, y, z, edge ? trimBlock : "minecraft:" + trim + "_slab[type=bottom]");
                    if (edge) p.set(x, y + 1, z, "minecraft:" + trim + "_slab[type=bottom]");
                }
            return;
        }
        int ax1 = x1 - 1, az1 = z1 - 1, ax2 = x2 + 1, az2 = z2 + 1;
        for (int k = 0; ; k++) {
            int rx1 = ax1 + k, rz1 = az1 + k, rx2 = ax2 - k, rz2 = az2 - k;
            if (rx1 > rx2 || rz1 > rz2) break;
            int yy = y + k;
            if (rx1 == rx2 || rz1 == rz2) {
                for (int x = rx1; x <= rx2; x++) for (int z = rz1; z <= rz2; z++) p.set(x, yy, z, ridge);
                for (int x = rx1; x <= rx2; x++) for (int z = rz1; z <= rz2; z++) p.set(x, yy + 1, z, BuildUtil.slab(ridge.replace("minecraft:", "").replace("bricks", "brick"), false));
                break;
            }
            for (int x = rx1; x <= rx2; x++)
                for (int z = rz1; z <= rz2; z++) {
                    boolean ex = x == rx1 || x == rx2, ez = z == rz1 || z == rz2;
                    if (!ex && !ez) continue;
                    String face = ez && !ex ? (z == rz1 ? "south" : "north") : ex && !ez ? (x == rx1 ? "east" : "west") : (z == rz1 ? "south" : "north");
                    String mat = noise(x, yy, z, seed + 3) < 0.7 ? roofA : roofB;
                    p.set(x, yy, z, "minecraft:" + mat + "_stairs[facing=" + face + ",half=bottom]");
                    if (k == 0 && ex && ez) p.set(x, yy - 1, z, trimBlock);
                }
        }
    }
}

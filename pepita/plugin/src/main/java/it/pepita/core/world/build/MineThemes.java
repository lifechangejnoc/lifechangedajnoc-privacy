package it.pepita.core.world.build;

import it.pepita.core.world.build.MineBuild.Ctx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static it.pepita.core.world.build.BuildUtil.*;

/** Decorazioni delle isole delle miniere, una per tema (monumento a nord + elementi sparsi). */
final class MineThemes {
    private MineThemes() {}

    static void decorate(Ctx c) {
        switch (c.t.name()) {
            case "ardesia" -> ardesia(c);
            case "nera" -> nera(c);
            case "prismarino" -> prismarino(c);
            case "end" -> end(c);
            case "prestigio" -> prestigio(c);
            case "vip" -> vip(c);
            case "evasione" -> evasione(c);
            default -> pietra(c);
        }
    }

    // =====================================================================
    //  Strumenti
    // =====================================================================

    /** Punti liberi sul terreno ben distanziati (minDist) tra raggio minR e maxR dal centro. */
    static List<int[]> spots(Ctx c, int count, int minDist, int minR, int maxR, List<int[]> taken) {
        List<int[]> cand = new ArrayList<>();
        for (int x = c.cx - maxR; x <= c.cx + maxR; x += 2)
            for (int z = c.cz - maxR; z <= c.cz + maxR; z += 2) {
                double d = Math.hypot(x - c.cx, z - c.cz);
                if (d < minR || d > maxR) continue;
                int jx = x + (int) (c.n(x, 3, z, 71) * 2), jz = z + (int) (c.n(x, 4, z, 72) * 2);
                if (c.free(jx, jz)) cand.add(new int[]{jx, jz});
            }
        Collections.shuffle(cand, c.r);
        List<int[]> out = new ArrayList<>();
        for (int[] p : cand) {
            if (out.size() >= count) break;
            boolean ok = true;
            for (int[] q : out) if (Math.hypot(p[0] - q[0], p[1] - q[1]) < minDist) { ok = false; break; }
            if (ok && taken != null) for (int[] q : taken) if (Math.hypot(p[0] - q[0], p[1] - q[1]) < minDist * 0.7) { ok = false; break; }
            if (ok) out.add(p);
        }
        if (taken != null) taken.addAll(out);
        return out;
    }

    /** Masso irregolare appoggiato al terreno. */
    static void boulder(Ctx c, int x, int z, double rx, double ry, Island.Mat mat, long s) {
        int y0 = c.gy(x, z) + 1;
        int ix = (int) Math.ceil(rx + 1), iy = (int) Math.ceil(ry + 1);
        for (int ax = -ix; ax <= ix; ax++)
            for (int az = -ix; az <= ix; az++)
                for (int ay = -1; ay <= iy; ay++) {
                    double d = ax * ax / (rx * rx) + (ay + 0.5) * (ay + 0.5) / (ry * ry) + az * az / (rx * rx);
                    double nn = noise(x + ax, y0 + ay, z + az, c.seed + s);
                    if (d > 1 + (nn - 0.5) * 0.5) continue;
                    if (c.cheb(x + ax, z + az) <= c.h + 5) continue;
                    c.p.set(x + ax, y0 + ay, z + az, mat.at(x + ax, y0 + ay, z + az, 5, 5, true));
                }
    }

    static void tree(Ctx c, int x, int z, Deco.TreeType t, double size) {
        Deco.tree(c.p, x, c.gy(x, z) + 1, z, t, size, c.seed + x * 31L + z);
    }

    static void plants(Ctx c, int x, int z, int r, double chance, String... list) {
        for (int ax = -r; ax <= r; ax++)
            for (int az = -r; az <= r; az++) {
                int bx = x + ax, bz = z + az;
                if (ax * ax + az * az > r * r || !c.free(bx, bz) || c.p.has(bx, c.gy(bx, bz) + 1, bz)) continue;
                double n = c.n(bx, 7, bz, 81);
                if (n < chance) c.p.set(bx, c.gy(bx, bz) + 1, bz, mc(list[(int) (c.n(bx, 8, bz, 82) * list.length) % list.length]));
            }
    }

    /** Prato sparso su tutta l'isola libera. */
    static void meadow(Ctx c, double density, String... list) {
        int R = (int) Math.ceil(c.isl.radius * 1.25);
        for (int x = c.cx - R; x <= c.cx + R; x++)
            for (int z = c.cz - R; z <= c.cz + R; z++) {
                if (!c.free(x, z) || c.p.has(x, c.gy(x, z) + 1, z)) continue;
                String g = c.p.get(x, c.gy(x, z), z);
                if (g == null || !(g.contains("grass_block") || g.contains("moss_block") || g.contains("podzol") || g.contains("dirt")
                        || g.contains("sand") || g.contains("soul") || g.contains("nylium") || g.contains("end_stone"))) continue;
                double n = c.n(x, 9, z, 91);
                if (n < density) c.p.set(x, c.gy(x, z) + 1, z, mc(list[(int) (c.n(x, 10, z, 92) * list.length) % list.length]));
            }
    }

    static void lamp(Ctx c, int x, int z, String post, String lantern, int h) {
        int y = c.gy(x, z) + 1;
        for (int k = 0; k < h; k++) c.set(x, y + k, z, post);
        c.set(x, y + h, z, lantern);
    }

    static String hang(String lantern) {
        return mc(lantern) + "[hanging=true,waterlogged=false]";
    }

    static String stand(String lantern) {
        return mc(lantern) + "[hanging=false,waterlogged=false]";
    }

    /** Frame locale verso nord per il monumento dietro l'insegna: u lungo x, v verso nord (lontano dalla buca). */
    static Frame north(Ctx c) {
        return new Frame(c.p, c.cx, c.top, c.cz - (c.h + 7), "north");
    }

    // =====================================================================
    //  PIETRA: cava di montagna con ponteggi, gru e carrelli
    // =====================================================================

    static void pietra(Ctx c) {
        List<int[]> taken = new ArrayList<>();
        // parete della cava a nord con l'ingresso della galleria
        Frame f = north(c);
        for (int u = -13; u <= 13; u++)
            for (int v = 0; v <= 9; v++) {
                double hgt = 13 - Math.abs(u) * 0.35 - v * 0.2 + (noise(u, 0, v, c.seed + 5) - 0.5) * 4;
                if (v == 0) hgt -= 3;
                for (int y = 1; y <= hgt; y++) {
                    int wx = f.wx(u, v), wz = f.wz(u, v);
                    f.set(u, y, v, Island.stone(wx, c.top + y, wz, (int) (hgt - y) + 3, y, true));
                }
            }
        // galleria: varco 5x6 incorniciato da travi
        for (int u = -2; u <= 2; u++) for (int y = 1; y <= 6; y++) for (int v = -1; v <= 5; v++) f.air(u, y, v);
        for (int u = -2; u <= 2; u++) f.set(u, 0, 0, "minecraft:spruce_planks");
        for (int y = 1; y <= 6; y++) {
            f.set(-3, y, 0, "minecraft:stripped_spruce_log[axis=y]");
            f.set(3, y, 0, "minecraft:stripped_spruce_log[axis=y]");
            f.set(-3, y, 3, "minecraft:spruce_log[axis=y]");
            f.set(3, y, 3, "minecraft:spruce_log[axis=y]");
        }
        for (int u = -3; u <= 3; u++) {
            f.set(u, 7, 0, "minecraft:stripped_spruce_log[axis={axisu}]");
            f.set(u, 7, 3, "minecraft:spruce_log[axis={axisu}]");
        }
        f.set(0, 6, 0, hang("lantern"));
        f.set(0, 6, 3, hang("lantern"));
        // binari che entrano nella galleria con un carrello pieno
        for (int v = -6; v <= 5; v++) f.set(0, 1, v, v == -2 ? "minecraft:hopper[enabled=true,facing=down]" : "minecraft:rail[shape=north_south,waterlogged=false]");
        f.set(0, 2, -2, "minecraft:raw_iron_block");
        // ponteggio sulla parete
        for (int s = -1; s <= 1; s += 2)
            for (int y = 1; y <= 9; y++) {
                f.set(s * 7, y, -1, "minecraft:spruce_fence");
                f.set(s * 10, y, -1, "minecraft:spruce_fence");
                if (y % 3 == 0) for (int u = 7; u <= 10; u++) f.set(s * u, y, -1, "minecraft:spruce_slab[type=bottom]");
            }
        f.set(-8, 4, -1, "minecraft:ladder[facing=south]");

        // gru di legno a est che guarda fuori dall'isola
        int gx = c.cx + c.h + 12, gz = c.cz - 6;
        if (c.ground(gx, gz)) crane(c, gx, gz);
        // binari sul lato est del bordo con due carrelli
        int rx = c.cx + c.h + 5;
        for (int z = c.cz - 9; z <= c.cz + 9; z++) {
            if (!c.ground(rx, z)) continue;
            boolean cart = z == c.cz - 4 || z == c.cz + 5;
            c.p.set(rx, c.gy(rx, z) + 1, z, cart ? "minecraft:hopper[enabled=true,facing=down]" : "minecraft:rail[shape=north_south,waterlogged=false]");
            if (cart) c.p.set(rx, c.gy(rx, z) + 2, z, c.n(rx, 0, z, 9) < 0.5 ? "minecraft:raw_gold_block" : "minecraft:coal_block");
        }
        // massi, alberi, cassoni, cumuli di minerale
        for (int[] s : spots(c, 8, 9, c.h + 9, (int) c.isl.radius - 2, taken))
            boulder(c, s[0], s[1], 2 + c.r.nextDouble() * 2.2, 1.6 + c.r.nextDouble() * 2.2, Island::stone, s[0] * 7L + s[1]);
        for (int[] s : spots(c, 10, 7, c.h + 11, (int) c.isl.radius - 3, taken))
            tree(c, s[0], s[1], c.r.nextInt(3) == 0 ? Deco.TreeType.OAK : Deco.TreeType.SPRUCE, 0.75 + c.r.nextDouble() * 0.4);
        for (int[] s : spots(c, 5, 6, c.h + 7, (int) c.isl.radius - 5, taken)) crates(c, s[0], s[1]);
        meadow(c, 0.22, "short_grass", "short_grass", "fern", "dandelion", "poppy", "cornflower");
    }

    static void crane(Ctx c, int x, int z) {
        int y0 = c.gy(x, z) + 1;
        for (int y = 0; y <= 13; y++)
            for (int dx = 0; dx <= 1; dx++)
                for (int dz = 0; dz <= 1; dz++) c.p.set(x + dx, y0 + y, z + dz, y % 4 == 3 ? "minecraft:spruce_planks" : "minecraft:stripped_spruce_log[axis=y]");
        for (int k = -2; k <= 9; k++) {
            c.p.set(x + k, y0 + 14, z, "minecraft:spruce_log[axis=x]");
            c.p.set(x + k, y0 + 14, z + 1, "minecraft:spruce_slab[type=bottom]");
        }
        c.p.set(x - 2, y0 + 13, z, "minecraft:cobblestone");
        c.p.set(x - 2, y0 + 12, z, "minecraft:cobblestone");
        for (int y = 6; y <= 13; y++) c.p.set(x + 9, y0 + y, z, "minecraft:iron_chain[axis=y,waterlogged=false]");
        c.p.set(x + 9, y0 + 5, z, "minecraft:anvil[facing=north]");
        c.p.set(x + 9, y0 + 15, z, "minecraft:lantern[hanging=false,waterlogged=false]");
    }

    static void crates(Ctx c, int x, int z) {
        int y = c.gy(x, z) + 1;
        c.p.set(x, y, z, "minecraft:barrel[facing=up,open=false]");
        c.p.set(x + 1, y, z, "minecraft:spruce_planks");
        c.p.set(x, y, z + 1, "minecraft:barrel[facing=north,open=false]");
        c.p.set(x, y + 1, z, "minecraft:spruce_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]");
        c.p.set(x + 1, y + 1, z, "minecraft:lantern[hanging=false,waterlogged=false]");
        c.p.set(x - 1, y, z, "minecraft:raw_iron_block");
        c.p.set(x - 1, y, z - 1, "minecraft:coal_block");
    }

    // =====================================================================
    //  ARDESIA: miniera profonda, castelletto, armature di sostegno
    // =====================================================================

    static void ardesia(Ctx c) {
        List<int[]> taken = new ArrayList<>();
        Frame f = north(c);
        // castelletto (headframe) a quattro gambe con la grande ruota
        for (int s = -1; s <= 1; s += 2)
            for (int v = 0; v <= 4; v += 4)
                for (int y = 1; y <= 19; y++) f.set(s * 5, y, v, "minecraft:dark_oak_log[axis=y]");
        for (int y = 4; y <= 19; y += 5)
            for (int u = -5; u <= 5; u++) {
                f.set(u, y, 0, "minecraft:dark_oak_log[axis={axisu}]");
                f.set(u, y, 4, "minecraft:dark_oak_log[axis={axisu}]");
                if (Math.abs(u) == 5) for (int v = 1; v <= 3; v++) f.set(u, y, v, "minecraft:dark_oak_log[axis={axisv}]");
            }
        // controventature
        for (int k = 0; k < 4; k++) {
            f.set(-5 + k + 1, 5 + k, 0, "minecraft:dark_oak_fence");
            f.set(5 - k - 1, 5 + k, 0, "minecraft:dark_oak_fence");
            f.set(-5 + k + 1, 10 + k, 4, "minecraft:dark_oak_fence");
            f.set(5 - k - 1, 10 + k, 4, "minecraft:dark_oak_fence");
        }
        for (int u = -5; u <= 5; u++) for (int v = 0; v <= 4; v++) f.set(u, 20, v, "minecraft:dark_oak_planks");
        // ruota (cerchio verticale nel piano u-y)
        for (int a = 0; a < 360; a += 8) {
            double rad = Math.toRadians(a);
            int u = (int) Math.round(Math.cos(rad) * 4), y = 25 + (int) Math.round(Math.sin(rad) * 4);
            f.set(u, y, 2, "minecraft:iron_block");
        }
        for (int k = -3; k <= 3; k++) {
            f.set(k, 25, 2, "minecraft:dark_oak_fence");
            f.set(0, 25 + k, 2, "minecraft:dark_oak_fence");
        }
        f.set(0, 25, 2, "minecraft:polished_deepslate");
        for (int y = 21; y <= 21; y++) {
            f.set(-1, y, 2, "minecraft:dark_oak_planks");
            f.set(1, y, 2, "minecraft:dark_oak_planks");
        }
        for (int y = 2; y <= 19; y++) f.set(0, y, 2, "minecraft:iron_chain[axis=y,waterlogged=false]");
        f.set(0, 1, 2, "minecraft:dark_oak_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]");
        for (int s = -1; s <= 1; s += 2) {
            f.set(s * 5, 21, 0, "minecraft:lantern[hanging=false,waterlogged=false]");
            f.set(s * 5, 21, 4, "minecraft:lantern[hanging=false,waterlogged=false]");
            f.set(s * 3, 3, 0, hang("lantern"));
        }
        // armature di sostegno (travi) lungo i lati del bordo, con lanterne appese
        int d = c.h + 5;
        for (int k = -12; k <= 12; k += 8) {
            supportFrame(c, c.cx + d, c.cz + k, true);
            supportFrame(c, c.cx - d, c.cz + k, true);
            if (Math.abs(k) > 4) supportFrame(c, c.cx + k, c.cz + d, false);
        }
        // guglie di ardesia, alberi scuri, funghi e licheni
        for (int[] s : spots(c, 9, 8, c.h + 9, (int) c.isl.radius - 2, taken)) spire(c, s[0], s[1], 5 + c.r.nextInt(8));
        for (int[] s : spots(c, 6, 8, c.h + 11, (int) c.isl.radius - 3, taken)) tree(c, s[0], s[1], Deco.TreeType.DARK_OAK, 0.8 + c.r.nextDouble() * 0.3);
        for (int[] s : spots(c, 5, 6, c.h + 7, (int) c.isl.radius - 5, taken)) {
            crates(c, s[0], s[1]);
            c.p.set(s[0] + 1, c.gy(s[0], s[1]) + 2, s[1], "minecraft:lantern[hanging=false,waterlogged=false]");
        }
        meadow(c, 0.18, "fern", "short_grass", "brown_mushroom", "red_mushroom", "moss_carpet", "small_dripleaf[facing=north,half=lower,waterlogged=false]");
        // le piccole dripleaf hanno due metà: sostituiamo con muschio se manca la parte alta
        fixDripleaf(c);
    }

    static void fixDripleaf(Ctx c) {
        List<int[]> fix = new ArrayList<>();
        c.p.forEach((k, d) -> {
            if (d.startsWith("minecraft:small_dripleaf")) fix.add(new int[]{it.pepita.core.world.BuildPlan.kx(k), it.pepita.core.world.BuildPlan.ky(k), it.pepita.core.world.BuildPlan.kz(k)});
        });
        for (int[] b : fix) {
            if (c.p.has(b[0], b[1] + 1, b[2])) c.p.set(b[0], b[1], b[2], "minecraft:moss_carpet");
            else c.p.set(b[0], b[1] + 1, b[2], "minecraft:small_dripleaf[facing=north,half=upper,waterlogged=false]");
        }
    }

    static void supportFrame(Ctx c, int x, int z, boolean alongZ) {
        if (!c.ground(x, z)) return;
        int y = c.gy(x, z) + 1;
        int ax = alongZ ? 0 : 1, az = alongZ ? 1 : 0;
        for (int s = -1; s <= 1; s += 2)
            for (int k = 0; k < 4; k++) c.p.set(x + ax * s * 2, y + k, z + az * s * 2, "minecraft:stripped_dark_oak_log[axis=y]");
        for (int s = -2; s <= 2; s++) c.p.set(x + ax * s, y + 4, z + az * s, "minecraft:dark_oak_log[axis=" + (alongZ ? "z" : "x") + "]");
        c.p.set(x, y + 3, z, hang("lantern"));
    }

    static void spire(Ctx c, int x, int z, int h) {
        for (int y = 0; y < h; y++) {
            double r = (1 - y / (double) h) * 2.2 + 0.3;
            int ri = (int) Math.ceil(r);
            for (int ax = -ri; ax <= ri; ax++)
                for (int az = -ri; az <= ri; az++) {
                    if (Math.sqrt(ax * ax + az * az) + (c.n(x + ax, y, z + az, 51) - 0.5) * 0.8 > r) continue;
                    String b = MineBuild.matDeepslate(x + ax, c.gy(x, z) + 1 + y, z + az, 5, 5, true);
                    c.p.set(x + ax, c.gy(x, z) + 1 + y, z + az, b);
                    if (y > 1 && c.n(x + ax, y, z + az, 52) < 0.08 && !c.p.has(x + ax + 1, c.gy(x, z) + 1 + y, z + az))
                        c.p.set(x + ax + 1, c.gy(x, z) + 1 + y, z + az, "minecraft:glow_lichen[down=false,east=false,north=false,south=false,up=false,waterlogged=false,west=true]");
                }
        }
        c.p.set(x, c.gy(x, z) + 1 + h, z, "minecraft:pointed_dripstone[thickness=tip,vertical_direction=up,waterlogged=false]");
    }

    // =====================================================================
    //  NERA: fortezza di blackstone e oro
    // =====================================================================

    static void nera(Ctx c) {
        List<int[]> taken = new ArrayList<>();
        Frame f = north(c);
        // porta della fortezza: due torri e un arco con saracinesca d'oro
        for (int s = -1; s <= 1; s += 2) tower(f, s * 9, 2, 3, 16, "polished_blackstone_bricks", "gilded_blackstone", "soul_lantern");
        for (int u = -6; u <= 6; u++)
            for (int y = 1; y <= 12; y++)
                for (int v = 1; v <= 3; v++) {
                    int au = Math.abs(u);
                    boolean arch = au <= 3 && y <= 7 + (au <= 1 ? 1 : 0) - (au == 3 ? 1 : 0);
                    if (arch && v <= 3) {
                        if (v == 2 && y >= 5) f.set(u, y, v, "minecraft:iron_chain[axis=y,waterlogged=false]");
                        continue;
                    }
                    String b = y == 12 ? (Math.floorMod(u, 2) == 0 ? "minecraft:polished_blackstone_bricks" : "minecraft:air")
                            : y == 11 ? "minecraft:chiseled_polished_blackstone" : noise(u, y, v, c.seed + 11) < 0.8 ? "minecraft:polished_blackstone_bricks" : "minecraft:cracked_polished_blackstone_bricks";
                    if (y == 9 && au <= 4 && v == 1) b = "minecraft:gold_block";
                    if (!b.equals("minecraft:air")) f.set(u, y, v, b);
                }
        f.set(0, 9, 0, "minecraft:gilded_blackstone");
        f.set(0, 10, 0, "minecraft:gold_block");
        for (int s = -1; s <= 1; s += 2) {
            f.set(s * 4, 1, 0, "minecraft:polished_blackstone");
            f.set(s * 4, 2, 0, "minecraft:soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]");
        }
        // mura merlate sul bordo dell'isola (tratti)
        int R = (int) (c.isl.radius * 0.95);
        for (int a = 0; a < 360; a += 2) {
            if (a > 60 && a < 120) continue; // apertura sul lato dell'arrivo (sud)
            double rad = Math.toRadians(a);
            int x = c.cx + (int) Math.round(Math.cos(rad) * (R - 4)), z = c.cz + (int) Math.round(Math.sin(rad) * (R - 4));
            if (!c.ground(x, z) || c.reserved(x, z)) continue;
            int hgt = 3 + (int) (noise(a, 0, 0, c.seed + 13) * 3);
            for (int y = 1; y <= hgt; y++) c.p.set(x, c.gy(x, z) + y, z, noise(x, y, z, c.seed + 14) < 0.85 ? "minecraft:polished_blackstone_bricks" : "minecraft:gilded_blackstone");
            if (a % 4 == 0) c.p.set(x, c.gy(x, z) + hgt + 1, z, "minecraft:polished_blackstone_brick_wall");
            if (a % 24 == 0) c.p.set(x, c.gy(x, z) + hgt + 2, z, stand("soul_lantern"));
        }
        // funghi giganti del Nether, colonne di basalto, tesori
        for (int[] s : spots(c, 7, 9, c.h + 9, R - 7, taken)) fungus(c, s[0], s[1], c.r.nextBoolean(), 6 + c.r.nextInt(5));
        for (int[] s : spots(c, 8, 6, c.h + 8, R - 6, taken)) basaltColumn(c, s[0], s[1], 3 + c.r.nextInt(6));
        for (int[] s : spots(c, 4, 7, c.h + 8, R - 7, taken)) treasure(c, s[0], s[1]);
        meadow(c, 0.16, "crimson_roots", "warped_roots", "nether_sprouts", "crimson_fungus");
    }

    static void tower(Frame f, int cu, int cv, int half, int h, String mat, String accent, String lantern) {
        for (int u = cu - half; u <= cu + half; u++)
            for (int v = cv - half; v <= cv + half; v++)
                for (int y = 1; y <= h; y++) {
                    boolean edge = Math.abs(u - cu) == half || Math.abs(v - cv) == half;
                    boolean corner = Math.abs(u - cu) == half && Math.abs(v - cv) == half;
                    if (!edge) continue;
                    String b = corner ? "minecraft:" + accent : "minecraft:" + mat;
                    if (y % 5 == 0 && !corner && (u == cu || v == cv)) b = "minecraft:" + (mat.contains("blackstone") ? "polished_blackstone_brick_wall" : "iron_bars");
                    f.set(u, y, v, b);
                }
        for (int u = cu - half - 1; u <= cu + half + 1; u++)
            for (int v = cv - half - 1; v <= cv + half + 1; v++) {
                boolean edge = Math.abs(u - cu) == half + 1 || Math.abs(v - cv) == half + 1;
                f.set(u, h + 1, v, "minecraft:" + mat);
                if (edge && Math.floorMod(u + v, 2) == 0) f.set(u, h + 2, v, "minecraft:" + mat);
            }
        f.set(cu, h + 2, cv, "minecraft:" + accent);
        f.set(cu, h + 3, cv, "minecraft:" + lantern + "[hanging=false,waterlogged=false]");
    }

    static void fungus(Ctx c, int x, int z, boolean crimson, int h) {
        String stem = crimson ? "minecraft:crimson_stem[axis=y]" : "minecraft:warped_stem[axis=y]";
        String wart = crimson ? "minecraft:nether_wart_block" : "minecraft:warped_wart_block";
        int y0 = c.gy(x, z) + 1;
        for (int y = 0; y < h; y++) c.p.set(x, y0 + y, z, stem);
        int cr = 3;
        for (int ax = -cr; ax <= cr; ax++)
            for (int az = -cr; az <= cr; az++)
                for (int ay = -2; ay <= 1; ay++) {
                    double d = Math.sqrt(ax * ax + az * az);
                    if (d > cr + 0.3 - (ay > 0 ? 1.2 : 0)) continue;
                    if (ay < 0 && d < cr - 0.6) continue;
                    double n = c.n(x + ax, ay, z + az, 61);
                    c.p.set(x + ax, y0 + h + ay, z + az, n < 0.12 ? "minecraft:shroomlight" : wart);
                    if (ay == -2 && n > 0.8) for (int k = 1; k <= 1 + (int) (n * 3); k++)
                        c.p.set(x + ax, y0 + h + ay - k, z + az, crimson ? "minecraft:weeping_vines_plant" : "minecraft:twisting_vines_plant");
                }
    }

    static void basaltColumn(Ctx c, int x, int z, int h) {
        for (int y = 1; y <= h; y++) c.p.set(x, c.gy(x, z) + y, z, "minecraft:basalt[axis=y]");
        c.p.set(x + 1, c.gy(x, z) + 1, z, "minecraft:basalt[axis=y]");
        if (h > 4) c.p.set(x, c.gy(x, z) + h + 1, z, "minecraft:polished_basalt[axis=y]");
    }

    static void treasure(Ctx c, int x, int z) {
        int y = c.gy(x, z) + 1;
        c.p.set(x, y, z, "minecraft:gold_block");
        c.p.set(x + 1, y, z, "minecraft:raw_gold_block");
        c.p.set(x, y, z + 1, "minecraft:gilded_blackstone");
        c.p.set(x, y + 1, z, "minecraft:chest[facing=south,type=single,waterlogged=false]");
        c.p.set(x - 1, y, z, "minecraft:gold_block");
        c.p.set(x + 1, y + 1, z, "minecraft:soul_lantern[hanging=false,waterlogged=false]");
    }

    // =====================================================================
    //  PRISMARINO: rovina sottomarina
    // =====================================================================

    static void prismarino(Ctx c) {
        List<int[]> taken = new ArrayList<>();
        Frame f = north(c);
        // facciata del tempio sommerso: gradoni e colonnato
        for (int step = 0; step < 4; step++) {
            int w = 11 - step * 2, y = 1 + step * 3;
            for (int u = -w; u <= w; u++)
                for (int v = step; v <= 6; v++)
                    for (int k = 0; k < 3; k++) {
                        boolean face = v == step;
                        String b = face && k == 1 && Math.floorMod(u, 3) == 0 ? "minecraft:sea_lantern"
                                : noise(u, y + k, v, c.seed + 21) < 0.7 ? "minecraft:prismarine_bricks" : "minecraft:dark_prismarine";
                        if (face && Math.abs(u) <= 1 && y + k <= 9) continue; // portale
                        f.set(u, y + k, v, b);
                    }
        }
        for (int y = 1; y <= 9; y++) for (int u = -1; u <= 1; u++) for (int v = 0; v <= 5; v++) f.air(u, y, v);
        for (int y = 1; y <= 9; y++) {
            f.set(-2, y, 0, "minecraft:dark_prismarine");
            f.set(2, y, 0, "minecraft:dark_prismarine");
        }
        f.set(0, 10, 0, "minecraft:sea_lantern");
        f.set(0, 13, 3, "minecraft:dark_prismarine");
        f.set(0, 14, 3, "minecraft:conduit[waterlogged=false]");
        // colonne spezzate, archi in rovina, piscine con alghe, coralli, relitto
        for (int[] s : spots(c, 9, 7, c.h + 8, (int) c.isl.radius - 3, taken)) brokenColumn(c, s[0], s[1], 2 + c.r.nextInt(7));
        for (int[] s : spots(c, 4, 12, c.h + 10, (int) c.isl.radius - 6, taken)) pool(c, s[0], s[1], 2 + c.r.nextInt(2));
        for (int[] s : spots(c, 8, 6, c.h + 8, (int) c.isl.radius - 3, taken)) coral(c, s[0], s[1]);
        int[] w = spots(c, 1, 10, c.h + 12, (int) c.isl.radius - 8, taken).stream().findFirst().orElse(null);
        if (w != null) wreck(c, w[0], w[1]);
        for (int[] s : spots(c, 6, 9, c.h + 7, (int) c.isl.radius - 4, taken)) lamp(c, s[0], s[1], "prismarine_wall", "minecraft:sea_lantern", 3);
        meadow(c, 0.14, "sea_pickle[pickles=2,waterlogged=false]", "sea_pickle[pickles=1,waterlogged=false]", "dead_bush", "tube_coral_fan[waterlogged=false]");
    }

    static void brokenColumn(Ctx c, int x, int z, int h) {
        int y0 = c.gy(x, z) + 1;
        c.p.set(x, y0, z, "minecraft:dark_prismarine");
        for (int y = 1; y < h; y++) c.p.set(x, y0 + y, z, noise(x, y, z, c.seed + 25) < 0.8 ? "minecraft:prismarine_bricks" : "minecraft:prismarine");
        if (h >= 6) c.p.set(x, y0 + h, z, "minecraft:dark_prismarine_slab[type=bottom,waterlogged=false]");
        // frammento caduto accanto
        String[] dirs = CARD;
        int[] v = vec(dirs[(int) (noise(x, 0, z, c.seed + 26) * 4) & 3]);
        c.p.set(x + v[0] * 2, y0, z + v[1] * 2, "minecraft:prismarine_bricks");
        c.p.set(x + v[0] * 3, y0, z + v[1] * 3, "minecraft:prismarine_brick_slab[type=bottom,waterlogged=false]");
    }

    static void pool(Ctx c, int x, int z, int r) {
        if (!c.level(x, z, r + 1)) return;
        for (int ax = -r - 1; ax <= r + 1; ax++)
            for (int az = -r - 1; az <= r + 1; az++) {
                double d = Math.sqrt(ax * ax + az * az);
                int bx = x + ax, bz = z + az;
                if (d > r + 1.2 || !c.ground(bx, bz)) continue;
                if (d > r + 0.2) {
                    c.p.set(bx, c.gy(x, z), bz, "minecraft:prismarine_bricks");
                    continue;
                }
                c.p.set(bx, c.gy(x, z), bz, "minecraft:water[level=0]");
                c.p.set(bx, c.gy(x, z) - 1, bz, "minecraft:water[level=0]");
                c.p.set(bx, c.gy(x, z) - 2, bz, "minecraft:sand");
                double n = c.n(bx, 0, bz, 27);
                if (n < 0.35) {
                    c.p.set(bx, c.gy(x, z) - 1, bz, "minecraft:kelp_plant");
                    c.p.set(bx, c.gy(x, z), bz, "minecraft:kelp[age=20]");
                } else if (n < 0.55) c.p.set(bx, c.gy(x, z) - 1, bz, "minecraft:seagrass");
            }
    }

    static void coral(Ctx c, int x, int z) {
        String[] t = {"tube", "brain", "bubble", "fire", "horn"};
        String k = t[(int) (c.n(x, 0, z, 28) * 5) % 5];
        int y0 = c.gy(x, z) + 1;
        c.p.set(x, y0, z, "minecraft:" + k + "_coral_block");
        c.p.set(x, y0 + 1, z, "minecraft:" + k + "_coral[waterlogged=false]");
        for (String d : CARD) {
            int[] v = vec(d);
            if (c.n(x + v[0], 1, z + v[1], 29) < 0.6) {
                c.p.set(x + v[0], y0, z + v[1], "minecraft:" + t[(int) (c.n(x, 2, z + v[1], 30) * 5) % 5] + "_coral_block");
                c.p.set(x + v[0] * 2, y0, z + v[1] * 2, "minecraft:" + k + "_coral_wall_fan[facing=" + d + ",waterlogged=false]");
            }
        }
    }

    static void wreck(Ctx c, int x, int z) {
        if (!c.level(x, z, 2)) return;
        int y0 = c.gy(x, z) + 1;
        for (int k = -5; k <= 5; k++) {
            int w = k > 3 ? 1 : 2;
            for (int s = -w; s <= w; s++) {
                c.p.set(x + k, y0, z + s, Math.abs(s) == w ? "minecraft:spruce_planks" : "minecraft:stripped_spruce_wood[axis=y]");
                if (Math.abs(s) == w && (k + s) % 3 != 0) c.p.set(x + k, y0 + 1, z + s, "minecraft:spruce_planks");
            }
        }
        for (int y = 1; y <= 8; y++) c.p.set(x - 1, y0 + y, z, "minecraft:spruce_log[axis=y]");
        for (int s = -3; s <= 3; s++) c.p.set(x - 1, y0 + 6, z + s, "minecraft:spruce_fence");
        for (int s = -2; s <= 2; s++) for (int y = 3; y <= 5; y++) if (c.n(x, y, z + s, 31) < 0.7) c.p.set(x - 1, y0 + y, z + s, "minecraft:white_wool");
        c.p.set(x + 2, y0 + 1, z, "minecraft:chest[facing=west,type=single,waterlogged=false]");
    }

    // =====================================================================
    //  END: isola dell'End, torre purpur, chorus
    // =====================================================================

    static void end(Ctx c) {
        List<int[]> taken = new ArrayList<>();
        Frame f = north(c);
        // torre della città dell'End
        endBox(f, 0, 3, 5, 1, 9);
        endBox(f, 0, 3, 4, 10, 16);
        endBox(f, 0, 3, 3, 17, 23);
        for (int y = 24; y <= 27; y++) f.set(0, y, 3, "minecraft:end_rod[facing=up]");
        for (int s = -1; s <= 1; s += 2) {
            f.set(s * 6, 10, 3, "minecraft:purpur_pillar[axis={axisu}]");
            f.set(s * 7, 10, 3, "minecraft:end_rod[facing=" + (s < 0 ? "{left}" : "{right}") + "]");
            f.set(s * 5, 17, 3, "minecraft:purple_shulker_box[facing=up]");
        }
        for (int u = -1; u <= 1; u++) for (int y = 1; y <= 4; y++) f.air(u, y, -2);
        for (int u = -1; u <= 1; u++) for (int y = 1; y <= 4; y++) for (int v = -2; v <= 0; v++) f.air(u, y, v);
        f.set(0, 4, -2, "minecraft:magenta_stained_glass");
        // pilastri di ossidiana con gabbia, chorus, massi di end stone
        for (int[] s : spots(c, 5, 12, c.h + 10, (int) c.isl.radius - 5, taken)) obsidianPillar(c, s[0], s[1], 6 + c.r.nextInt(7));
        for (int[] s : spots(c, 12, 5, c.h + 8, (int) c.isl.radius - 2, taken)) chorus(c, s[0], s[1], 3 + c.r.nextInt(4));
        for (int[] s : spots(c, 6, 8, c.h + 9, (int) c.isl.radius - 2, taken))
            boulder(c, s[0], s[1], 1.6 + c.r.nextDouble() * 2, 1.2 + c.r.nextDouble() * 1.6, MineBuild::matEnd, s[0] * 3L + s[1]);
        for (int[] s : spots(c, 6, 9, c.h + 7, (int) c.isl.radius - 4, taken)) lamp(c, s[0], s[1], "purpur_pillar[axis=y]", "minecraft:end_rod[facing=up]", 2);
    }

    static void endBox(Frame f, int cu, int cv, int half, int y1, int y2) {
        for (int u = cu - half; u <= cu + half; u++)
            for (int v = cv - half; v <= cv + half; v++)
                for (int y = y1; y <= y2; y++) {
                    boolean eu = Math.abs(u - cu) == half, ev = Math.abs(v - cv) == half;
                    if (!eu && !ev && y != y1 && y != y2) continue;
                    String b = eu && ev ? "minecraft:purpur_pillar[axis=y]" : y == y1 || y == y2 ? "minecraft:purpur_block" : "minecraft:end_stone_bricks";
                    if ((eu ^ ev) && y == (y1 + y2) / 2 && (u == cu || v == cv)) b = "minecraft:magenta_stained_glass";
                    f.set(u, y, v, b);
                }
        for (int u = cu - half - 1; u <= cu + half + 1; u++)
            for (int v = cv - half - 1; v <= cv + half + 1; v++) {
                boolean edge = Math.abs(u - cu) == half + 1 || Math.abs(v - cv) == half + 1;
                if (edge) f.set(u, y2, v, "minecraft:purpur_slab[type=bottom,waterlogged=false]");
            }
    }

    static void obsidianPillar(Ctx c, int x, int z, int h) {
        int y0 = c.gy(x, z) + 1;
        for (int y = 0; y < h; y++)
            for (int ax = -1; ax <= 1; ax++)
                for (int az = -1; az <= 1; az++) if (Math.abs(ax) + Math.abs(az) < 2 || y < h - 2) c.p.set(x + ax, y0 + y, z + az, "minecraft:obsidian");
        for (int ax = -1; ax <= 1; ax++)
            for (int az = -1; az <= 1; az++)
                for (int y = h; y <= h + 2; y++) {
                    boolean edge = ax != 0 || az != 0;
                    if (edge) c.p.set(x + ax, y0 + y, z + az, "minecraft:iron_bars");
                }
        c.p.set(x, y0 + h, z, "minecraft:bedrock");
        c.p.set(x, y0 + h + 1, z, "minecraft:pearlescent_froglight");
        for (int ax = -1; ax <= 1; ax++) for (int az = -1; az <= 1; az++) c.p.set(x + ax, y0 + h + 3, z + az, "minecraft:iron_bars");
    }

    /** Pianta di chorus ramificata con gli stati di collegamento espliciti. */
    static void chorus(Ctx c, int x, int z, int h) {
        int y0 = c.gy(x, z) + 1;
        java.util.Set<Long> pts = new java.util.HashSet<>();
        java.util.Map<Long, int[]> pos = new java.util.HashMap<>();
        List<long[]> flowers = new ArrayList<>();
        java.util.function.BiConsumer<int[], Boolean> add = (b, fl) -> {
            long k = ((long) b[0] & 0xFFFFF) << 40 | ((long) b[2] & 0xFFFFF) << 16 | (b[1] & 0xFFFF);
            pts.add(k);
            pos.put(k, b);
            if (fl) flowers.add(new long[]{k});
        };
        for (int y = 0; y < h; y++) add.accept(new int[]{x, y0 + y, z}, false);
        String[] dirs = CARD;
        for (int b = 0; b < 2; b++) {
            int[] v = vec(dirs[(int) (c.n(x, b, z, 33) * 4) & 3]);
            int by = y0 + h - 2 + b;
            int[] e = {x + v[0], by, z + v[1]};
            add.accept(e, false);
            add.accept(new int[]{e[0], by + 1, e[2]}, false);
            add.accept(new int[]{e[0], by + 2, e[2]}, true);
        }
        add.accept(new int[]{x, y0 + h, z}, true);
        java.util.Set<Long> fl = new java.util.HashSet<>();
        for (long[] f : flowers) fl.add(f[0]);
        for (long k : pts) {
            int[] b = pos.get(k);
            if (fl.contains(k)) {
                c.p.set(b[0], b[1], b[2], "minecraft:chorus_flower[age=5]");
                continue;
            }
            StringBuilder s = new StringBuilder("minecraft:chorus_plant[");
            int[][] nb = {{0, -1, 0}, {1, 0, 0}, {0, 0, -1}, {0, 0, 1}, {0, 1, 0}, {-1, 0, 0}};
            String[] nm = {"down", "east", "north", "south", "up", "west"};
            for (int i = 0; i < 6; i++) {
                long nk = ((long) (b[0] + nb[i][0]) & 0xFFFFF) << 40 | ((long) (b[2] + nb[i][2]) & 0xFFFFF) << 16 | ((b[1] + nb[i][1]) & 0xFFFF);
                boolean con = pts.contains(nk) || (i == 0 && b[1] == y0);
                s.append(nm[i]).append('=').append(con).append(i < 5 ? "," : "]");
            }
            c.p.set(b[0], b[1], b[2], s.toString());
        }
    }

    // =====================================================================
    //  PRESTIGIO: tempio viola e ametista
    // =====================================================================

    static void prestigio(Ctx c) {
        List<int[]> taken = new ArrayList<>();
        Frame f = north(c);
        // pronao: gradini, sei colonne, architrave e timpano
        for (int u = -10; u <= 10; u++) {
            f.set(u, 1, 0, "minecraft:smooth_quartz_stairs[facing={in},half=bottom]");
            for (int v = 1; v <= 7; v++) f.set(u, 1, v, "minecraft:smooth_quartz");
            for (int v = 1; v <= 7; v++) f.set(u, 2, v, v == 1 ? "minecraft:smooth_quartz_stairs[facing={in},half=bottom]" : "minecraft:quartz_bricks");
        }
        for (int u = -9; u <= 9; u += 6)
            for (int v = 2; v <= 6; v += 4)
                for (int y = 3; y <= 11; y++)
                    f.set(u, y, v, y == 3 ? "minecraft:purpur_block" : y == 11 ? "minecraft:chiseled_quartz_block" : "minecraft:quartz_pillar[axis=y]");
        for (int u = -10; u <= 10; u++)
            for (int v = 1; v <= 7; v++) {
                f.set(u, 12, v, v == 1 || v == 7 ? "minecraft:purpur_block" : "minecraft:quartz_bricks");
                if (v == 1) f.set(u, 13, v, Math.floorMod(u, 2) == 0 ? "minecraft:amethyst_block" : "minecraft:purpur_block");
            }
        for (int k = 0; k <= 10; k++)
            for (int v = 1; v <= 7; v++) {
                int y = 13 + k / 2;
                for (int s = -1; s <= 1; s += 2) {
                    int u = s * (10 - k);
                    String st = "minecraft:purpur_stairs[facing=" + (s < 0 ? "{right}" : "{left}") + ",half=bottom]";
                    f.set(u, y + 1, v, (k % 2 == 0) ? st : "minecraft:purpur_slab[type=bottom,waterlogged=false]");
                }
            }
        for (int y = 14; y <= 18; y++) f.set(0, y, 1, "minecraft:purpur_block");
        f.set(0, 19, 1, "minecraft:amethyst_block");
        f.set(0, 20, 1, "minecraft:amethyst_cluster[facing=up,waterlogged=false]");
        for (int s = -1; s <= 1; s += 2) Deco.brazier(f.p, f.wx(s * 11, 1), c.top + 2, f.wz(s * 11, 1), "minecraft:purpur_pillar[axis=y]", true);
        // geodi di ametista, obelischi, alberi fioriti, aiuole viola
        for (int[] s : spots(c, 6, 10, c.h + 9, (int) c.isl.radius - 4, taken)) geode(c, s[0], s[1], 2 + c.r.nextInt(2));
        for (int[] s : spots(c, 6, 9, c.h + 8, (int) c.isl.radius - 3, taken)) obelisk(c, s[0], s[1], 6 + c.r.nextInt(5));
        for (int[] s : spots(c, 8, 8, c.h + 11, (int) c.isl.radius - 3, taken))
            tree(c, s[0], s[1], c.r.nextBoolean() ? Deco.TreeType.CHERRY : Deco.TreeType.AZALEA, 0.75 + c.r.nextDouble() * 0.35);
        meadow(c, 0.26, "allium", "allium", "short_grass", "lilac[half=lower]", "pink_tulip", "azure_bluet");
        fixTall(c, "lilac");
    }

    static void fixTall(Ctx c, String plant) {
        List<int[]> fix = new ArrayList<>();
        c.p.forEach((k, d) -> {
            if (d.equals("minecraft:" + plant + "[half=lower]")) fix.add(new int[]{it.pepita.core.world.BuildPlan.kx(k), it.pepita.core.world.BuildPlan.ky(k), it.pepita.core.world.BuildPlan.kz(k)});
        });
        for (int[] b : fix) {
            if (c.p.has(b[0], b[1] + 1, b[2])) c.p.set(b[0], b[1], b[2], "minecraft:short_grass");
            else c.p.set(b[0], b[1] + 1, b[2], "minecraft:" + plant + "[half=upper]");
        }
    }

    static void geode(Ctx c, int x, int z, int r) {
        int y0 = c.gy(x, z) + 1;
        for (int ax = -r; ax <= r; ax++)
            for (int az = -r; az <= r; az++)
                for (int ay = 0; ay <= r; ay++) {
                    double d = Math.sqrt(ax * ax + ay * ay * 1.4 + az * az);
                    if (d > r + 0.3) continue;
                    double n = c.n(x + ax, ay, z + az, 41);
                    c.p.set(x + ax, y0 + ay, z + az, d > r - 0.7 ? (n < 0.5 ? "minecraft:calcite" : "minecraft:smooth_basalt") : "minecraft:amethyst_block");
                }
        c.p.set(x, y0 + r + 1, z, "minecraft:amethyst_cluster[facing=up,waterlogged=false]");
        for (String dd : CARD) {
            int[] v = vec(dd);
            if (c.n(x + v[0], 0, z, 42) < 0.7) c.p.set(x + v[0] * (r + 1), y0, z + v[1] * (r + 1), "minecraft:large_amethyst_bud[facing=" + dd + ",waterlogged=false]");
        }
    }

    static void obelisk(Ctx c, int x, int z, int h) {
        int y0 = c.gy(x, z) + 1;
        for (int ax = -1; ax <= 1; ax++) for (int az = -1; az <= 1; az++) c.p.set(x + ax, y0, z + az, "minecraft:quartz_bricks");
        for (int y = 1; y < h; y++) c.p.set(x, y0 + y, z, y % 3 == 0 ? "minecraft:purpur_pillar[axis=y]" : "minecraft:quartz_pillar[axis=y]");
        c.p.set(x, y0 + h, z, "minecraft:amethyst_block");
        c.p.set(x, y0 + h + 1, z, "minecraft:amethyst_cluster[facing=up,waterlogged=false]");
        for (String dd : CARD) {
            int[] v = vec(dd);
            c.p.set(x + v[0], y0 + 1, z + v[1], "minecraft:smooth_quartz_stairs[facing=" + opp(dd) + ",half=bottom]");
        }
    }

    // =====================================================================
    //  VIP: giardino di lusso in oro, quarzo e smeraldi
    // =====================================================================

    static void vip(Ctx c) {
        List<int[]> taken = new ArrayList<>();
        Frame f = north(c);
        // cancello d'oro monumentale
        for (int s = -1; s <= 1; s += 2) {
            for (int y = 1; y <= 9; y++)
                for (int du = 0; du <= 1; du++)
                    for (int v = 0; v <= 1; v++) f.set(s * (6 + du), y, v, y == 1 || y == 9 ? "minecraft:chiseled_quartz_block" : y == 5 ? "minecraft:emerald_block" : "minecraft:quartz_pillar[axis=y]");
            f.set(s * 6, 10, 0, "minecraft:gold_block");
            f.set(s * 7, 10, 1, "minecraft:gold_block");
            f.set(s * 6, 11, 0, "minecraft:lantern[hanging=false,waterlogged=false]");
            for (int u = 8; u <= 13; u++) {
                f.set(s * u, 1, 0, "minecraft:smooth_quartz");
                f.set(s * u, 2, 0, "minecraft:iron_bars");
                f.set(s * u, 3, 0, "minecraft:iron_bars");
                if (u % 2 == 0) f.set(s * u, 4, 0, "minecraft:gold_block");
            }
        }
        for (int u = -5; u <= 5; u++) {
            int au = Math.abs(u);
            int yb = au <= 2 ? 9 : au <= 4 ? 8 : 7;
            f.set(u, yb, 0, "minecraft:smooth_quartz");
            f.set(u, yb + 1, 0, au % 2 == 0 ? "minecraft:gold_block" : "minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]");
            for (int y = 2; y < yb; y++) if (au >= 4 || y >= yb - 1) f.set(u, y, 0, "minecraft:iron_bars");
        }
        for (int dy = 0; dy <= 2; dy++) f.set(0, 11 + dy, 0, dy == 2 ? "minecraft:emerald_block" : "minecraft:gold_block");
        // viali in quarzo con intarsi, siepi, topiarie, fontane, cipressi
        int R = (int) c.isl.radius - 3;
        for (int a = 0; a < 4; a++) {
            String dir = CARD[a];
            int[] v = vec(dir);
            for (int k = c.h + 7; k <= R; k++)
                for (int w = -1; w <= 1; w++) {
                    int x = c.cx + v[0] * k + (v[0] == 0 ? w : 0), z = c.cz + v[1] * k + (v[1] == 0 ? w : 0);
                    if (!c.ground(x, z) || c.reserved(x, z)) continue;
                    c.p.set(x, c.gy(x, z), z, w == 0 ? (k % 4 == 0 ? "minecraft:emerald_block" : "minecraft:smooth_quartz") : "minecraft:polished_diorite");
                    int hx = x + (v[0] == 0 ? w * 2 : 0), hz = z + (v[1] == 0 ? w * 2 : 0);
                    if (w != 0 && c.free(hx, hz) && k % 7 != 0) c.p.set(hx, c.gy(hx, hz) + 1, hz, "minecraft:azalea_leaves[persistent=true]");
                }
        }
        for (int[] s : spots(c, 3, 14, c.h + 11, R - 4, taken)) fountain(c, s[0], s[1]);
        for (int[] s : spots(c, 10, 6, c.h + 8, R, taken)) tree(c, s[0], s[1], Deco.TreeType.CYPRESS, 0.7 + c.r.nextDouble() * 0.3);
        for (int[] s : spots(c, 8, 6, c.h + 8, R, taken)) topiary(c, s[0], s[1]);
        for (int[] s : spots(c, 3, 10, c.h + 9, R - 3, taken)) statue(c, s[0], s[1]);
        meadow(c, 0.34, "poppy", "red_tulip", "white_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley", "short_grass");
    }

    static void fountain(Ctx c, int x, int z) {
        if (!c.level(x, z, 3)) return;
        int y = c.gy(x, z);
        for (int ax = -3; ax <= 3; ax++)
            for (int az = -3; az <= 3; az++) {
                double d = Math.sqrt(ax * ax + az * az);
                if (d > 3.4) continue;
                int bx = x + ax, bz = z + az;
                if (d > 2.5) {
                    c.p.set(bx, y, bz, "minecraft:smooth_quartz");
                    c.p.set(bx, y + 1, bz, "minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]");
                } else {
                    c.p.set(bx, y, bz, "minecraft:water[level=0]");
                    c.p.set(bx, y - 1, bz, "minecraft:prismarine_bricks");
                }
            }
        c.p.set(x, y, z, "minecraft:gold_block");
        c.p.set(x, y + 1, z, "minecraft:quartz_pillar[axis=y]");
        c.p.set(x, y + 2, z, "minecraft:quartz_pillar[axis=y]");
        c.p.set(x, y + 3, z, "minecraft:gold_block");
        c.p.set(x, y + 4, z, "minecraft:water[level=0]");
    }

    static void topiary(Ctx c, int x, int z) {
        int y0 = c.gy(x, z) + 1;
        c.p.set(x, y0, z, "minecraft:polished_diorite");
        c.p.set(x, y0 + 1, z, "minecraft:oak_log[axis=y]");
        for (int ax = -1; ax <= 1; ax++) for (int ay = 0; ay <= 2; ay++) for (int az = -1; az <= 1; az++)
            if (Math.abs(ax) + Math.abs(az) + Math.abs(ay - 1) <= 2) c.p.set(x + ax, y0 + 2 + ay, z + az, "minecraft:azalea_leaves[persistent=true]");
    }

    static void statue(Ctx c, int x, int z) {
        int y0 = c.gy(x, z) + 1;
        for (int ax = -1; ax <= 1; ax++) for (int az = -1; az <= 1; az++) c.p.set(x + ax, y0, z + az, "minecraft:chiseled_quartz_block");
        c.p.set(x, y0 + 1, z, "minecraft:quartz_pillar[axis=y]");
        PrisonBuild.nugget(c.p, x, y0 + 3, z, 1.6, 1.6, 1.4, c.seed + x);
    }

    // =====================================================================
    //  EVASIONE: prigione in rovina, ossidiana, anime e catene
    // =====================================================================

    static void evasione(Ctx c) {
        List<int[]> taken = new ArrayList<>();
        Frame f = north(c);
        // portone della prigione sfondato
        for (int u = -11; u <= 11; u++)
            for (int v = 0; v <= 2; v++) {
                int hgt = 14 - (int) (Math.abs(u) * 0.4) - (int) (noise(u, 0, v, c.seed + 51) * 5);
                for (int y = 1; y <= hgt; y++) {
                    if (Math.abs(u) <= 3 && y <= 8 - (Math.abs(u) == 3 ? 1 : 0)) continue;
                    double n = noise(u, y, v, c.seed + 52);
                    String b = n < 0.45 ? "minecraft:stone_bricks" : n < 0.7 ? "minecraft:cracked_stone_bricks" : n < 0.85 ? "minecraft:obsidian" : "minecraft:crying_obsidian";
                    if (y == hgt && n > 0.6) b = "minecraft:stone_brick_stairs[facing={out},half=bottom]";
                    f.set(u, y, v, b);
                }
            }
        // sbarre divelte nel varco e catene
        for (int u = -3; u <= 3; u++) {
            int keep = (int) (noise(u, 0, 0, c.seed + 53) * 6);
            for (int y = 8 - keep; y <= 7; y++) f.set(u, y, 1, "minecraft:iron_bars");
        }
        f.set(-1, 1, -2, "minecraft:iron_bars");
        f.set(2, 1, -3, "minecraft:iron_bars");
        for (int s = -1; s <= 1; s += 2) {
            for (int y = 9; y <= 13; y++) f.set(s * 6, y, -1, "minecraft:iron_chain[axis=y,waterlogged=false]");
            f.set(s * 6, 8, -1, "minecraft:soul_lantern[hanging=true,waterlogged=false]");
            f.set(s * 8, 1, -2, "minecraft:soul_soil");
            f.set(s * 8, 2, -2, "minecraft:soul_fire");
        }
        // muri crollati con sbarre, forche, bracieri d'anima, teschi e ragnatele
        for (int[] s : spots(c, 7, 10, c.h + 9, (int) c.isl.radius - 3, taken)) ruin(c, s[0], s[1]);
        for (int[] s : spots(c, 3, 12, c.h + 10, (int) c.isl.radius - 5, taken)) gallows(c, s[0], s[1]);
        for (int[] s : spots(c, 7, 7, c.h + 8, (int) c.isl.radius - 3, taken)) {
            int sy = c.gy(s[0], s[1]);
            c.p.set(s[0], sy + 1, s[1], "minecraft:soul_soil");
            c.p.set(s[0], sy + 2, s[1], "minecraft:soul_fire");
            for (String dd : CARD) {
                int[] v = vec(dd);
                c.p.set(s[0] + v[0], sy + 1, s[1] + v[1], "minecraft:polished_blackstone_brick_stairs[facing=" + opp(dd) + ",half=bottom]");
            }
        }
        for (int[] s : spots(c, 6, 8, c.h + 8, (int) c.isl.radius - 3, taken)) deadTree(c, s[0], s[1], 4 + c.r.nextInt(4));
        meadow(c, 0.12, "skeleton_skull[powered=false,rotation=4]", "cobweb", "dead_bush", "wither_rose", "soul_torch");
    }

    static void ruin(Ctx c, int x, int z) {
        boolean alongX = c.n(x, 0, z, 61) < 0.5;
        int len = 4 + (int) (c.n(x, 1, z, 62) * 4);
        for (int k = -len; k <= len; k++) {
            int bx = x + (alongX ? k : 0), bz = z + (alongX ? 0 : k);
            int h = 1 + (int) ((1 - Math.abs(k) / (double) (len + 1)) * 5 * c.n(bx, 2, bz, 63) + 1);
            for (int y = 1; y <= h; y++) {
                double n = c.n(bx, y, bz, 64);
                String b = (k % 3 == 0 && y >= 2 && y <= 3) ? "minecraft:iron_bars" : n < 0.5 ? "minecraft:stone_bricks" : n < 0.8 ? "minecraft:cracked_stone_bricks" : "minecraft:mossy_stone_bricks";
                c.p.set(bx, c.gy(bx, bz) + y, bz, b);
            }
            if (c.n(bx, 9, bz, 65) < 0.3) c.p.set(bx + (alongX ? 0 : 1), c.gy(bx, bz) + 1, bz + (alongX ? 1 : 0), "minecraft:cobblestone");
        }
        c.p.set(x, c.gy(x, z) + 1, z + (alongX ? 2 : 0), "minecraft:cobweb");
    }

    static void gallows(Ctx c, int x, int z) {
        int y0 = c.gy(x, z) + 1;
        for (int y = 0; y <= 5; y++) c.p.set(x, y0 + y, z, "minecraft:dark_oak_log[axis=y]");
        for (int k = 1; k <= 3; k++) c.p.set(x + k, y0 + 5, z, "minecraft:dark_oak_log[axis=x]");
        c.p.set(x + 1, y0 + 4, z, "minecraft:dark_oak_fence");
        for (int y = 2; y <= 4; y++) c.p.set(x + 3, y0 + y, z, "minecraft:iron_chain[axis=y,waterlogged=false]");
        c.p.set(x + 3, y0 + 1, z, "minecraft:soul_lantern[hanging=true,waterlogged=false]");
        for (int ax = -1; ax <= 3; ax++) for (int az = -1; az <= 1; az++) if (!c.p.has(x + ax, y0, z + az)) c.p.set(x + ax, y0 - 1, z + az, "minecraft:dark_oak_planks");
    }

    static void deadTree(Ctx c, int x, int z, int h) {
        int y0 = c.gy(x, z) + 1;
        for (int y = 0; y < h; y++) c.p.set(x, y0 + y, z, "minecraft:stripped_dark_oak_log[axis=y]");
        int[] v = vec(CARD[(int) (c.n(x, 0, z, 71) * 4) & 3]);
        for (int k = 1; k <= 2; k++) c.p.set(x + v[0] * k, y0 + h - 2 + k, z + v[1] * k, "minecraft:dark_oak_fence");
        c.p.set(x - v[0], y0 + h - 1, z - v[1], "minecraft:dark_oak_fence");
        c.p.set(x, y0 + h, z, "minecraft:dark_oak_fence");
    }
}

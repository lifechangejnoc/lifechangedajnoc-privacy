package it.pepita.core.world.build;

import it.pepita.core.world.BuildPlan;

import java.util.Random;

import static it.pepita.core.world.Layout.*;
import static it.pepita.core.world.build.BuildUtil.*;

/**
 * Cortile della prigione (mondo "pepita"): isola sospesa, piazza circolare con la fontana della Pepita Gigante,
 * bracci delle celle a due piani lungo le mura, mura alte con camminamento merlato e otto torri di guardia con fari,
 * il cancello delle miniere a nord (portale), il padiglione delle casse a est, la galleria delle classifiche a ovest
 * e il mercato a sud (incantesimi, negozio, corazza, battle pass, giornaliero). Ciliegi, lampioni, panchine.
 * I blocchi interattivi sono esattamente nelle posizioni di Layout.
 */
public final class PrisonBuild {
    private PrisonBuild() {}

    static final int Y = 99;              // pavimento (si sta a 100)
    static final long SEED = 1337;
    static final double COURT = 26.5;     // raggio del cortile libero
    static final double BLOCK_IN = 27, BLOCK_OUT = 33.5;   // bracci delle celle
    static final double WALL_IN = 33.5, WALL_OUT = 37;     // mura
    static final int WALL_TOP = 113;

    public static void build(BuildPlan p) {
        Island isl = new Island(0, 0, Y, PRISON_RADIUS + 2, 30, SEED);
        isl.rough = 0.1;
        isl.prepare();
        isl.build(p);

        piazza(p);
        fountain(p);
        cellBlocks(p);
        walls(p);
        for (int k = 0; k < 8; k++) tower(p, 22.5 + k * 45);
        gatehouse(p);
        crates(p);
        boards(p);
        market(p);
        gardens(p, isl);
        outside(p, isl);
        keepClear(p);
        finish(p);
    }

    static double r(int x, int z) {
        return Math.hypot(x + 0.5, z + 0.5);
    }

    static double a(int x, int z) {
        return ang(x + 0.5, z + 0.5);
    }

    /** Settori dei bracci delle celle (gradi, 0 = est, 90 = sud): lasciano liberi i quattro lati funzionali. */
    static boolean inBlockArc(double a) {
        return (a > 22 && a < 68) || (a > 112 && a < 158) || (a > 202 && a < 248) || (a > 292 && a < 338);
    }

    // =====================================================================
    //  Piazza
    // =====================================================================

    static void piazza(BuildPlan p) {
        int R = (int) WALL_OUT + 1;
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++) {
                double d = r(x, z);
                if (d > WALL_IN) continue;
                double a = a(x, z);
                String b;
                boolean path = (Math.abs(x) <= 2 || Math.abs(z) <= 2) && d > 10;
                if (d <= 10) b = d > 9.3 ? "minecraft:polished_deepslate" : d > 8.6 ? "minecraft:gold_block" : "minecraft:smooth_stone";
                else if (path) {
                    int off = Math.abs(x) <= 2 && Math.abs(z) > 2 ? Math.abs(x) : Math.abs(z);
                    b = off == 0 ? (((int) d) % 4 == 0 ? "minecraft:gold_block" : "minecraft:polished_andesite")
                            : off == 1 ? "minecraft:polished_andesite" : "minecraft:polished_blackstone_bricks";
                } else if (d > 19.4 && d <= 20.5) b = "minecraft:polished_blackstone_bricks";
                else if (d > COURT - 0.6) b = "minecraft:polished_andesite";
                else {
                    // lastricato a settori con fughe
                    int ring = (int) (d / 3.2);
                    int seg = (int) (a / (360.0 / (8 + ring * 4)));
                    double n = noise(x, Y, z, SEED + 1);
                    boolean joint = (d % 3.2) < 0.55;
                    b = joint ? "minecraft:andesite" : ((ring + seg) % 2 == 0)
                            ? (n < 0.8 ? "minecraft:stone_bricks" : "minecraft:cracked_stone_bricks")
                            : (n < 0.75 ? "minecraft:polished_andesite" : "minecraft:mossy_stone_bricks");
                }
                p.set(x, Y, z, b);
                p.set(x, Y - 1, z, "minecraft:stone");
            }
    }

    // =====================================================================
    //  Fontana della Pepita Gigante
    // =====================================================================

    static void fountain(BuildPlan p) {
        for (int x = -9; x <= 9; x++)
            for (int z = -9; z <= 9; z++) {
                double d = r(x, z);
                if (d > 8.6) continue;
                if (d > 7.6) {
                    p.set(x, Y + 1, z, "minecraft:polished_blackstone_bricks");
                    p.set(x, Y + 2, z, "minecraft:polished_blackstone_brick_slab[type=bottom,waterlogged=false]");
                } else {
                    p.set(x, Y, z, "minecraft:water[level=0]");
                    p.set(x, Y + 1, z, "minecraft:water[level=0]");
                    p.set(x, Y - 1, z, "minecraft:dark_prismarine");
                    if (d < 7 && noise(x, 0, z, SEED + 3) < 0.07) p.set(x, Y, z, "minecraft:sea_lantern");
                }
            }
        // basamento a gradoni e colonne con catene d'oro
        for (int x = -3; x <= 3; x++)
            for (int z = -3; z <= 3; z++) {
                int d = Math.max(Math.abs(x), Math.abs(z));
                for (int y = Y; y <= Y + 4 - d; y++)
                    p.set(x, y, z, d == 3 ? "minecraft:polished_blackstone_bricks" : d == 2 ? "minecraft:chiseled_polished_blackstone" : "minecraft:polished_blackstone");
            }
        for (int y = Y + 4; y <= Y + 6; y++) p.set(0, y, 0, "minecraft:gold_block");
        nugget(p, 0, Y + 10, 0, 3.4, 3.6, 3.0, SEED + 5);
        // getti d'acqua dagli angoli
        for (int sx = -1; sx <= 1; sx += 2)
            for (int sz = -1; sz <= 1; sz += 2) {
                p.set(sx * 3, Y + 2, sz * 3, "minecraft:chiseled_polished_blackstone");
                p.set(sx * 3, Y + 3, sz * 3, "minecraft:water[level=0]");
                p.set(sx * 6, Y + 2, sz * 6, "minecraft:polished_blackstone_wall");
                p.set(sx * 6, Y + 3, sz * 6, "minecraft:lantern[hanging=false,waterlogged=false]");
            }
    }

    /** Pepita d'oro irregolare (oro grezzo + oro). */
    static void nugget(BuildPlan p, int cx, int cy, int cz, double rx, double ry, double rz, long seed) {
        int ix = (int) Math.ceil(rx + 1), iy = (int) Math.ceil(ry + 1), iz = (int) Math.ceil(rz + 1);
        for (int x = -ix; x <= ix; x++)
            for (int y = -iy; y <= iy; y++)
                for (int z = -iz; z <= iz; z++) {
                    double v = (x / rx) * (x / rx) + (y / ry) * (y / ry) + (z / rz) * (z / rz);
                    double bump = (noise(x, y, z, seed) - 0.5) * 0.55;
                    if (v > 1 + bump) continue;
                    double n = noise(x, y, z, seed + 1);
                    p.set(cx + x, cy + y, cz + z, n < 0.62 ? "minecraft:raw_gold_block" : "minecraft:gold_block");
                }
    }

    // =====================================================================
    //  Bracci delle celle (due piani con ballatoio)
    // =====================================================================

    static void cellBlocks(BuildPlan p) {
        int R = (int) BLOCK_OUT + 1;
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++) {
                double d = r(x, z);
                if (d < BLOCK_IN || d >= BLOCK_OUT) continue;
                double a = a(x, z);
                if (!inBlockArc(a)) continue;
                boolean face = d < BLOCK_IN + 1;
                // celle di 9° l'una: muri divisori e finestre con sbarre
                double cellA = a / 9.0;
                double inCell = (cellA - Math.floor(cellA)) * (2 * Math.PI * d / 40);
                boolean divider = inCell < 0.8;
                for (int y = Y; y <= Y + 12; y++) {
                    int ly = y - Y;
                    String b;
                    if (ly == 0) b = "minecraft:polished_andesite";
                    else if (ly == 6) b = face ? "minecraft:stone_brick_slab[type=top,waterlogged=false]" : "minecraft:smooth_stone";
                    else if (ly == 12) b = "minecraft:smooth_stone_slab[type=bottom,waterlogged=false]";
                    else if (ly == 11) b = "minecraft:stone_bricks";
                    else if (face) {
                        boolean window = !divider && (ly == 3 || ly == 4 || ly == 8 || ly == 9) && Math.abs(inCell - 1.6) < 0.9;
                        boolean door = !divider && ly <= 2 && Math.abs(inCell - 1.6) < 0.5;
                        b = window ? "minecraft:iron_bars" : door ? null
                                : divider ? "minecraft:chiseled_stone_bricks" : noise(x, y, z, SEED + 11) < 0.78 ? "minecraft:stone_bricks" : "minecraft:cracked_stone_bricks";
                        if (door && ly == 1) b = "minecraft:iron_door[facing=" + face(-(x + 0.5), -(z + 0.5)) + ",half=lower,hinge=left,open=false,powered=false]";
                        if (door && ly == 2) b = "minecraft:iron_door[facing=" + face(-(x + 0.5), -(z + 0.5)) + ",half=upper,hinge=left,open=false,powered=false]";
                    } else if (d >= BLOCK_OUT - 1) b = "minecraft:stone_bricks";
                    else b = null; // interno vuoto (non si entra)
                    if (b != null) p.set(x, y, z, b);
                }
                // ballatoio del primo piano con ringhiera
            }
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++) {
                double d = r(x, z);
                if (d < BLOCK_IN - 1.6 || d >= BLOCK_IN) continue;
                double a = a(x, z);
                if (!inBlockArc(a)) continue;
                p.set(x, Y + 6, z, "minecraft:stone_brick_slab[type=top,waterlogged=false]");
                p.set(x, Y + 7, z, "minecraft:iron_bars");
                if (Math.floorMod((int) (a * 2), 9) == 0) {
                    p.set(x, Y + 5, z, stairs("stone_brick", face(x + 0.5, z + 0.5), true));
                    p.set(x, Y + 8, z, "minecraft:lantern[hanging=false,waterlogged=false]");
                }
            }
    }

    // =====================================================================
    //  Mura e torri
    // =====================================================================

    static void walls(BuildPlan p) {
        int R = (int) WALL_OUT + 1;
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++) {
                double d = r(x, z);
                if (d < WALL_IN || d >= WALL_OUT) continue;
                double a = a(x, z);
                boolean inner = d < WALL_IN + 1, outer = d >= WALL_OUT - 1;
                for (int y = Y - 1; y <= WALL_TOP; y++) {
                    double n = noise(x, y, z, SEED + 21);
                    String b;
                    if (y == WALL_TOP) b = "minecraft:polished_andesite";
                    else if (y == WALL_TOP - 1 && outer) b = "minecraft:chiseled_stone_bricks";
                    else if (y < Y + 2) b = "minecraft:polished_andesite";
                    else b = n < 0.62 ? "minecraft:stone_bricks" : n < 0.82 ? "minecraft:cracked_stone_bricks" : n < 0.95 ? "minecraft:mossy_stone_bricks" : "minecraft:andesite";
                    // feritoie esterne
                    if (outer && (y == Y + 9 || y == Y + 10) && Math.floorMod((int) a, 10) == 0) b = "minecraft:iron_bars";
                    p.set(x, y, z, b);
                }
                // merli verso l'esterno e filo spinato (ragnatele/sbarre)
                if (outer) {
                    boolean merlon = Math.floorMod((int) Math.floor(a * 1.6), 2) == 0;
                    if (merlon) {
                        p.set(x, WALL_TOP + 1, z, "minecraft:stone_bricks");
                        p.set(x, WALL_TOP + 2, z, "minecraft:stone_brick_slab[type=bottom,waterlogged=false]");
                    } else p.set(x, WALL_TOP + 1, z, "minecraft:iron_bars");
                }
                if (inner) p.set(x, WALL_TOP + 1, z, "minecraft:stone_brick_wall");
                // contrafforti esterni
                if (outer && Math.floorMod((int) Math.round(a), 15) == 0) {
                    int ox = (int) Math.round(Math.cos(Math.toRadians(a))), oz = (int) Math.round(Math.sin(Math.toRadians(a)));
                    for (int y = Y - 1; y <= WALL_TOP - 3; y++) p.set(x + ox, y, z + oz, "minecraft:stone_bricks");
                    p.set(x + ox, WALL_TOP - 2, z + oz, stairs("stone_brick", face(-ox, -oz), false));
                }
            }
    }

    /** Torre di guardia rotonda con faro in cima. */
    static void tower(BuildPlan p, double angDeg) {
        double a = Math.toRadians(angDeg);
        int cx = (int) Math.round(Math.cos(a) * 35.2), cz = (int) Math.round(Math.sin(a) * 35.2);
        int TOP = WALL_TOP + 9;
        for (int x = cx - 5; x <= cx + 5; x++)
            for (int z = cz - 5; z <= cz + 5; z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d > 4.5) continue;
                for (int y = Y - 1; y <= TOP; y++) {
                    boolean shell = d > 3.3;
                    String b = null;
                    if (shell) {
                        double n = noise(x, y, z, SEED + 31);
                        b = y % 7 == 0 ? "minecraft:polished_andesite" : n < 0.7 ? "minecraft:stone_bricks" : "minecraft:mossy_stone_bricks";
                        if ((y == Y + 12 || y == Y + 13 || y == TOP - 3) && Math.floorMod((int) Math.round(ang(x - cx, z - cz)), 45) < 12) b = "minecraft:iron_bars";
                    } else if (y == TOP) b = "minecraft:polished_andesite";
                    if (b != null) p.set(x, y, z, b);
                }
                // terrazza sporgente con merli
                if (d > 3.6) p.set(x, TOP + 1, z, Math.floorMod(x + z, 2) == 0 ? "minecraft:stone_bricks" : "minecraft:stone_brick_slab[type=bottom,waterlogged=false]");
            }
        for (int x = cx - 6; x <= cx + 6; x++)
            for (int z = cz - 6; z <= cz + 6; z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d > 5.6 || d <= 4.5) continue;
                p.set(x, TOP - 1, z, stairs("stone_brick", face(cx - x, cz - z), true));
                p.set(x, TOP, z, "minecraft:polished_andesite");
                if (Math.floorMod(x + z, 2) == 0) p.set(x, TOP + 1, z, "minecraft:stone_brick_wall");
            }
        // tetto conico in ardesia con il faro
        for (int k = 0; k <= 4; k++) {
            double rr = 4.2 - k;
            for (int x = cx - 5; x <= cx + 5; x++)
                for (int z = cz - 5; z <= cz + 5; z++) {
                    double d = Math.hypot(x - cx, z - cz);
                    if (d > rr || d <= rr - 1.2) continue;
                    p.set(x, TOP + 4 + k, z, stairs("deepslate_tile", face(cx - x, cz - z), false));
                }
        }
        for (int s = -1; s <= 1; s += 2) {
            p.set(cx + s * 2, TOP + 1, cz, "minecraft:stone_brick_wall");
            p.set(cx + s * 2, TOP + 2, cz, "minecraft:stone_brick_wall");
            p.set(cx, TOP + 1, cz + s * 2, "minecraft:stone_brick_wall");
            p.set(cx, TOP + 2, cz + s * 2, "minecraft:stone_brick_wall");
        }
        for (int x = cx - 2; x <= cx + 2; x++) for (int z = cz - 2; z <= cz + 2; z++) p.set(x, TOP + 3, z, "minecraft:polished_deepslate");
        p.set(cx, TOP + 1, cz, "minecraft:redstone_lamp[lit=true]");
        p.set(cx, TOP + 2, cz, "minecraft:sea_lantern");
        p.set(cx, TOP + 9, cz, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
    }

    // =====================================================================
    //  Cancello delle miniere (nord) con il portale
    // =====================================================================

    static void gatehouse(BuildPlan p) {
        int zf = PRISON_PORTAL_Z;          // -22: piano del portale
        // corpo del cancello tra il portale e le mura
        for (int x = -9; x <= 9; x++)
            for (int z = zf - 12; z <= zf; z++) {
                if (r(x, z) >= WALL_OUT) continue;
                int ax = Math.abs(x);
                int topY = ax <= 4 ? Y + 16 : Y + 13;
                for (int y = Y; y <= topY; y++) {
                    boolean front = z == zf;
                    double n = noise(x, y, z, SEED + 41);
                    String b = y == topY ? "minecraft:polished_andesite"
                            : front && y % 5 == 0 ? "minecraft:chiseled_stone_bricks"
                            : n < 0.7 ? "minecraft:stone_bricks" : n < 0.88 ? "minecraft:cracked_stone_bricks" : "minecraft:mossy_stone_bricks";
                    if (!front && z > zf - 11 && ax < 8 && y > Y && y < topY - 1) continue; // interno vuoto
                    p.set(x, y, z, b);
                }
            }
        // varco del portale e cornice monumentale
        for (int x = -3; x <= 3; x++)
            for (int y = Y + 1; y <= Y + 6; y++) {
                p.remove(x, y, zf);
                p.set(x, y, zf - 1, "minecraft:obsidian");
            }
        for (int x = -2; x <= 2; x++) for (int y = Y + 1; y <= Y + 5; y++) p.set(x, y, zf, "minecraft:nether_portal[axis=x]");
        for (int y = Y + 1; y <= Y + 5; y++) {
            p.set(-3, y, zf, "minecraft:crying_obsidian");
            p.set(3, y, zf, "minecraft:crying_obsidian");
        }
        for (int x = -3; x <= 3; x++) p.set(x, Y + 6, zf, Math.abs(x) <= 1 ? "minecraft:gold_block" : "minecraft:crying_obsidian");
        // colonne, architrave e saracinesca sollevata
        for (int s = -1; s <= 1; s += 2) {
            for (int y = Y + 1; y <= Y + 9; y++) {
                p.set(s * 5, y, zf + 1, y == Y + 1 ? "minecraft:polished_blackstone_bricks" : y == Y + 9 ? "minecraft:chiseled_polished_blackstone" : "minecraft:polished_blackstone");
                p.set(s * 5, y, zf, "minecraft:polished_blackstone_bricks");
            }
            p.set(s * 5, Y + 10, zf + 1, "minecraft:soul_lantern[hanging=false,waterlogged=false]");
            p.set(s * 7, Y + 1, zf + 1, "minecraft:polished_blackstone");
            p.set(s * 7, Y + 2, zf + 1, "minecraft:soul_campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]");
            for (int y = Y + 3; y <= Y + 8; y++) p.set(s * 4, y, zf + 1, "minecraft:iron_chain[axis=y,waterlogged=false]");
        }
        for (int x = -5; x <= 5; x++) {
            p.set(x, Y + 9, zf + 1, Math.abs(x) <= 1 ? "minecraft:gilded_blackstone" : "minecraft:polished_blackstone_bricks");
            p.set(x, Y + 8, zf + 1, Math.abs(x) <= 3 ? "minecraft:iron_bars" : "minecraft:polished_blackstone_bricks");
        }
        // gradini davanti al portale
        for (int x = -4; x <= 4; x++) p.set(x, Y, zf + 1, "minecraft:polished_blackstone_bricks");
        for (int x = -4; x <= 4; x++) p.set(x, Y, zf + 2, "minecraft:polished_deepslate");
        // torrette ai lati del cancello
        for (int s = -1; s <= 1; s += 2)
            for (int y = Y + 13; y <= Y + 19; y++)
                for (int dx = 0; dx <= 2; dx++)
                    for (int dz = 0; dz <= 2; dz++) {
                        boolean edge = dx != 1 || dz != 1;
                        int x = s * 7 + (s < 0 ? -dx : dx), z = zf - dz;
                        if (y == Y + 19) {
                            if ((dx + dz) % 2 == 0) p.set(x, y, z, "minecraft:stone_bricks");
                        } else if (edge) p.set(x, y, z, y == Y + 17 && (dx == 1 || dz == 1) ? "minecraft:iron_bars" : "minecraft:stone_bricks");
                    }
    }

    // =====================================================================
    //  Padiglione delle casse (est)
    // =====================================================================

    static void crates(BuildPlan p) {
        // pedana, colonnato e tetto a spioventi; parete di fondo con lo stemma
        for (int x = 19; x <= 27; x++)
            for (int z = -10; z <= 10; z++) {
                p.set(x, Y, z, x == 19 || Math.abs(z) == 10 ? "minecraft:polished_blackstone_bricks" : (x + z) % 2 == 0 ? "minecraft:smooth_quartz" : "minecraft:polished_diorite");
            }
        for (int z = -10; z <= 10; z++)
            for (int y = Y + 1; y <= Y + 11; y++) {
                p.set(28, y, z, y % 4 == 0 ? "minecraft:chiseled_quartz_block" : "minecraft:quartz_bricks");
                p.set(29, y, z, "minecraft:stone_bricks");
            }
        for (int zc : new int[]{-10, -4, 4, 10})
            for (int y = Y + 1; y <= Y + 8; y++) p.set(19, y, zc, y == Y + 8 ? "minecraft:chiseled_quartz_block" : "minecraft:quartz_pillar[axis=y]");
        for (int x = 19; x <= 28; x++)
            for (int z = -11; z <= 11; z++) {
                int dzEdge = Math.min(z + 11, 11 - z);
                int y = Y + 9 + Math.min(4, dzEdge / 2);
                if (x == 19 || x == 28) p.set(x, Y + 9, z, "minecraft:quartz_bricks");
                p.set(x, y, z, Math.abs(z) <= 1 ? "minecraft:gold_block" : "minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]");
            }
        for (int z = -9; z <= 9; z += 3) p.set(20, Y + 8, z, "minecraft:lantern[hanging=true,waterlogged=false]");
        // le quattro casse, ognuna sul suo piedistallo
        String[] blocks = {"minecraft:chest[facing=west,type=single,waterlogged=false]", "minecraft:ender_chest[facing=west,waterlogged=false]",
                "minecraft:purple_shulker_box[facing=up]", "minecraft:yellow_shulker_box[facing=up]"};
        String[] carpet = {"white", "light_blue", "purple", "yellow"};
        for (int i = 0; i < 4; i++) {
            int[] c = PRISON_CRATES[i];
            p.set(c[0], c[1], c[2], blocks[i]);
            p.set(c[0], c[1] - 1, c[2], "minecraft:gold_block");
            for (int dz = -1; dz <= 1; dz++) {
                p.set(c[0] - 1, c[1], c[2] + dz, "minecraft:" + carpet[i] + "_carpet");
                p.set(c[0] + 2, c[1], c[2] + dz, "minecraft:" + carpet[i] + "_carpet");
            }
            p.set(c[0] + 3, c[1] + 4, c[2], "minecraft:" + carpet[i] + "_wall_banner[facing=west]");
        }
        // stemma: pepita d'oro sulla parete di fondo, sopra il titolo
        nugget(p, 27, Y + 12, 0, 1.4, 1.6, 1.8, SEED + 51);
    }

    // =====================================================================
    //  Galleria delle classifiche (ovest)
    // =====================================================================

    static void boards(BuildPlan p) {
        for (int x = -28; x <= -19; x++)
            for (int z = -12; z <= 12; z++) p.set(x, Y, z, x == -19 || Math.abs(z) == 12 ? "minecraft:polished_blackstone_bricks" : "minecraft:polished_andesite");
        // parete di fondo in deepslate con tre nicchie e cornici d'oro
        for (int z = -12; z <= 12; z++)
            for (int y = Y + 1; y <= Y + 12; y++) {
                p.set(-27, y, z, "minecraft:polished_deepslate");
                p.set(-28, y, z, "minecraft:deepslate_bricks");
                if (y == Y + 12) p.set(-26, y, z, stairs("polished_deepslate", "east", true));
            }
        for (int[] b : PRISON_BOARDS) {
            p.set(b[0], b[1], b[2], "minecraft:chiseled_polished_blackstone");
            p.set(b[0], b[1] - 1, b[2], "minecraft:gold_block");
            for (int dz = -3; dz <= 3; dz++)
                for (int y = Y + 1; y <= Y + 7; y++) {
                    boolean frame = Math.abs(dz) == 3 || y == Y + 1 || y == Y + 7;
                    p.set(-26, y, b[2] + dz, frame ? "minecraft:gold_block" : "minecraft:black_concrete");
                }
            p.set(-25, Y + 8, b[2], "minecraft:lantern[hanging=true,waterlogged=false]");
        }
        for (int z : new int[]{-12, -4, 4, 12})
            for (int y = Y + 1; y <= Y + 9; y++) p.set(-19, y, z, y == Y + 9 ? "minecraft:chiseled_polished_blackstone" : "minecraft:polished_blackstone_wall");
        for (int z = -12; z <= 12; z++) p.set(-19, Y + 10, z, "minecraft:polished_blackstone_brick_slab[type=bottom,waterlogged=false]");
    }

    // =====================================================================
    //  Mercato (sud): incantesimi, negozio, corazza, battle pass, giornaliero
    // =====================================================================

    static void market(BuildPlan p) {
        stall(p, PRISON_ENCHANT, "minecraft:enchanting_table", "purple", "bookshelf");
        stall(p, PRISON_SHOP, "minecraft:emerald_block", "lime", "emerald_block");
        stall(p, PRISON_ARMOR, "minecraft:smithing_table", "cyan", "anvil[facing=east]");
        stall(p, PRISON_PASS, "minecraft:lectern[facing=south,has_book=false,powered=false]", "orange", "chiseled_bookshelf[facing=south,slot_0_occupied=false,slot_1_occupied=false,slot_2_occupied=false,slot_3_occupied=false,slot_4_occupied=false,slot_5_occupied=false]");
        // giornaliero: barile su un palco con l'insegna della posta
        int[] d = PRISON_DAILY;
        for (int x = d[0] - 2; x <= d[0] + 2; x++)
            for (int z = d[2] - 1; z <= d[2] + 1; z++) p.set(x, Y, z, "minecraft:spruce_planks");
        p.set(d[0], d[1], d[2], "minecraft:barrel[facing=up,open=false]");
        for (int s = -1; s <= 1; s += 2) {
            p.set(d[0] + s * 2, Y + 1, d[2], "minecraft:barrel[facing=up,open=false]");
            p.set(d[0] + s * 2, Y + 2, d[2], "minecraft:lantern[hanging=false,waterlogged=false]");
            for (int y = Y + 1; y <= Y + 4; y++) p.set(d[0] + s * 3, y, d[2] + 1, "minecraft:spruce_fence");
        }
        for (int x = d[0] - 3; x <= d[0] + 3; x++) p.set(x, Y + 5, d[2] + 1, "minecraft:lime_wool");
        for (int x = d[0] - 3; x <= d[0] + 3; x++) p.set(x, Y + 5, d[2] + 2, "minecraft:white_wool");
        // panchine lungo il viale dello spawn
        for (int s = -1; s <= 1; s += 2) {
            Deco.bench(p, s * 4, Y + 1, 26, s < 0 ? "east" : "west", "spruce", 2);
            Deco.lamp(p, s * 4, Y, 29, "minecraft:polished_blackstone_wall", "minecraft:lantern", 3, false);
        }
    }

    /** Bancarella con tendone a strisce attorno a un blocco interattivo (che resta libero sopra). */
    static void stall(BuildPlan p, int[] pos, String block, String color, String side) {
        int x0 = pos[0], z0 = pos[2];
        for (int x = x0 - 2; x <= x0 + 2; x++) for (int z = z0 - 2; z <= z0 + 2; z++) p.set(x, Y, z, "minecraft:spruce_planks");
        p.set(x0, pos[1], z0, block);
        p.set(x0 - 1, pos[1], z0 + 1, "minecraft:" + color + "_carpet");
        p.set(x0 + 1, pos[1], z0 + 1, "minecraft:" + color + "_carpet");
        p.set(x0 - 1, pos[1], z0, "minecraft:" + side);
        p.set(x0 + 1, pos[1], z0, "minecraft:" + side);
        for (int sx = -1; sx <= 1; sx += 2)
            for (int sz = -1; sz <= 1; sz += 2)
                for (int y = Y + 1; y <= Y + 4; y++) p.set(x0 + sx * 2, y, z0 + sz * 2, "minecraft:spruce_fence");
        for (int x = x0 - 2; x <= x0 + 2; x++)
            for (int z = z0 - 2; z <= z0 + 2; z++) {
                boolean stripe = Math.floorMod(x - x0, 2) == 0;
                p.set(x, Y + 5, z, stripe ? "minecraft:" + color + "_wool" : "minecraft:white_wool");
            }
        p.set(x0, Y + 4, z0 - 1, "minecraft:lantern[hanging=true,waterlogged=false]");
    }

    // =====================================================================
    //  Giardini interni e spazio esterno
    // =====================================================================

    static void gardens(BuildPlan p, Island isl) {
        int[][] trees = {{14, 13}, {-14, 13}, {14, -13}, {-14, -13}};
        Random r = new Random(SEED + 61);
        for (int[] t : trees) {
            for (int x = -3; x <= 3; x++)
                for (int z = -3; z <= 3; z++) {
                    boolean edge = Math.abs(x) == 3 || Math.abs(z) == 3;
                    p.set(t[0] + x, Y, t[1] + z, edge ? "minecraft:polished_blackstone_bricks" : "minecraft:moss_block");
                    if (edge) p.set(t[0] + x, Y + 1, t[1] + z, "minecraft:polished_blackstone_brick_slab[type=bottom,waterlogged=false]");
                }
            Deco.tree(p, t[0], Y + 1, t[1], Deco.TreeType.CHERRY, 0.85, SEED + t[0] * 7L + t[1]);
            for (int x = -2; x <= 2; x++)
                for (int z = -2; z <= 2; z++)
                    if (!p.has(t[0] + x, Y + 1, t[1] + z) && r.nextInt(3) == 0)
                        p.set(t[0] + x, Y + 1, t[1] + z, "minecraft:pink_petals[facing=north,flower_amount=" + (1 + r.nextInt(4)) + "]");
        }
        // lampioni sul cerchio dei 20 blocchi
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(22.5 + k * 45);
            int x = (int) Math.round(Math.cos(a) * 20), z = (int) Math.round(Math.sin(a) * 20);
            Deco.lamp(p, x, Y, z, "minecraft:polished_blackstone_wall", "minecraft:lantern", 3, true, Math.abs(Math.cos(a)) > 0.5 ? "z" : "x");
        }
    }

    static void outside(BuildPlan p, Island isl) {
        Random r = new Random(SEED + 71);
        int R = PRISON_RADIUS + 8;
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++) {
                double d = r(x, z);
                if (d < WALL_OUT + 0.5 || isl.topAt(x, z) != Y || p.has(x, Y + 1, z)) continue;
                String m = Deco.meadow(noise(x, 0, z, SEED + 72), noise(x, 1, z, SEED + 73));
                if (m != null) p.set(x, Y + 1, z, m);
            }
        for (int k = 0; k < 18; k++) {
            double a = r.nextDouble() * Math.PI * 2, d = WALL_OUT + 4 + r.nextDouble() * 6;
            int x = (int) Math.round(Math.cos(a) * d), z = (int) Math.round(Math.sin(a) * d);
            if (isl.topAt(x, z) != Y || isl.t(x, z) > 0.9) continue;
            Deco.TreeType t = r.nextInt(3) == 0 ? Deco.TreeType.CHERRY : r.nextBoolean() ? Deco.TreeType.CYPRESS : Deco.TreeType.OAK;
            Deco.tree(p, x, Y + 1, z, t, 0.75 + r.nextDouble() * 0.3, SEED + k);
        }
    }

    // =====================================================================
    //  Aria garantita (spawn, ologrammi, logo)
    // =====================================================================

    static void keepClear(BuildPlan p) {
        int lx = (int) Math.floor(PRISON_LOGO[0]), ly = (int) Math.floor(PRISON_LOGO[1]), lz = (int) Math.floor(PRISON_LOGO[2]);
        clear(p, lx - 9, ly - 7, lz - 3, lx + 9, ly + 7, lz + 3);
        int sx = (int) Math.floor(PRISON_SPAWN[0]), sz = (int) Math.floor(PRISON_SPAWN[2]);
        clear(p, sx - 1, Y + 1, sz - 1, sx + 1, Y + 3, sz + 1);
        // sopra i blocchi interattivi e le classifiche
        int[][] spots = {PRISON_ENCHANT, PRISON_SHOP, PRISON_DAILY, PRISON_ARMOR, PRISON_PASS};
        for (int[] s : spots) clear(p, s[0], s[1] + 1, s[2], s[0], s[1] + 3, s[2]);
        for (int[] c : PRISON_CRATES) clear(p, c[0], c[1] + 1, c[2], c[0], c[1] + 3, c[2]);
        for (int[] b : PRISON_BOARDS) clear(p, b[0] - 1, b[1] + 1, b[2] - 2, b[0] + 1, b[1] + 6, b[2] + 2);
        // titoli "CASSE" e "CLASSIFICHE" e benvenuto
        clear(p, 22, 104, -2, 25, 107, 2);
        clear(p, -25, 104, -2, -22, 107, 2);
        clear(p, -1, Y + 1, 22, 1, Y + 4, 25);
        clear(p, -1, 105, PRISON_PORTAL_Z + 1, 1, 107, PRISON_PORTAL_Z + 1);
    }
}

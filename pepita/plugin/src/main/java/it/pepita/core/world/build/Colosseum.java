package it.pepita.core.world.build;

import it.pepita.core.world.BuildPlan;
import it.pepita.core.world.CellGeometry;

import static it.pepita.core.world.Layout.*;
import static it.pepita.core.world.build.BuildUtil.*;

/**
 * Il Colosseo delle celle: arena in sabbia e travertino con la statua della Pepita, podio con le gallerie e i vomitori,
 * 10 piani di celle (CellGeometry), ballatoi con ringhiera e lanterne, 4 ascensori di vetro e oro, facciata esterna
 * ad archi su quattro ordini con lesene e cornici, attico finestrato e coronamento con merli, statue, bracieri e stendardi.
 */
final class Colosseum {
    private Colosseum() {}

    static final long SEED = 2626;
    static final int Y = COL_ARENA_Y;                          // 80
    static final int ROOF = CELL_Y0 + CELL_FLOORS * CELL_FH;   // 144
    static final double SH_IN = 50, SH_OUT = 53;                // guscio esterno
    static final int BAYS = 64;
    static final int[] ORDERS = {Y, 96, 112, 128};              // basi dei quattro ordini di archi (alti 16)
    static final int ATTIC = ROOF;                              // attico 144..158
    static final int CROWN = 159;

    static double rad(int x, int z) {
        return CellGeometry.radius(x, z);
    }

    static double angDeg(int x, int z) {
        return Math.toDegrees(CellGeometry.angle(x, z));
    }

    static void build(BuildPlan p) {
        island(p);
        arena(p);
        podium(p);
        for (int f = 0; f < CELL_FLOORS; f++)
            for (int s = 0; s < CELL_SECTORS; s++) ColosseumBuild.buildCell(p, f, s, true);
        roof(p);
        balconies(p);
        shell(p);
        crown(p);
        elevators(p);
        vomitoria(p);
        terrace(p);
        finish(p);
    }

    // =====================================================================
    //  Isola e terrazza
    // =====================================================================

    static void island(BuildPlan p) {
        Island isl = new Island(0, 0, Y - 1, 72, 34, SEED);
        isl.rough = 0.09;
        isl.mat = (x, y, z, ft, fb, side) -> {
            double n = noise(x, y, z, SEED + 5);
            if (ft <= 1) return n < 0.6 ? "minecraft:dirt" : "minecraft:coarse_dirt";
            double o = noise(x, y, z, SEED + 6);
            if (o < 0.03) return "minecraft:gold_ore";
            return n < 0.4 ? "minecraft:calcite" : n < 0.65 ? "minecraft:diorite" : n < 0.85 ? "minecraft:stone" : "minecraft:smooth_sandstone";
        };
        isl.prepare();
        isl.build(p);
        // prato con cipressi e ulivi attorno alla terrazza
        java.util.Random r = new java.util.Random(SEED + 7);
        for (int k = 0; k < 40; k++) {
            double a = r.nextDouble() * Math.PI * 2, d = 64 + r.nextDouble() * 5;
            int x = (int) Math.round(Math.cos(a) * d), z = (int) Math.round(Math.sin(a) * d);
            if (isl.topAt(x, z) != Y - 1 || isl.t(x, z) > 0.92) continue;
            Deco.tree(p, x, Y, z, r.nextInt(3) == 0 ? Deco.TreeType.OLIVE : Deco.TreeType.CYPRESS, 0.7 + r.nextDouble() * 0.4, SEED + k);
        }
        for (int x = -76; x <= 76; x++)
            for (int z = -76; z <= 76; z++) {
                if (isl.topAt(x, z) != Y - 1 || rad(x, z) < 63 || p.has(x, Y, z)) continue;
                String m = Deco.meadow(noise(x, 0, z, SEED + 8), noise(x, 1, z, SEED + 9));
                if (m != null) p.set(x, Y, z, m);
            }
    }

    /** Terrazza in travertino attorno al colosseo con gradoni verso il prato. */
    static void terrace(BuildPlan p) {
        for (int x = -64; x <= 64; x++)
            for (int z = -64; z <= 64; z++) {
                double r = rad(x, z);
                if (r < SH_OUT || r >= 63) continue;
                if (r < 60) {
                    double a = angDeg(x, z);
                    boolean ray = Math.floorMod((int) Math.round(a / (360.0 / BAYS)), 2) == 0;
                    p.set(x, Y, z, r < 54 ? "minecraft:smooth_sandstone" : ray ? "minecraft:cut_sandstone" : "minecraft:smooth_sandstone");
                    p.set(x, Y - 1, z, "minecraft:sandstone");
                } else {
                    String in = face(-(x + 0.5), -(z + 0.5));
                    p.set(x, Y - 1, z, stairs("smooth_sandstone", in, false));
                    p.remove(x, Y, z);
                }
            }
        // lampioni sulla terrazza davanti ai pilastri principali
        for (int b = 0; b < BAYS; b += 4) {
            double a = Math.toRadians((b + 0.5) * 360.0 / BAYS);
            int x = (int) Math.floor(Math.cos(a) * 57.5), z = (int) Math.floor(Math.sin(a) * 57.5);
            if (b % 16 == 0) continue; // davanti agli ingressi
            Deco.lamp(p, x, Y + 1, z, "minecraft:polished_blackstone_wall", "minecraft:lantern", 3, false);
        }
    }

    // =====================================================================
    //  Arena
    // =====================================================================

    static void arena(BuildPlan p) {
        for (int x = -33; x <= 33; x++)
            for (int z = -33; z <= 33; z++) {
                double r = rad(x, z);
                if (r >= BALCONY_IN) continue;
                double a = angDeg(x, z);
                String b;
                if (r < 4.5) b = "minecraft:gold_block";
                else if (r < 5.5) b = "minecraft:chiseled_sandstone";
                else if (r < 9) b = Math.floorMod((int) (a / 22.5), 2) == 0 ? "minecraft:smooth_sandstone" : "minecraft:cut_sandstone";
                else if (r < 10) b = "minecraft:smooth_quartz";
                else if (r > 30.5) b = "minecraft:smooth_sandstone";
                else {
                    double n = noise(x, 0, z, SEED + 11);
                    // rosa dei venti: otto raggi in pietra lavorata, il resto sabbia battuta
                    boolean ray = Math.abs(((a + 11.25) % 45) - 11.25) * Math.PI / 180 * r < 1.1;
                    b = ray ? "minecraft:cut_sandstone" : n < 0.55 ? "minecraft:sand" : n < 0.8 ? "minecraft:smooth_sandstone" : "minecraft:sandstone";
                }
                p.set(x, Y, z, b);
                p.set(x, Y - 1, z, "minecraft:sandstone");
            }
        // statua della Pepita sulla fontana centrale
        for (int x = -4; x <= 4; x++)
            for (int z = -4; z <= 4; z++) {
                double r = rad(x, z);
                if (r > 4.4) continue;
                if (r > 3.4) p.set(x, Y + 1, z, "minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]");
                else p.set(x, Y, z, "minecraft:water[level=0]");
            }
        for (int y = 1; y <= 4; y++) {
            for (int x = -1; x <= 0; x++) for (int z = -1; z <= 0; z++) p.set(x, Y + y, z, y == 4 ? "minecraft:chiseled_quartz_block" : "minecraft:quartz_pillar[axis=y]");
        }
        PrisonBuild.nugget(p, 0, Y + 8, 0, 3.0, 3.2, 2.6, SEED + 13);
        // quattro lampioni e fioriere attorno alla fontana
        for (int k = 0; k < 4; k++) {
            double a = Math.toRadians(45 + k * 90);
            int x = (int) Math.round(Math.cos(a) * 8), z = (int) Math.round(Math.sin(a) * 8);
            Deco.lamp(p, x, Y + 1, z, "minecraft:polished_blackstone_wall", "minecraft:lantern", 3, true, k % 2 == 0 ? "x" : "z");
        }
        // anello di bracieri e rastrelliere lungo il muro del podio
        for (int b = 0; b < 32; b++) {
            double a = Math.toRadians((b + 0.5) * 360.0 / 32);
            int x = (int) Math.floor(Math.cos(a) * 30.5), z = (int) Math.floor(Math.sin(a) * 30.5);
            if (b % 8 == 4 || b % 8 == 3) continue;
            if (b % 2 == 0) Deco.brazier(p, x, Y + 1, z, "minecraft:chiseled_sandstone", false);
            else {
                p.set(x, Y + 1, z, "minecraft:barrel[facing=up,open=false]");
                p.set(x, Y + 2, z, "minecraft:spruce_fence");
            }
        }
    }

    // =====================================================================
    //  Podio: muro dell'arena, gallerie sotto il ballatoio, basamento sotto le celle
    // =====================================================================

    static void podium(BuildPlan p) {
        int R = 50;
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++) {
                double r = rad(x, z);
                if (r < 32 || r >= SH_IN) continue;
                double a = angDeg(x, z);
                for (int y = Y; y <= CELL_Y0 - 1; y++) {
                    int ly = y - Y;
                    String b = null;
                    if (r < BALCONY_IN) {
                        // muro dell'arena con archi ogni 1/32 di giro: dietro c'è la galleria sotto il ballatoio
                        double into = (a / (360.0 / 32)) % 1.0;
                        double s = Math.abs(into - 0.5) * (2 * Math.PI * r / 32);
                        boolean open = ly >= 1 && ly <= 2 && s < 1.6 || ly == 3 && s < 0.8;
                        if (ly == 0) b = "minecraft:smooth_sandstone";
                        else if (open) continue;
                        else b = ly == 3 ? "minecraft:chiseled_sandstone" : s > 2.2 ? "minecraft:cut_sandstone" : "minecraft:smooth_sandstone";
                    } else if (r < CELL_RIN) {
                        // galleria sotto il ballatoio del piano 0 (ascensori al livello dell'arena)
                        if (ly == 0) b = "minecraft:polished_andesite";
                        else continue;
                    } else if (r < CELL_ROUT) {
                        // basamento sotto le celle: pieno, con nicchie illuminate verso la galleria
                        boolean niche = r < CELL_RIN + 1 && ly == 2 && Math.floorMod((int) (a * 2), 23) == 0;
                        b = ly == 0 ? "minecraft:polished_andesite" : niche ? "minecraft:lantern[hanging=false,waterlogged=false]"
                                : noise(x, y, z, SEED + 21) < 0.75 ? "minecraft:stone_bricks" : "minecraft:cracked_stone_bricks";
                    } else {
                        // corridoio esterno (ambulacro) tra le celle e il guscio
                        b = ly == 0 ? "minecraft:smooth_sandstone" : null;
                    }
                    if (b != null) p.set(x, y, z, b);
                }
            }
        // lanterne appese nella galleria e stendardi sul muro dell'arena
        for (int k = 0; k < 32; k++) {
            double a = Math.toRadians((k + 0.5) * 360.0 / 32);
            int x = (int) Math.floor(Math.cos(a) * 35.5), z = (int) Math.floor(Math.sin(a) * 35.5);
            p.set(x, CELL_Y0 - 1, z, "minecraft:lantern[hanging=true,waterlogged=false]");
            if (k % 4 == 2) {
                int bx = (int) Math.floor(Math.cos(a) * 31.6), bz = (int) Math.floor(Math.sin(a) * 31.6);
                String in = face(-Math.cos(a), -Math.sin(a));
                p.set(bx, Y + 3, bz, "minecraft:" + (k % 8 == 2 ? "red" : "yellow") + "_wall_banner[facing=" + in + "]");
            }
        }
    }

    // =====================================================================
    //  Tetto e ballatoi
    // =====================================================================

    static void roof(BuildPlan p) {
        for (int x = -49; x <= 49; x++)
            for (int z = -49; z <= 49; z++) {
                double r = rad(x, z);
                if (r < BALCONY_IN || r >= SH_IN) continue;
                CellGeometry.Info i = CellGeometry.classify(x, ROOF, z);
                String b = r >= CELL_RIN && r < CELL_ROUT ? (i.kind() == CellGeometry.Kind.ROOF ? "minecraft:smooth_stone" : null)
                        : r < CELL_RIN ? "minecraft:polished_andesite" : "minecraft:smooth_sandstone";
                if (b == null) continue;
                if (r >= CELL_RIN && r < CELL_ROUT && Math.floorMod((int) (angDeg(x, z) / (360.0 / CELL_SECTORS) * 2), 2) == 0) b = "minecraft:smooth_stone_slab[type=double,waterlogged=false]";
                p.set(x, ROOF, z, b);
                // parapetto interno sopra l'ultimo ballatoio
                if (r < BALCONY_IN + 1) {
                    p.set(x, ROOF + 1, z, "minecraft:smooth_sandstone");
                    if (Math.floorMod(x + z, 2) == 0) p.set(x, ROOF + 2, z, "minecraft:smooth_sandstone_slab[type=bottom,waterlogged=false]");
                }
            }
    }

    static boolean nearElevator(int x, int z) {
        for (int[] pd : ColosseumBuild.elevatorPads()) if (Math.abs(pd[0] - x) <= 1 && Math.abs(pd[2] - z) <= 1) return true;
        return false;
    }

    static void balconies(BuildPlan p) {
        for (int f = 0; f < CELL_FLOORS; f++) {
            int y0 = CELL_Y0 + f * CELL_FH;
            String color = ColosseumBuild.FLOOR_COLOR[f % ColosseumBuild.FLOOR_COLOR.length];
            for (int x = -39; x <= 39; x++)
                for (int z = -39; z <= 39; z++) {
                    double r = rad(x, z);
                    if (r < BALCONY_IN || r >= CELL_RIN) continue;
                    double a = angDeg(x, z);
                    // pavimento con la fascia colorata del piano davanti alle porte
                    double into = (a / (360.0 / CELL_SECTORS)) % 1.0;
                    double s = Math.abs(into - 0.5) * (2 * Math.PI * r / CELL_SECTORS);
                    String b = r > CELL_RIN - 1.2 && s < 1.5 ? "minecraft:" + color + "_concrete"
                            : noise(x, y0, z, SEED + 31) < 0.7 ? "minecraft:polished_andesite" : "minecraft:andesite";
                    p.set(x, y0, z, b);
                    // ringhiera verso l'arena
                    if (r < BALCONY_IN + 1 && !nearElevator(x, z)) {
                        p.set(x, y0 + 1, z, "minecraft:stone_brick_wall");
                        if (s < 0.5 && Math.floorMod((int) Math.floor(a / (360.0 / CELL_SECTORS)), 2) == 0)
                            p.set(x, y0 + 2, z, "minecraft:lantern[hanging=false,waterlogged=false]");
                    }
                    // mensole sotto il ballatoio (vista dall'arena)
                    if (r < BALCONY_IN + 1 && f > 0 && Math.floorMod((int) Math.floor(a / (360.0 / 64)), 2) == 0)
                        p.set(x, y0 - 1, z, stairs("stone_brick", face(x + 0.5, z + 0.5), true));
                }
            // lanterne appese davanti a ogni porta
            for (int sct = 0; sct < CELL_SECTORS; sct++) {
                double a = (sct + 0.5) * CellGeometry.STEP;
                int x = (int) Math.floor(Math.cos(a) * 35.6), z = (int) Math.floor(Math.sin(a) * 35.6);
                if (!nearElevator(x, z)) p.set(x, y0 + CELL_FH - 1, z, "minecraft:lantern[hanging=true,waterlogged=false]");
            }
        }
    }

    // =====================================================================
    //  Guscio esterno: quattro ordini di archi
    // =====================================================================

    static void shell(BuildPlan p) {
        int R = 56;
        double bayDeg = 360.0 / BAYS;
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++) {
                double r = rad(x, z);
                if (r < SH_IN || r >= SH_OUT + 2) continue;
                double a = angDeg(x, z);
                int bay = (int) Math.floor(a / bayDeg);
                double into = a / bayDeg - bay;
                double bw = 2 * Math.PI * r / BAYS;                       // larghezza della campata in blocchi
                double s = (into - 0.5) * bw;                              // distanza dal centro dell'arco
                double fromPier = (0.5 - Math.abs(into - 0.5)) * bw;       // distanza dal centro del pilastro
                boolean entrance = bay % 16 == 0;                          // quattro ingressi monumentali
                String in = face(-(x + 0.5), -(z + 0.5)), out = opp(in);
                for (int o = 0; o < ORDERS.length; o++) {
                    int yb = ORDERS[o];
                    for (int y = yb; y < yb + 16; y++) {
                        int ly = y - yb;
                        String b = null;
                        boolean main = r < SH_OUT;
                        if (main) {
                            double w = entrance && o == 0 ? 2.2 : 1.55;
                            int archTop = (entrance && o == 0 ? 9 : 7);
                            double rise = Math.sqrt(Math.max(0, w * w - s * s)) * 1.6;
                            boolean open = ly >= 1 && ly <= archTop + rise && Math.abs(s) < w && ly < 14;
                            if (open) continue;
                            if (ly == 0 || ly == 15) b = ly == 15 ? "minecraft:chiseled_sandstone" : "minecraft:smooth_sandstone";
                            else if (Math.abs(s) < w + 0.9 && ly == (int) (archTop + rise) + 1) b = Math.abs(s) < 0.6 ? "minecraft:gold_block" : "minecraft:cut_sandstone";
                            else b = noise(x, y, z, SEED + 41) < 0.8 ? "minecraft:smooth_sandstone" : "minecraft:sandstone";
                            if (o == 3 && noise(x, y, z, SEED + 42) < 0.3) b = "minecraft:calcite";
                        } else {
                            // lesene (semicolonne) sui pilastri e cornici marcapiano
                            boolean pil = fromPier < 0.9 && r < SH_OUT + 1;
                            if (ly == 15 && r < SH_OUT + 1.5) b = stairs("smooth_sandstone", in, true);
                            else if (ly == 14 && r < SH_OUT + 1) b = "minecraft:smooth_sandstone";
                            else if (pil) {
                                String cap = switch (o) {
                                    case 0 -> "minecraft:chiseled_sandstone";
                                    case 1 -> "minecraft:chiseled_quartz_block";
                                    case 2 -> "minecraft:chiseled_quartz_block";
                                    default -> "minecraft:smooth_quartz";
                                };
                                b = ly == 13 ? cap : ly == 1 ? "minecraft:cut_sandstone" : ly == 12 && o == 2 ? "minecraft:gold_block"
                                        : o == 3 ? "minecraft:calcite" : "minecraft:smooth_sandstone";
                            }
                        }
                        if (b != null) p.set(x, y, z, b);
                    }
                }
                // lanterne appese negli archi del primo e secondo ordine
                if (r >= SH_IN + 1 && r < SH_IN + 2 && Math.abs(s) < 0.5) {
                    p.set(x, ORDERS[0] + 10, z, "minecraft:lantern[hanging=true,waterlogged=false]");
                    p.set(x, ORDERS[1] + 9, z, "minecraft:lantern[hanging=true,waterlogged=false]");
                }
            }
    }

    // =====================================================================
    //  Attico e coronamento
    // =====================================================================

    static void crown(BuildPlan p) {
        int R = 56;
        double bayDeg = 360.0 / BAYS;
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++) {
                double r = rad(x, z);
                if (r < SH_IN - 2 || r >= SH_OUT + 2) continue;
                double a = angDeg(x, z);
                int bay = (int) Math.floor(a / bayDeg);
                double into = a / bayDeg - bay;
                double bw = 2 * Math.PI * r / BAYS;
                double s = (into - 0.5) * bw;
                double fromPier = (0.5 - Math.abs(into - 0.5)) * bw;
                String in = face(-(x + 0.5), -(z + 0.5)), out = opp(in);
                // camminamento sul tetto tra le celle e l'attico
                if (r < SH_IN) {
                    p.set(x, ATTIC, z, "minecraft:smooth_sandstone");
                    continue;
                }
                for (int y = ATTIC; y < CROWN; y++) {
                    int ly = y - ATTIC;
                    String b = null;
                    if (r < SH_OUT) {
                        boolean window = ly >= 5 && ly <= 8 && Math.abs(s) < 1.0 && bay % 2 == 0;
                        if (window) b = r < SH_IN + 1 ? "minecraft:iron_bars" : null;
                        else b = ly == 0 ? "minecraft:smooth_sandstone" : noise(x, y, z, SEED + 51) < 0.6 ? "minecraft:smooth_sandstone" : "minecraft:calcite";
                    } else if (fromPier < 0.9 && r < SH_OUT + 1) {
                        b = ly == 13 ? "minecraft:smooth_quartz" : ly < 13 ? "minecraft:smooth_quartz" : null;
                    }
                    if (b != null) p.set(x, y, z, b);
                }
                // cornice finale e merli
                if (r < SH_OUT + 1.5) p.set(x, CROWN, z, r >= SH_OUT ? stairs("smooth_sandstone", in, true) : "minecraft:chiseled_sandstone");
                if (r >= SH_OUT - 1 && r < SH_OUT) {
                    boolean merlon = Math.floorMod(bay * 4 + (int) Math.floor(into * 4), 2) == 0;
                    p.set(x, CROWN + 1, z, "minecraft:smooth_sandstone");
                    if (merlon) {
                        p.set(x, CROWN + 2, z, "minecraft:smooth_sandstone");
                        p.set(x, CROWN + 3, z, "minecraft:smooth_sandstone_slab[type=bottom,waterlogged=false]");
                    }
                }
                if (r >= SH_IN && r < SH_OUT - 1) p.set(x, CROWN, z, "minecraft:smooth_sandstone");
            }
        // statue, bracieri e stendardi
        for (int b = 0; b < BAYS; b++) {
            double a = Math.toRadians((b + 0.5) * bayDeg);
            double ca = Math.cos(a), sa = Math.sin(a);
            String out = face(ca, sa);
            int x = (int) Math.floor(ca * 51.5), z = (int) Math.floor(sa * 51.5);
            if (b % 8 == 4) statue(p, x, CROWN + 1, z, out);
            else if (b % 4 == 2) Deco.brazier(p, x, CROWN + 1, z, "minecraft:chiseled_sandstone", false);
            if (b % 2 == 1) {
                int bx = (int) Math.floor(ca * 53.6), bz = (int) Math.floor(sa * 53.6);
                p.set(bx, ATTIC + 11, bz, "minecraft:" + (b % 4 == 1 ? "red" : "yellow") + "_wall_banner[facing=" + out + "]");
            }
        }
    }

    /** Statua di gladiatore stilizzata (pietra + elmo d'oro). */
    static void statue(BuildPlan p, int x, int y, int z, String facing) {
        p.set(x, y, z, "minecraft:chiseled_quartz_block");
        p.set(x, y + 1, z, "minecraft:smooth_quartz");
        p.set(x, y + 2, z, "minecraft:quartz_pillar[axis=y]");
        p.set(x, y + 3, z, "minecraft:quartz_block");
        p.set(x, y + 4, z, "minecraft:gold_block");
        int[] v = vec(cw(facing));
        p.set(x + v[0], y + 3, z + v[1], "minecraft:smooth_quartz_stairs[facing=" + ccw(facing) + ",half=top]");
        p.set(x - v[0], y + 3, z - v[1], "minecraft:smooth_quartz_stairs[facing=" + cw(facing) + ",half=top]");
        p.set(x + v[0], y + 4, z + v[1], "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
    }

    // =====================================================================
    //  Ascensori di vetro e oro
    // =====================================================================

    static void elevators(BuildPlan p) {
        for (int ang : ELEVATOR_ANGLES) {
            double a = Math.toRadians(ang);
            int cx = (int) Math.floor(Math.cos(a) * ELEVATOR_R), cz = (int) Math.floor(Math.sin(a) * ELEVATOR_R);
            for (int y = Y + 1; y <= ROOF + 6; y++)
                for (int dx = -1; dx <= 1; dx += 2)
                    for (int dz = -1; dz <= 1; dz += 2) {
                        int lvlY = (y - CELL_Y0) % CELL_FH;
                        boolean band = y >= CELL_Y0 && lvlY == 0 || y == Y + 1 || y == ROOF;
                        p.set(cx + dx, y, cz + dz, band ? "minecraft:gold_block" : "minecraft:yellow_stained_glass");
                    }
            // cupola d'oro in cima
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) p.set(cx + dx, ROOF + 7, cz + dz, "minecraft:gold_block");
            p.set(cx, ROOF + 8, cz, "minecraft:gold_block");
            p.set(cx, ROOF + 9, cz, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int y = ROOF + 1; y <= ROOF + 6; y++)
                if (dx == 0 || dz == 0) p.remove(cx + dx, y, cz + dz);
        }
        for (int[] pd : ColosseumBuild.elevatorPads()) {
            p.set(pd[0], pd[1] - 1, pd[2], "minecraft:gold_block");
            p.set(pd[0], pd[1], pd[2], "minecraft:light_weighted_pressure_plate[power=0]");
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                if ((dx == 0) != (dz == 0)) p.set(pd[0] + dx, pd[1] - 1, pd[2] + dz, "minecraft:smooth_quartz");
            // aria sopra la piastra
            for (int y = 1; y <= 2; y++) p.remove(pd[0], pd[1] + y, pd[2]);
            p.set(pd[0], pd[1] + (pd[3] < 0 ? CELL_Y0 - Y - 2 : CELL_FH - 2), pd[2], "minecraft:lantern[hanging=true,waterlogged=false]");
        }
    }

    // =====================================================================
    //  Vomitori: quattro gallerie dall'arena alla terrazza
    // =====================================================================

    static void vomitoria(BuildPlan p) {
        for (int k = 0; k < 4; k++) {
            double a = Math.toRadians((k * 16 + 0.5) * 360.0 / BAYS);
            double ca = Math.cos(a), sa = Math.sin(a);
            for (double d = 31; d <= SH_OUT + 1; d += 0.5)
                for (double w = -1.5; w <= 1.5; w += 0.5) {
                    int x = (int) Math.floor(ca * d - sa * w), z = (int) Math.floor(sa * d + ca * w);
                    for (int y = Y + 1; y <= Y + 3; y++) p.air(x, y, z);
                    p.set(x, Y, z, "minecraft:polished_andesite");
                }
            for (double d = 39; d <= 49; d += 3) {
                int x = (int) Math.floor(ca * d), z = (int) Math.floor(sa * d);
                p.set(x, Y + 3, z, "minecraft:lantern[hanging=true,waterlogged=false]");
            }
        }
    }
}

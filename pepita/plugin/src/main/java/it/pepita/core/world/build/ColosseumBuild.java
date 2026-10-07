package it.pepita.core.world.build;

import it.pepita.core.world.BuildPlan;
import it.pepita.core.world.CellGeometry;

import java.util.ArrayList;
import java.util.List;

import static it.pepita.core.world.Layout.*;
import static it.pepita.core.world.build.BuildUtil.*;

/**
 * Colosseo delle celle (mondo pepita_celle): arena centrale, 10 piani × 32 celle ad anello secondo
 * {@link CellGeometry#classify}, ballatoi con ringhiera, 4 ascensori in vetro e oro e facciata monumentale.
 * Classe pura: solo BuildPlan, Layout e CellGeometry.
 */
public final class ColosseumBuild {
    private ColosseumBuild() {}

    static final long SEED = 2626;
    static final int ROOF_Y = CELL_Y0 + CELL_FLOORS * CELL_FH;     // 144
    static final int RIN = (int) Math.floor(CELL_RIN);              // 38

    /** Colore della fascia/insegna di ogni piano (sopra le porte). */
    static final String[] FLOOR_COLOR = {"red", "orange", "yellow", "lime", "green", "cyan", "light_blue", "blue", "purple", "magenta"};

    // =====================================================================
    //  Celle (contratto con il plugin)
    // =====================================================================

    /** Indice del settore che contiene l'angolo (radianti). */
    static double sectorCenter(int sector) {
        return (sector + 0.5) * CellGeometry.STEP;
    }

    /**
     * Scrive tutti i blocchi della cella (piano, settore): pavimento, muro interno con porta di ferro, muro esterno con
     * finestra, i due muri laterali (indici sector e sector+1) e l'interno (aria + arredamento se furnish).
     */
    public static void buildCell(BuildPlan p, int floor, int sector, boolean furnish) {
        sector = Math.floorMod(sector, CELL_SECTORS);
        int y0 = CELL_Y0 + floor * CELL_FH;
        int R = (int) Math.ceil(CELL_ROUT) + 1;
        int sideB = (sector + 1) % CELL_SECTORS;
        double ac = sectorCenter(sector);
        double ca = Math.cos(ac), sa = Math.sin(ac);
        String inward = face(-ca, -sa);
        List<int[]> interiorFloor = new ArrayList<>();
        List<long[]> mine = new ArrayList<>();
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++) {
                double r = CellGeometry.radius(x, z);
                if (r < CELL_RIN || r >= CELL_ROUT) continue;
                double a = CellGeometry.angle(x, z);
                // scarta velocemente i blocchi lontani dal settore
                double da = Math.abs(((a - ac) % (2 * Math.PI) + 3 * Math.PI) % (2 * Math.PI) - Math.PI);
                if (da > CellGeometry.STEP * 0.75) continue;
                for (int y = y0; y < y0 + CELL_FH; y++) {
                    CellGeometry.Info i = CellGeometry.classify(x, y, z);
                    if (i.floor() != floor) continue;
                    boolean side = i.kind() == CellGeometry.Kind.WALL_SIDE && (i.sector() == sector || i.sector() == sideB);
                    if (!side && i.sector() != sector) continue;
                    String b = switch (i.kind()) {
                        case SLAB -> cellFloor(x, z, r);
                        case WALL_IN -> wallIn(x, y, z, i.ly(), floor);
                        case DOOR -> door(x, z, i.ly(), ac, inward);
                        case WALL_OUT -> wallOut(x, y, z, i.ly());
                        case WINDOW -> "minecraft:iron_bars";
                        case WALL_SIDE -> side ? wallSide(x, y, z, i.ly()) : null;
                        case INTERIOR -> BuildPlan.AIR;
                        default -> null;
                    };
                    if (b == null) continue;
                    p.set(x, y, z, b);
                    mine.add(new long[]{x, y, z});
                    if (i.kind() == CellGeometry.Kind.INTERIOR && i.ly() == 1) interiorFloor.add(new int[]{x, y, z});
                }
            }
        if (furnish) furnish(p, floor, sector, interiorFloor);
        java.util.Set<Long> keys = new java.util.HashSet<>();
        for (long[] m : mine) keys.add(pk((int) m[0], (int) m[1], (int) m[2]));
        finish(p, (x, y, z) -> keys.contains(pk(x, y, z)));
    }

    private static long pk(int x, int y, int z) {
        return ((long) x & 0xFFFFF) << 40 | ((long) z & 0xFFFFF) << 16 | (y & 0xFFFF);
    }

    static String cellFloor(int x, int z, double r) {
        double n = noise(x, 0, z, SEED + 1);
        if (r < CELL_RIN + 1.5) return "minecraft:polished_andesite";
        return n < 0.55 ? "minecraft:stone_bricks" : n < 0.8 ? "minecraft:polished_andesite" : n < 0.93 ? "minecraft:cracked_stone_bricks" : "minecraft:mossy_stone_bricks";
    }

    static String wallIn(int x, int y, int z, int ly, int floor) {
        if (ly == 3) return "minecraft:" + FLOOR_COLOR[floor % FLOOR_COLOR.length] + "_glazed_terracotta";
        if (ly == 5) return "minecraft:chiseled_stone_bricks";
        double n = noise(x, y, z, SEED + 2);
        return n < 0.7 ? "minecraft:stone_bricks" : n < 0.88 ? "minecraft:cracked_stone_bricks" : "minecraft:mossy_stone_bricks";
    }

    static String wallOut(int x, int y, int z, int ly) {
        double n = noise(x, y, z, SEED + 3);
        if (ly == 5) return "minecraft:smooth_sandstone";
        return n < 0.75 ? "minecraft:stone_bricks" : "minecraft:cracked_stone_bricks";
    }

    static String wallSide(int x, int y, int z, int ly) {
        double n = noise(x, y, z, SEED + 4);
        if (ly == 5) return "minecraft:polished_andesite";
        return n < 0.8 ? "minecraft:stone_bricks" : "minecraft:mossy_stone_bricks";
    }

    /** Porta di ferro: metà inferiore a ly=1, superiore a ly=2, cerniera opposta tra le due ante. */
    static String door(int x, int z, int ly, double ac, String inward) {
        // posizione tangenziale rispetto al centro della porta (verso antiorario = +)
        double tx = -Math.sin(ac), tz = Math.cos(ac);
        double u = (x + 0.5) * tx + (z + 0.5) * tz;
        String hinge = u < 0 ? "left" : "right";
        return "minecraft:iron_door[facing=" + inward + ",half=" + (ly == 1 ? "lower" : "upper") + ",hinge=" + hinge + ",open=false,powered=false]";
    }

    /** Arredamento da cella: branda, baule, scrivania con lanterna, gabinetto, libreria, tappeto, lanterna appesa. */
    static void furnish(BuildPlan p, int floor, int sector, List<int[]> fl) {
        double ac = sectorCenter(sector);
        double ca = Math.cos(ac), sa = Math.sin(ac);
        String out = face(ca, sa), in = opp(out);
        String tan = face(-sa, ca);           // tangente "sinistra" guardando la porta dall'interno
        java.util.Map<Long, int[]> by = new java.util.HashMap<>();
        for (int[] b : fl) by.put(pk(b[0], b[1], b[2]), b);
        // coordinate locali: t = profondità dal muro interno, u = spostamento tangenziale
        double best = -1;
        int[] bed = null, chest = null, toilet = null, desk = null, shelf = null;
        double bBed = -1e9, bChest = -1e9, bToilet = -1e9, bDesk = -1e9, bShelf = -1e9;
        for (int[] b : fl) {
            double cx = b[0] + 0.5, cz = b[2] + 0.5;
            double t = Math.hypot(cx, cz) - (CELL_RIN + 1);
            double u = cx * -sa + cz * ca;
            double sBed = t * 2 + (-u) * 1.2;           // in fondo a sinistra
            double sChest = t * 2 + u * 1.2;            // in fondo a destra
            double sToilet = -t * 2 - u * 1.4;          // davanti a sinistra
            double sDesk = u * 2 - Math.abs(t - 3.5) * 1.5;   // metà parete destra
            double sShelf = -u * 2 - Math.abs(t - 3.8) * 1.5; // metà parete sinistra
            if (sBed > bBed && neighbor(by, b, opp(out)) != null) { bBed = sBed; bed = b; }
            if (sChest > bChest) { bChest = sChest; chest = b; }
            if (sToilet > bToilet && t > 0.6) { bToilet = sToilet; toilet = b; }
            if (sDesk > bDesk) { bDesk = sDesk; desk = b; }
            if (sShelf > bShelf) { bShelf = sShelf; shelf = b; }
        }
        java.util.Set<Long> used = new java.util.HashSet<>();
        if (bed != null) {
            int[] foot = neighbor(by, bed, opp(out));
            if (foot != null) {
                p.set(bed[0], bed[1], bed[2], "minecraft:" + (floor % 2 == 0 ? "gray" : "light_gray") + "_bed[facing=" + out + ",part=head,occupied=false]");
                p.set(foot[0], foot[1], foot[2], "minecraft:" + (floor % 2 == 0 ? "gray" : "light_gray") + "_bed[facing=" + out + ",part=foot,occupied=false]");
                used.add(pk(bed[0], bed[1], bed[2]));
                used.add(pk(foot[0], foot[1], foot[2]));
            }
        }
        if (chest != null && used.add(pk(chest[0], chest[1], chest[2])))
            p.set(chest[0], chest[1], chest[2], "minecraft:chest[facing=" + in + ",type=single,waterlogged=false]");
        if (toilet != null && used.add(pk(toilet[0], toilet[1], toilet[2])))
            p.set(toilet[0], toilet[1], toilet[2], "minecraft:cauldron");
        if (desk != null && used.add(pk(desk[0], desk[1], desk[2]))) {
            p.set(desk[0], desk[1], desk[2], "minecraft:spruce_fence");
            p.set(desk[0], desk[1] + 1, desk[2], "minecraft:lantern[hanging=false,waterlogged=false]");
            int[] chair = neighbor(by, desk, BuildUtil.opp(tan));
            if (chair == null) chair = neighbor(by, desk, in);
            if (chair != null && used.add(pk(chair[0], chair[1], chair[2])))
                p.set(chair[0], chair[1], chair[2], "minecraft:spruce_stairs[facing=" + face(desk[0] - chair[0], desk[2] - chair[2]) + ",half=bottom]");
        }
        if (shelf != null && used.add(pk(shelf[0], shelf[1], shelf[2]))) {
            p.set(shelf[0], shelf[1], shelf[2], "minecraft:bookshelf");
            p.set(shelf[0], shelf[1] + 1, shelf[2], "minecraft:flower_pot");
            p.set(shelf[0], shelf[1] + 1, shelf[2], "minecraft:potted_" + (sector % 3 == 0 ? "cactus" : sector % 3 == 1 ? "fern" : "red_tulip"));
        }
        // tappeto al centro (lascia libero il varco della porta)
        for (int[] b : fl) {
            if (used.contains(pk(b[0], b[1], b[2]))) continue;
            double cx = b[0] + 0.5, cz = b[2] + 0.5;
            double t = Math.hypot(cx, cz) - (CELL_RIN + 1);
            double u = cx * -sa + cz * ca;
            if (t > 2.2 && t < 6.2 && Math.abs(u) < 2.2)
                p.set(b[0], b[1], b[2], "minecraft:" + (Math.abs(u) < 0.9 ? "red" : "gray") + "_carpet");
        }
        // lanterna appesa al soffitto al centro della cella
        double rc = (CELL_RIN + CELL_ROUT) / 2.0;
        int lx = (int) Math.floor(ca * rc), lz = (int) Math.floor(sa * rc);
        int ly = CELL_Y0 + floor * CELL_FH + CELL_FH - 1;
        if (CellGeometry.classify(lx, ly, lz).kind() == CellGeometry.Kind.INTERIOR)
            p.set(lx, ly, lz, "minecraft:lantern[hanging=true,waterlogged=false]");
    }

    private static int[] neighbor(java.util.Map<Long, int[]> by, int[] b, String dir) {
        int[] v = vec(dir);
        return by.get(pk(b[0] + v[0], b[1], b[2] + v[1]));
    }

    // =====================================================================
    //  Ascensori
    // =====================================================================

    /** Posizione della piastra di ogni ascensore per ogni livello: {x, y, z, livello} (-1 = arena, 0..9 = piani). */
    public static List<int[]> elevatorPads() {
        List<int[]> out = new ArrayList<>();
        for (int ang : ELEVATOR_ANGLES) {
            double a = Math.toRadians(ang);
            int x = (int) Math.floor(Math.cos(a) * ELEVATOR_R), z = (int) Math.floor(Math.sin(a) * ELEVATOR_R);
            out.add(new int[]{x, COL_ARENA_Y + 1, z, -1});
            for (int f = 0; f < CELL_FLOORS; f++) out.add(new int[]{x, CELL_Y0 + f * CELL_FH + 1, z, f});
        }
        return out;
    }

    // =====================================================================
    //  Colosseo
    // =====================================================================

    public static void build(BuildPlan p) {
        Colosseum.build(p);
    }
}

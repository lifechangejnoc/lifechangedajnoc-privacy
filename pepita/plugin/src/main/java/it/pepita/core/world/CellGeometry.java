package it.pepita.core.world;

import static it.pepita.core.world.Layout.*;

/**
 * Geometria delle celle del colosseo. Ogni blocco (x,y,z) viene classificato in modo univoco:
 * il costruttore e il gestore delle celle usano la stessa funzione, così protezioni e muri coincidono.
 *
 * Una cella è identificata da (piano, settore). Il settore 0 parte dall'asse +X e gira verso +Z.
 * Il muro laterale "b" separa il settore b-1 dal settore b.
 */
public final class CellGeometry {
    private CellGeometry() {}

    public enum Kind { NONE, SLAB, ROOF, WALL_IN, DOOR, WALL_OUT, WINDOW, WALL_SIDE, INTERIOR }

    public static final double STEP = Math.PI * 2 / CELL_SECTORS;

    /** Risultato della classificazione. sector = settore (o indice del muro laterale per WALL_SIDE). */
    public record Info(Kind kind, int floor, int sector, int ly) {
        public boolean isCellPart() { return kind != Kind.NONE; }
    }

    private static final Info NONE = new Info(Kind.NONE, -1, -1, -1);

    public static int id(int floor, int sector) {
        return floor * CELL_SECTORS + Math.floorMod(sector, CELL_SECTORS);
    }

    public static int floorOf(int id) { return id / CELL_SECTORS; }
    public static int sectorOf(int id) { return id % CELL_SECTORS; }

    public static double angle(int x, int z) {
        double a = Math.atan2(z + 0.5, x + 0.5);
        return a < 0 ? a + Math.PI * 2 : a;
    }

    public static double radius(int x, int z) {
        return Math.hypot(x + 0.5, z + 0.5);
    }

    public static Info classify(int x, int y, int z) {
        double r = radius(x, z);
        if (r < CELL_RIN || r >= CELL_ROUT) return NONE;
        int dy = y - CELL_Y0;
        if (dy < 0 || dy > CELL_FLOORS * CELL_FH) return NONE;
        double a = angle(x, z);
        int sector = (int) Math.floor(a / STEP);
        if (sector >= CELL_SECTORS) sector = CELL_SECTORS - 1;
        if (dy == CELL_FLOORS * CELL_FH) return new Info(Kind.ROOF, CELL_FLOORS - 1, sector, 0);
        int floor = dy / CELL_FH;
        int ly = dy % CELL_FH;
        if (ly == 0) return new Info(Kind.SLAB, floor, sector, 0);

        double into = a - sector * STEP;                    // radianti dall'inizio del settore
        double arcStart = into * r, arcEnd = (STEP - into) * r;
        double arcCenter = Math.abs(into - STEP / 2) * r;

        if (r < CELL_RIN + 1) {
            if (arcCenter < 1.0 && ly <= 2) return new Info(Kind.DOOR, floor, sector, ly);
            return new Info(Kind.WALL_IN, floor, sector, ly);
        }
        if (r >= CELL_ROUT - 1) {
            if (arcCenter < 1.6 && ly >= 2 && ly <= 3) return new Info(Kind.WINDOW, floor, sector, ly);
            return new Info(Kind.WALL_OUT, floor, sector, ly);
        }
        if (arcStart < 0.75) return new Info(Kind.WALL_SIDE, floor, sector, ly);
        if (arcEnd < 0.75) return new Info(Kind.WALL_SIDE, floor, (sector + 1) % CELL_SECTORS, ly);
        return new Info(Kind.INTERIOR, floor, sector, ly);
    }

    /** Centro della cella al pavimento (dove si appare con /cella). Restituisce x, y, z, yaw. */
    public static double[] home(int floor, int sector) {
        double a = (sector + 0.5) * STEP;
        double r = (CELL_RIN + CELL_ROUT) / 2.0;
        double x = Math.cos(a) * r, z = Math.sin(a) * r;
        // yaw Minecraft: direzione = (-sin yaw, cos yaw). Verso il centro = (-cos a, -sin a)
        double yaw = Math.toDegrees(Math.atan2(Math.cos(a), -Math.sin(a)));
        return new double[]{x, CELL_Y0 + floor * CELL_FH + 1, z, yaw};
    }

    /** Davanti alla porta, sul ballatoio. */
    public static double[] doorstep(int floor, int sector) {
        double a = (sector + 0.5) * STEP;
        double r = CELL_RIN - 1.5;
        double yaw = Math.toDegrees(Math.atan2(Math.cos(a), -Math.sin(a))) + 180;
        return new double[]{Math.cos(a) * r, CELL_Y0 + floor * CELL_FH + 1, Math.sin(a) * r, yaw};
    }

    /** Due celle sono adiacenti se stanno sullo stesso piano e in settori consecutivi. */
    public static boolean adjacent(int idA, int idB) {
        if (floorOf(idA) != floorOf(idB)) return false;
        int d = Math.floorMod(sectorOf(idA) - sectorOf(idB), CELL_SECTORS);
        return d == 1 || d == CELL_SECTORS - 1;
    }

    /** Indice del muro laterale tra due celle adiacenti (il settore "più alto" in senso orario). */
    public static int wallBetween(int idA, int idB) {
        int a = sectorOf(idA), b = sectorOf(idB);
        return Math.floorMod(a - b, CELL_SECTORS) == 1 ? a : b;
    }
}

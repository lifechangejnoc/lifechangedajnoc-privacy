package it.pepita.core.world;

/**
 * Coordinate condivise tra costruzioni e gameplay. Tutte le costruzioni (hub, spawn, miniere, PvP, colosseo)
 * devono rispettare questi punti perché il plugin ci aggancia portali, NPC, ologrammi e protezioni.
 */
public final class Layout {
    private Layout() {}

    // ---------------- Nomi dei mondi ----------------
    public static final String W_HUB = "pepita_hub";
    public static final String W_PRISON = "pepita";
    public static final String W_MINES = "pepita_miniere";
    public static final String W_PVP = "pepita_pvp";
    public static final String W_CELLS = "pepita_celle";

    // =====================================================================
    //  HUB (mondo pepita_hub). Pavimento calpestabile: blocchi a y=99, si sta a y=100.
    // =====================================================================
    public static final int HUB_FLOOR = 99;
    /** x, y, z, yaw */
    public static final double[] HUB_SPAWN = {0.5, 100, 16.5, 180};
    /** Secondino della modalità Prison nella lobby del plugin (guarda verso sud, verso chi arriva). */
    public static final double[] HUB_NPC = {0.5, 100, 7.5, 0};
    /** Centro del grande logo animato (ItemDisplay), visibile dallo spawn guardando a nord. */
    public static final double[] HUB_LOGO = {0.5, 124, -34.5};
    /** Raggio dell'isola dell'hub (area protetta). */
    public static final int HUB_RADIUS = 64;

    /**
     * Portali dell'hub: id, x1,y1,z1, x2,y2,z2 (box INCLUSIVO dei blocchi del varco, spessore 1).
     * miniere e pvp sono sul piano z=-24 (guardano a sud), prigione sul piano x=-24 e celle sul piano x=+24.
     */
    public static final Object[][] HUB_PORTALS = {
            {"prigione", -24, 100, -9, -24, 105, -3},
            {"miniere", -11, 100, -24, -5, 105, -24},
            {"pvp", 5, 100, -24, 11, 105, -24},
            {"celle", 24, 100, -9, 24, 105, -3},
    };
    /** Posizione delle scritte sopra ogni portale (stesso ordine). */
    public static final double[][] HUB_PORTAL_LABELS = {
            {-23.5, 108.2, -5.5}, {-7.5, 108.2, -23.5}, {8.5, 108.2, -23.5}, {24.5, 108.2, -5.5}};

    // =====================================================================
    //  PRIGIONE (mondo "pepita"): resta lo spawn originale, vedi WorldService.
    // =====================================================================
    public static final double[] PRISON_SPAWN = {0.5, 100, 27.5, 180};
    /** Beppe il Secondino (tutorial), a sinistra di chi arriva nel cortile. */
    public static final double[] PRISON_NPC = {-2.5, 100, 25.5, -56};
    /** Blocchi interattivi dello spawn della prigione (x, y, z del blocco). */
    public static final int[][] PRISON_CRATES = {{22, 100, -6}, {22, 100, -2}, {22, 100, 2}, {22, 100, 6}};
    public static final int[][] PRISON_BOARDS = {{-22, 100, -8}, {-22, 100, 0}, {-22, 100, 8}};
    public static final int[] PRISON_ENCHANT = {-6, 100, 22};
    public static final int[] PRISON_SHOP = {6, 100, 22};
    public static final int[] PRISON_DAILY = {0, 100, 31};
    /** Banco delle armature/skin (nuovo) e cartellone del battle pass. */
    public static final int[] PRISON_ARMOR = {-10, 100, 18};
    public static final int[] PRISON_PASS = {10, 100, 18};
    /** Portale delle miniere: varco nether_portal (axis x) x -2..2, y 100..104, sul piano z=-22. */
    public static final int PRISON_PORTAL_Z = -22;
    /** Logo animato sopra la piazza della prigione. */
    public static final double[] PRISON_LOGO = {0.5, 128, -6.5};
    public static final int PRISON_RADIUS = 40;

    // =====================================================================
    //  MINIERE (mondo pepita_miniere). Ogni miniera è un'isola a sé.
    // =====================================================================
    public static final int MINE_HALF = 14;      // interno 29x29
    public static final int MINE_DEPTH = 30;     // strati di blocchi
    public static final int MINE_TOP = 98;       // ultimo strato di blocchi (maxY); bordo a y=99, si sta a y=100
    public static final int MINE_SPACING = 128;  // distanza tra i centri delle isole
    /** Le isole occupano al massimo questo raggio dal centro della miniera (decorazioni comprese). */
    public static final int MINE_ISLAND_RADIUS = 52;

    public static int mineCenterX(int col) { return col * MINE_SPACING; }
    public static int mineCenterZ(int row) { return row * MINE_SPACING; }

    // =====================================================================
    //  PVP (mondo pepita_pvp): una grande miniera dei Quantum dentro un'arena.
    // =====================================================================
    public static final int PVP_HALF = 20;       // interno 41x41
    public static final int PVP_DEPTH = 24;
    public static final int PVP_TOP = 98;
    /** Arrivo sicuro (niente PvP entro PVP_SAFE_RADIUS da qui). */
    public static final double[] PVP_SPAWN = {0.5, 100, 46.5, 180};
    public static final int PVP_SAFE_RADIUS = 7;
    public static final int PVP_RADIUS = 80;

    // =====================================================================
    //  CELLE (mondo pepita_celle): colosseo con le celle disposte ad anello.
    //  Vedi CellGeometry per la classificazione blocco per blocco.
    // =====================================================================
    public static final int COL_ARENA_Y = 80;    // pavimento dell'arena (si sta a 81)
    public static final double[] CELLS_SPAWN = {0.5, 81, 22.5, 180};
    public static final int CELL_Y0 = 84;        // pavimento del piano 0 delle celle
    public static final int CELL_FH = 6;         // altezza di un piano (pavimento + 5 di interno)
    public static final int CELL_FLOORS = 10;
    public static final int CELL_SECTORS = 32;
    public static final double CELL_RIN = 38;    // muro interno (con la porta) tra RIN e RIN+1
    public static final double CELL_ROUT = 48;   // muro esterno tra ROUT-1 e ROUT
    public static final double BALCONY_IN = 33;  // ballatoio tra BALCONY_IN e RIN, ringhiera a BALCONY_IN
    /** Angoli (gradi, 0 = +X, 90 = +Z) dei 4 ascensori sul ballatoio. Non ci sono celle davanti: sono sul ballatoio. */
    public static final int[] ELEVATOR_ANGLES = {45, 135, 225, 315};
    public static final double ELEVATOR_R = 35.5;
    public static final int COLOSSEUM_TOP = 172;
}

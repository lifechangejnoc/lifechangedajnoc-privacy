package it.pepita.core.world.build;

import it.pepita.core.world.BuildPlan;

import static it.pepita.core.world.Layout.*;
import static it.pepita.core.world.build.BuildUtil.*;

/**
 * Hub (mondo pepita_hub): una piazza italiana sospesa nel cielo.
 * Piazza in mattoni a spina di pesce con raggi di travertino (stile Piazza del Campo), fontana della Pepita,
 * palazzi con portici su tre lati, quattro porte monumentali a tema (prigione, miniere, pvp, celle),
 * due campanili che incorniciano il logo gigante con un arco trionfale, giardini con cipressi, ulivi, ciliegi,
 * vigneto, campo di lavanda, giardino all'italiana, tempietto sul belvedere a sud e isolotti sospesi.
 */
public final class HubBuild {
    private HubBuild() {}

    static final int Y = HUB_FLOOR;          // 99
    static final long SEED = 4242;

    public static void build(BuildPlan p) {
        Island isl = new Island(0, -2, Y, 53, 36, SEED);
        isl.rough = 0.11;
        isl.prepare();
        isl.build(p);

        piazza(p, isl);
        palazzi(p);
        gates(p);
        centralPier(p);
        logoFrame(p);
        terrace(p);
        fountain(p, 0, -8);
        squareFurniture(p);
        spawnAndNpc(p);
        south(p, isl);
        westVineyard(p, isl);
        eastLavender(p, isl);
        northGarden(p, isl);
        meadow(p, isl);
        satellites(p, isl);

        keepClear(p);
        finish(p);
    }

    // =====================================================================================
    //  Pavimentazione
    // =====================================================================================

    static boolean inSquare(int x, int z) { return x >= -24 && x <= 24 && z >= -24 && z <= 24; }

    static void piazza(BuildPlan p, Island isl) {
        for (int x = -24; x <= 24; x++)
            for (int z = -24; z <= 24; z++) {
                if (!isl.inside(x, z)) continue;
                p.set(x, Y, z, square(x, z));
            }
    }

    /** Piazza del Campo: mattoni a spina di pesce, raggi di travertino dalla fontana, cordolo esterno. */
    static String square(int x, int z) {
        double dx = x, dz = z + 8;
        double r = Math.sqrt(dx * dx + dz * dz);
        double a = Math.toDegrees(Math.atan2(dz, dx));
        if (z > 12) {
            // sagrato verso il belvedere: lastre di pietra con bordo
            if (Math.abs(x) >= 23 || z >= 23) return "minecraft:polished_andesite";
            double n = noise(x, Y, z, SEED + 9);
            return (Math.floorMod(x, 4) == 0 || Math.floorMod(z, 4) == 0) ? "minecraft:smooth_stone" : n < 0.7 ? "minecraft:stone_bricks" : n < 0.9 ? "minecraft:polished_andesite" : "minecraft:cracked_stone_bricks";
        }
        if (x == -24 || x == 24 || z == -24) return "minecraft:polished_andesite";
        if (x == -23 || x == 23 || z == -23 || z == 12) return "minecraft:smooth_stone";
        if (r <= 9.6) return "minecraft:polished_diorite";
        double seg = 360.0 / 18;
        double off = Math.abs(((a + 360 + seg / 2) % seg) - seg / 2);
        if (off * Math.PI / 180 * r < 0.62) return "minecraft:smooth_sandstone";
        if (Math.abs(r - 17.5) < 0.55 || Math.abs(r - 26.5) < 0.55) return "minecraft:smooth_sandstone";
        int h = Math.floorMod(x + z, 4), k = Math.floorMod(x - z, 4);
        double n = noise(x, Y, z, SEED + 7);
        String brick = n < 0.86 ? "minecraft:bricks" : n < 0.94 ? "minecraft:granite" : "minecraft:polished_granite";
        if ((h < 2) == (k < 2)) return brick;
        return n < 0.5 ? "minecraft:terracotta" : brick;
    }

    // =====================================================================================
    //  Palazzi
    // =====================================================================================

    static void palazzi(BuildPlan p) {
        // Ovest (ocra), fronte a est; frame lato est: u = 10 - z
        Palazzo w = new Palazzo();
        w.wall = "minecraft:yellow_terracotta"; w.wall2 = "minecraft:yellow_terracotta";
        w.seed = 11;
        w.skip.add(new int[]{10 - 2, 10 + 14});
        w.build(p, -36, -36, -25, 10, Y, "east", true);
        // Est (terra di Siena), fronte a ovest; u = z + 36
        Palazzo e = new Palazzo();
        e.wall = "minecraft:orange_terracotta"; e.wall2 = "minecraft:orange_terracotta";
        e.curtain = "minecraft:yellow_wool";
        e.seed = 12;
        e.skip.add(new int[]{-14 + 36, 2 + 36});
        e.build(p, 25, -36, 36, 10, Y, "west", true);
        // Nord: palazzo della Pepita (avorio e quarzo), terrazza piana; u = x + 24
        Palazzo n = new Palazzo();
        n.wall = "minecraft:white_terracotta"; n.wall2 = "minecraft:white_terracotta";
        n.trim = "smooth_quartz"; n.trimBlock = "minecraft:smooth_quartz"; n.column = "minecraft:quartz_pillar";
        n.curtain = "minecraft:purple_wool";
        n.seed = 13;
        n.flatRoof = true;
        n.skip.add(new int[]{-16 + 24, 16 + 24});
        n.build(p, -24, -36, 24, -25, Y, "south", true);
    }

    // =====================================================================================
    //  Porte monumentali (padiglioni aggettanti)
    // =====================================================================================

    static void gates(BuildPlan p) {
        gate(new Frame(p, -24, Y, -6, "east"), "prigione", "z", 8, 8);
        gate(new Frame(p, -8, Y, -24, "south"), "miniere", "x", 7, 6);
        gate(new Frame(p, 8, Y, -24, "south"), "pvp", "x", 6, 7);
        gate(new Frame(p, 24, Y, -6, "west"), "celle", "z", 8, 8);
    }

    static String mix(Frame f, int u, int y, int v, long s, String a, String b, double pa) {
        return noise(f.wx(u, v), f.oy + y, f.wz(u, v), s) < pa ? a : b;
    }

    /**
     * Porta: varco u -3..3, y 1..6, v=0 (piano del portale). Dietro (v=-1) il fondo della nicchia.
     * L'etichetta del plugin sta a y=9, v=0: tutto ciò che sta sopra l'architrave al centro resta arretrato (v<=-1).
     * wl/wr = estensione a sinistra/destra (u = -wl .. wr).
     */
    static void gate(Frame f, String theme, String axis, int wl, int wr) {
        for (int u = -wl; u <= wr; u++) for (int y = 1; y <= 20; y++) for (int v = -1; v <= 4; v++) f.remove(u, y, v);
        // fondo della nicchia sopra il varco
        String back = switch (theme) {
            case "prigione" -> "minecraft:stone_bricks";
            case "miniere" -> "minecraft:stone";
            case "pvp" -> "minecraft:polished_blackstone_bricks";
            default -> "minecraft:smooth_sandstone";
        };
        for (int u = -4; u <= 4; u++) for (int y = 0; y <= 15; y++) f.set(u, y, -1, back);
        for (int u = -3; u <= 3; u++) for (int y = 1; y <= 6; y++) {
            f.set(u, y, 0, "minecraft:nether_portal[axis=" + axis + "]");
            f.set(u, y, -1, "minecraft:obsidian");
        }
        switch (theme) {
            case "prigione" -> prisonGate(f, wl, wr);
            case "miniere" -> mineGate(f, wl, wr);
            case "pvp" -> pvpGate(f, wl, wr);
            default -> cellsGate(f, wl, wr);
        }
    }

    // --------------------------------------------------------------- prigione: castello di guardia
    static void prisonGate(Frame f, int wl, int wr) {
        for (int s = -1; s <= 1; s += 2) {
            int u0 = s * 5, u1 = s * 8;
            for (int u = Math.min(u0, u1); u <= Math.max(u0, u1); u++)
                for (int v = -1; v <= 2; v++)
                    for (int y = 0; y <= 17; y++) {
                        boolean cu = u == u1, cv = v == 2;
                        if (cu && cv && y < 15) continue;                 // spigolo smussato
                        boolean shell = u == u0 || u == u1 || v == -1 || v == 2;
                        if (!shell && y > 0 && y < 15) continue;
                        String b = y <= 1 ? "minecraft:polished_andesite" : mix(f, u, y, v, 3, "minecraft:stone_bricks", mix(f, u, y, v, 4, "minecraft:cracked_stone_bricks", "minecraft:mossy_stone_bricks", 0.5), 0.72);
                        if (y == 8 || y == 14) b = "minecraft:chiseled_stone_bricks";
                        f.set(u, y, v, b);
                    }
            // feritoie
            int um = s * 6 + (s > 0 ? 1 : -1) * 0;
            for (int y = 10; y <= 12; y++) { f.set(um, y, 2, "minecraft:iron_bars"); f.set(s * 8, y, 1, "minecraft:iron_bars"); }
            for (int y = 3; y <= 5; y++) f.set(s * 6, y, 2, "minecraft:iron_bars");
            // beccatelli e merli
            for (int u = Math.min(u0, u1) - (s < 0 ? 1 : 0); u <= Math.max(u0, u1) + (s > 0 ? 1 : 0); u++) {
                f.set(u, 15, 3, "minecraft:stone_brick_stairs[facing={in},half=top]");
                f.set(u, 16, 3, "minecraft:stone_bricks");
                if (Math.floorMod(u, 2) == 0) f.set(u, 17, 3, "minecraft:stone_brick_wall");
            }
            int ue = s * 9;
            for (int v = -1; v <= 3; v++) {
                f.set(ue, 15, v, "minecraft:stone_brick_stairs[facing=" + (s < 0 ? "{right}" : "{left}") + ",half=top]");
                f.set(ue, 16, v, "minecraft:stone_bricks");
                if (Math.floorMod(v, 2) == 1) f.set(ue, 17, v, "minecraft:stone_brick_wall");
            }
            for (int u = Math.min(u0, u1); u <= Math.max(u0, u1); u++) for (int v = -1; v <= 2; v++) f.set(u, 16, v, "minecraft:polished_andesite");
            Deco.brazier(f.p, f.wx(s * 7, 0), f.oy + 17, f.wz(s * 7, 0), "minecraft:polished_andesite", false);
            // lanterne sulle catene
            f.set(s * 4, 8, 2, "minecraft:stone_brick_slab[type=top]");
            f.set(s * 4, 7, 2, "minecraft:iron_chain[axis=y]");
            f.set(s * 4, 6, 2, "minecraft:lantern[hanging=true]");
            // gradini davanti alle torri
            for (int u = Math.min(u0, u1); u <= Math.max(u0, u1); u++) f.set(u, 1, 3, "minecraft:stone_brick_stairs[facing={in},half=bottom]");
            f.set(s * 8, 1, 3, "minecraft:stone_brick_slab[type=bottom]");
        }
        // architrave, saracinesca alzata (punte visibili), arco
        for (int u = -4; u <= 4; u++) {
            f.set(u, 7, 0, Math.abs(u) == 4 ? "minecraft:chiseled_stone_bricks" : "minecraft:stone_bricks");
            f.set(u, 0, 0, "minecraft:polished_andesite");
            f.set(u, 0, 1, "minecraft:polished_andesite");
            f.set(u, 0, 2, Math.abs(u) <= 1 ? "minecraft:iron_block" : "minecraft:polished_andesite");
        }
        for (int y = 1; y <= 6; y++) { f.set(-4, y, 0, "minecraft:stone_bricks"); f.set(4, y, 0, "minecraft:stone_bricks"); }
        f.set(0, 7, 0, "minecraft:iron_block");
        for (int u = -3; u <= 3; u++) f.set(u, 6, 1, "minecraft:iron_bars");
        for (int u = -3; u <= 3; u += 2) f.set(u, 5, 1, "minecraft:iron_bars");
        f.set(-4, 7, 1, "minecraft:stone_brick_stairs[facing={right},half=top]");
        f.set(4, 7, 1, "minecraft:stone_brick_stairs[facing={left},half=top]");
        // camminamento merlato tra le torri (sopra l'etichetta)
        for (int u = -4; u <= 4; u++) {
            f.set(u, 13, 0, "minecraft:stone_brick_stairs[facing={in},half=top]");
            f.set(u, 14, 0, "minecraft:stone_bricks");
            f.set(u, 14, -1, "minecraft:stone_bricks");
            f.set(u, 15, 0, Math.floorMod(u, 2) == 0 ? "minecraft:stone_brick_wall" : "minecraft:stone_brick_slab[type=bottom]");
        }
        // stemma: grata di ferro con blocco di ferro
        for (int u = -2; u <= 2; u++) for (int y = 10; y <= 12; y++) f.set(u, y, -1, Math.abs(u) == 2 || y != 11 ? "minecraft:chiseled_stone_bricks" : "minecraft:iron_bars");
        f.set(0, 11, -1, "minecraft:iron_block");
        f.set(-3, 9, -1, "minecraft:iron_chain[axis=y]");
        f.set(3, 9, -1, "minecraft:iron_chain[axis=y]");
    }

    // --------------------------------------------------------------- miniere: imbocco di galleria nella roccia
    static void mineGate(Frame f, int wl, int wr) {
        String[] ores = {"minecraft:gold_ore", "minecraft:gold_ore", "minecraft:raw_gold_block", "minecraft:diamond_ore", "minecraft:iron_ore", "minecraft:emerald_ore", "minecraft:redstone_ore[lit=false]", "minecraft:lapis_ore", "minecraft:coal_ore", "minecraft:copper_ore"};
        // roccia
        for (int u = -wl; u <= wr; u++)
            for (int y = 0; y <= 19; y++)
                for (int v = -1; v <= 3; v++) {
                    double side = Math.abs(u) - 3.5;
                    double hmax = 17 - Math.abs(u) * 0.35 + (noise(f.wx(u, 0), 0, f.wz(u, 0), 17) - 0.5) * 3;
                    if (y > hmax) continue;
                    // profondità della roccia: piloni davanti (v fino a 2-3), sopra il varco arretrata
                    double vmax = side > 0 ? 1.2 + side * 0.5 - y * 0.06 : (y >= 8 ? -1 : -2);
                    vmax += (noise(f.wx(u, v), y, f.wz(u, v), 19) - 0.5) * 1.4;
                    if (v > vmax) continue;
                    if (Math.abs(u) <= 3 && y >= 1 && y <= 6 && v >= 0) continue;
                    if (Math.abs(u) <= 4 && y >= 7 && y <= 11 && v >= 0) continue;   // etichetta
                    double n = noise(f.wx(u, v), y, f.wz(u, v), 21);
                    String b = n < 0.38 ? "minecraft:stone" : n < 0.6 ? "minecraft:andesite" : n < 0.75 ? "minecraft:cobblestone" : n < 0.85 ? "minecraft:tuff" : n < 0.9 ? "minecraft:mossy_cobblestone" : ores[(int) ((n - 0.9) * 100) % ores.length];
                    if (y == 0) b = "minecraft:cobblestone";
                    f.set(u, y, v, b);
                }
        // armatura di legno
        for (int s = -1; s <= 1; s += 2) {
            for (int y = 1; y <= 7; y++) f.set(s * 4, y, 0, "minecraft:stripped_spruce_log[axis=y]");
            for (int y = 1; y <= 7; y++) f.set(s * 4, y, 1, "minecraft:spruce_log[axis=y]");
            f.set(s * 3, 6, 1, "minecraft:spruce_stairs[facing=" + (s < 0 ? "{left}" : "{right}") + ",half=top]");
        }
        for (int u = -4; u <= 4; u++) { f.set(u, 7, 1, "minecraft:spruce_log[axis={axisu}]"); f.set(u, 7, 0, "minecraft:stripped_spruce_log[axis={axisu}]"); }
        f.set(0, 6, 1, "minecraft:lantern[hanging=true]");
        f.set(-2, 6, 1, "minecraft:iron_chain[axis=y]");
        f.set(-2, 5, 1, "minecraft:lantern[hanging=true]");
        f.set(2, 6, 1, "minecraft:iron_chain[axis=y]");
        f.set(2, 5, 1, "minecraft:lantern[hanging=true]");
        // binari d'oro che escono dal portale e carrello
        String railAxis = f.out.equals("north") || f.out.equals("south") ? "north_south" : "east_west";
        for (int v = 0; v <= 8; v++) {
            for (int u = -2; u <= 2; u++) f.set(u, 0, v, Math.abs(u) <= 1 ? "minecraft:spruce_planks" : "minecraft:coarse_dirt");
            if (v >= 1) f.set(0, 1, v, v % 3 == 0 ? "minecraft:powered_rail[powered=true,shape=" + railAxis + ",waterlogged=false]" : "minecraft:rail[shape=" + railAxis + ",waterlogged=false]");
        }
        for (int v = 1; v <= 8; v++) f.set(Math.floorMod(v, 2) == 0 ? -1 : 1, 0, v, "minecraft:stripped_spruce_log[axis={axisu}]");
        f.set(0, 1, 5, "minecraft:hopper[enabled=true,facing=down]");
        f.set(0, 2, 5, "minecraft:raw_gold_block");
        // casse e barili
        f.set(-3, 1, 3, "minecraft:barrel[facing=up,open=false]");
        f.set(-3, 2, 3, "minecraft:lantern[hanging=false]");
        f.set(-3, 1, 4, "minecraft:barrel[facing={right},open=false]");
        f.set(3, 1, 3, "minecraft:spruce_planks");
        f.set(3, 2, 3, "minecraft:spruce_trapdoor[facing={out},half=bottom,open=false]");
        f.set(3, 1, 4, "minecraft:raw_iron_block");
        // gru di legno sul pilone sinistro
        int cu = -wl + 1;
        for (int y = 1; y <= 14; y++) f.set(cu, y, 3, "minecraft:spruce_fence");
        for (int v = 3; v <= 7; v++) f.set(cu, 15, v, "minecraft:spruce_log[axis={axisv}]");
        f.set(cu, 14, 4, "minecraft:spruce_stairs[facing={in},half=top]");
        for (int y = 9; y <= 14; y++) f.set(cu, y, 7, "minecraft:iron_chain[axis=y]");
        f.set(cu, 8, 7, "minecraft:barrel[facing=up,open=false]");
        // picconi incrociati sulla roccia sopra l'etichetta
        int cy = 14;
        for (int k = -2; k <= 2; k++) {
            f.set(k, cy + k, -2, "minecraft:stripped_spruce_log[axis=y]");
            f.set(-k, cy + k, -2, "minecraft:stripped_spruce_log[axis=y]");
        }
        for (int s = -1; s <= 1; s += 2) {
            f.set(s * 2, cy + 3, -2, "minecraft:iron_block");
            f.set(s * 3, cy + 3, -2, "minecraft:iron_block");
            f.set(s * 1, cy + 3, -2, "minecraft:iron_block");
            f.set(s * 4, cy + 2, -2, "minecraft:iron_block");
            f.set(s * 0, cy + 3, -2, "minecraft:gold_block");
        }
    }

    // --------------------------------------------------------------- pvp: porta di fortezza del Nether
    static void pvpGate(Frame f, int wl, int wr) {
        for (int s = -1; s <= 1; s += 2) {
            int w = s < 0 ? wl : wr;
            int u0 = s * 4, u1 = s * w;
            for (int u = Math.min(u0, u1); u <= Math.max(u0, u1); u++)
                for (int v = -1; v <= 2; v++)
                    for (int y = 0; y <= 15; y++) {
                        boolean shell = u == u0 || u == u1 || v == -1 || v == 2;
                        if (!shell && y > 0 && y < 15) continue;
                        if (u == u1 && v == 2 && y > 2 && y < 14) continue;
                        String b = y <= 1 ? "minecraft:polished_blackstone" : mix(f, u, y, v, 5, "minecraft:red_nether_bricks", mix(f, u, y, v, 6, "minecraft:nether_bricks", "minecraft:cracked_nether_bricks", 0.6), 0.6);
                        if (y == 2 || y == 9 || y == 15) b = "minecraft:polished_blackstone_bricks";
                        if (u == u0 && v == 2 && y > 2 && y < 9) b = y % 2 == 0 ? "minecraft:crying_obsidian" : "minecraft:magma_block";
                        f.set(u, y, v, b);
                    }
            // merli a punta e braciere di anime in cima
            for (int u = Math.min(u0, u1); u <= Math.max(u0, u1); u++)
                for (int v = -1; v <= 2; v++) {
                    boolean edge = u == u0 || u == u1 || v == -1 || v == 2;
                    if (!edge) continue;
                    if (Math.floorMod(u + v, 2) == 0) {
                        f.set(u, 16, v, "minecraft:polished_blackstone_brick_wall");
                        f.set(u, 17, v, "minecraft:pointed_dripstone[thickness=frustum,vertical_direction=up,waterlogged=false]");
                        f.set(u, 18, v, "minecraft:pointed_dripstone[thickness=tip,vertical_direction=up,waterlogged=false]");
                    } else f.set(u, 16, v, "minecraft:polished_blackstone_slab[type=bottom]");
                }
            int hu = s * ((Math.abs(u0) + Math.abs(u1)) / 2);
            f.set(hu, 16, 0, "minecraft:soul_soil");
            f.set(hu, 17, 0, "minecraft:soul_fire");
            f.set(hu, 16, 1, "minecraft:soul_soil");
            f.set(hu, 17, 1, "minecraft:soul_fire");
            // bracieri di anime davanti
            f.set(s * 5, 0, 4, "minecraft:soul_soil");
            Deco.brazier(f.p, f.wx(s * 5, 3), f.oy + 0, f.wz(s * 5, 3), "minecraft:polished_blackstone_bricks", true);
            f.set(s * 5, 2, 3, "minecraft:air");
            f.set(s * 5, 1, 3, "minecraft:soul_campfire[lit=true,facing={in},signal_fire=false,waterlogged=false]");
            f.set(s * 5, 0, 3, "minecraft:polished_blackstone_bricks");
            // stendardi rossi
            f.set(s * 6, 13, 3, "minecraft:red_wall_banner[facing={out}]");
            // catene
            for (int y = 10; y <= 14; y++) f.set(s * 4, y, 3, "minecraft:iron_chain[axis=y]");
            f.set(s * 4, 15, 3, "minecraft:polished_blackstone_slab[type=top]");
            f.set(s * 4, 9, 3, "minecraft:soul_lantern[hanging=true]");
            for (int v = 2; v <= 3; v++) f.set(s * 4, 15, v, "minecraft:polished_blackstone_slab[type=top]");
        }
        // architrave con zanne e liane cremisi pendenti
        for (int u = -3; u <= 3; u++) {
            f.set(u, 7, 0, u == 0 ? "minecraft:gilded_blackstone" : "minecraft:polished_blackstone_bricks");
            f.set(u, 7, 1, "minecraft:polished_blackstone_brick_stairs[facing={in},half=top]");
            f.set(u, 0, 0, "minecraft:polished_blackstone");
            f.set(u, 0, 1, noise(u, 0, 1, 77) < 0.5 ? "minecraft:crimson_nylium" : "minecraft:netherrack");
            f.set(u, 0, 2, noise(u, 0, 2, 77) < 0.4 ? "minecraft:crimson_nylium" : "minecraft:blackstone");
            if (Math.abs(u) == 2 || u == 0) {
                f.set(u, 6, 1, "minecraft:weeping_vines_plant");
                f.set(u, 5, 1, u == 0 ? "minecraft:weeping_vines_plant" : "minecraft:weeping_vines[age=20]");
                if (u == 0) f.set(u, 4, 1, "minecraft:weeping_vines[age=22]");
            }
        }
        for (int y = 0; y <= 7; y++) { f.set(-4, y, 0, "minecraft:polished_blackstone_bricks"); f.set(4, y, 0, "minecraft:polished_blackstone_bricks"); }
        f.set(-1, 1, 1, "minecraft:crimson_roots");
        f.set(2, 1, 2, "minecraft:crimson_fungus");
        // ponte sopra la nicchia
        for (int u = -4; u <= 4; u++) {
            f.set(u, 14, 0, "minecraft:polished_blackstone_brick_stairs[facing={in},half=top]");
            f.set(u, 15, 0, "minecraft:polished_blackstone_bricks");
            f.set(u, 15, -1, "minecraft:polished_blackstone_bricks");
            f.set(u, 16, 0, Math.floorMod(u, 2) == 0 ? "minecraft:polished_blackstone_brick_wall" : "minecraft:polished_blackstone_slab[type=bottom]");
        }
        // spade incrociate
        int cy = 11;
        for (int k = -3; k <= 3; k++) {
            String blade = k >= 2 ? "minecraft:crimson_hyphae[axis=y]" : k == 1 ? "minecraft:gold_block" : "minecraft:iron_block";
            f.set(k, cy - k + 1, -1, k >= 2 ? "minecraft:crimson_hyphae[axis=y]" : k == 1 ? "minecraft:gold_block" : "minecraft:iron_block");
            f.set(-k, cy - k + 1, -1, blade);
        }
        f.set(-2, cy, -1, "minecraft:gold_block");
        f.set(2, cy, -1, "minecraft:gold_block");
        f.set(0, cy + 2, -1, "minecraft:redstone_block");
    }

    // --------------------------------------------------------------- celle: arco del colosseo
    static void cellsGate(Frame f, int wl, int wr) {
        for (int s = -1; s <= 1; s += 2) {
            for (int u = s * 5; Math.abs(u) <= 8; u += s)
                for (int v = -1; v <= 2; v++)
                    for (int y = 0; y <= 16; y++) {
                        boolean shell = Math.abs(u) == 5 || Math.abs(u) == 8 || v == -1 || v == 2;
                        if (!shell && y > 0 && y < 16) continue;
                        String b = noise(f.wx(u, v), y, f.wz(u, v), 8) < 0.7 ? "minecraft:smooth_sandstone" : "minecraft:cut_sandstone";
                        if (y == 0) b = "minecraft:polished_diorite";
                        if (y == 8 || y == 9) b = "minecraft:smooth_quartz";
                        f.set(u, y, v, b);
                    }
            // semicolonne binate sul fronte
            for (int du : new int[]{5, 8}) {
                int u = s * du;
                f.set(u, 1, 3, "minecraft:smooth_quartz_slab[type=bottom]");
                for (int y = 1; y <= 6; y++) f.set(u, y, 3, "minecraft:quartz_pillar[axis=y]");
                f.set(u, 7, 3, "minecraft:chiseled_quartz_block");
                for (int y = 10; y <= 14; y++) f.set(u, y, 3, "minecraft:quartz_pillar[axis=y]");
                f.set(u, 15, 3, "minecraft:chiseled_quartz_block");
            }
            for (int du = 5; du <= 8; du++) {
                int u = s * du;
                f.set(u, 8, 3, "minecraft:smooth_quartz_stairs[facing={in},half=top]");
                f.set(u, 9, 3, "minecraft:smooth_quartz_slab[type=bottom]");
                f.set(u, 16, 3, "minecraft:smooth_quartz_stairs[facing={in},half=top]");
                f.set(u, 17, 3, du % 3 == 2 ? "minecraft:gold_block" : "minecraft:smooth_quartz_slab[type=bottom]");
            }
            // nicchie ad arco con statua d'oro (piano superiore)
            int un = s * 6, un2 = s * 7;
            for (int y = 10; y <= 13; y++) { f.air(un, y, 2); f.air(un2, y, 2); }
            f.set(un, 13, 2, "minecraft:smooth_sandstone_stairs[facing=" + (s < 0 ? "{left}" : "{right}") + ",half=top]");
            f.set(un2, 13, 2, "minecraft:smooth_sandstone_stairs[facing=" + (s < 0 ? "{right}" : "{left}") + ",half=top]");
            f.set(un, 10, 2, "minecraft:gold_block");
            f.set(un, 11, 2, "minecraft:raw_gold_block");
            f.set(un, 12, 2, "minecraft:gold_block".replace("gold_block", "player_head[rotation=" + (s < 0 ? 4 : 12) + "]"));
            f.set(un2, 10, 2, "minecraft:potted_azalea_bush");
            // arco a piano terra (nicchia con lanterna)
            for (int y = 1; y <= 4; y++) { f.air(un, y, 2); f.air(un2, y, 2); }
            f.set(un, 4, 2, "minecraft:smooth_sandstone_stairs[facing=" + (s < 0 ? "{left}" : "{right}") + ",half=top]");
            f.set(un2, 4, 2, "minecraft:smooth_sandstone_stairs[facing=" + (s < 0 ? "{right}" : "{left}") + ",half=top]");
            f.set(un, 3, 2, "minecraft:lantern[hanging=true]");
            f.set(un2, 1, 2, "minecraft:decorated_pot[facing={out},cracked=false,waterlogged=false]");
        }
        // arco principale a tutto sesto in quarzo e oro sopra il varco
        for (int u = -4; u <= 4; u++) {
            f.set(u, 0, 0, Math.abs(u) <= 1 ? "minecraft:gold_block" : "minecraft:polished_diorite");
            f.set(u, 0, 1, "minecraft:polished_diorite");
        }
        for (int y = 1; y <= 7; y++) { f.set(-4, y, 0, "minecraft:quartz_pillar[axis=y]"); f.set(4, y, 0, "minecraft:quartz_pillar[axis=y]"); }
        for (int u = -3; u <= 3; u++) f.set(u, 7, 0, Math.abs(u) <= 0 ? "minecraft:gold_block" : "minecraft:smooth_quartz");
        f.set(-3, 7, 1, "minecraft:smooth_quartz_stairs[facing={left},half=top]");
        f.set(3, 7, 1, "minecraft:smooth_quartz_stairs[facing={right},half=top]");
        for (int u = -2; u <= 2; u++) f.set(u, 8, 0, u == 0 ? "minecraft:chiseled_quartz_block" : "minecraft:smooth_quartz_slab[type=bottom]");
        // attico con archetti (sopra l'etichetta, arretrato)
        for (int u = -4; u <= 4; u++) {
            int m = Math.floorMod(u + 1, 3);
            for (int y = 10; y <= 13; y++) {
                String b = m == 0 ? "minecraft:quartz_pillar[axis=y]" : (y == 13 ? "minecraft:smooth_quartz" : y == 12 ? "minecraft:smooth_quartz_stairs[facing={" + (m == 1 ? "left" : "right") + "},half=top]" : "minecraft:black_concrete");
                f.set(u, y, -1, b);
            }
            f.set(u, 14, 0, "minecraft:smooth_quartz_stairs[facing={in},half=top]");
            f.set(u, 15, 0, "minecraft:smooth_sandstone");
            f.set(u, 15, -1, "minecraft:smooth_sandstone");
            f.set(u, 16, 0, "minecraft:smooth_quartz_stairs[facing={in},half=top]");
            f.set(u, 17, 0, Math.abs(u) % 2 == 0 ? "minecraft:gold_block" : "minecraft:smooth_quartz_slab[type=bottom]");
        }
        // aquila d'oro sulla sommità
        f.set(0, 18, 0, "minecraft:gold_block");
        f.set(0, 19, 0, "minecraft:gold_block");
        f.set(-1, 19, 0, "minecraft:gold_block".replace("gold_block", "smooth_quartz_stairs[facing={right},half=bottom]"));
        f.set(1, 19, 0, "minecraft:smooth_quartz_stairs[facing={left},half=bottom]");
        f.set(0, 20, 0, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
    }

    /** Pilastro centrale tra le due porte nord: obelisco con orologio dorato. */
    static void centralPier(BuildPlan p) {
        for (int x = -1; x <= 1; x++)
            for (int z = -26; z <= -24; z++)
                for (int y = Y; y <= Y + 16; y++) {
                    boolean c = x == 0 && z == -24;
                    String b = (y - Y) % 5 == 0 ? "minecraft:smooth_quartz" : "minecraft:white_terracotta";
                    if (y == Y || y == Y + 1) b = "minecraft:polished_diorite";
                    p.set(x, y, z, b);
                    if (c && y > Y + 1 && y < Y + 8) p.set(x, y, z, "minecraft:quartz_pillar[axis=y]");
                }
        // orologio
        p.set(0, Y + 12, -23, "minecraft:gold_block");
        p.set(0, Y + 13, -23, "minecraft:smooth_quartz_slab[type=bottom]");
        for (int y = Y + 2; y <= Y + 5; y++) p.set(0, y, -23, "minecraft:quartz_pillar[axis=y]");
        p.set(0, Y + 6, -23, "minecraft:chiseled_quartz_block");
        p.set(0, Y + 7, -23, "minecraft:lantern[hanging=false]");
        p.set(0, Y + 17, -25, "minecraft:gold_block");
        p.set(0, Y + 18, -25, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
        for (int x = -1; x <= 1; x += 2) p.set(x, Y + 17, -25, "minecraft:smooth_quartz_stairs[facing=" + (x < 0 ? "east" : "west") + ",half=bottom]");
    }

    // =====================================================================================
    //  Campanili e arco trionfale che incorniciano il logo
    // =====================================================================================

    static void logoFrame(BuildPlan p) {
        for (int s = -1; s <= 1; s += 2) tower(p, s * 15, -32);
        // arco ribassato tra i campanili: intradosso che lascia libero il box del logo (|x|<=10, y<=134)
        for (int x = -12; x <= 12; x++) {
            int ax = Math.abs(x);
            int yi = ax <= 10 ? 135 : ax == 11 ? 133 : 131;
            for (int z = -34; z <= -30; z++) {
                boolean face = z == -34 || z == -30;
                for (int y = yi; y <= 140; y++) {
                    if (!face && y > yi && y < 139) continue;
                    String b;
                    if (y == yi) b = ax <= 1 ? "minecraft:gold_block" : "minecraft:smooth_quartz";
                    else if (y == 139) b = "minecraft:smooth_quartz";
                    else if (y == 140) b = "minecraft:quartz_bricks";
                    else if (y == yi + 1) b = "minecraft:chiseled_quartz_block";
                    else b = Math.floorMod(x, 3) == 0 ? "minecraft:quartz_pillar[axis=y]" : "minecraft:white_terracotta";
                    p.set(x, y, z, b);
                }
                // cornice e mensole
                if (face) {
                    p.set(x, 141, z, Math.floorMod(x, 2) == 0 ? "minecraft:quartz_block" : "minecraft:smooth_quartz_slab[type=bottom]");
                    int zo = z == -34 ? -35 : -29;
                    p.set(x, 139, zo, "minecraft:smooth_quartz_stairs[facing=" + (z == -34 ? "south" : "north") + ",half=top]");
                    if (Math.floorMod(x, 3) == 0 && ax <= 10) p.set(x, yi, zo, "minecraft:smooth_quartz_stairs[facing=" + (z == -34 ? "south" : "north") + ",half=top]");
                }
            }
            // conci a raggiera sulla fronte sud
            if (ax >= 2 && Math.floorMod(x, 4) == 2) p.set(x, yi + 1, -30, "minecraft:gold_block");
        }
        // cimasa: grande pepita d'oro tra due volute
        for (int x = -3; x <= 3; x++)
            for (int y = 0; y <= 5; y++)
                for (int z = -2; z <= 2; z++) {
                    double d = x * x / 6.0 + (y - 2.3) * (y - 2.3) / 6.5 + z * z / 3.0;
                    if (d <= 1 + (noise(x, y, z, 155) - 0.5) * 0.45) p.set(x, 142 + y, -32 + z, noise(x, y, z, 156) < 0.55 ? "minecraft:raw_gold_block" : "minecraft:gold_block");
                }
        for (int s = -1; s <= 1; s += 2) {
            for (int k = 0; k < 4; k++) p.set(s * (5 + k), 142, -32, "minecraft:smooth_quartz_stairs[facing=" + (s < 0 ? "east" : "west") + ",half=bottom]");
            p.set(s * 9, 142, -32, "minecraft:smooth_quartz");
            p.set(s * 9, 143, -32, "minecraft:lantern[hanging=false]");
            p.set(s * 4, 142, -32, "minecraft:chiseled_quartz_block");
        }
        p.set(0, 148, -32, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
    }

    static void tower(BuildPlan p, int cx, int cz) {
        int x1 = cx - 3, x2 = cx + 3, z1 = cz - 3, z2 = cz + 3;
        for (int y = Y; y <= 139; y++)
            for (int x = x1; x <= x2; x++)
                for (int z = z1; z <= z2; z++) {
                    boolean ex = x == x1 || x == x2, ez = z == z1 || z == z2;
                    if (!ex && !ez) continue;
                    boolean corner = ex && ez;
                    int dx = x - cx, dz = z - cz;
                    String b = corner ? ((y % 2 == 0) ? "minecraft:smooth_quartz" : "minecraft:quartz_bricks") : "minecraft:white_terracotta";
                    if (!corner && (Math.abs(dx) == 2 && ez || Math.abs(dz) == 2 && ex)) b = "minecraft:quartz_pillar[axis=y]";
                    if (y <= Y + 1) b = "minecraft:polished_diorite";
                    // finestre bifore ai piani
                    int ly = y - 114;
                    boolean mid = Math.abs(dx) <= 1 && ez || Math.abs(dz) <= 1 && ex;
                    boolean center = dx == 0 && ez || dz == 0 && ex;
                    if (y >= 115 && y <= 125 && mid && ly % 6 >= 1 && ly % 6 <= 3) b = center ? "minecraft:smooth_quartz" : "minecraft:iron_bars";
                    if (y >= 128 && y <= 133 && mid) b = y == 133 ? "minecraft:smooth_quartz" : center && y <= 130 ? "minecraft:air" : center ? "minecraft:air" : y == 132 ? "minecraft:smooth_quartz_stairs[facing=" + (ez ? (dx < 0 ? "east" : "west") : (dz < 0 ? "south" : "north")) + ",half=top]" : "minecraft:air";
                    p.set(x, y, z, b);
                }
        // orologio sul fronte sud
        int fz = z2 + 1;
        for (int x = cx - 2; x <= cx + 2; x++)
            for (int y = 119; y <= 123; y++) {
                double d = Math.sqrt((x - cx) * (x - cx) + (y - 121) * (y - 121));
                if (d > 2.4) continue;
                String b = d > 1.5 ? "minecraft:gold_block" : "minecraft:white_concrete";
                if (x == cx && y >= 121 && y <= 122 || y == 121 && x == cx + 1) b = "minecraft:black_concrete";
                p.set(x, y, z2, b);
            }
        // cornici marcapiano
        for (int y : new int[]{113, 126, 134}) {
            for (int x = x1 - 1; x <= x2 + 1; x++)
                for (int z = z1 - 1; z <= z2 + 1; z++) {
                    boolean e = x == x1 - 1 || x == x2 + 1 || z == z1 - 1 || z == z2 + 1;
                    if (e) p.set(x, y, z, "minecraft:smooth_quartz_stairs[facing=" + face(cx - x, cz - z) + ",half=top]");
                }
        }
        for (int x = x1 - 1; x <= x2 + 1; x++)
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                boolean e = x == x1 - 1 || x == x2 + 1 || z == z1 - 1 || z == z2 + 1;
                if (e) p.set(x, 135, z, Math.floorMod(x + z, 2) == 0 ? "minecraft:smooth_quartz" : "minecraft:smooth_quartz_slab[type=bottom]");
            }
        p.set(cx, 132, cz, "minecraft:bell[attachment=ceiling,facing=north,powered=false]");
        for (int x = x1 + 1; x <= x2 - 1; x++) for (int z = z1 + 1; z <= z2 - 1; z++) p.set(x, 133, z, "minecraft:smooth_quartz");
        // tamburo ottagonale e cupola a bulbo in rame e oro
        for (int y = 136; y <= 147; y++) {
            double r = y <= 138 ? 2.6 : y <= 141 ? 3.2 - (y - 139) * 0.0 : 3.2 - (y - 141) * 0.55;
            if (y == 139) r = 3.0;
            for (int x = cx - 4; x <= cx + 4; x++)
                for (int z = cz - 4; z <= cz + 4; z++) {
                    double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                    if (d > r + 0.2) continue;
                    if (y <= 138 && d < r - 1) continue;
                    String b;
                    if (y <= 138) b = (Math.abs(x - cx) == 0 || Math.abs(z - cz) == 0) && y >= 137 ? "minecraft:iron_bars" : "minecraft:smooth_quartz";
                    else b = (x - cx == 0 || z - cz == 0) ? "minecraft:gold_block" : "minecraft:waxed_oxidized_cut_copper";
                    p.set(x, y, z, b);
                }
        }
        p.set(cx, 148, cz, "minecraft:gold_block");
        p.set(cx, 149, cz, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
        for (int x = x1 + 1; x <= x2 - 1; x += 2) p.set(x, 117, z1 - 1, "minecraft:yellow_wall_banner[facing=north]");
    }

    /** Terrazza del palazzo nord: balaustra, statue, vasi e pergolato (sotto il box del logo). */
    static void terrace(BuildPlan p) {
        int y = Y + 14;  // 113 = solaio piano
        for (int x = -24; x <= 24; x++) {
            if (Math.abs(x) <= 16) continue;
            p.set(x, y + 1, -25, "minecraft:smooth_quartz_slab[type=bottom]");
        }
        for (int x = -12; x <= 12; x += 3) {
            if (Math.abs(x) < 3) continue;
            p.set(x, y + 1, -27, "minecraft:potted_flowering_azalea_bush");
        }
        for (int x = -24; x <= 24; x += 4) {
            if (Math.abs(x) <= 16) continue;
            p.set(x, y + 1, -25, "minecraft:smooth_quartz");
            p.set(x, y + 2, -25, "minecraft:lantern[hanging=false]");
        }
        // aiuole sulla terrazza ai lati
        for (int s = -1; s <= 1; s += 2)
            for (int x = s * 19; Math.abs(x) <= 22; x += s)
                for (int z = -34; z <= -28; z++) {
                    p.set(x, y + 1, z, Math.abs(x) == 22 || Math.abs(x) == 19 || z == -34 || z == -28 ? "minecraft:smooth_quartz_slab[type=bottom]" : "minecraft:moss_block");
                    if (!(Math.abs(x) == 22 || Math.abs(x) == 19 || z == -34 || z == -28) && noise(x, y, z, 3) < 0.6) p.set(x, y + 2, z, noise(x, y, z, 4) < 0.5 ? "minecraft:flowering_azalea" : "minecraft:azalea");
                }
    }

    // =====================================================================================
    //  Fontana della Pepita
    // =====================================================================================

    static void fountain(BuildPlan p, int cx, int cz) {
        for (int x = cx - 9; x <= cx + 9; x++)
            for (int z = cz - 9; z <= cz + 9; z++) {
                double r = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (r > 8.5) continue;
                if (r > 7.5) {
                    p.set(x, Y - 1, z, "minecraft:polished_diorite");
                    p.set(x, Y, z, "minecraft:polished_diorite");
                    p.set(x, Y + 1, z, "minecraft:smooth_quartz_stairs[facing=" + face(x - cx, z - cz) + ",half=bottom]");
                } else {
                    p.set(x, Y - 2, z, "minecraft:dark_prismarine");
                    p.set(x, Y - 1, z, (Math.floorMod(x * 3 + z, 7) == 0 && r > 3.5) ? "minecraft:sea_lantern" : "minecraft:prismarine_bricks");
                    p.set(x, Y, z, "minecraft:water[level=0]");
                }
            }
        for (int x = cx - 3; x <= cx + 3; x++)
            for (int z = cz - 3; z <= cz + 3; z++) {
                double r = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (r > 3.3) continue;
                p.set(x, Y, z, "minecraft:polished_diorite");
                if (r > 2.3) p.set(x, Y + 1, z, "minecraft:smooth_quartz_stairs[facing=" + face(x - cx, z - cz) + ",half=bottom]");
                else if (r < 1.2) p.set(x, Y + 1, z, "minecraft:chiseled_quartz_block");
                else { p.set(x, Y + 1, z, "minecraft:water[level=0]"); }
            }
        for (int y = Y + 2; y <= Y + 3; y++) p.set(cx, y, cz, "minecraft:quartz_pillar[axis=y]");
        for (String f : CARD) {
            int[] v = vec(f);
            p.set(cx + v[0], Y + 2, cz + v[1], "minecraft:smooth_quartz_stairs[facing=" + opp(f) + ",half=bottom]");
            p.set(cx + v[0], Y + 4, cz + v[1], "minecraft:smooth_quartz_stairs[facing=" + opp(f) + ",half=top]");
            // bocche dei delfini: blocchi d'oro sul bordo della vasca alta che versano acqua
            p.set(cx + v[0] * 2, Y + 2, cz + v[1] * 2, "minecraft:water[level=0]");
        }
        p.set(cx, Y + 4, cz, "minecraft:gold_block");
        for (int x = -2; x <= 2; x++)
            for (int y = 0; y <= 3; y++)
                for (int z = -2; z <= 2; z++) {
                    double d = x * x / 3.2 + (y - 1.4) * (y - 1.4) / 2.4 + z * z / 3.2;
                    if (d <= 1 + (noise(x, y, z, 55) - 0.5) * 0.5) p.set(cx + x, Y + 5 + y, cz + z, noise(x, y, z, 56) < 0.55 ? "minecraft:raw_gold_block" : "minecraft:gold_block");
                }
        // vasca alta: anello di sbarre e acqua -> sostituita da coppa
        for (String f : CARD) {
            int[] v = vec(f);
            p.set(cx + v[0] * 2, Y + 2, cz + v[1] * 2, "minecraft:smooth_quartz_slab[type=bottom]");
        }
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 4 + k * Math.PI / 2;
            int x = cx + (int) Math.round(Math.cos(a) * 5.5), z = cz + (int) Math.round(Math.sin(a) * 5.5);
            p.set(x, Y, z, "minecraft:polished_diorite");
            p.set(x, Y + 1, z, "minecraft:polished_diorite");
            p.set(x, Y + 2, z, "minecraft:quartz_pillar[axis=y]");
            p.set(x, Y + 3, z, "minecraft:gold_block");
            p.set(x, Y + 4, z, "minecraft:lantern[hanging=false]");
        }
    }

    static void squareFurniture(BuildPlan p) {
        // panchine attorno alla fontana e lampioni sulle diagonali
        for (int k = 0; k < 4; k++) {
            int sx = (k & 1) == 0 ? -1 : 1, sz = (k & 2) == 0 ? -1 : 1;
            int lx = sx * 10, lz = -8 + sz * 10;
            Deco.lamp(p, lx, Y, lz, "minecraft:polished_blackstone_wall", "minecraft:lantern", 3, true, (k == 0 || k == 3) ? "x" : "z");
        }
        for (String f : CARD) {
            int[] v = vec(f);
            if (f.equals("south")) continue;
            int bx = v[0] * 12, bz = -8 + v[1] * 12;
            String along = cw(f);
            int[] a = vec(along);
            Deco.bench(p, bx - a[0], Y + 1, bz - a[1], opp(f), "dark_oak", 3);
        }
        // fioriere lungo i portici
        int[][] pl = {{-20, -20}, {20, -20}, {-20, 8}, {20, 8}, {-20, 20}, {20, 20}};
        for (int[] c : pl) planter(p, c[0], c[1]);
        // aste con stendardi agli angoli sud della piazza
        for (int s = -1; s <= 1; s += 2) {
            int x = s * 22, z = 22;
            p.set(x, Y + 1, z, "minecraft:polished_andesite");
            for (int y = Y + 2; y <= Y + 9; y++) p.set(x, y, z, "minecraft:iron_chain[axis=y]".replace("iron_chain[axis=y]", "spruce_fence"));
            p.set(x, Y + 10, z, "minecraft:gold_block".replace("gold_block", "lightning_rod[facing=up,powered=false,waterlogged=false]"));
            String[] cols = {"green", "white", "red"};
            for (int i = 0; i < 3; i++) p.set(x + s * 0, Y + 9 - i * 0, z, "minecraft:spruce_fence");
            p.set(x, Y + 8, z - 1, "minecraft:yellow_wall_banner[facing=north]");
            p.set(x, Y + 8, z + 1, "minecraft:white_wall_banner[facing=south]");
        }
    }

    static void planter(BuildPlan p, int cx, int cz) {
        for (int x = cx - 1; x <= cx + 1; x++)
            for (int z = cz - 1; z <= cz + 1; z++) {
                boolean c = x == cx && z == cz;
                p.set(x, Y + 1, z, c ? "minecraft:moss_block" : (x == cx || z == cz) ? "minecraft:polished_andesite_slab[type=bottom]" : "minecraft:polished_andesite");
            }
        p.set(cx, Y + 2, cz, "minecraft:flowering_azalea");
        p.set(cx, Y + 3, cz, "minecraft:flowering_azalea_leaves[persistent=true]");
    }

    // =====================================================================================
    //  Spawn e NPC
    // =====================================================================================

    static void spawnAndNpc(BuildPlan p) {
        int sx = (int) Math.floor(HUB_SPAWN[0]), sz = (int) Math.floor(HUB_SPAWN[2]);
        for (int x = -4; x <= 4; x++)
            for (int z = -4; z <= 4; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r > 4.5) continue;
                String b;
                boolean star = (x == 0 || z == 0) && r <= 4 || Math.abs(x) == Math.abs(z) && r <= 2.9;
                if (r <= 0.5) b = "minecraft:gold_block";
                else if (star) b = (x == 0 || z == 0) ? "minecraft:gold_block" : "minecraft:raw_gold_block";
                else if (r > 3.6) b = "minecraft:polished_deepslate";
                else b = "minecraft:smooth_quartz";
                p.set(sx + x, Y, sz + z, b);
            }
        int nx = (int) Math.floor(HUB_NPC[0]), nz = (int) Math.floor(HUB_NPC[2]);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) p.set(nx + x, Y, nz + z, x == 0 && z == 0 ? "minecraft:gold_block" : "minecraft:chiseled_quartz_block");
        for (int s = -1; s <= 1; s += 2) Deco.lamp(p, sx + s * 6, Y, sz + 1, "minecraft:polished_blackstone_wall", "minecraft:lantern", 3, true, "z");
    }

    // =====================================================================================
    //  Sud: viale di cipressi, tempietto, belvedere
    // =====================================================================================

    static void south(BuildPlan p, Island isl) {
        for (int z = 25; z <= 52; z++)
            for (int x = -2; x <= 2; x++) {
                if (!isl.inside(x, z) || isl.topAt(x, z) != Y) continue;
                p.set(x, Y, z, Math.abs(x) == 2 ? "minecraft:polished_andesite" : noise(x, 0, z, 3) < 0.5 ? "minecraft:smooth_sandstone" : "minecraft:cut_sandstone");
            }
        for (int z = 28; z <= 40; z += 4)
            for (int s = -1; s <= 1; s += 2) {
                Deco.tree(p, s * 5, Y + 1, z, Deco.TreeType.CYPRESS, 1.0, SEED + z * 7L + s);
                // siepi basse tra i cipressi
                for (int dz = 1; dz <= 3; dz++) p.set(s * 5, Y + 1, z + dz, "minecraft:azalea_leaves[persistent=true]");
                for (int dz = 0; dz <= 3 && z + dz <= 38; dz++) {
                    p.set(s * 3, Y + 1, z + dz, Math.floorMod(z + dz, 2) == 0 ? "minecraft:rose_bush[half=lower]" : "minecraft:peony[half=lower]");
                    p.set(s * 3, Y + 2, z + dz, Math.floorMod(z + dz, 2) == 0 ? "minecraft:rose_bush[half=upper]" : "minecraft:peony[half=upper]");
                    p.set(s * 3, Y, z + dz, "minecraft:grass_block");
                    p.set(s * 4, Y, z + dz, "minecraft:grass_block");
                }
            }
        for (int s = -1; s <= 1; s += 2) Deco.bench(p, s * 3, Y + 1, 26, s < 0 ? "east" : "west", "dark_oak", 1);
        tempietto(p, 0, 45);
        // balaustra del belvedere lungo il bordo sud
        for (int x = -40; x <= 40; x++)
            for (int z = 20; z <= 60; z++) {
                if (!isl.inside(x, z) || isl.inside(x, z + 1) && isl.inside(x + 1, z) && isl.inside(x - 1, z)) continue;
                if (Math.abs(x) <= 1) continue;
                if (z < 30) continue;
                p.set(x, Y, z, "minecraft:polished_andesite");
                if (Math.floorMod(x, 4) == 0) {
                    p.set(x, Y + 1, z, "minecraft:polished_diorite");
                    p.set(x, Y + 2, z, "minecraft:polished_diorite_slab[type=bottom]");
                } else p.set(x, Y + 1, z, "minecraft:diorite_wall");
            }
    }

    static void tempietto(BuildPlan p, int cx, int cz) {
        for (int x = cx - 6; x <= cx + 6; x++)
            for (int z = cz - 6; z <= cz + 6; z++) {
                double r = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (r > 5.6) continue;
                p.set(x, Y, z, "minecraft:polished_diorite");
                if (r > 4.6) p.set(x, Y + 1, z, "minecraft:smooth_quartz_stairs[facing=" + face(cx - x, cz - z) + ",half=bottom]".replace("facing=" + face(cx - x, cz - z), "facing=" + face(cx - x, cz - z)));
                else p.set(x, Y + 1, z, r < 1 ? "minecraft:gold_block" : "minecraft:smooth_quartz_slab[type=top]");
            }
        // 8 colonne
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            int x = cx + (int) Math.round(Math.cos(a) * 3.6), z = cz + (int) Math.round(Math.sin(a) * 3.6);
            p.set(x, Y + 2, z, "minecraft:smooth_quartz");
            for (int y = Y + 3; y <= Y + 6; y++) p.set(x, y, z, "minecraft:quartz_pillar[axis=y]");
            p.set(x, Y + 7, z, "minecraft:chiseled_quartz_block");
        }
        // trabeazione e cupola
        for (int x = cx - 5; x <= cx + 5; x++)
            for (int z = cz - 5; z <= cz + 5; z++) {
                double r = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (r <= 4.6 && r > 2.6) p.set(x, Y + 8, z, "minecraft:smooth_quartz");
                if (r <= 5.1 && r > 4.1) p.set(x, Y + 8, z, "minecraft:smooth_quartz_stairs[facing=" + face(cx - x, cz - z) + ",half=top]");
                for (int y = Y + 9; y <= Y + 13; y++) {
                    double rr = 4.3 * Math.sqrt(Math.max(0, 1 - Math.pow((y - Y - 8.5) / 5.0, 2)));
                    if (r <= rr && r > rr - 1.3) p.set(x, y, z, y == Y + 9 ? "minecraft:smooth_quartz" : "minecraft:waxed_oxidized_copper");
                }
            }
        p.set(cx, Y + 14, cz, "minecraft:gold_block");
        p.set(cx, Y + 15, cz, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
        p.set(cx, Y + 8, cz, "minecraft:smooth_quartz");
        p.set(cx, Y + 7, cz, "minecraft:iron_chain[axis=y]");
        p.set(cx, Y + 6, cz, "minecraft:lantern[hanging=true]");
        // statua d'oro della Pepita al centro
        p.set(cx, Y + 2, cz, "minecraft:chiseled_quartz_block");
        p.set(cx, Y + 3, cz, "minecraft:raw_gold_block");
        p.set(cx, Y + 4, cz, "minecraft:gold_block");
    }

    // =====================================================================================
    //  Ovest: vigneto
    // =====================================================================================

    static void westVineyard(BuildPlan p, Island isl) {
        for (int x = -56; x <= -39; x++)
            for (int z = -30; z <= 24; z++) {
                if (!isl.inside(x, z) || isl.topAt(x, z) != Y || !isl.inside(x - 2, z) || !isl.inside(x, z + 2) || !isl.inside(x, z - 2)) continue;
                boolean row = Math.floorMod(x, 3) == 0;
                if (row) {
                    p.set(x, Y, z, "minecraft:coarse_dirt".replace("coarse_dirt", "rooted_dirt"));
                    if (Math.floorMod(z, 5) == 0) {
                        p.set(x, Y + 1, z, "minecraft:dark_oak_fence");
                        p.set(x, Y + 2, z, "minecraft:dark_oak_fence");
                    } else {
                        p.set(x, Y + 1, z, noise(x, 1, z, 61) < 0.75 ? "minecraft:sweet_berry_bush[age=3]" : "minecraft:short_grass");
                    }
                    p.set(x, Y + 3, z, noise(x, 3, z, 62) < 0.8 ? "minecraft:oak_leaves[persistent=true]" : "minecraft:azalea_leaves[persistent=true]");
                } else p.set(x, Y, z, "minecraft:dirt_path");
            }
        // casale del vignaiolo
        Palazzo h = new Palazzo();
        h.wall = "minecraft:white_terracotta"; h.wall2 = "minecraft:mud_bricks";
        h.groundH = 5; h.floors = 0; h.seed = 21;
        h.build(p, -54, -42, -46, -35, Y, "east", false);
    }

    // =====================================================================================
    //  Est: campo di lavanda e uliveto
    // =====================================================================================

    static void eastLavender(BuildPlan p, Island isl) {
        for (int x = 39; x <= 58; x++)
            for (int z = -30; z <= 26; z++) {
                if (!isl.inside(x, z) || isl.topAt(x, z) != Y || !isl.inside(x + 2, z) || !isl.inside(x, z + 2) || !isl.inside(x, z - 2)) continue;
                if (Math.floorMod(z, 3) == 0) {
                    p.set(x, Y, z, "minecraft:grass_block");
                    double n = noise(x, 1, z, 71);
                    if (n < 0.5) BuildUtil.tall(p, x, Y + 1, z, "lilac");
                    else p.set(x, Y + 1, z, n < 0.85 ? "minecraft:allium" : "minecraft:cornflower");
                } else if (Math.floorMod(z, 3) == 1) {
                    p.set(x, Y, z, "minecraft:dirt_path");
                    p.remove(x, Y + 1, z);
                } else {
                    p.set(x, Y, z, "minecraft:grass_block");
                    p.set(x, Y + 1, z, noise(x, 2, z, 72) < 0.5 ? "minecraft:allium" : "minecraft:short_grass");
                }
            }
    }

    // =====================================================================================
    //  Nord: giardino all'italiana dietro il palazzo
    // =====================================================================================

    static void northGarden(BuildPlan p, Island isl) {
        int cz = -45;
        for (int x = -26; x <= 26; x++)
            for (int z = -54; z <= -38; z++) {
                if (!isl.inside(x, z) || isl.topAt(x, z) != Y) continue;
                double r = Math.sqrt(x * x + (z - cz) * (z - cz) * 2.2);
                boolean path = Math.abs(x) <= 1 || Math.abs(z - cz) <= 0 || Math.abs(r - 9) < 0.8 || Math.abs(x) == 14;
                if (r < 3.5) {
                    p.set(x, Y, z, "minecraft:polished_diorite");
                    continue;
                }
                if (path) {
                    p.set(x, Y, z, "minecraft:smooth_sandstone");
                    p.remove(x, Y + 1, z);
                    continue;
                }
                p.set(x, Y, z, "minecraft:grass_block");
                boolean hedge = Math.floorMod(x, 4) == 0 && Math.abs(x) < 14 || Math.floorMod(z, 4) == 0;
                if (hedge) p.set(x, Y + 1, z, "minecraft:azalea_leaves[persistent=true]");
                else p.set(x, Y + 1, z, noise(x, 1, z, 81) < 0.5 ? "minecraft:red_tulip" : "minecraft:white_tulip");
            }
        // vasca rotonda centrale con zampillo
        for (int x = -3; x <= 3; x++)
            for (int z = -3; z <= 3; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r > 3.4) continue;
                if (r > 2.4) p.set(x, Y + 1, cz + z, "minecraft:polished_diorite_slab[type=bottom]");
                else { p.set(x, Y, cz + z, "minecraft:water[level=0]"); p.set(x, Y - 1, cz + z, "minecraft:prismarine_bricks"); p.remove(x, Y + 1, cz + z); }
            }
        p.set(0, Y, cz, "minecraft:polished_diorite");
        p.set(0, Y + 1, cz, "minecraft:quartz_pillar[axis=y]");
        p.set(0, Y + 2, cz, "minecraft:gold_block");
        for (int s = -1; s <= 1; s += 2) for (int z = -52; z <= -40; z += 6) Deco.tree(p, s * 20, Y + 1, z, Deco.TreeType.CYPRESS, 0.9, SEED + z + s * 5L);
    }

    // =====================================================================================
    //  Prato fiorito, alberi sparsi, isolotti
    // =====================================================================================

    static void meadow(BuildPlan p, Island isl) {
        int[][] trees = {{-44, 30}, {44, 32}, {-30, 36}, {30, 38}, {-20, 44}, {20, 44}, {-40, -46}, {40, -46}, {-12, 34}, {12, 34}, {-50, 12}, {52, -6}, {-32, 22}, {32, 22}};
        Deco.TreeType[] tt = {Deco.TreeType.CHERRY, Deco.TreeType.OLIVE, Deco.TreeType.OLIVE, Deco.TreeType.CHERRY, Deco.TreeType.AZALEA, Deco.TreeType.OAK};
        for (int i = 0; i < trees.length; i++) {
            int x = trees[i][0], z = trees[i][1];
            if (!isl.inside(x, z) || isl.topAt(x, z) != Y) continue;
            String g = p.get(x, Y, z);
            if (g == null || !g.contains("grass_block") || p.has(x, Y + 1, z)) continue;
            Deco.tree(p, x, Y + 1, z, tt[i % tt.length], 1.05, SEED + i * 13L);
        }
        for (int x = -62; x <= 62; x++)
            for (int z = -62; z <= 62; z++) {
                int t = isl.topAt(x, z);
                if (t == Integer.MIN_VALUE) continue;
                String g = p.get(x, t, z);
                if (g == null || !g.contains("grass_block") || p.has(x, t + 1, z)) continue;
                double n = noise(x, t, z, SEED + 1);
                String m = Deco.meadow(n, noise(x, t, z, SEED + 2));
                if (n > 0.96) m = "minecraft:azalea";
                if (m != null) p.set(x, t + 1, z, m);
            }
    }

    static void satellites(BuildPlan p, Island main) {
        int[][] sats = {{-49, 30, 84, 6}, {47, 34, 80, 5}, {52, -30, 88, 6}, {-53, -24, 78, 5}, {-8, 56, 74, 4}};
        for (int i = 0; i < sats.length; i++) {
            int[] s = sats[i];
            Island il = new Island(s[0], s[1], s[2], s[3], s[3] * 1.8, SEED + 100 + i);
            il.rough = 0.3;
            il.edgeDrop = false;
            il.prepare();
            il.build(p);
            Deco.tree(p, s[0], s[2] + 1, s[1], i % 2 == 0 ? Deco.TreeType.CHERRY : Deco.TreeType.AZALEA, 0.75, SEED + i);
            for (int x = s[0] - s[3]; x <= s[0] + s[3]; x++)
                for (int z = s[1] - s[3]; z <= s[1] + s[3]; z++)
                    if (il.topAt(x, z) == s[2] && !p.has(x, s[2] + 1, z)) {
                        String m = Deco.meadow(noise(x, 0, z, SEED + 3), noise(x, 1, z, SEED + 4));
                        if (m != null) p.set(x, s[2] + 1, z, m);
                    }
        }
    }

    /** Aria garantita dove il plugin mette NPC, etichette e logo. */
    static void keepClear(BuildPlan p) {
        int lx = (int) Math.floor(HUB_LOGO[0]), ly = (int) Math.floor(HUB_LOGO[1]), lz = (int) Math.floor(HUB_LOGO[2]);
        clear(p, lx - 10, ly - 10, lz - 3, lx + 10, ly + 10, lz + 3);
        int nx = (int) Math.floor(HUB_NPC[0]), nz = (int) Math.floor(HUB_NPC[2]);
        for (int x = nx - 3; x <= nx + 3; x++) for (int z = nz - 3; z <= nz + 3; z++) for (int y = Y + 1; y <= Y + 4; y++) p.remove(x, y, z);
        for (double[] l : HUB_PORTAL_LABELS) {
            int x = (int) Math.floor(l[0]), y = (int) Math.floor(l[1]), z = (int) Math.floor(l[2]);
            for (int dy = -1; dy <= 1; dy++) p.remove(x, y + dy, z);
        }
        int sx = (int) Math.floor(HUB_SPAWN[0]), sz = (int) Math.floor(HUB_SPAWN[2]);
        for (int y = Y + 1; y <= Y + 3; y++) p.remove(sx, y, sz);
    }
}

package it.pepita.core.world.build;

import it.pepita.core.mine.Mine;
import it.pepita.core.world.BuildPlan;

import static it.pepita.core.world.Layout.*;
import static it.pepita.core.world.build.BuildUtil.*;

/**
 * Miniera PvP dei Quantum (mondo pepita_pvp): grande buca 41x41 dentro una fortezza in rovina tetra.
 * Anello di mura con quattro torri (punti alti) collegate da camminamenti, passerelle che scendono verso la buca,
 * ripari sparsi, cristalli di ametista, catene e fuochi d'anima. A sud la zona sicura recintata con il cancello.
 * Classe pura: solo BuildPlan, Layout e Mine.
 */
public final class PvpBuild {
    private PvpBuild() {}

    static final long SEED = 6060;
    static final int RING = 60;      // raggio delle mura
    static final int WALL_H = 8;     // altezza delle mura sopra il bordo

    public static void build(BuildPlan p, Mine m) {
        int top = m.maxY + 1;           // 99
        int h = (m.maxX - m.minX) / 2;  // 20
        Island isl = new Island(0, 0, top, 72, top - (m.minY - 1) + 14, SEED);
        isl.rough = 0.08;
        isl.lumpy = 0.3;
        isl.mat = PvpBuild::mat;
        isl.surface = "minecraft:blackstone";
        isl.vine = "minecraft:weeping_vines_plant";
        isl.hanging = "minecraft:weeping_vines";
        isl.dripstone = false;
        isl.prepare();
        isl.build(p);
        clear(p, m.minX - 1, m.minY - 3, m.minZ - 1, m.maxX + 1, top + 30, m.maxZ + 1);

        ground(p, isl, top, h);
        pit(p, m, top);
        rim(p, top, h);
        ring(p, isl, top);
        for (int a = 45; a < 360; a += 90) tower(p, a, top);
        bridges(p, top, h);
        safeZone(p, top);
        quantumGate(p, top, h);
        scatter(p, isl, top, h);

        // aria garantita: insegna, sopra la buca, zona sicura
        int lx = (int) Math.floor(m.labelX), ly = (int) Math.floor(m.labelY), lz = (int) Math.floor(m.labelZ);
        clear(p, lx - 2, ly - 2, lz - 2, lx + 2, ly + 2, lz + 2);
        clear(p, m.minX, top, m.minZ, m.maxX, top + 16, m.maxZ);
        int sx = (int) Math.floor(PVP_SPAWN[0]), sz = (int) Math.floor(PVP_SPAWN[2]);
        for (int y = top + 1; y <= top + 3; y++) p.remove(sx, y, sz);
        for (int x = m.minX; x <= m.maxX; x++)
            for (int z = m.minZ; z <= m.maxZ; z++)
                for (int y = m.minY; y <= m.maxY; y++) p.remove(x, y, z);
        finish(p);
    }

    static String mat(int x, int y, int z, int ft, int fb, boolean side) {
        double n = noise(x, y, z, SEED + 1);
        double o = noise(x, y, z, SEED + 3);
        if (o < 0.03) return "minecraft:magma_block";
        if (o < 0.05) return "minecraft:crying_obsidian";
        if (o < 0.06) return "minecraft:amethyst_block";
        if (ft <= 1) return n < 0.5 ? "minecraft:blackstone" : "minecraft:basalt[axis=y]";
        return n < 0.35 ? "minecraft:blackstone" : n < 0.6 ? "minecraft:basalt[axis=y]" : n < 0.78 ? "minecraft:netherrack" : n < 0.9 ? "minecraft:smooth_basalt" : "minecraft:polished_blackstone";
    }

    // ---------------------------------------------------------------- terreno

    static void ground(BuildPlan p, Island isl, int top, int h) {
        int R = 92;
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++) {
                if (isl.topAt(x, z) != top || Math.max(Math.abs(x), Math.abs(z)) <= h + 1) continue;
                double n = fbm(x / 10.0, z / 10.0, SEED + 11, 3);
                double r = Math.hypot(x, z);
                String b = n < 0.28 ? "minecraft:soul_soil" : n < 0.4 ? "minecraft:basalt[axis=y]" : n > 0.78 ? "minecraft:crimson_nylium"
                        : n > 0.7 ? "minecraft:netherrack" : noise(x, 0, z, SEED + 12) < 0.5 ? "minecraft:blackstone" : "minecraft:polished_blackstone";
                if (r > RING + 4 && n > 0.62) b = "minecraft:warped_nylium";
                p.set(x, top, z, b);
            }
    }

    // ---------------------------------------------------------------- buca

    static void pit(BuildPlan p, Mine m, int top) {
        int x1 = m.minX - 1, x2 = m.maxX + 1, z1 = m.minZ - 1, z2 = m.maxZ + 1, bottom = m.minY - 1;
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                p.set(x, bottom, z, (x + z) % 2 == 0 ? "minecraft:polished_blackstone_bricks" : "minecraft:blackstone");
                p.set(x, bottom - 1, z, "minecraft:blackstone");
                boolean side = x == x1 || x == x2 || z == z1 || z == z2;
                if (!side) continue;
                int along = (x == x1 || x == x2) ? z : x;
                for (int y = bottom; y <= m.maxY; y++) {
                    int ft = m.maxY - y;
                    String b;
                    if (Math.floorMod(along, 6) == 0) b = "minecraft:basalt[axis=y]";
                    else if (ft % 6 == 2) b = "minecraft:gilded_blackstone";
                    else b = noise(x, y, z, SEED + 21) < 0.75 ? "minecraft:polished_blackstone_bricks" : "minecraft:cracked_polished_blackstone_bricks";
                    if (ft % 6 == 5 && Math.floorMod(along - 3, 6) == 0) b = "minecraft:shroomlight";
                    p.set(x, y, z, b);
                    int ox = x == x1 ? -1 : x == x2 ? 1 : 0, oz = z == z1 ? -1 : z == z2 ? 1 : 0;
                    if (y < m.maxY - 2) p.set(x + ox, y, z + oz, "minecraft:blackstone");
                }
            }
    }

    static void rim(BuildPlan p, int top, int h) {
        for (int x = -(h + 7); x <= h + 7; x++)
            for (int z = -(h + 7); z <= h + 7; z++) {
                int d = Math.max(Math.abs(x), Math.abs(z));
                if (d <= h) continue;
                String b;
                if (d == h + 1) b = "minecraft:chiseled_polished_blackstone";
                else if (d <= h + 5) b = (Math.floorMod(x + z, 2) == 0) ? "minecraft:polished_blackstone_bricks" : "minecraft:polished_blackstone";
                else if (d == h + 6) b = "minecraft:red_nether_bricks";
                else b = "minecraft:nether_bricks";
                p.set(x, top, z, b);
                p.set(x, top - 1, z, "minecraft:blackstone");
                if (d <= h + 4) p.set(x, top - 2, z, "minecraft:blackstone");
            }
        // bracieri d'anima e pilastri spezzati agli angoli del bordo
        for (int sx = -1; sx <= 1; sx += 2)
            for (int sz = -1; sz <= 1; sz += 2) {
                int x = sx * (h + 5), z = sz * (h + 5);
                for (int y = 1; y <= 5; y++) p.set(x, top + y, z, y == 5 ? "minecraft:chiseled_polished_blackstone" : "minecraft:basalt[axis=y]");
                p.set(x, top + 6, z, "minecraft:soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]");
                for (int y = 1; y <= 3; y++) p.set(x + sx, top + 6 - y + 1, z, "minecraft:iron_chain[axis=y,waterlogged=false]");
                p.set(x + sx, top + 3, z, "minecraft:soul_lantern[hanging=true,waterlogged=false]");
            }
        for (int k = -12; k <= 12; k += 12)
            for (int s = -1; s <= 1; s += 2) {
                if (k == 0 && s > 0) continue; // lato sud: verso la zona sicura
                brazier(p, k, top + 1, s * (h + 5));
                brazier(p, s * (h + 5), top + 1, k);
            }
    }

    static void brazier(BuildPlan p, int x, int y, int z) {
        p.set(x, y, z, "minecraft:polished_blackstone");
        p.set(x, y + 1, z, "minecraft:soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]");
    }

    // ---------------------------------------------------------------- mura e torri

    static boolean gate(double a) {
        // varchi nelle mura: est, ovest, nord e sud (verso la zona sicura)
        double[] g = {0, 90, 180, 270};
        for (double v : g) {
            double d = Math.abs(((a - v) % 360 + 540) % 360 - 180);
            if (d < 5.5) return true;
        }
        return false;
    }

    static void ring(BuildPlan p, Island isl, int top) {
        int R = RING + 2;
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++) {
                double r = Math.hypot(x + 0.5, z + 0.5);
                if (r < RING - 1.5 || r > RING + 1.5) continue;
                double a = ang(x + 0.5, z + 0.5);
                boolean outerFace = r > RING + 0.5, innerFace = r < RING - 0.5;
                // rovina: l'altezza varia e in alcuni tratti la muraglia è crollata
                double ruin = fbm(a / 12.0, 3, SEED + 31, 2);
                int hh = ruin < 0.32 ? 2 + (int) (ruin * 6) : WALL_H;
                if (gate(a)) {
                    // arco del varco
                    for (int y = 6; y <= WALL_H; y++) p.set(x, top + y, z, y == 6 ? "minecraft:polished_blackstone_bricks" : "minecraft:blackstone");
                    continue;
                }
                for (int y = 1; y <= hh; y++) {
                    double n = noise(x, y, z, SEED + 32);
                    String b = n < 0.6 ? "minecraft:polished_blackstone_bricks" : n < 0.82 ? "minecraft:cracked_polished_blackstone_bricks" : n < 0.94 ? "minecraft:blackstone" : "minecraft:gilded_blackstone";
                    if ((y == 3 || y == 4) && (outerFace || innerFace) && Math.floorMod((int) a, 9) == 0) b = "minecraft:iron_bars";
                    p.set(x, top + y, z, b);
                }
                if (hh == WALL_H) {
                    // camminamento in cima con parapetto merlato verso l'esterno
                    p.set(x, top + WALL_H + 1, z, outerFace ? (Math.floorMod((int) (a * 1.5), 2) == 0 ? "minecraft:polished_blackstone_brick_wall" : "minecraft:air")
                            : innerFace ? "minecraft:air" : "minecraft:air");
                    if (outerFace && Math.floorMod((int) a, 15) == 0)
                        p.set(x, top + WALL_H + 2, z, "minecraft:soul_lantern[hanging=false,waterlogged=false]");
                } else {
                    // macerie ai piedi del tratto crollato
                    if (noise(x, 0, z, SEED + 33) < 0.35) p.set(x + (int) Math.signum(-x), top + 1, z + (int) Math.signum(-z), "minecraft:blackstone");
                }
            }
        // ripulisci l'aria messa per errore sul camminamento (manteniamo solo i merli)
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++) {
                String b = p.get(x, top + WALL_H + 1, z);
                if (BuildPlan.AIR.equals(b)) p.remove(x, top + WALL_H + 1, z);
            }
    }

    /** Torre a pianta quadrata 9x9 ai 45° delle mura: scala interna, piattaforme, catene e cristalli. */
    static void tower(BuildPlan p, int angle, int top) {
        double a = Math.toRadians(angle);
        int cx = (int) Math.round(Math.cos(a) * RING), cz = (int) Math.round(Math.sin(a) * RING);
        int half = 4, H = 22;
        for (int x = cx - half; x <= cx + half; x++)
            for (int z = cz - half; z <= cz + half; z++)
                for (int y = 0; y <= H; y++) {
                    boolean ex = Math.abs(x - cx) == half, ez = Math.abs(z - cz) == half;
                    boolean corner = ex && ez;
                    int yy = top + y;
                    if (y == 0) {
                        p.set(x, yy, z, "minecraft:polished_blackstone");
                        continue;
                    }
                    if (corner) {
                        p.set(x, yy, z, "minecraft:basalt[axis=y]");
                        continue;
                    }
                    if (ex || ez) {
                        int along = ex ? z - cz : x - cx;
                        boolean window = (y % 6 == 3 || y % 6 == 4) && Math.abs(along) <= 1;
                        boolean door = y <= 3 && Math.abs(along) <= 1 && (ex ? (x - cx) * cx < 0 : (z - cz) * cz < 0);
                        if (door) continue;
                        double n = noise(x, y, z, SEED + 41);
                        // la parte alta è sbrecciata
                        if (y > H - 4 && n < 0.35) continue;
                        p.set(x, yy, z, window ? "minecraft:iron_bars" : n < 0.7 ? "minecraft:polished_blackstone_bricks" : "minecraft:cracked_polished_blackstone_bricks");
                    } else if (y % 6 == 0) {
                        // solai con botola per la scala
                        boolean hole = x == cx && z == cz + 2;
                        if (!hole) p.set(x, yy, z, "minecraft:polished_blackstone_bricks");
                    }
                }
        // scala a pioli al centro di un lato interno
        for (int y = 1; y <= H - 1; y++) p.set(cx, top + y, cz + 2, "minecraft:ladder[facing=north,waterlogged=false]");
        for (int y = 1; y <= H - 1; y++) p.set(cx, top + y, cz + 3, "minecraft:polished_blackstone_bricks");
        // terrazza in cima con merli, braciere e cristallo di ametista
        for (int x = cx - half - 1; x <= cx + half + 1; x++)
            for (int z = cz - half - 1; z <= cz + half + 1; z++) {
                boolean edge = Math.abs(x - cx) == half + 1 || Math.abs(z - cz) == half + 1;
                p.set(x, top + H, z, edge ? "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]" : "minecraft:polished_blackstone_bricks");
                if (edge && Math.floorMod(x + z, 2) == 0) p.set(x, top + H + 1, z, "minecraft:polished_blackstone_brick_wall");
            }
        p.remove(cx, top + H, cz + 2);
        p.set(cx, top + H, cz + 2, "minecraft:ladder[facing=north,waterlogged=false]");
        for (int y = 1; y <= 4; y++) p.set(cx - 2, top + H + y, cz - 2, y == 4 ? "minecraft:amethyst_block" : "minecraft:crying_obsidian");
        p.set(cx - 2, top + H + 5, cz - 2, "minecraft:amethyst_cluster[facing=up,waterlogged=false]");
        p.set(cx + 2, top + H + 1, cz - 2, "minecraft:soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]");
        // catene che pendono dalla terrazza
        for (int s = -1; s <= 1; s += 2) {
            for (int y = 1; y <= 6; y++) p.set(cx + s * (half + 1), top + H - y, cz, "minecraft:iron_chain[axis=y,waterlogged=false]");
            p.set(cx + s * (half + 1), top + H - 7, cz, "minecraft:soul_lantern[hanging=true,waterlogged=false]");
        }
    }

    // ---------------------------------------------------------------- passerelle

    /** Tre passerelle (nord, est, ovest) che scendono dai camminamenti delle mura fino al bordo della buca. */
    static void bridges(BuildPlan p, int top, int h) {
        String[] dirs = {"north", "east", "west"};
        for (String d : dirs) {
            int[] v = vec(d);
            int ux = -v[1], uz = v[0]; // perpendicolare
            int from = h + 7, to = RING - 2;
            int len = to - from;
            for (int k = 0; k <= len; k++) {
                int dist = from + k;
                int y = top + Math.min(WALL_H, (int) Math.round(k * (WALL_H + 0.0) / len));
                for (int w = -1; w <= 1; w++) {
                    int x = v[0] * dist + ux * (w + 4), z = v[1] * dist + uz * (w + 4);
                    if (y == top) continue;
                    p.set(x, y, z, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]");
                    if (w != 0) p.set(x, y + 1, z, "minecraft:polished_blackstone_brick_wall");
                    // pilastri ogni 5 blocchi
                    if (k % 5 == 0 && w != 0) for (int yy = top + 1; yy < y; yy++) p.set(x, yy, z, "minecraft:basalt[axis=y]");
                }
                if (k % 6 == 3) {
                    int x = v[0] * dist + ux * 6, z = v[1] * dist + uz * 6;
                    p.set(x, y + 2, z, "minecraft:soul_lantern[hanging=false,waterlogged=false]");
                }
            }
        }
    }

    // ---------------------------------------------------------------- zona sicura

    static void safeZone(BuildPlan p, int top) {
        int sx = (int) Math.floor(PVP_SPAWN[0]), sz = (int) Math.floor(PVP_SPAWN[2]);
        int R = PVP_SAFE_RADIUS;
        for (int x = sx - R - 2; x <= sx + R + 2; x++)
            for (int z = sz - R - 2; z <= sz + R + 2; z++) {
                double d = Math.hypot(x - sx, z - sz);
                if (d > R + 1.6) continue;
                for (int y = top + 1; y <= top + 6; y++) p.remove(x, y, z);
                String b;
                if (d <= 1.5) b = "minecraft:gold_block";
                else if (d <= 2.6) b = "minecraft:crying_obsidian";
                else if (d > R - 0.4 && d <= R + 0.6) b = "minecraft:gold_block";
                else if (d > R + 0.6) b = "minecraft:polished_blackstone_bricks";
                else b = Math.floorMod((int) Math.floor(ang(x - sx, z - sz) / 22.5), 2) == 0 ? "minecraft:polished_blackstone" : "minecraft:polished_deepslate";
                p.set(x, top, z, b);
                p.set(x, top - 1, z, "minecraft:blackstone");
                // muretto con varchi: verso la buca (nord) e ai lati
                if (d > R + 0.6) {
                    double a = ang(x - sx, z - sz);
                    boolean open = Math.abs(a - 270) < 16 || Math.abs(a - 180) < 10 || Math.abs(a - 0) < 10 || Math.abs(a - 360) < 10;
                    if (!open) {
                        p.set(x, top + 1, z, "minecraft:polished_blackstone_brick_wall");
                        if (Math.floorMod((int) a, 30) < 4) {
                            p.set(x, top + 2, z, "minecraft:polished_blackstone_brick_wall");
                            p.set(x, top + 3, z, "minecraft:soul_lantern[hanging=false,waterlogged=false]");
                        }
                    }
                }
            }
        // cancello verso la buca: due pilastri con l'arco e la saracinesca alzata
        for (int s = -1; s <= 1; s += 2) {
            int x = sx + s * 3, z = sz - R - 1;
            for (int y = 1; y <= 6; y++) p.set(x, top + y, z, y == 6 ? "minecraft:chiseled_polished_blackstone" : "minecraft:polished_blackstone_bricks");
            p.set(x, top + 7, z, "minecraft:soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]");
        }
        for (int dx = -2; dx <= 2; dx++) {
            p.set(sx + dx, top + 6, sz - R - 1, "minecraft:polished_blackstone_bricks");
            p.set(sx + dx, top + 5, sz - R - 1, "minecraft:iron_bars");
        }
        // stendardi della zona sicura
        for (int s = -1; s <= 1; s += 2) p.set(sx + s * 3, top + 4, sz - R, "minecraft:lime_wall_banner[facing=south]");
        // panchine e fuoco al centro
        p.set(sx, top + 1, sz - 3, "minecraft:soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]");
        for (int s = -1; s <= 1; s += 2) {
            p.set(sx + s * 4, top + 1, sz, "minecraft:polished_blackstone_stairs[facing=" + (s < 0 ? "west" : "east") + ",half=bottom]");
            p.set(sx + s * 4, top + 1, sz + 1, "minecraft:polished_blackstone_stairs[facing=" + (s < 0 ? "west" : "east") + ",half=bottom]");
        }
        // sentiero fino al bordo della buca
        for (int z = 27; z < sz - R - 1; z++)
            for (int dx = -2; dx <= 2; dx++)
                p.set(sx + dx, top, z, Math.abs(dx) == 2 ? "minecraft:polished_blackstone_bricks" : "minecraft:polished_blackstone");
    }

    // ---------------------------------------------------------------- portale dei Quantum (nord)

    static void quantumGate(BuildPlan p, int top, int h) {
        Frame f = new Frame(p, 0, top, -(h + 8), "north");
        // grande arco di ossidiana con cristalli e fasci di end rod
        for (int u = -9; u <= 9; u++)
            for (int y = 1; y <= 18; y++)
                for (int v = 0; v <= 2; v++) {
                    double e = Math.pow(u / 9.5, 2) + Math.pow(y / 18.5, 2);
                    double ei = Math.pow(u / 6.0, 2) + Math.pow(y / 14.0, 2);
                    if (e > 1 || (ei < 1 && v < 2)) continue;
                    double n = noise(u, y, v, SEED + 51);
                    f.set(u, y, v, ei < 1 ? "minecraft:crying_obsidian" : n < 0.65 ? "minecraft:obsidian" : n < 0.85 ? "minecraft:crying_obsidian" : "minecraft:amethyst_block");
                }
        for (int y = 2; y <= 12; y += 5)
            for (int s = -1; s <= 1; s += 2) {
                f.set(s * 7, y, -1, "minecraft:amethyst_cluster[facing={out},waterlogged=false]");
            }
        for (int u = -4; u <= 4; u++) f.set(u, 1, 1, "minecraft:polished_blackstone_bricks");
        f.set(0, 19, 1, "minecraft:amethyst_block");
        f.set(0, 20, 1, "minecraft:end_rod[facing=up]");
        f.set(0, 21, 1, "minecraft:end_rod[facing=up]");
        for (int s = -1; s <= 1; s += 2) {
            for (int y = 1; y <= 8; y++) f.set(s * 11, y, 1, y == 8 ? "minecraft:amethyst_block" : "minecraft:basalt[axis=y]");
            f.set(s * 11, 9, 1, "minecraft:amethyst_cluster[facing=up,waterlogged=false]");
        }
    }

    // ---------------------------------------------------------------- ripari e dettagli

    static void scatter(BuildPlan p, Island isl, int top, int h) {
        java.util.Random r = new java.util.Random(SEED + 61);
        int placed = 0;
        for (int tries = 0; tries < 900 && placed < 70; tries++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = h + 9 + r.nextDouble() * (RING - h - 13);
            if (r.nextInt(3) == 0) d = RING + 5 + r.nextDouble() * 12;
            int x = (int) Math.round(Math.cos(a) * d), z = (int) Math.round(Math.sin(a) * d);
            if (isl.topAt(x, z) != top || p.has(x, top + 1, z)) continue;
            if (Math.hypot(x - PVP_SPAWN[0], z - PVP_SPAWN[2]) < PVP_SAFE_RADIUS + 4) continue;
            if (Math.abs(x) <= 12 && z < -(h + 3) && z > -(h + 12)) continue;
            int kind = r.nextInt(7);
            switch (kind) {
                case 0, 1 -> cover(p, x, top, z, r);
                case 2 -> {
                    int hh = 3 + r.nextInt(6);
                    for (int y = 1; y <= hh; y++) p.set(x, top + y, z, "minecraft:basalt[axis=y]");
                    if (r.nextBoolean()) p.set(x + 1, top + 1, z, "minecraft:basalt[axis=y]");
                }
                case 3 -> crystal(p, x, top, z, r);
                case 4 -> {
                    p.set(x, top + 1, z, "minecraft:soul_soil");
                    p.set(x, top + 2, z, "minecraft:soul_fire");
                }
                case 5 -> {
                    int hh = 5 + r.nextInt(4);
                    for (int y = 1; y <= hh; y++) p.set(x, top + y, z, "minecraft:crimson_stem[axis=y]");
                    for (int ax = -2; ax <= 2; ax++) for (int az = -2; az <= 2; az++) for (int ay = 0; ay <= 1; ay++)
                        if (Math.abs(ax) + Math.abs(az) <= 3 - ay) p.set(x + ax, top + hh + ay, z + az, noise(ax, ay, az, SEED + x) < 0.12 ? "minecraft:shroomlight" : "minecraft:nether_wart_block");
                }
                default -> {
                    p.set(x, top + 1, z, "minecraft:wither_skeleton_skull[powered=false,rotation=" + r.nextInt(16) + "]");
                    p.set(x + 1, top + 1, z, "minecraft:cobweb");
                }
            }
            placed++;
        }
        // vegetazione del Nether
        for (int x = -80; x <= 80; x++)
            for (int z = -80; z <= 80; z++) {
                if (isl.topAt(x, z) != top || p.has(x, top + 1, z) || Math.max(Math.abs(x), Math.abs(z)) <= h + 7) continue;
                String g = p.get(x, top, z);
                if (g == null) continue;
                double n = noise(x, 5, z, SEED + 71);
                if (g.contains("crimson_nylium") && n < 0.4) p.set(x, top + 1, z, n < 0.2 ? "minecraft:crimson_roots" : "minecraft:crimson_fungus");
                else if (g.contains("warped_nylium") && n < 0.4) p.set(x, top + 1, z, n < 0.25 ? "minecraft:warped_roots" : "minecraft:nether_sprouts");
                else if (g.contains("soul_soil") && n < 0.05) p.set(x, top + 1, z, "minecraft:bone_block[axis=y]");
            }
    }

    /** Riparo: muretto a L di blackstone con una feritoia. */
    static void cover(BuildPlan p, int x, int top, int z, java.util.Random r) {
        boolean ax = r.nextBoolean();
        int len = 3 + r.nextInt(3);
        for (int k = 0; k < len; k++)
            for (int y = 1; y <= 2; y++) {
                int bx = x + (ax ? k : 0), bz = z + (ax ? 0 : k);
                p.set(bx, top + y, bz, y == 2 && k == len / 2 ? "minecraft:iron_bars" : r.nextInt(5) == 0 ? "minecraft:cracked_polished_blackstone_bricks" : "minecraft:polished_blackstone_bricks");
            }
        for (int k = 1; k <= 2; k++) p.set(x + (ax ? 0 : k), top + 1, z + (ax ? k : 0), "minecraft:polished_blackstone_bricks");
        p.set(x, top + 3, z, "minecraft:polished_blackstone_brick_slab[type=bottom,waterlogged=false]");
    }

    /** Cristallo di ametista (blocchi e grappoli). */
    static void crystal(BuildPlan p, int x, int top, int z, java.util.Random r) {
        int hh = 2 + r.nextInt(4);
        for (int y = 1; y <= hh; y++) p.set(x, top + y, z, "minecraft:amethyst_block");
        p.set(x, top + hh + 1, z, "minecraft:amethyst_cluster[facing=up,waterlogged=false]");
        for (String d : CARD) {
            if (r.nextInt(3) == 0) continue;
            int[] v = vec(d);
            p.set(x + v[0], top + 1, z + v[1], "minecraft:amethyst_block");
            p.set(x + v[0] * 2, top + 1, z + v[1] * 2, "minecraft:medium_amethyst_bud[facing=" + d + ",waterlogged=false]");
            p.set(x + v[0], top + 2, z + v[1], "minecraft:large_amethyst_bud[facing=up,waterlogged=false]");
        }
    }
}

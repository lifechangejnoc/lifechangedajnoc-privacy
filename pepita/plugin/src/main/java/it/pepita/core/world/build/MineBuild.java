package it.pepita.core.world.build;

import it.pepita.core.mine.Mine;
import it.pepita.core.world.BuildPlan;
import it.pepita.core.world.Layout;

import java.util.Random;

import static it.pepita.core.world.build.BuildUtil.*;

/**
 * Isola tematica attorno a una miniera (mondo pepita_miniere). L'interno della buca non viene mai scritto:
 * lo riempie il plugin. Pareti e fondo a ±1, bordo calpestabile a maxY+1, pedana d'arrivo a sud, spazio libero
 * per l'insegna a nord con un monumento dietro, niente sopra la buca per almeno 14 blocchi.
 * Temi: pietra, ardesia, nera, prismarino, end, prestigio, vip, evasione.
 */
public final class MineBuild {
    private MineBuild() {}

    /** Tavolozza di un tema. */
    record Theme(String name, String wall, String wall2, String stripe, String light, String rim, String rim2, String edge,
                 String edgeStairs, String post, String lantern, String surface, Island.Mat mat) {}

    /** Contesto condiviso dalle decorazioni. */
    static final class Ctx {
        final BuildPlan p;
        final Mine m;
        final Theme t;
        final Island isl;
        final int cx, cz, h, top; // h = semilato interno, top = y del bordo (maxY+1)
        final long seed;
        final Random r;

        /** Rilievo sopra la cima piatta dell'isola (0 = piano). */
        int[][] hill;
        int hx0, hz0;

        int hill(int x, int z) {
            int i = x - hx0, k = z - hz0;
            if (hill == null || i < 0 || k < 0 || i >= hill.length || k >= hill[0].length) return 0;
            return hill[i][k];
        }

        /** Quota del terreno (ultimo blocco pieno) in x,z. */
        int gy(int x, int z) {
            return top + hill(x, z);
        }

        /** Pendenza massima con i vicini. */
        int slope(int x, int z) {
            int h0 = hill(x, z), s = 0;
            for (String d : CARD) {
                int[] v = vec(d);
                s = Math.max(s, Math.abs(hill(x + v[0], z + v[1]) - h0));
            }
            return s;
        }

        /** Terreno piano (stessa quota) in un quadrato di raggio r. */
        boolean level(int x, int z, int r) {
            int h0 = hill(x, z);
            for (int ax = -r; ax <= r; ax++) for (int az = -r; az <= r; az++)
                if (!ground(x + ax, z + az) || hill(x + ax, z + az) != h0) return false;
            return true;
        }

        Ctx(BuildPlan p, Mine m, Theme t, Island isl, long seed) {
            this.p = p;
            this.m = m;
            this.t = t;
            this.isl = isl;
            this.cx = (m.minX + m.maxX) / 2;
            this.cz = (m.minZ + m.maxZ) / 2;
            this.h = (m.maxX - m.minX) / 2;
            this.top = m.maxY + 1;
            this.seed = seed;
            this.r = new Random(seed);
        }

        int cheb(int x, int z) {
            return Math.max(Math.abs(x - cx), Math.abs(z - cz));
        }

        /** Zone da non decorare: buca + bordo, piazzola d'arrivo, insegna e monumento nord. */
        boolean reserved(int x, int z) {
            int dx = x - cx, dz = z - cz;
            if (cheb(x, z) <= h + 6) return true;
            if (Math.abs(dx) <= 5 && dz >= h + 3 && dz <= h + 14) return true;
            if (Math.abs(dx) <= 10 && dz <= -(h + 4) && dz >= -(h + 15)) return true;
            return false;
        }

        /** Terreno dell'isola non riservato e poco inclinato. */
        boolean free(int x, int z) {
            return !reserved(x, z) && isl.inside(x, z) && isl.topAt(x, z) == top && isl.t(x, z) < 0.9 && slope(x, z) <= 1;
        }

        boolean ground(int x, int z) {
            return isl.inside(x, z) && isl.topAt(x, z) == top;
        }

        void set(int x, int y, int z, String b) {
            p.set(x, y, z, mc(b));
        }

        double n(int x, int y, int z, long s) {
            return noise(x, y, z, seed + s);
        }
    }

    static Theme theme(String t) {
        return switch (t) {
            case "ardesia" -> new Theme(t, "deepslate_bricks", "cracked_deepslate_bricks", "deepslate_tiles", "shroomlight",
                    "polished_deepslate", "deepslate_tiles", "chiseled_deepslate", "polished_deepslate_stairs", "deepslate_brick_wall",
                    "lantern", "moss_block", MineBuild::matDeepslate);
            case "nera" -> new Theme(t, "polished_blackstone_bricks", "cracked_polished_blackstone_bricks", "gilded_blackstone", "shroomlight",
                    "polished_blackstone", "polished_blackstone_bricks", "gold_block", "polished_blackstone_brick_stairs",
                    "polished_blackstone_brick_wall", "soul_lantern", "blackstone", MineBuild::matBlack);
            case "prismarino" -> new Theme(t, "prismarine_bricks", "prismarine", "dark_prismarine", "sea_lantern",
                    "dark_prismarine", "prismarine_bricks", "sea_lantern", "dark_prismarine_stairs", "prismarine_wall",
                    "sea_lantern", "sand", MineBuild::matSea);
            case "end" -> new Theme(t, "end_stone_bricks", "end_stone", "purpur_pillar[axis=y]", "pearlescent_froglight",
                    "purpur_block", "end_stone_bricks", "purpur_pillar[axis=x]", "purpur_stairs", "end_stone_brick_wall",
                    "end_rod[facing=up]", "end_stone", MineBuild::matEnd);
            case "prestigio" -> new Theme(t, "purpur_block", "amethyst_block", "quartz_bricks", "pearlescent_froglight",
                    "smooth_quartz", "quartz_bricks", "amethyst_block", "smooth_quartz_stairs", "polished_blackstone_brick_wall",
                    "soul_lantern", "moss_block", MineBuild::matAmethyst);
            case "vip" -> new Theme(t, "smooth_quartz", "quartz_bricks", "gold_block", "ochre_froglight",
                    "smooth_quartz", "polished_diorite", "gold_block", "smooth_quartz_stairs", "polished_blackstone_wall",
                    "lantern", "grass_block", MineBuild::matVip);
            case "evasione" -> new Theme(t, "obsidian", "crying_obsidian", "polished_blackstone_bricks", "shroomlight",
                    "polished_blackstone", "cracked_polished_blackstone_bricks", "crying_obsidian", "polished_blackstone_stairs",
                    "blackstone_wall", "soul_lantern", "soul_soil", MineBuild::matEvasione);
            default -> new Theme("pietra", "stone_bricks", "mossy_stone_bricks", "chiseled_stone_bricks", "ochre_froglight",
                    "polished_andesite", "stone_bricks", "chiseled_stone_bricks", "stone_brick_stairs", "stone_brick_wall",
                    "lantern", "grass_block", Island::stone);
        };
    }

    public static void build(BuildPlan p, Mine m) {
        Theme t = theme(m.theme == null ? "pietra" : m.theme);
        int cx = (m.minX + m.maxX) / 2, cz = (m.minZ + m.maxZ) / 2;
        long seed = 7001L + t.name.hashCode() * 31L + cx * 7L + cz * 13L;
        int top = m.maxY + 1;
        Island isl = new Island(cx, cz, top, Layout.MINE_ISLAND_RADIUS - 8, top - (m.minY - 1) + 12, seed);
        isl.rough = 0.13;
        isl.lumpy = 0.35;
        isl.mat = t.mat;
        isl.surface = mc(t.surface);
        if (t.name.equals("end") || t.name.equals("prismarino") || t.name.equals("nera") || t.name.equals("evasione")) {
            isl.vine = t.name.equals("nera") || t.name.equals("evasione") ? "minecraft:weeping_vines_plant" : "minecraft:vine";
            isl.vines = !t.name.equals("end");
            isl.hanging = t.name.equals("prismarino") ? "minecraft:hanging_roots" : t.name.equals("end") ? "minecraft:chorus_flower[age=5]" : "minecraft:weeping_vines";
            isl.dripstone = false;
        }
        isl.prepare();
        isl.build(p);
        Ctx c = new Ctx(p, m, t, isl, seed);
        terrain(c);

        // la buca: via tutto quello che l'isola ha messo dentro e attorno, poi pareti e fondo a ±1
        clear(p, m.minX - 1, m.minY - 3, m.minZ - 1, m.maxX + 1, top + 20, m.maxZ + 1);
        pit(c);
        rim(c);
        surfacePatches(c);
        arrival(c);
        MineThemes.decorate(c);
        // aria garantita attorno all'insegna e sopra la buca
        int lx = (int) Math.floor(m.labelX), ly = (int) Math.floor(m.labelY), lz = (int) Math.floor(m.labelZ);
        clear(p, lx - 2, ly - 2, lz - 2, lx + 2, ly + 2, lz + 2);
        clear(p, m.minX, top, m.minZ, m.maxX, top + 16, m.maxZ);
        int sx = (int) Math.floor(m.spawnX), sz = (int) Math.floor(m.spawnZ);
        for (int y = top + 1; y <= top + 3; y++) p.remove(sx, y, sz);
        p.set(sx, top, sz, "minecraft:gold_block");
        // l'interno non va mai toccato
        for (int x = m.minX; x <= m.maxX; x++)
            for (int z = m.minZ; z <= m.maxZ; z++)
                for (int y = m.minY; y <= m.maxY; y++) p.remove(x, y, z);
        finish(p);
    }

    // =====================================================================
    //  Rilievo
    // =====================================================================

    /** Ampiezza delle colline per tema. */
    static double amp(String t) {
        return switch (t) {
            case "pietra" -> 16;
            case "ardesia" -> 9;
            case "evasione" -> 6;
            case "end" -> 6;
            case "prismarino" -> 5;
            case "prestigio" -> 5;
            case "nera" -> 4;
            default -> 3;
        };
    }

    static double smooth(double v) {
        v = Math.max(0, Math.min(1, v));
        return v * v * (3 - 2 * v);
    }

    /** Colline attorno alla buca: la miniera sta in una conca, con passaggi piani verso l'arrivo e il monumento. */
    static void terrain(Ctx c) {
        int R = (int) Math.ceil(c.isl.radius * 1.3) + 2;
        c.hx0 = c.cx - R;
        c.hz0 = c.cz - R;
        c.hill = new int[2 * R + 1][2 * R + 1];
        double A = amp(c.t.name());
        int h = c.h;
        for (int i = 0; i <= 2 * R; i++)
            for (int k = 0; k <= 2 * R; k++) {
                int x = c.hx0 + i, z = c.hz0 + k;
                if (!c.ground(x, z)) continue;
                int dx = x - c.cx, dz = z - c.cz;
                double d = Math.max(Math.abs(dx), Math.abs(dz)) * 0.6 + Math.hypot(dx, dz) * 0.4;
                double ramp = smooth((d - (h + 9)) / 13.0);
                double t = c.isl.t(x, z);
                double coast = 0.45 + 0.55 * (1 - smooth((t - 0.8) / 0.2));
                double n = fbm(x / 15.0, z / 15.0, c.seed + 101, 3);
                double ridge = 1 - Math.abs(2 * fbm(x / 22.0, z / 22.0, c.seed + 103, 2) - 1);
                double v = A * ramp * coast * (0.25 + 0.5 * n + 0.35 * ridge);
                // corridoi piani: arrivo a sud e monumento a nord
                if (dz > 0) v *= smooth((Math.abs(dx) - 4) / 5.0);
                if (dz < 0 && dz > -(h + 16)) v *= smooth((Math.abs(dx) - 11) / 4.0);
                int hv = Math.max(0, (int) Math.floor(v));
                // gradoni di cava: quote a multipli di 3 (pietra) o di 2 (ardesia)
                if (c.t.name().equals("pietra")) hv = hv / 3 * 3;
                else if (c.t.name().equals("ardesia")) hv = hv / 2 * 2;
                c.hill[i][k] = hv;
            }
        // scrivi le colonne delle colline
        for (int i = 0; i <= 2 * R; i++)
            for (int k = 0; k <= 2 * R; k++) {
                int hh = c.hill[i][k];
                if (hh <= 0) continue;
                int x = c.hx0 + i, z = c.hz0 + k;
                int sl = c.slope(x, z);
                for (int y = c.top; y <= c.top + hh; y++) {
                    int ft = c.top + hh - y;
                    String b;
                    if (ft == 0) b = sl >= 2 ? rock(c, x, y, z) : mc(c.t.surface());
                    else if (sl >= 2 && ft < 3) b = rock(c, x, y, z);
                    else b = c.t.mat().at(x, y, z, ft, 9, sl >= 2);
                    if (b != null) c.p.set(x, y, z, b);
                }
            }
    }

    static String rock(Ctx c, int x, int y, int z) {
        double n = noise(x, y, z, c.seed + 107);
        return switch (c.t.name()) {
            case "ardesia" -> n < 0.5 ? "minecraft:cobbled_deepslate" : n < 0.8 ? "minecraft:deepslate" : "minecraft:tuff";
            case "nera", "evasione" -> n < 0.5 ? "minecraft:blackstone" : n < 0.8 ? "minecraft:basalt[axis=y]" : "minecraft:smooth_basalt";
            case "prismarino" -> n < 0.6 ? "minecraft:sandstone" : "minecraft:prismarine";
            case "end" -> "minecraft:end_stone";
            case "prestigio" -> n < 0.5 ? "minecraft:calcite" : n < 0.8 ? "minecraft:smooth_basalt" : "minecraft:amethyst_block";
            case "vip" -> n < 0.6 ? "minecraft:stone" : "minecraft:diorite";
            default -> n < 0.45 ? "minecraft:stone" : n < 0.7 ? "minecraft:andesite" : n < 0.88 ? "minecraft:cobblestone" : "minecraft:gravel";
        };
    }

    // =====================================================================
    //  Buca, bordo, arrivo
    // =====================================================================

    static void pit(Ctx c) {
        Mine m = c.m;
        Theme t = c.t;
        int x1 = m.minX - 1, x2 = m.maxX + 1, z1 = m.minZ - 1, z2 = m.maxZ + 1, bottom = m.minY - 1;
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                c.set(x, bottom, z, (x + z) % 2 == 0 ? t.wall : t.rim);
                c.set(x, bottom - 1, z, t.wall);
                boolean side = x == x1 || x == x2 || z == z1 || z == z2;
                if (!side) continue;
                for (int y = bottom; y <= m.maxY; y++) {
                    int along = (x == x1 || x == x2) ? z : x;
                    int fromTop = m.maxY - y;
                    String b;
                    if (fromTop % 6 == 2) b = t.stripe;
                    else b = c.n(x, y, z, 31) < 0.78 ? t.wall : t.wall2;
                    // luci incassate a scacchiera: la buca resta illuminata fino in fondo
                    if (fromTop % 6 == 5 && Math.floorMod(along - (fromTop / 6) * 3, 6) == 0) b = t.light;
                    c.set(x, y, z, b);
                    // contrafforte esterno (spessore 2) per dare profondità alle pareti viste da sotto
                    int ox = x == x1 ? -1 : x == x2 ? 1 : 0, oz = z == z1 ? -1 : z == z2 ? 1 : 0;
                    if (y < m.maxY - 2) c.set(x + ox, y, z + oz, c.n(x, y, z, 33) < 0.6 ? t.wall : t.wall2);
                }
            }
    }

    static void rim(Ctx c) {
        Theme t = c.t;
        int h = c.h, top = c.top;
        for (int dx = -(h + 6); dx <= h + 6; dx++)
            for (int dz = -(h + 6); dz <= h + 6; dz++) {
                int d = Math.max(Math.abs(dx), Math.abs(dz));
                if (d <= h) continue;
                int x = c.cx + dx, z = c.cz + dz;
                if (d > h + 4 && !c.isl.inside(x, z)) continue;
                String b;
                if (d == h + 1) b = t.edge;                       // cordolo sopra la parete
                else if (d <= h + 4) {
                    boolean corner = Math.abs(dx) > h && Math.abs(dz) > h;
                    b = corner ? t.rim2 : (Math.floorMod(dx + dz, 2) == 0 || c.n(x, 0, z, 41) < 0.3) ? t.rim : t.rim2;
                } else if (d == h + 5) b = t.rim2;
                else {
                    if (!c.ground(x, z) || c.hill(x, z) > 0) continue;
                    b = c.n(x, 1, z, 43) < 0.5 ? t.rim2 : mc(t.surface);
                }
                c.set(x, top, z, b);
                // sotto il bordo: pieno fino alle pareti (niente buchi tra bordo e isola)
                c.set(x, top - 1, z, t.wall);
                if (d <= h + 3) c.set(x, top - 2, z, t.wall);
            }
        // gradino decorativo sul lato esterno del bordo (scalini rivolti verso fuori) e lampioni agli angoli
        for (int sx = -1; sx <= 1; sx += 2)
            for (int sz = -1; sz <= 1; sz += 2) {
                int x = c.cx + sx * (h + 4), z = c.cz + sz * (h + 4);
                lampPost(c, x, z, 4);
            }
        // lampioni intermedi sui lati (non davanti all'arrivo e all'insegna)
        for (int k = -1; k <= 1; k += 2) {
            lampPost(c, c.cx + k * (h + 4), c.cz, 3);
            lampPost(c, c.cx + k * 8, c.cz + h + 4, 3);
            lampPost(c, c.cx + k * 8, c.cz - (h + 4), 3);
        }
    }

    static void lampPost(Ctx c, int x, int z, int hgt) {
        Theme t = c.t;
        int y = c.top + 1;
        c.set(x, y, z, t.rim2);
        for (int k = 1; k < hgt; k++) c.set(x, y + k, z, t.post);
        String lan = t.lantern.contains("[") ? mc(t.lantern) : mc(t.lantern) + (t.lantern.endsWith("lantern") && !t.lantern.equals("sea_lantern") ? "[hanging=false]" : "");
        c.set(x, y + hgt, z, lan);
    }

    /** Macchie di terreno diverso sulla cima dell'isola (prato con sentieri, sabbia con ghiaia...). */
    static void surfacePatches(Ctx c) {
        String a, b;
        switch (c.t.name) {
            case "ardesia" -> { a = "minecraft:coarse_dirt"; b = "minecraft:podzol"; }
            case "nera" -> { a = "minecraft:basalt[axis=y]"; b = "minecraft:crimson_nylium"; }
            case "prismarino" -> { a = "minecraft:gravel"; b = "minecraft:suspicious_sand[dusted=0]"; }
            case "end" -> { a = "minecraft:end_stone_bricks"; b = "minecraft:end_stone"; }
            case "prestigio" -> { a = "minecraft:calcite"; b = "minecraft:grass_block[snowy=false]"; }
            case "vip" -> { a = "minecraft:moss_block"; b = "minecraft:grass_block[snowy=false]"; }
            case "evasione" -> { a = "minecraft:soul_sand"; b = "minecraft:blackstone"; }
            default -> { a = "minecraft:coarse_dirt"; b = "minecraft:podzol"; }
        }
        int R = (int) Math.ceil(c.isl.radius * 1.25);
        for (int x = c.cx - R; x <= c.cx + R; x++)
            for (int z = c.cz - R; z <= c.cz + R; z++) {
                if (!c.ground(x, z) || c.cheb(x, z) <= c.h + 5 || c.slope(x, z) >= 2) continue;
                double n = fbm(x / 9.0, z / 9.0, c.seed + 61, 3);
                int y = c.gy(x, z);
                if (n < 0.33) c.p.set(x, y, z, a);
                else if (n > 0.70) c.p.set(x, y, z, b);
            }
    }

    /** Piazzola d'arrivo a sud, collegata al bordo, con panchine e insegne. */
    static void arrival(Ctx c) {
        Theme t = c.t;
        int sx = (int) Math.floor(c.m.spawnX), sz = (int) Math.floor(c.m.spawnZ);
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -3; dz <= 4; dz++) {
                int x = sx + dx, z = sz + dz;
                int ad = Math.max(Math.abs(dx), Math.abs(dz));
                String b = ad <= 1 ? t.edge : ad == 4 || Math.abs(dx) == 4 ? t.rim2 : t.rim;
                c.set(x, c.top, z, b);
                c.set(x, c.top - 1, z, t.wall);
            }
        // muretti laterali con lanterne
        for (int dz = -2; dz <= 4; dz++)
            for (int s = -1; s <= 1; s += 2) {
                int x = sx + s * 5, z = sz + dz;
                if (!c.ground(x, z) && dz > 2) continue;
                c.set(x, c.top, z, t.rim2);
                c.set(x, c.top + 1, z, t.post);
                if (dz == -2 || dz == 4) {
                    c.set(x, c.top + 2, z, t.post);
                    lampOn(c, x, c.top + 3, z);
                }
            }
        // sentiero verso sud fino al bordo dell'isola
        for (int z = sz + 5; z <= sz + 20; z++)
            for (int dx = -1; dx <= 1; dx++) {
                int x = sx + dx;
                if (!c.ground(x, z)) continue;
                c.set(x, c.gy(x, z), z, dx == 0 ? t.rim : t.rim2);
            }
    }

    static void lampOn(Ctx c, int x, int y, int z) {
        Theme t = c.t;
        String lan = t.lantern.contains("[") ? mc(t.lantern) : mc(t.lantern) + (t.lantern.endsWith("lantern") && !t.lantern.equals("sea_lantern") ? "[hanging=false]" : "");
        c.set(x, y, z, lan);
    }

    // =====================================================================
    //  Materiali delle isole
    // =====================================================================

    static String matDeepslate(int x, int y, int z, int ft, int fb, boolean side) {
        double n = noise(x, y, z, 1201);
        if (ft <= 1) return n < 0.5 ? "minecraft:rooted_dirt" : "minecraft:coarse_dirt";
        if (side && ft < 6 && noise(x, y, z, 1203) < 0.25) return "minecraft:moss_block";
        double o = noise(x, y, z, 1207);
        if (o < 0.03) return "minecraft:deepslate_coal_ore";
        if (o < 0.04) return "minecraft:deepslate_iron_ore";
        if (o < 0.045) return "minecraft:deepslate_gold_ore";
        return n < 0.45 ? "minecraft:deepslate" : n < 0.7 ? "minecraft:cobbled_deepslate" : n < 0.88 ? "minecraft:tuff" : "minecraft:smooth_basalt";
    }

    static String matBlack(int x, int y, int z, int ft, int fb, boolean side) {
        double n = noise(x, y, z, 1301);
        double o = noise(x, y, z, 1303);
        if (o < 0.035) return "minecraft:gilded_blackstone";
        if (o < 0.045) return "minecraft:nether_gold_ore";
        if (o < 0.05) return "minecraft:magma_block";
        if (ft <= 1) return n < 0.6 ? "minecraft:blackstone" : "minecraft:basalt[axis=y]";
        return n < 0.45 ? "minecraft:blackstone" : n < 0.7 ? "minecraft:basalt[axis=y]" : n < 0.88 ? "minecraft:smooth_basalt" : "minecraft:polished_blackstone";
    }

    static String matSea(int x, int y, int z, int ft, int fb, boolean side) {
        double n = noise(x, y, z, 1401);
        if (ft <= 2) return n < 0.6 ? "minecraft:sandstone" : "minecraft:sand";
        double o = noise(x, y, z, 1403);
        if (o < 0.04) return "minecraft:sea_lantern";
        if (side && ft < 8 && o < 0.12) return n < 0.5 ? "minecraft:tube_coral_block" : "minecraft:brain_coral_block";
        return n < 0.4 ? "minecraft:prismarine" : n < 0.65 ? "minecraft:dark_prismarine" : n < 0.85 ? "minecraft:prismarine_bricks" : "minecraft:sandstone";
    }

    static String matEnd(int x, int y, int z, int ft, int fb, boolean side) {
        double n = noise(x, y, z, 1501);
        if (ft <= 2) return "minecraft:end_stone";
        double o = noise(x, y, z, 1503);
        if (o < 0.03) return "minecraft:obsidian";
        if (o < 0.045) return "minecraft:purpur_block";
        return n < 0.75 ? "minecraft:end_stone" : "minecraft:end_stone_bricks";
    }

    static String matAmethyst(int x, int y, int z, int ft, int fb, boolean side) {
        double n = noise(x, y, z, 1601);
        if (ft <= 1) return n < 0.6 ? "minecraft:dirt" : "minecraft:rooted_dirt";
        double o = noise(x, y, z, 1603);
        if (o < 0.06) return "minecraft:amethyst_block";
        if (o < 0.075) return "minecraft:calcite";
        return n < 0.45 ? "minecraft:smooth_basalt" : n < 0.7 ? "minecraft:calcite" : n < 0.9 ? "minecraft:deepslate" : "minecraft:tuff";
    }

    static String matVip(int x, int y, int z, int ft, int fb, boolean side) {
        double n = noise(x, y, z, 1701);
        if (ft <= 2) return n < 0.7 ? "minecraft:dirt" : "minecraft:coarse_dirt";
        double o = noise(x, y, z, 1703);
        if (o < 0.03) return "minecraft:gold_ore";
        if (o < 0.045) return "minecraft:emerald_ore";
        if (o < 0.05) return "minecraft:raw_gold_block";
        return n < 0.5 ? "minecraft:stone" : n < 0.75 ? "minecraft:diorite" : n < 0.9 ? "minecraft:calcite" : "minecraft:andesite";
    }

    static String matEvasione(int x, int y, int z, int ft, int fb, boolean side) {
        double n = noise(x, y, z, 1801);
        if (ft <= 1) return n < 0.5 ? "minecraft:soul_soil" : "minecraft:soul_sand";
        double o = noise(x, y, z, 1803);
        if (o < 0.04) return "minecraft:crying_obsidian";
        if (o < 0.07) return "minecraft:obsidian";
        return n < 0.45 ? "minecraft:blackstone" : n < 0.75 ? "minecraft:basalt[axis=y]" : "minecraft:polished_blackstone";
    }
}

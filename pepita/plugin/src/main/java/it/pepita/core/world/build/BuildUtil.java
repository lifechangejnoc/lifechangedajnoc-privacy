package it.pepita.core.world.build;

import it.pepita.core.world.BuildPlan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Funzioni comuni alle costruzioni (pure: nessun riferimento al server).
 *
 * <p>I blocchi vengono piazzati dal plugin SENZA fisica (setBlockData(.., false)), quindi muretti, recinzioni,
 * sbarre, vetri e scale non si collegano da soli: {@link #finish(BuildPlan)} calcola gli stati di collegamento
 * come farebbe il gioco (muretti alti/bassi e pilastrino, sbarre, recinzioni, angoli delle scale, foglie persistenti).
 * Ogni build lo chiama alla fine.</p>
 */
public final class BuildUtil {
    private BuildUtil() {}

    // =====================================================================================
    //  Rumore deterministico
    // =====================================================================================

    public static double noise(int x, int y, int z, long seed) {
        long h = seed;
        h ^= x * 0x9E3779B97F4A7C15L;
        h = Long.rotateLeft(h, 27) ^ (y * 0xC2B2AE3D27D4EB4FL);
        h = Long.rotateLeft(h, 31) ^ (z * 0x165667B19E3779F9L);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (h & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    private static double smooth(double t) { return t * t * (3 - 2 * t); }

    /** Rumore di valore 2D interpolato (0..1). */
    public static double vnoise(double x, double z, long seed) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        double fx = smooth(x - x0), fz = smooth(z - z0);
        double a = noise(x0, 0, z0, seed), b = noise(x0 + 1, 0, z0, seed), c = noise(x0, 0, z0 + 1, seed), d = noise(x0 + 1, 0, z0 + 1, seed);
        return (a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + d * fx) * fz;
    }

    /** Rumore di valore 3D interpolato (0..1). */
    public static double vnoise3(double x, double y, double z, long seed) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
        double fx = smooth(x - x0), fy = smooth(y - y0), fz = smooth(z - z0);
        double r = 0;
        for (int dx = 0; dx <= 1; dx++)
            for (int dy = 0; dy <= 1; dy++)
                for (int dz = 0; dz <= 1; dz++)
                    r += noise(x0 + dx, y0 + dy, z0 + dz, seed) * (dx == 1 ? fx : 1 - fx) * (dy == 1 ? fy : 1 - fy) * (dz == 1 ? fz : 1 - fz);
        return r;
    }

    /** Rumore frattale 2D (0..1 circa). */
    public static double fbm(double x, double z, long seed, int oct) {
        double s = 0, amp = 1, tot = 0, f = 1;
        for (int i = 0; i < oct; i++) {
            s += vnoise(x * f, z * f, seed + i * 101) * amp;
            tot += amp;
            amp *= 0.5;
            f *= 2.03;
        }
        return s / tot;
    }

    /** Scelta pesata deterministica: pick(n, "a", 3, "b", 1) con n in 0..1. */
    public static String pick(double n, Object... opts) {
        double tot = 0;
        for (int i = 1; i < opts.length; i += 2) tot += ((Number) opts[i]).doubleValue();
        double r = n * tot;
        for (int i = 0; i < opts.length; i += 2) {
            r -= ((Number) opts[i + 1]).doubleValue();
            if (r < 0) return (String) opts[i];
        }
        return (String) opts[opts.length - 2];
    }

    // =====================================================================================
    //  Direzioni
    // =====================================================================================

    public static final String[] CARD = {"north", "east", "south", "west"};

    public static int[] vec(String f) {
        return switch (f) {
            case "north" -> new int[]{0, -1};
            case "south" -> new int[]{0, 1};
            case "west" -> new int[]{-1, 0};
            default -> new int[]{1, 0};
        };
    }

    public static String opp(String f) {
        return switch (f) {
            case "north" -> "south";
            case "south" -> "north";
            case "west" -> "east";
            default -> "west";
        };
    }

    /** Rotazione oraria vista dall'alto (north -> east). */
    public static String cw(String f) {
        return switch (f) {
            case "north" -> "east";
            case "east" -> "south";
            case "south" -> "west";
            default -> "north";
        };
    }

    public static String ccw(String f) { return opp(cw(f)); }

    /** Direzione cardinale più vicina al vettore (dx, dz). */
    public static String face(double dx, double dz) {
        if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? "east" : "west";
        return dz >= 0 ? "south" : "north";
    }

    // =====================================================================================
    //  Stringhe di blocco
    // =====================================================================================

    public static String mc(String s) { return s.startsWith("minecraft:") ? s : "minecraft:" + s; }

    public static String stairs(String base, String facing, boolean top) {
        return mc(base) + "_stairs[facing=" + facing + ",half=" + (top ? "top" : "bottom") + "]";
    }

    public static String stairs(String base, String facing, boolean top, String shape) {
        return mc(base) + "_stairs[facing=" + facing + ",half=" + (top ? "top" : "bottom") + ",shape=" + shape + "]";
    }

    public static String slab(String base, boolean top) {
        return mc(base) + "_slab[type=" + (top ? "top" : "bottom") + "]";
    }

    public static String id(String d) {
        String s = d.startsWith("minecraft:") ? d.substring(10) : d;
        int b = s.indexOf('[');
        return b >= 0 ? s.substring(0, b) : s;
    }

    public static Map<String, String> props(String d) {
        Map<String, String> m = new TreeMap<>();
        int b = d.indexOf('[');
        if (b < 0 || !d.endsWith("]")) return m;
        String body = d.substring(b + 1, d.length() - 1);
        if (body.isEmpty()) return m;
        for (String kv : body.split(",")) {
            String[] p = kv.split("=");
            if (p.length == 2) m.put(p[0].trim(), p[1].trim());
        }
        return m;
    }

    public static String with(String id, Map<String, String> props) {
        StringBuilder sb = new StringBuilder(mc(id));
        if (!props.isEmpty()) {
            sb.append('[');
            boolean first = true;
            for (Map.Entry<String, String> e : props.entrySet()) {
                if (!first) sb.append(',');
                sb.append(e.getKey()).append('=').append(e.getValue());
                first = false;
            }
            sb.append(']');
        }
        return sb.toString();
    }

    // =====================================================================================
    //  Oggetti a più blocchi
    // =====================================================================================

    /** Porta a due blocchi. facing = direzione verso cui guarda chi l'ha piazzata. */
    public static void door(BuildPlan p, int x, int y, int z, String mat, String facing, String hinge, boolean open) {
        String b = mc(mat) + "_door[facing=" + facing + ",hinge=" + hinge + ",open=" + open + ",powered=false,half=";
        p.set(x, y, z, b + "lower]");
        p.set(x, y + 1, z, b + "upper]");
    }

    /** Letto: piedi in (x,y,z), testa verso facing. */
    public static void bed(BuildPlan p, int x, int y, int z, String color, String facing) {
        int[] v = vec(facing);
        p.set(x, y, z, "minecraft:" + color + "_bed[facing=" + facing + ",part=foot,occupied=false]");
        p.set(x + v[0], y, z + v[1], "minecraft:" + color + "_bed[facing=" + facing + ",part=head,occupied=false]");
    }

    /** Pianta alta a due blocchi (tall_grass, large_fern, rose_bush, peony, lilac, sunflower). */
    public static void tall(BuildPlan p, int x, int y, int z, String plant) {
        p.set(x, y, z, mc(plant) + "[half=lower]");
        p.set(x, y + 1, z, mc(plant) + "[half=upper]");
    }

    // =====================================================================================
    //  Classificazione dei blocchi
    // =====================================================================================

    private static final Set<String> PLANTS = new HashSet<>(Arrays.asList(
            "short_grass", "tall_grass", "fern", "large_fern", "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip",
            "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley", "wither_rose", "torchflower",
            "sunflower", "lilac", "rose_bush", "peony", "pink_petals", "wildflowers", "dead_bush", "sweet_berry_bush", "sugar_cane", "bamboo",
            "cactus", "kelp", "kelp_plant", "seagrass", "tall_seagrass", "crimson_roots", "warped_roots", "nether_sprouts", "glow_lichen",
            "hanging_roots", "spore_blossom", "moss_carpet", "pale_moss_carpet", "azalea", "flowering_azalea", "cave_vines", "cave_vines_plant",
            "weeping_vines", "weeping_vines_plant", "twisting_vines", "twisting_vines_plant", "chorus_plant", "chorus_flower", "big_dripleaf",
            "big_dripleaf_stem", "small_dripleaf", "firefly_bush", "bush", "short_dry_grass", "tall_dry_grass", "leaf_litter", "cactus_flower",
            "pale_hanging_moss", "closed_eyeblossom", "open_eyeblossom", "pitcher_plant", "vine", "crimson_fungus", "warped_fungus",
            "brown_mushroom", "red_mushroom", "sea_pickle", "lily_pad", "snow", "cobweb", "ladder", "lever", "scaffolding", "pointed_dripstone",
            "iron_chain", "end_rod", "lightning_rod", "bell", "anvil", "chest", "trapped_chest", "ender_chest", "campfire", "soul_campfire",
            "lectern", "enchanting_table", "brewing_stand", "cauldron", "water_cauldron", "lava_cauldron", "hopper", "grindstone", "stonecutter",
            "daylight_detector", "end_portal_frame", "flower_pot", "decorated_pot", "conduit", "lantern", "soul_lantern", "torch", "wall_torch",
            "soul_torch", "soul_wall_torch", "redstone_torch", "redstone_wall_torch", "copper_torch", "copper_wall_torch", "fire", "soul_fire",
            "water", "lava", "air", "cave_air", "void_air", "light", "barrier", "nether_portal", "end_portal", "end_gateway", "dragon_egg",
            "turtle_egg", "sniffer_egg", "frogspawn", "heavy_core", "vault", "trial_spawner", "spawner", "composter", "chain"));

    private static final String[] NON_FULL_SUFFIX = {"_stairs", "_slab", "_wall", "_fence", "_fence_gate", "_pane", "_bars", "_door", "_trapdoor",
            "_carpet", "_pressure_plate", "_button", "_sign", "_banner", "_torch", "_lantern", "_chain", "_rod", "rail", "_bed", "_head", "_skull",
            "candle", "candles", "_sapling", "_coral", "_coral_fan", "_bud", "amethyst_cluster", "_mushroom", "_fungus", "_roots", "_vines",
            "_propagule", "_shulker_box", "shulker_box", "_shelf", "_tulip", "_orchid", "_pot", "_leaves", "_grate", "_glass"};

    /** Blocco pieno e solido (le facce laterali reggono muretti, sbarre, recinzioni). */
    public static boolean full(String d) {
        if (d == null) return false;
        String i = id(d);
        if (i.equals("sea_lantern") || i.equals("glass") || i.equals("tinted_glass") || i.endsWith("stained_glass")) return true;
        if (i.startsWith("potted_") || PLANTS.contains(i)) return false;
        if (i.contains("pumpkin") || i.equals("melon") || i.equals("jack_o_lantern")) return false;
        for (String s : NON_FULL_SUFFIX) if (i.endsWith(s)) return false;
        return true;
    }

    static boolean isWall(String i) { return i.endsWith("_wall"); }

    static boolean isFence(String i) { return i.endsWith("_fence"); }

    static boolean isPane(String i) { return i.endsWith("_pane") || i.endsWith("_bars"); }

    static boolean isGate(String i) { return i.endsWith("_fence_gate"); }

    static boolean isStairs(String i) { return i.endsWith("_stairs"); }

    private static boolean gateConnects(String gate, String dir) {
        String f = props(gate).getOrDefault("facing", "north");
        boolean gateNS = f.equals("north") || f.equals("south");
        boolean dirEW = dir.equals("east") || dir.equals("west");
        return gateNS == dirEW;
    }

    private static boolean wallConnects(String nb, String dir) {
        if (nb == null) return false;
        String i = id(nb);
        if (isWall(i) || isPane(i)) return true;
        if (isGate(i)) return gateConnects(nb, dir);
        return full(nb) && !i.endsWith("_leaves");
    }

    private static boolean fenceConnects(String self, String nb, String dir) {
        if (nb == null) return false;
        String i = id(nb);
        if (isFence(i)) return id(self).equals("nether_brick_fence") == i.equals("nether_brick_fence");
        if (isGate(i)) return gateConnects(nb, dir);
        return full(nb) && !i.endsWith("_leaves");
    }

    private static boolean paneConnects(String nb) {
        if (nb == null) return false;
        String i = id(nb);
        if (isPane(i) || isWall(i)) return true;
        return full(nb) && !i.endsWith("_leaves");
    }

    // =====================================================================================
    //  Finitura degli stati (collegamenti)
    // =====================================================================================

    /** Calcola i collegamenti di tutti i blocchi del piano che non li hanno già espliciti. */
    public static void finish(BuildPlan p) {
        finish(p, null);
    }

    /**
     * Come {@link #finish(BuildPlan)} ma solo per le posizioni accettate dal filtro (x,y,z) (null = tutte).
     */
    public static void finish(BuildPlan p, PosFilter only) {
        List<long[]> walls = new ArrayList<>();
        Map<Long, String> out = new HashMap<>();
        List<Long> keys = new ArrayList<>();
        p.forEach((k, d) -> keys.add(k));
        // passo 1: lati collegati
        Map<Long, boolean[]> wallSides = new HashMap<>();
        for (long k : keys) {
            int x = BuildPlan.kx(k), y = BuildPlan.ky(k), z = BuildPlan.kz(k);
            if (only != null && !only.test(x, y, z)) continue;
            String d = p.get(x, y, z);
            String i = id(d);
            Map<String, String> pr = props(d);
            if (isWall(i) && !pr.containsKey("north")) {
                boolean[] s = new boolean[4];
                for (int c = 0; c < 4; c++) {
                    int[] v = vec(CARD[c]);
                    s[c] = wallConnects(p.get(x + v[0], y, z + v[1]), CARD[c]);
                }
                wallSides.put(k, s);
                walls.add(new long[]{k});
            } else if ((isFence(i) || isPane(i)) && !pr.containsKey("north")) {
                for (int c = 0; c < 4; c++) {
                    int[] v = vec(CARD[c]);
                    String nb = p.get(x + v[0], y, z + v[1]);
                    boolean con = isFence(i) ? fenceConnects(d, nb, CARD[c]) : paneConnects(nb);
                    pr.put(CARD[c], String.valueOf(con));
                }
                if (!pr.containsKey("waterlogged")) pr.put("waterlogged", "false");
                out.put(k, with(i, pr));
            } else if (isStairs(i) && !pr.containsKey("shape")) {
                pr.put("shape", stairShape(p, x, y, z, pr));
                if (!pr.containsKey("waterlogged")) pr.put("waterlogged", "false");
                out.put(k, with(i, pr));
            } else if (i.endsWith("_leaves") && !pr.containsKey("persistent")) {
                pr.put("persistent", "true");
                out.put(k, with(i, pr));
            }
        }
        // passo 2: altezze dei muretti e pilastrino
        for (long[] w : walls) {
            long k = w[0];
            int x = BuildPlan.kx(k), y = BuildPlan.ky(k), z = BuildPlan.kz(k);
            String d = p.get(x, y, z);
            boolean[] s = wallSides.get(k);
            String above = p.get(x, y + 1, z);
            String ai = above == null ? "air" : id(above);
            boolean aboveFull = full(above);
            boolean[] aboveSides = wallSides.get(BuildPlanKey.key(x, y + 1, z));
            if (aboveSides == null && above != null && isWall(ai)) {
                Map<String, String> ap = props(above);
                aboveSides = new boolean[4];
                for (int c = 0; c < 4; c++) aboveSides[c] = !"none".equals(ap.getOrDefault(CARD[c], "none"));
            }
            String[] side = new String[4];
            for (int c = 0; c < 4; c++) {
                if (!s[c]) side[c] = "none";
                else side[c] = aboveFull || (aboveSides != null && aboveSides[c]) ? "tall" : "low";
            }
            boolean nN = side[0].equals("none"), eN = side[1].equals("none"), sN = side[2].equals("none"), wN = side[3].equals("none");
            boolean up;
            boolean aboveWallPost = isWall(ai) && (aboveSides == null || wallPost(aboveSides) || "true".equals(props(above).get("up")));
            if (aboveWallPost) up = true;
            else if ((nN && sN && wN && eN) || (sN != nN) || (wN != eN)) up = true;
            else if (side[0].equals("tall") && side[2].equals("tall") || side[1].equals("tall") && side[3].equals("tall")) up = false;
            else up = postOverride(ai);
            Map<String, String> pr = props(d);
            for (int c = 0; c < 4; c++) pr.put(CARD[c], side[c]);
            pr.put("up", String.valueOf(up));
            if (!pr.containsKey("waterlogged")) pr.put("waterlogged", "false");
            out.put(k, with(id(d), pr));
        }
        for (Map.Entry<Long, String> e : out.entrySet()) {
            long k = e.getKey();
            p.set(BuildPlan.kx(k), BuildPlan.ky(k), BuildPlan.kz(k), e.getValue());
        }
    }

    private static boolean wallPost(boolean[] s) {
        boolean n = s[0], e = s[1], so = s[2], w = s[3];
        return !(n && so && !e && !w || e && w && !n && !so || n && e && so && w);
    }

    private static boolean postOverride(String ai) {
        return ai.contains("torch") || ai.contains("lantern") && !ai.equals("sea_lantern") || ai.endsWith("_sign") || ai.endsWith("_banner")
                || ai.endsWith("pressure_plate") || ai.contains("candle") || ai.equals("end_rod") || ai.equals("lightning_rod") || ai.equals("flower_pot")
                || ai.startsWith("potted_") || ai.endsWith("_head") || ai.endsWith("_skull") || ai.contains("campfire") || ai.equals("iron_chain")
                || ai.endsWith("_chain") || ai.endsWith("amethyst_cluster") || ai.equals("decorated_pot") || ai.equals("bell");
    }

    /** Forma delle scale come la calcola il gioco quando si piazzano. */
    static String stairShape(BuildPlan p, int x, int y, int z, Map<String, String> pr) {
        String dir = pr.getOrDefault("facing", "north");
        String half = pr.getOrDefault("half", "bottom");
        int[] v = vec(dir);
        String behind = p.get(x + v[0], y, z + v[1]);
        if (behind != null && isStairs(id(behind))) {
            Map<String, String> bp = props(behind);
            if (half.equals(bp.getOrDefault("half", "bottom"))) {
                String d2 = bp.getOrDefault("facing", "north");
                if (axis(d2) != axis(dir) && canTake(p, x, y, z, opp(d2), dir, half)) return d2.equals(ccw(dir)) ? "outer_left" : "outer_right";
            }
        }
        String front = p.get(x - v[0], y, z - v[1]);
        if (front != null && isStairs(id(front))) {
            Map<String, String> fp = props(front);
            if (half.equals(fp.getOrDefault("half", "bottom"))) {
                String d3 = fp.getOrDefault("facing", "north");
                if (axis(d3) != axis(dir) && canTake(p, x, y, z, d3, dir, half)) return d3.equals(ccw(dir)) ? "inner_left" : "inner_right";
            }
        }
        return "straight";
    }

    private static boolean canTake(BuildPlan p, int x, int y, int z, String side, String dir, String half) {
        int[] v = vec(side);
        String n = p.get(x + v[0], y, z + v[1]);
        if (n == null || !isStairs(id(n))) return true;
        Map<String, String> np = props(n);
        return !np.getOrDefault("facing", "north").equals(dir) || !np.getOrDefault("half", "bottom").equals(half);
    }

    private static char axis(String d) { return d.equals("north") || d.equals("south") ? 'z' : 'x'; }

    @FunctionalInterface
    public interface PosFilter {
        boolean test(int x, int y, int z);
    }

    /** Stessa chiave di BuildPlan (che è privata). */
    static final class BuildPlanKey {
        static long key(int x, int y, int z) {
            return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
        }
    }

    // =====================================================================================
    //  Geometria di comodo
    // =====================================================================================

    public static double dist(double x, double z) { return Math.sqrt(x * x + z * z); }

    /** Angolo in gradi 0..360 (0 = +X, 90 = +Z). */
    public static double ang(double x, double z) {
        double a = Math.toDegrees(Math.atan2(z, x));
        return a < 0 ? a + 360 : a;
    }

    public static void line(BuildPlan p, int x1, int y1, int z1, int x2, int y2, int z2, String b) {
        int n = Math.max(Math.abs(x2 - x1), Math.max(Math.abs(y2 - y1), Math.abs(z2 - z1)));
        for (int i = 0; i <= n; i++) {
            double t = n == 0 ? 0 : i / (double) n;
            p.set((int) Math.round(x1 + (x2 - x1) * t), (int) Math.round(y1 + (y2 - y1) * t), (int) Math.round(z1 + (z2 - z1) * t), b);
        }
    }

    /** Toglie dal piano tutto ciò che sta nel box (inclusivo). */
    public static void clear(BuildPlan p, int x1, int y1, int z1, int x2, int y2, int z2) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) p.remove(x, y, z);
    }

    /** Forza aria nel box (inclusivo) solo dove il piano ha già qualcosa. */
    public static void carve(BuildPlan p, int x1, int y1, int z1, int x2, int y2, int z2) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) if (p.has(x, y, z)) p.remove(x, y, z);
    }
}

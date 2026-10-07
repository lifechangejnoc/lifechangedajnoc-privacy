import it.pepita.core.world.BuildPlan;
import it.pepita.core.world.build.Builds;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strumento offline per le costruzioni di Pepita:
 * esegue i builder (classi pure), valida ogni stringa di blocco contro gli stati ufficiali 26.2 e renderizza
 * anteprime PNG (vista dall'alto, isometriche da quattro lati, sezioni orizzontali).
 *
 * java PlanTool <build> <cartella uscita> [scala] [opzioni...]
 *   build: hub | prigione | pvp | colosseo | mine:<tema>
 *   opzioni: slice=<y> zoom=x1,z1,x2,z2 views=se,nw,sw,ne top=1
 */
public final class PlanTool {
    static Map<String, int[]> colors = new HashMap<>();   // id -> top rgb, side rgb, alpha*100
    static Map<String, Map<String, List<String>>> states = new HashMap<>();

    public static void main(String[] args) throws Exception {
        String toolsDir = System.getProperty("pepita.tools", ".");
        loadColors(new File(toolsDir, "data/colors.tsv"));
        loadStates(new File(toolsDir, "data/blocks_26.2.json"));
        String name = args[0];
        File out = new File(args.length > 1 ? args[1] : "out");
        out.mkdirs();
        int scale = args.length > 2 ? Integer.parseInt(args[2]) : 4;
        Map<String, String> opt = new HashMap<>();
        for (int i = 3; i < args.length; i++) {
            String[] kv = args[i].split("=", 2);
            opt.put(kv[0], kv.length > 1 ? kv[1] : "1");
        }
        long t0 = System.currentTimeMillis();
        BuildPlan p = Builds.plan(name);
        long t1 = System.currentTimeMillis();
        String tag = name.replace(':', '_');
        System.out.println(tag + ": " + p.size() + " blocchi, calcolato in " + (t1 - t0) + " ms");
        validate(p);
        stats(p);
        Grid g = new Grid(p);
        if (opt.containsKey("zoom")) {
            String[] z = opt.get("zoom").split(",");
            g.clip(Integer.parseInt(z[0]), Integer.parseInt(z[1]), Integer.parseInt(z[2]), Integer.parseInt(z[3]));
        }
        if (!"0".equals(opt.getOrDefault("top", "1"))) ImageIO.write(top(g, Math.max(2, scale)), "png", new File(out, tag + "_top.png"));
        for (String v : opt.getOrDefault("views", "se,nw").split(",")) {
            int rot = switch (v) {
                case "se" -> 0;
                case "sw" -> 1;
                case "nw" -> 2;
                default -> 3;
            };
            ImageIO.write(iso(g, scale, rot), "png", new File(out, tag + "_iso_" + v + ".png"));
        }
        if (opt.containsKey("slice")) for (String y : opt.get("slice").split(","))
            ImageIO.write(slice(g, Integer.parseInt(y), Math.max(3, scale + 2)), "png", new File(out, tag + "_y" + y + ".png"));
        System.out.println("immagini in " + out.getPath());
    }

    // =====================================================================
    //  Dati
    // =====================================================================

    static void loadColors(File f) throws Exception {
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String l;
            while ((l = r.readLine()) != null) {
                String[] c = l.split("\t");
                if (c.length < 5) continue;
                String[] t = c[1].split(","), s = c[2].split(",");
                colors.put(c[0], new int[]{Integer.parseInt(t[0]), Integer.parseInt(t[1]), Integer.parseInt(t[2]),
                        Integer.parseInt(s[0]), Integer.parseInt(s[1]), Integer.parseInt(s[2]), (int) (Double.parseDouble(c[3]) * 100)});
            }
        }
    }

    /** Parser minimale del JSON di misode/mcmeta (blocks/data.json): id -> [proprietà -> valori, default]. */
    static void loadStates(File f) throws Exception {
        String s = Files.readString(f.toPath());
        Pattern blk = Pattern.compile("\"([a-z0-9_]+)\":\\[\\{(.*?)\\},\\{");
        Matcher m = blk.matcher(s);
        Pattern prop = Pattern.compile("\"([a-z0-9_]+)\":\\[([^\\]]*)\\]");
        while (m.find()) {
            Map<String, List<String>> props = new HashMap<>();
            Matcher pm = prop.matcher(m.group(2));
            while (pm.find()) {
                List<String> vals = new ArrayList<>();
                for (String v : pm.group(2).split(",")) vals.add(v.replace("\"", "").trim());
                props.put(pm.group(1), vals);
            }
            states.put(m.group(1), props);
        }
        // blocchi senza proprietà: "id":[{},{}]
        Matcher e = Pattern.compile("\"([a-z0-9_]+)\":\\[\\{\\},\\{\\}\\]").matcher(s);
        while (e.find()) states.putIfAbsent(e.group(1), new HashMap<>());
    }

    static String id(String d) {
        String s = d.startsWith("minecraft:") ? d.substring(10) : d;
        int b = s.indexOf('[');
        return b >= 0 ? s.substring(0, b) : s;
    }

    static Map<String, String> props(String d) {
        Map<String, String> m = new TreeMap<>();
        int b = d.indexOf('[');
        if (b < 0) return m;
        String body = d.substring(b + 1, d.length() - 1);
        if (body.isEmpty()) return m;
        for (String kv : body.split(",")) {
            String[] x = kv.split("=");
            m.put(x[0].trim(), x.length > 1 ? x[1].trim() : "");
        }
        return m;
    }

    static int invalid;

    static void validate(BuildPlan p) {
        Map<String, Integer> bad = new LinkedHashMap<>();
        Map<String, String> why = new HashMap<>();
        p.forEach((k, d) -> {
            String i = id(d);
            Map<String, List<String>> st = states.get(i);
            String err = null;
            if (st == null) err = "blocco inesistente";
            else if (!d.contains(":") || !d.startsWith("minecraft:")) err = "namespace";
            else {
                if (d.contains("[") && !d.endsWith("]")) err = "parentesi";
                for (Map.Entry<String, String> e : props(d).entrySet()) {
                    List<String> vals = st.get(e.getKey());
                    if (vals == null) { err = "proprietà " + e.getKey() + " inesistente"; break; }
                    if (!vals.contains(e.getValue())) { err = e.getKey() + "=" + e.getValue() + " non valido " + vals; break; }
                }
            }
            if (err != null) {
                bad.merge(d, 1, Integer::sum);
                why.put(d, err);
            }
        });
        invalid = bad.size();
        if (bad.isEmpty()) System.out.println("validazione: OK, tutti gli stati sono validi in 26.2");
        else {
            System.out.println("validazione: " + bad.size() + " stringhe NON valide:");
            bad.entrySet().stream().limit(40).forEach(e -> System.out.println("  x" + e.getValue() + "  " + e.getKey() + "   <- " + why.get(e.getKey())));
        }
    }

    static void stats(BuildPlan p) {
        Map<String, Integer> c = new HashMap<>();
        p.forEach((k, d) -> c.merge(id(d), 1, Integer::sum));
        List<Map.Entry<String, Integer>> l = new ArrayList<>(c.entrySet());
        l.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder sb = new StringBuilder("tipi: " + l.size() + " — ");
        for (int i = 0; i < Math.min(25, l.size()); i++) sb.append(l.get(i).getKey()).append(' ').append(l.get(i).getValue()).append(", ");
        System.out.println(sb);
    }

    // =====================================================================
    //  Griglia
    // =====================================================================

    static final class Grid {
        int x1 = Integer.MAX_VALUE, y1 = Integer.MAX_VALUE, z1 = Integer.MAX_VALUE, x2 = Integer.MIN_VALUE, y2 = Integer.MIN_VALUE, z2 = Integer.MIN_VALUE;
        final Map<Long, String> m = new HashMap<>();

        Grid(BuildPlan p) {
            p.forEach((k, d) -> {
                String i = id(d);
                if (i.equals("air") || i.equals("cave_air") || i.equals("void_air") || i.equals("barrier") || i.equals("light")
                        || i.equals("structure_void")) return;
                int x = BuildPlan.kx(k), y = BuildPlan.ky(k), z = BuildPlan.kz(k);
                m.put(key(x, y, z), d);
                x1 = Math.min(x1, x); x2 = Math.max(x2, x);
                y1 = Math.min(y1, y); y2 = Math.max(y2, y);
                z1 = Math.min(z1, z); z2 = Math.max(z2, z);
            });
        }

        void clip(int ax, int az, int bx, int bz) {
            m.keySet().removeIf(k -> {
                int x = (int) (k >> 40), z = (int) (k << 24 >> 40);
                return x < ax || x > bx || z < az || z > bz;
            });
            x1 = ax; x2 = bx; z1 = az; z2 = bz;
        }

        static long key(int x, int y, int z) {
            return ((long) x & 0xFFFFFF) << 40 | ((long) z & 0xFFFFFF) << 16 | (y & 0xFFFF);
        }

        String get(int x, int y, int z) {
            return m.get(key(x, y, z));
        }
    }

    // =====================================================================
    //  Forme e colori
    // =====================================================================

    /** {x0,x1,y0,y1,z0,z1} in coordinate del blocco, alpha 0..1, luminoso */
    record Shape(double x0, double x1, double y0, double y1, double z0, double z1, double alpha) {}

    static final Shape FULL = new Shape(0, 1, 0, 1, 0, 1, 1);

    static Shape shape(String d) {
        String i = id(d);
        Map<String, String> pr = props(d);
        if (i.endsWith("_slab")) {
            String t = pr.getOrDefault("type", "bottom");
            if (t.equals("top")) return new Shape(0, 1, 0.5, 1, 0, 1, 1);
            if (t.equals("double")) return FULL;
            return new Shape(0, 1, 0, 0.5, 0, 1, 1);
        }
        if (i.endsWith("_stairs")) return pr.getOrDefault("half", "bottom").equals("top") ? new Shape(0, 1, 0.25, 1, 0, 1, 1) : new Shape(0, 1, 0, 0.75, 0, 1, 1);
        if (i.endsWith("_wall") || i.endsWith("_fence")) return new Shape(0.3, 0.7, 0, 1, 0.3, 0.7, 1);
        if (i.endsWith("_pane") || i.equals("iron_bars") || i.endsWith("_bars")) return new Shape(0.42, 0.58, 0, 1, 0.42, 0.58, 0.8);
        if (i.endsWith("chain") || i.endsWith("_rod") || i.equals("end_rod") || i.equals("lightning_rod")) return new Shape(0.42, 0.58, 0, 1, 0.42, 0.58, 1);
        if (i.endsWith("_carpet") || i.endsWith("pressure_plate") || i.contains("rail") || i.equals("moss_carpet") || i.equals("snow")
                || i.equals("pink_petals") || i.equals("wildflowers") || i.equals("leaf_litter") || i.equals("lily_pad"))
            return new Shape(0, 1, 0, 0.1, 0, 1, 1);
        if (i.endsWith("_trapdoor")) return pr.getOrDefault("open", "false").equals("true") ? new Shape(0, 1, 0, 1, 0, 0.18, 1)
                : pr.getOrDefault("half", "bottom").equals("top") ? new Shape(0, 1, 0.82, 1, 0, 1, 1) : new Shape(0, 1, 0, 0.18, 0, 1, 1);
        if (i.endsWith("_door")) return new Shape(0, 1, 0, 1, 0, 0.18, 1);
        if (i.contains("lantern") && !i.equals("sea_lantern") || i.contains("torch") || i.contains("candle") || i.endsWith("campfire")
                || i.equals("flower_pot") || i.startsWith("potted_") || i.endsWith("_head") || i.endsWith("_skull") || i.endsWith("_button")
                || i.endsWith("amethyst_cluster") || i.endsWith("_bud") || i.equals("decorated_pot") || i.equals("bell"))
            return new Shape(0.3, 0.7, 0, 0.55, 0.3, 0.7, 1);
        if (i.equals("water")) return new Shape(0, 1, 0, 0.88, 0, 1, 0.85);
        if (i.equals("nether_portal")) return new Shape(0.35, 0.65, 0, 1, 0.35, 0.65, 0.75);
        if (i.endsWith("glass")) return new Shape(0, 1, 0, 1, 0, 1, 0.5);
        if (isPlant(i)) return new Shape(0.2, 0.8, 0, 0.8, 0.2, 0.8, 0.85);
        return FULL;
    }

    static boolean isPlant(String i) {
        return i.endsWith("_sapling") || i.endsWith("_tulip") || i.equals("short_grass") || i.equals("tall_grass") || i.equals("fern")
                || i.equals("large_fern") || i.equals("dandelion") || i.equals("poppy") || i.equals("blue_orchid") || i.equals("allium")
                || i.equals("azure_bluet") || i.equals("oxeye_daisy") || i.equals("cornflower") || i.equals("lily_of_the_valley")
                || i.equals("sunflower") || i.equals("lilac") || i.equals("rose_bush") || i.equals("peony") || i.equals("dead_bush")
                || i.equals("sweet_berry_bush") || i.equals("sugar_cane") || i.endsWith("_roots") || i.endsWith("_vines") || i.endsWith("_vines_plant")
                || i.equals("vine") || i.equals("hanging_roots") || i.equals("glow_lichen") || i.endsWith("_mushroom") || i.endsWith("_fungus")
                || i.equals("pointed_dripstone") || i.equals("seagrass") || i.equals("kelp") || i.equals("kelp_plant") || i.equals("torchflower")
                || i.equals("bush") || i.equals("firefly_bush") || i.equals("cobweb") || i.equals("chorus_flower") || i.equals("chorus_plant")
                || i.equals("pale_hanging_moss") || i.equals("spore_blossom") || i.contains("coral") || i.equals("wither_rose") || i.equals("ladder");
    }

    static boolean glows(String i) {
        return i.contains("lantern") || i.contains("torch") || i.equals("glowstone") || i.equals("shroomlight") || i.contains("froglight")
                || i.equals("end_rod") || i.contains("campfire") || i.equals("redstone_lamp") || i.contains("candle") || i.equals("lava")
                || i.equals("magma_block") || i.equals("jack_o_lantern") || i.equals("sea_pickle") || i.equals("beacon");
    }

    static int[] col(String d, boolean top) {
        String i = id(d);
        int[] c = colors.get(i);
        if (c == null) {
            String base = i.replace("_stairs", "").replace("_slab", "").replace("_wall", "").replace("_fence", "");
            c = colors.get(base);
            if (c == null) c = colors.get(base + "s");
            if (c == null) c = colors.get(base + "_planks");
            if (c == null) c = colors.get(base.replace("brick", "bricks"));
        }
        if (glows(i) && !i.equals("magma_block") && !i.equals("lava")) return new int[]{255, 214, 120};
        if (c == null) return new int[]{200, 0, 200};
        return top ? new int[]{c[0], c[1], c[2]} : new int[]{c[3], c[4], c[5]};
    }

    static Color shade(int[] c, double f, double a) {
        return new Color(cl(c[0] * f), cl(c[1] * f), cl(c[2] * f), cl(255 * a));
    }

    static int cl(double v) {
        return (int) Math.max(0, Math.min(255, v));
    }

    // =====================================================================
    //  Viste
    // =====================================================================

    static BufferedImage top(Grid g, int s) {
        int w = (g.x2 - g.x1 + 1) * s, h = (g.z2 - g.z1 + 1) * s;
        BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_RGB);
        Graphics2D gr = img.createGraphics();
        gr.setColor(new Color(24, 30, 44));
        gr.fillRect(0, 0, w, h);
        int[][] hm = new int[g.x2 - g.x1 + 1][g.z2 - g.z1 + 1];
        String[][] top = new String[g.x2 - g.x1 + 1][g.z2 - g.z1 + 1];
        for (Map.Entry<Long, String> e : g.m.entrySet()) {
            long k = e.getKey();
            int x = (int) (k >> 40), z = (int) (k << 24 >> 40), y = (int) (k & 0xFFFF);
            if ((y & 0x8000) != 0) y -= 0x10000;
            int ix = x - g.x1, iz = z - g.z1;
            if (ix < 0 || iz < 0 || ix >= hm.length || iz >= hm[0].length) continue;
            if (top[ix][iz] == null || y > hm[ix][iz]) {
                hm[ix][iz] = y;
                top[ix][iz] = e.getValue();
            }
        }
        for (int ix = 0; ix < hm.length; ix++)
            for (int iz = 0; iz < hm[0].length; iz++) {
                if (top[ix][iz] == null) continue;
                int[] c = col(top[ix][iz], true);
                int hn = ix > 0 && iz > 0 && top[ix - 1][iz - 1] != null ? hm[ix - 1][iz - 1] : hm[ix][iz] - 3;
                double f = 0.82 + Math.max(-0.35, Math.min(0.35, (hm[ix][iz] - hn) * 0.07)) + (hm[ix][iz] - 100) * 0.004;
                gr.setColor(shade(c, f, 1));
                gr.fillRect(ix * s, iz * s, s, s);
            }
        gr.dispose();
        return img;
    }

    static BufferedImage slice(Grid g, int y, int s) {
        int w = (g.x2 - g.x1 + 1) * s, h = (g.z2 - g.z1 + 1) * s;
        BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_RGB);
        Graphics2D gr = img.createGraphics();
        gr.setColor(new Color(20, 22, 30));
        gr.fillRect(0, 0, w, h);
        for (int x = g.x1; x <= g.x2; x++)
            for (int z = g.z1; z <= g.z2; z++) {
                String d = g.get(x, y, z);
                String below = g.get(x, y - 1, z);
                if (d == null) {
                    if (below != null) {
                        gr.setColor(shade(col(below, true), 0.45, 1));
                        gr.fillRect((x - g.x1) * s, (z - g.z1) * s, s, s);
                    }
                    continue;
                }
                gr.setColor(shade(col(d, true), 1, 1));
                Shape sh = shape(d);
                int ox = (int) (sh.x0 * s), oz = (int) (sh.z0 * s), ww = Math.max(1, (int) ((sh.x1 - sh.x0) * s)), hh = Math.max(1, (int) ((sh.z1 - sh.z0) * s));
                gr.fillRect((x - g.x1) * s + ox, (z - g.z1) * s + oz, ww, hh);
            }
        gr.dispose();
        return img;
    }

    /** Isometrica: rot 0 = camera a sud-est, 1 = sud-ovest, 2 = nord-ovest, 3 = nord-est. */
    static BufferedImage iso(Grid g, int s, int rot) {
        int sx = g.x2 - g.x1 + 1, sz = g.z2 - g.z1 + 1, sy = g.y2 - g.y1 + 1;
        int W = (rot % 2 == 0) ? sx : sz, D = (rot % 2 == 0) ? sz : sx;
        int width = (W + D) * s + 40;
        int height = (W + D) * s / 2 + sy * s + 40;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gr = img.createGraphics();
        gr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        gr.setColor(new Color(118, 164, 222));
        gr.fillRect(0, 0, width, height);
        int ox = D * s + 20, oy = sy * s + 20;
        // raccogli e ordina i blocchi per il pittore
        List<long[]> list = new ArrayList<>(g.m.size());
        for (long k : g.m.keySet()) {
            int x = (int) (k >> 40), z = (int) (k << 24 >> 40), y = (int) (k & 0xFFFF);
            if ((y & 0x8000) != 0) y -= 0x10000;
            int a = x - g.x1, b = z - g.z1;
            int u, v; // u verso destra-giù, v verso sinistra-giù (camera in +u +v)
            switch (rot) {
                case 0 -> { u = a; v = b; }
                case 1 -> { u = sz - 1 - b; v = a; }
                case 2 -> { u = sx - 1 - a; v = sz - 1 - b; }
                default -> { u = b; v = sx - 1 - a; }
            }
            list.add(new long[]{u, v, y - g.y1, k});
        }
        list.sort((p, q) -> {
            long c = (p[0] + p[1]) - (q[0] + q[1]);
            if (c != 0) return Long.signum(c);
            c = p[2] - q[2];
            if (c != 0) return Long.signum(c);
            return Long.signum(p[0] - q[0]);
        });
        for (long[] e : list) {
            String d = g.m.get(e[3]);
            int u = (int) e[0], v = (int) e[1], y = (int) e[2];
            Shape sh = shape(d);
            // forma ruotata (approssimata: le forme centrate non cambiano)
            double u0 = sh.x0, u1 = sh.x1, v0 = sh.z0, v1 = sh.z1;
            if (id(d).endsWith("_door") || id(d).endsWith("_trapdoor") && props(d).getOrDefault("open", "false").equals("true")) {
                String f = props(d).getOrDefault("facing", "north");
                boolean alongX = f.equals("north") || f.equals("south");
                boolean alongU = (rot % 2 == 0) == alongX;
                if (alongU) { u0 = 0; u1 = 1; v0 = 0.41; v1 = 0.59; } else { u0 = 0.41; u1 = 0.59; v0 = 0; v1 = 1; }
            }
            int[] ct = col(d, true), cs = col(d, false);
            double depthF = 0.88 + 0.12 * (y / (double) Math.max(1, sy));
            drawBox(gr, ox, oy, s, u + u0, u + u1, y + sh.y0, y + sh.y1, v + v0, v + v1, ct, cs, sh.alpha, depthF);
        }
        gr.dispose();
        return img;
    }

    static int[] P(int ox, int oy, int s, double u, double y, double v) {
        return new int[]{(int) Math.round(ox + (u - v) * s), (int) Math.round(oy + (u + v) * s / 2.0 - y * s)};
    }

    static void drawBox(Graphics2D g, int ox, int oy, int s, double u0, double u1, double y0, double y1, double v0, double v1,
                        int[] ct, int[] cs, double a, double f) {
        if (a < 1) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) a));
        // faccia superiore
        int[] p1 = P(ox, oy, s, u0, y1, v0), p2 = P(ox, oy, s, u1, y1, v0), p3 = P(ox, oy, s, u1, y1, v1), p4 = P(ox, oy, s, u0, y1, v1);
        g.setColor(shade(ct, 1.0 * f, 1));
        g.fillPolygon(new Polygon(new int[]{p1[0], p2[0], p3[0], p4[0]}, new int[]{p1[1], p2[1], p3[1], p4[1]}, 4));
        // faccia +u (destra)
        int[] q1 = P(ox, oy, s, u1, y1, v0), q2 = P(ox, oy, s, u1, y1, v1), q3 = P(ox, oy, s, u1, y0, v1), q4 = P(ox, oy, s, u1, y0, v0);
        g.setColor(shade(cs, 0.72 * f, 1));
        g.fillPolygon(new Polygon(new int[]{q1[0], q2[0], q3[0], q4[0]}, new int[]{q1[1], q2[1], q3[1], q4[1]}, 4));
        // faccia +v (sinistra)
        int[] r1 = P(ox, oy, s, u0, y1, v1), r2 = P(ox, oy, s, u1, y1, v1), r3 = P(ox, oy, s, u1, y0, v1), r4 = P(ox, oy, s, u0, y0, v1);
        g.setColor(shade(cs, 0.86 * f, 1));
        g.fillPolygon(new Polygon(new int[]{r1[0], r2[0], r3[0], r4[0]}, new int[]{r1[1], r2[1], r3[1], r4[1]}, 4));
        if (s >= 4 && a >= 1) {
            g.setColor(shade(ct, 0.8 * f, 1));
            g.drawLine(p1[0], p1[1], p2[0], p2[1]);
            g.drawLine(p1[0], p1[1], p4[0], p4[1]);
        }
        if (a < 1) g.setComposite(AlphaComposite.SrcOver);
    }
}

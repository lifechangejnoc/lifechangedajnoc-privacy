package it.pepita.core.world;

import it.pepita.core.PepitaCore;
import it.pepita.core.mine.Mine;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/** Disegna un'anteprima isometrica di una zona del mondo (per lo staff). */
public final class Renderer {
    private final PepitaCore plugin;

    public Renderer(PepitaCore plugin) {
        this.plugin = plugin;
    }

    public File render(String what) {
        World w;
        int x1, y1, z1, x2, y2, z2;
        switch (what.toLowerCase()) {
            case "spawn", "prigione" -> {
                w = plugin.world().prison();
                x1 = -48; z1 = -48; x2 = 48; z2 = 48; y1 = 76; y2 = 136;
            }
            case "hub" -> {
                w = plugin.world().hub() != null ? plugin.world().hub() : plugin.world().prison();
                x1 = -60; z1 = -60; x2 = 60; z2 = 60; y1 = 80; y2 = 136;
            }
            case "celle", "colosseo" -> {
                w = plugin.world().cells();
                x1 = -56; z1 = -56; x2 = 56; z2 = 56; y1 = 70; y2 = 172;
            }
            default -> {
                Mine m = plugin.mines().get(what);
                if (m == null) m = plugin.mines().all().iterator().next();
                w = plugin.mines().worldOf(m);
                int pad = m.pvp ? 30 : 12;
                x1 = m.minX - pad; x2 = m.maxX + pad; z1 = m.minZ - pad; z2 = m.maxZ + pad; y1 = m.minY - 12; y2 = m.maxY + 16;
            }
        }
        File out = new File(plugin.getDataFolder(), "render_" + what.toLowerCase() + ".png");
        try {
            ImageIO.write(iso(w, x1, y1, z1, x2, y2, z2, 6), "png", out);
        } catch (IOException e) {
            plugin.getLogger().warning("Render fallito: " + e.getMessage());
        }
        return out;
    }

    private static int[] colorOf(Block b) {
        Material m = b.getType();
        if (m == Material.WATER) return new int[]{64, 110, 220};
        if (m == Material.NETHER_PORTAL) return new int[]{150, 60, 230};
        try {
            Color c = b.getBlockData().getMapColor();
            int r = c.getRed(), g = c.getGreen(), bl = c.getBlue();
            if (r + g + bl == 0 && !m.isAir()) return new int[]{90, 90, 90};
            return new int[]{r, g, bl};
        } catch (Exception e) {
            return new int[]{128, 128, 128};
        }
    }

    private static java.awt.Color shade(int[] c, double f) {
        return new java.awt.Color(clamp(c[0] * f), clamp(c[1] * f), clamp(c[2] * f));
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, v));
    }

    /** Vista isometrica da sud-est: ogni blocco disegnato come cubo. */
    private BufferedImage iso(World w, int x1, int y1, int z1, int x2, int y2, int z2, int s) {
        int sx = x2 - x1 + 1, sz = z2 - z1 + 1, sy = y2 - y1 + 1;
        int width = (sx + sz) * s + 20;
        int height = (sx + sz) * s / 2 + sy * s + 20;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(new java.awt.Color(135, 180, 235));
        g.fillRect(0, 0, width, height);
        int ox = sz * s + 10, oy = sy * s + 10;
        // ordine del pittore: da dietro (x piccolo, z piccolo, y basso) a davanti
        for (int sum = 0; sum <= sx + sz - 2; sum++) {
            for (int y = y1; y <= y2; y++) {
                for (int dx = 0; dx < sx; dx++) {
                    int dz = sum - dx;
                    if (dz < 0 || dz >= sz) continue;
                    int x = x1 + dx, z = z1 + dz;
                    Block b = w.getBlockAt(x, y, z);
                    if (b.getType().isAir()) continue;
                    int[] c = colorOf(b);
                    int px = ox + (dx - dz) * s;
                    int py = oy + (dx + dz) * s / 2 - (y - y1) * s;
                    Polygon top = new Polygon(new int[]{px, px + s, px, px - s}, new int[]{py - s / 2, py, py + s / 2, py}, 4);
                    Polygon left = new Polygon(new int[]{px - s, px, px, px - s}, new int[]{py, py + s / 2, py + s / 2 + s, py + s}, 4);
                    Polygon right = new Polygon(new int[]{px, px + s, px + s, px}, new int[]{py + s / 2, py, py + s, py + s / 2 + s}, 4);
                    g.setColor(shade(c, 1.0));
                    g.fillPolygon(top);
                    g.setColor(shade(c, 0.78));
                    g.fillPolygon(left);
                    g.setColor(shade(c, 0.6));
                    g.fillPolygon(right);
                }
            }
        }
        g.dispose();
        return img;
    }
}

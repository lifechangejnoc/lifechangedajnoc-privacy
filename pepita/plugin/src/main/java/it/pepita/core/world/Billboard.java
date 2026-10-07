package it.pepita.core.world;

import it.pepita.core.PepitaCore;
import it.pepita.core.data.PlayerData;
import it.pepita.core.util.ItemBuilder;
import it.pepita.core.util.Keys;
import it.pepita.core.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Cartellone animato del logo (ItemDisplay di carta con custom_model_data "logo", texture animata nel pack)
 * nell'hub e nella prigione: scala grande, leggero ondeggiamento interpolato, pepita rotante, particelle.
 * Chi non ha il resource pack vede invece una scritta (TextDisplay) di riserva.
 */
public final class Billboard {
    private final PepitaCore plugin;
    private final List<Entity> packOnly = new ArrayList<>();
    private final List<Entity> plainOnly = new ArrayList<>();
    private final List<ItemDisplay> logos = new ArrayList<>();
    private final List<ItemDisplay> nuggets = new ArrayList<>();
    private final List<Location> centers = new ArrayList<>();
    private int phase;

    public Billboard(PepitaCore plugin) {
        this.plugin = plugin;
    }

    public void spawnAll() {
        removeAll();
        spawn(plugin.world().hub(), Layout.HUB_LOGO, 18f);
        spawn(plugin.world().prison(), Layout.PRISON_LOGO, 13f);
        for (Player p : Bukkit.getOnlinePlayers()) refreshView(p);
    }

    private void spawn(World w, double[] at, float scale) {
        if (w == null) return;
        Location c = new Location(w, at[0], at[1], at[2]);
        centers.add(c);
        ItemDisplay logo = w.spawn(c, ItemDisplay.class, d -> {
            d.setItemStack(new ItemBuilder(Material.PAPER).model("logo").build());
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf()));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(4f);
            d.setPersistent(false);
            d.setVisibleByDefault(false);
            d.getPersistentDataContainer().set(Keys.HOLO, PersistentDataType.STRING, "logo");
        });
        logos.add(logo);
        packOnly.add(logo);
        Location nl = c.clone().add(0, -scale * 0.5 + 0.6, 2.0);
        ItemDisplay nug = w.spawn(nl, ItemDisplay.class, d -> {
            d.setItemStack(new ItemBuilder(Material.GOLD_NUGGET).model("pepita_oro").build());
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            float s = scale * 0.2f;
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(s, s, s), new Quaternionf()));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(3f);
            d.setPersistent(false);
            d.getPersistentDataContainer().set(Keys.HOLO, PersistentDataType.STRING, "logo_pepita");
        });
        nuggets.add(nug);
        TextDisplay txt = w.spawn(c, TextDisplay.class, t -> {
            t.text(Txt.mm("<gradient:#FFF6B7:#FFD54A:#FFA726><b>⛏ PEPITA ⛏</b></gradient>\n<gray>Il prison della <gold>Corsa all'Oro</gold>"));
            t.setBillboard(Display.Billboard.CENTER);
            t.setShadowed(true);
            t.setDefaultBackground(false);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            float s = scale * 0.42f;
            t.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(s, s, s), new Quaternionf()));
            t.setViewRange(4f);
            t.setPersistent(false);
            t.setVisibleByDefault(false);
            t.getPersistentDataContainer().set(Keys.HOLO, PersistentDataType.STRING, "logo_testo");
        });
        plainOnly.add(txt);
    }

    public void removeAll() {
        for (Entity e : packOnly) e.remove();
        for (Entity e : plainOnly) e.remove();
        for (Entity e : nuggets) e.remove();
        packOnly.clear();
        plainOnly.clear();
        logos.clear();
        nuggets.clear();
        centers.clear();
    }

    public void refreshView(Player p) {
        PlayerData d = plugin.data().get(p);
        for (Entity e : packOnly) {
            if (!e.isValid()) continue;
            if (d.hasPack) p.showEntity(plugin, e);
            else p.hideEntity(plugin, e);
        }
        for (Entity e : plainOnly) {
            if (!e.isValid()) continue;
            if (d.hasPack) p.hideEntity(plugin, e);
            else p.showEntity(plugin, e);
        }
    }

    /** Ogni 2 secondi: ondeggiamento, rotazione della pepita, particelle. */
    public void tick() {
        phase++;
        boolean left = (phase & 1) == 0;
        for (ItemDisplay d : logos) {
            if (!d.isValid()) continue;
            Transformation t = d.getTransformation();
            float roll = (float) Math.toRadians(left ? 2.2 : -2.2);
            Vector3f tr = new Vector3f(0, left ? 0.25f : -0.25f, 0);
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(40);
            d.setTransformation(new Transformation(tr, new Quaternionf(new AxisAngle4f(roll, 0, 0, 1)), t.getScale(), new Quaternionf()));
        }
        for (ItemDisplay d : nuggets) {
            if (!d.isValid()) continue;
            Transformation t = d.getTransformation();
            float ang = (float) Math.toRadians((phase % 4) * 90 + 90);
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(40);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(new AxisAngle4f(ang, 0, 1, 0)), t.getScale(), new Quaternionf()));
        }
        for (Location c : centers) {
            World w = c.getWorld();
            if (w == null || w.getPlayers().isEmpty()) continue;
            w.spawnParticle(Particle.WAX_ON, c, 14, 7, 6, 1.2, 0.02);
            w.spawnParticle(Particle.END_ROD, c.clone().add(0, -6, 1.5), 4, 6, 1, 0.5, 0.01);
        }
    }
}

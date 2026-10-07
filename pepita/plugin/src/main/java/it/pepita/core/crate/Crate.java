package it.pepita.core.crate;

import org.bukkit.Material;

import java.util.concurrent.ThreadLocalRandom;

/** Le quattro casse di Pepita. */
public enum Crate {
    COMUNE("comune", "Comune", "<white>", Material.CHEST, "chiave_comune"),
    RARA("rara", "Rara", "<#55E6FF>", Material.ENDER_CHEST, "chiave_rara"),
    LEGGENDARIA("leggendaria", "Leggendaria", "<#D17BFF>", Material.PURPLE_SHULKER_BOX, "chiave_leggendaria"),
    PEPITA("pepita", "Pepita", "<#FFD54A>", Material.YELLOW_SHULKER_BOX, "chiave_pepita");

    public final String id, display, color, keyModel;
    public final Material block;

    Crate(String id, String display, String color, Material block, String keyModel) {
        this.id = id;
        this.display = display;
        this.color = color;
        this.block = block;
        this.keyModel = keyModel;
    }

    public String title() {
        return color + "<b>Cassa " + display + "</b>";
    }

    public static Crate byId(String id) {
        for (Crate c : values()) if (c.id.equalsIgnoreCase(id)) return c;
        return null;
    }

    /** Chiave casuale (per incantesimi e premi). */
    public static Crate randomKey(ThreadLocalRandom r) {
        int roll = r.nextInt(1000);
        if (roll < 700) return COMUNE;
        if (roll < 920) return RARA;
        if (roll < 990) return LEGGENDARIA;
        return PEPITA;
    }
}

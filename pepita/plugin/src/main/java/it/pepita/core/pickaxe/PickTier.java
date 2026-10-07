package it.pepita.core.pickaxe;

import org.bukkit.Material;

/** Tier del piccone: materiale (velocità di scavo vanilla), aspetto senza skin e moltiplicatore delle skin. */
public enum PickTier {
    LEGNO("Legno", Material.WOODEN_PICKAXE, null, 1.0, "<#B98A55>"),
    PIETRA("Pietra", Material.STONE_PICKAXE, null, 1.15, "<#A0A0A0>"),
    FERRO("Ferro", Material.IRON_PICKAXE, null, 1.3, "<#E6E6E6>"),
    ORO("Oro", Material.GOLDEN_PICKAXE, null, 1.5, "<#FFD54A>"),
    DIAMANTE("Diamante", Material.DIAMOND_PICKAXE, null, 1.75, "<#55E6FF>"),
    NETHERITE("Netherite", Material.NETHERITE_PICKAXE, null, 2.0, "<#8C6E78>"),
    PEPITA("Pepita", Material.NETHERITE_PICKAXE, "tier_pepita", 2.5, "<gradient:#FFE259:#FFA751>"),
    QUANTUM("Quantum", Material.NETHERITE_PICKAXE, "tier_quantum", 3.0, "<gradient:#3FE0F0:#D15BFF>");

    public final String display;
    public final Material material;
    public final String model;
    public final double mult;
    public final String color;

    PickTier(String display, Material material, String model, double mult, String color) {
        this.display = display;
        this.material = material;
        this.model = model;
        this.mult = mult;
        this.color = color;
    }

    public static PickTier of(int i) {
        PickTier[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }

    public PickTier next() {
        return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null;
    }

    public String label() {
        return color + display + (color.startsWith("<gradient") ? "</gradient>" : "");
    }

    /** Velocità base del materiale (per il calcolo della lentezza nella miniera PvP). */
    public double toolSpeed() {
        return switch (material) {
            case WOODEN_PICKAXE -> 2;
            case STONE_PICKAXE -> 4;
            case IRON_PICKAXE -> 6;
            case GOLDEN_PICKAXE -> 12;
            case DIAMOND_PICKAXE -> 8;
            default -> 9;
        };
    }
}

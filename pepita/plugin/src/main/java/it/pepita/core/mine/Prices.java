package it.pepita.core.mine;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Scala dei blocchi delle miniere e prezzo di vendita di ciascuno. */
public final class Prices {
    private Prices() {}

    /** Scala in ordine crescente di valore. Ogni miniera usa 4 gradini consecutivi. */
    public static final List<Material> LADDER = List.of(
            Material.STONE, Material.COBBLESTONE, Material.ANDESITE, Material.DIORITE,
            Material.COAL_ORE, Material.GRANITE, Material.TUFF, Material.COPPER_ORE,
            Material.DEEPSLATE, Material.IRON_ORE, Material.DEEPSLATE_COAL_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.DEEPSLATE_IRON_ORE, Material.GOLD_ORE, Material.REDSTONE_ORE, Material.LAPIS_ORE,
            Material.DEEPSLATE_GOLD_ORE, Material.DEEPSLATE_REDSTONE_ORE, Material.DEEPSLATE_LAPIS_ORE, Material.DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE_EMERALD_ORE, Material.COAL_BLOCK,
            Material.COPPER_BLOCK, Material.IRON_BLOCK, Material.RAW_IRON_BLOCK, Material.REDSTONE_BLOCK,
            Material.LAPIS_BLOCK, Material.GOLD_BLOCK, Material.GILDED_BLACKSTONE, Material.AMETHYST_BLOCK,
            Material.QUARTZ_BLOCK, Material.DIAMOND_BLOCK, Material.EMERALD_BLOCK, Material.PRISMARINE_BRICKS,
            Material.ANCIENT_DEBRIS, Material.CRYING_OBSIDIAN, Material.NETHERITE_BLOCK);

    private static final Map<Material, Double> PRICE = new EnumMap<>(Material.class);

    static {
        double v = 10.0;
        for (Material m : LADDER) {
            PRICE.put(m, Math.round(v * 100) / 100.0);
            v *= 1.32;
        }
    }

    public static void load(ConfigurationSection sec) {
        if (sec == null) return;
        for (String k : sec.getKeys(false)) {
            Material m = Material.matchMaterial(k);
            if (m != null) PRICE.put(m, sec.getDouble(k));
        }
    }

    /** Minerale Quantico e Nucleo Quantico della miniera PvP (texture del resource pack). */
    public static final Material MINERALE = Material.BUDDING_AMETHYST;
    public static final Material NUCLEO = Material.LODESTONE;
    /** Lucky Block (texture del resource pack). */
    public static final Material LUCKY = Material.SPONGE;

    /** Blocco speciale "Pepita Gigante" che appare durante la Corsa all'Oro. */
    public static final Material PEPITA_GIGANTE = Material.RAW_GOLD_BLOCK;

    public static double of(Material m) {
        return PRICE.getOrDefault(m, 0.5);
    }

    /** Prezzo di un blocco dentro una certa miniera (la Pepita Gigante vale 15 volte la media). */
    public static double of(Material m, Mine mine) {
        if (m == PEPITA_GIGANTE) return mine.avgValue() * 15;
        if (m == LUCKY) return mine.avgValue() * 5;
        return of(m);
    }

    /** Valore (in "blocchi medi" della miniera migliore del giocatore) dei blocchi della miniera PvP. */
    public static double pvpFactor(Material m) {
        if (m == MINERALE) return 3.0;
        if (m == NUCLEO) return 8.0;
        if (m == Material.BLACKSTONE) return 1.4;
        if (m == Material.BASALT) return 1.2;
        return 1.0;
    }

    public static Map<Material, Double> all() {
        return PRICE;
    }
}

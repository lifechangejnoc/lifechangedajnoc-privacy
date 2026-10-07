package it.pepita.core.util;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Costruttore fluente di oggetti. */
public final class ItemBuilder {
    private final ItemStack item;
    private String name;
    private final List<String> lore = new ArrayList<>();
    private String model;
    private boolean glow;
    private boolean hide = true;
    private boolean unbreakable;
    private String itemId;
    private String owner;
    private Color dye;
    private int maxStack = -1;
    private String tooltipStyle;
    private boolean hideAll;

    public ItemBuilder(Material m) {
        this.item = new ItemStack(m);
    }

    public ItemBuilder(Material m, int amount) {
        this.item = new ItemStack(m, Math.max(1, Math.min(99, amount)));
    }

    public ItemBuilder name(String mm) {
        this.name = mm;
        return this;
    }

    public ItemBuilder lore(String... lines) {
        lore.addAll(Arrays.asList(lines));
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        lore.addAll(lines);
        return this;
    }

    /** Modello del resource pack, es. "piccone_pizza" (namespace pepita) o "minecraft:stone". */
    public ItemBuilder model(String m) {
        this.model = m;
        return this;
    }

    public ItemBuilder glow(boolean g) {
        this.glow = g;
        return this;
    }

    public ItemBuilder glow() {
        return glow(true);
    }

    public ItemBuilder showTooltip() {
        this.hide = false;
        return this;
    }

    public ItemBuilder unbreakable() {
        this.unbreakable = true;
        return this;
    }

    public ItemBuilder id(String id) {
        this.itemId = id;
        return this;
    }

    public ItemBuilder owner(String uuid) {
        this.owner = uuid;
        return this;
    }

    public ItemBuilder dye(Color c) {
        this.dye = c;
        return this;
    }

    public ItemBuilder maxStack(int n) {
        this.maxStack = n;
        return this;
    }

    /** Stile del tooltip del resource pack, es. "quantum" (pepita:quantum). */
    public ItemBuilder tooltipStyle(String style) {
        this.tooltipStyle = style;
        return this;
    }

    /** Nasconde completamente il tooltip (riempitivi dei menu). */
    public ItemBuilder hideTooltip() {
        this.hideAll = true;
        return this;
    }

    public ItemBuilder enchant(Enchantment e, int level) {
        item.addUnsafeEnchantment(e, level);
        return this;
    }

    public ItemStack build() {
        item.editMeta(meta -> {
            if (name != null) meta.itemName(Txt.item(name));
            if (!lore.isEmpty()) meta.lore(Txt.lore(lore));
            if (unbreakable) meta.setUnbreakable(true);
            if (glow) meta.setEnchantmentGlintOverride(true);
            if (maxStack > 0) meta.setMaxStackSize(maxStack);
            if (itemId != null) meta.getPersistentDataContainer().set(Keys.ITEM, PersistentDataType.STRING, itemId);
            if (owner != null) meta.getPersistentDataContainer().set(Keys.OWNER, PersistentDataType.STRING, owner);
            if (dye != null && meta instanceof LeatherArmorMeta lam) lam.setColor(dye);
        });
        if (tooltipStyle != null) {
            String k = tooltipStyle.contains(":") ? tooltipStyle : "pepita:" + tooltipStyle;
            item.setData(DataComponentTypes.TOOLTIP_STYLE, net.kyori.adventure.key.Key.key(k));
        }
        if (model != null) {
            // custom_model_data: senza resource pack il client mostra l'oggetto vanilla, mai il cubo viola
            String id = model.contains(":") ? model.substring(model.indexOf(':') + 1) : model;
            item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString(id).build());
        }
        if (hide || hideAll) {
            item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(hideAll)
                    .addHiddenComponents(DataComponentTypes.ATTRIBUTE_MODIFIERS, DataComponentTypes.ENCHANTMENTS,
                            DataComponentTypes.UNBREAKABLE, DataComponentTypes.DYED_COLOR,
                            DataComponentTypes.STORED_ENCHANTMENTS, DataComponentTypes.POTION_CONTENTS)
                    .build());
        }
        return item;
    }

    public static String idOf(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(Keys.ITEM, PersistentDataType.STRING);
    }

    public static boolean is(ItemStack it, String id) {
        String s = idOf(it);
        return s != null && s.equals(id);
    }

    public static boolean startsWith(ItemStack it, String prefix) {
        String s = idOf(it);
        return s != null && s.startsWith(prefix);
    }
}

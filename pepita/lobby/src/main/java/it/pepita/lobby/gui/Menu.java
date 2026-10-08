package it.pepita.lobby.gui;

import it.pepita.lobby.util.ItemBuilder;
import it.pepita.lobby.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Inventario cliccabile. Il titolo e i riempitivi dipendono da chi lo apre (con o senza resource pack),
 * per questo l'inventario vero viene creato in {@link #open(Player)}.
 */
public class Menu implements InventoryHolder {
    private final int rows;
    private final String title;
    private Gui.Emblem emblem = Gui.Emblem.MAIN;
    private final ItemStack[] items;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();
    private Runnable onClose;
    private Inventory inv;
    private int fillMode; // 0 niente, 1 riempi, 2 bordo oro + riempi interno, 3 solo bordo oro
    private Material fillColor;
    private boolean pack;

    public Menu(int rows, String title) {
        this.rows = Math.max(1, Math.min(6, rows));
        this.title = title;
        this.items = new ItemStack[this.rows * 9];
    }

    public Menu emblem(Gui.Emblem e) {
        this.emblem = e;
        return this;
    }

    public int size() {
        return rows * 9;
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inv == null) inv = Bukkit.createInventory(this, rows * 9, Txt.mm("<dark_gray>" + title));
        return inv;
    }

    public Menu set(int slot, ItemStack item) {
        if (slot < 0 || slot >= items.length) return this;
        items[slot] = item;
        actions.remove(slot);
        if (inv != null) inv.setItem(slot, item);
        return this;
    }

    public Menu set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        if (slot < 0 || slot >= items.length) return this;
        items[slot] = item;
        actions.put(slot, action);
        if (inv != null) inv.setItem(slot, item);
        return this;
    }

    public ItemStack get(int slot) {
        return slot < 0 || slot >= items.length ? null : items[slot];
    }

    public Menu onClose(Runnable r) {
        this.onClose = r;
        return this;
    }

    /** Riempie gli slot vuoti con la pietra scura del pack (o vetro nero). */
    public void fill() {
        fillMode = 1;
        fillColor = null;
        if (inv != null) applyFill();
    }

    /** Bordo con filo d'oro e interno in pietra. */
    public void frame() {
        fillMode = 2;
        fillColor = null;
        if (inv != null) applyFill();
    }

    /**
     * Compatibilità: vetro nero/grigio = riempitivo del pack; altri colori (es. verde della vincita) restano vetro colorato.
     */
    public void fill(Material glass) {
        if (glass == Material.BLACK_STAINED_GLASS_PANE || glass == Material.GRAY_STAINED_GLASS_PANE) {
            fill();
            return;
        }
        fillMode = 1;
        fillColor = glass;
        if (inv != null) {
            ItemStack g = new ItemBuilder(glass).name(" ").hideTooltip().build();
            for (int i = 0; i < items.length; i++) if (items[i] == null || isFiller(items[i])) inv.setItem(i, g);
        }
    }

    /** Solo il bordo (con filo d'oro). */
    public void border(Material ignored) {
        fillMode = 3;
        fillColor = null;
        if (inv != null) applyFill();
    }

    private static boolean isFiller(ItemStack it) {
        return it != null && (it.getType() == Material.BLACK_STAINED_GLASS_PANE || it.getType() == Material.GRAY_STAINED_GLASS_PANE
                || it.getType() == Material.LIME_STAINED_GLASS_PANE);
    }

    private void applyFill() {
        if (fillMode == 0) return;
        ItemStack plain = fillColor != null ? new ItemBuilder(fillColor).name(" ").hideTooltip().build() : Gui.filler(pack, false);
        ItemStack gold = Gui.filler(pack, true);
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null) continue;
            int r = i / 9, c = i % 9;
            boolean edge = r == 0 || r == rows - 1 || c == 0 || c == 8;
            ItemStack f = switch (fillMode) {
                case 2 -> edge ? gold : plain;
                case 3 -> edge ? gold : null;
                default -> plain;
            };
            inv.setItem(i, f);
        }
    }

    public void open(Player p) {
        pack = Gui.pack(p);
        inv = Bukkit.createInventory(this, rows * 9, Gui.title(pack, rows, emblem, title));
        for (int i = 0; i < items.length; i++) if (items[i] != null) inv.setItem(i, items[i]);
        applyFill();
        p.openInventory(inv);
    }

    void click(InventoryClickEvent e) {
        Consumer<InventoryClickEvent> a = actions.get(e.getRawSlot());
        if (a != null) a.accept(e);
    }

    void closed() {
        if (onClose != null) onClose.run();
    }
}

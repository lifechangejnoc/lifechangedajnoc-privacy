package it.pepita.core.gui;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Smista i click dei menu. */
public final class MenuListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu menu)) return;
        e.setCancelled(true);
        if (e.getRawSlot() < 0 || e.getRawSlot() >= e.getInventory().getSize()) return;
        if (e.getWhoClicked() instanceof Player p && e.getCurrentItem() != null && !e.getCurrentItem().getType().isAir())
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.4f);
        try {
            menu.click(e);
        } catch (Exception ex) {
            org.bukkit.Bukkit.getLogger().warning("[PepitaCore] Errore nel menu: " + ex);
            ex.printStackTrace();
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof Menu menu) menu.closed();
    }
}

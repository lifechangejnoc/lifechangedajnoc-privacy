package it.pepita.core.armor;

import it.pepita.core.PepitaCore;
import it.pepita.core.cosmetics.ArmorSkin;
import it.pepita.core.data.PlayerData;
import it.pepita.core.economy.Economy;
import it.pepita.core.gui.Gui;
import it.pepita.core.gui.Menu;
import it.pepita.core.pickaxe.PickaxeManager;
import it.pepita.core.util.Fmt;
import it.pepita.core.util.ItemBuilder;
import it.pepita.core.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Corazza potenziabile con i Quantum: 4 pezzi, livello 0..max ciascuno.
 * Ogni pezzo dà % Soldi, % Esperienza del piccone e % Pepite (gli stivali anche % Quantum).
 */
public final class ArmorService implements Listener {
    public static final int SOLDI = 0, XP = 1, PEPITE = 2, QUANTUM = 3;

    /** Peso di ciascun bonus per pezzo: elmo (xp), corpetto (soldi), gambali (pepite), stivali (quantum). */
    private static final double[][] WEIGHT = {
            {0.6, 1.5, 0.5, 0},
            {1.0, 1.0, 0.6, 0},
            {0.6, 1.0, 1.0, 0},
            {0.5, 0.8, 0.5, 1.0}};

    private final PepitaCore plugin;
    private int max;
    private double base, growth;
    private final double[] perLevel = new double[4];

    public ArmorService(PepitaCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        max = c.getInt("armatura.livello-max", 25);
        base = c.getDouble("armatura.costo-base", 40);
        growth = c.getDouble("armatura.costo-crescita", 1.18);
        perLevel[SOLDI] = c.getDouble("armatura.bonus-livello.soldi", 0.006);
        perLevel[XP] = c.getDouble("armatura.bonus-livello.esperienza", 0.012);
        perLevel[PEPITE] = c.getDouble("armatura.bonus-livello.pepite", 0.006);
        perLevel[QUANTUM] = c.getDouble("armatura.bonus-livello.quantum", 0.008);
    }

    public int maxLevel() {
        return max;
    }

    /** Bonus di un pezzo a un livello. */
    public double[] pieceBonus(int piece, int level) {
        double[] out = new double[4];
        for (int k = 0; k < 4; k++) out[k] = level * perLevel[k] * WEIGHT[piece][k];
        return out;
    }

    /** Bonus totale della corazza per un tipo (SOLDI, XP, PEPITE, QUANTUM). */
    public double bonus(PlayerData d, int kind) {
        double s = 0;
        for (int i = 0; i < 4; i++) s += d.armorLevels[i] * perLevel[kind] * WEIGHT[i][kind];
        return s;
    }

    /** Costo in Quantum per passare da level a level+1. */
    public double cost(int level) {
        return Math.ceil(base * Math.pow(growth, level));
    }

    // =====================================================================
    //  Menu
    // =====================================================================

    public void open(Player p) {
        PlayerData d = plugin.data().get(p);
        Menu m = new Menu(5, "Corazza Quantica").emblem(Gui.Emblem.ARMATURA);
        ArmorSkin skin = ArmorSkin.byId(d.armorSkin);
        m.set(4, Gui.button(p, "gui_quantum", Material.AMETHYST_CLUSTER)
                .name(Txt.S_QUANTUM + " <" + Txt.QUANTUM + "><b>" + Fmt.num(d.quantum) + " Quantum</b>")
                .lore("<gray>I Quantum si trovano solo nella", "<red>Miniera PvP</red><gray>: scavali e riportali", "<gray>allo spawn sicuro per non perderli.")
                .build());
        int[] slots = {19, 21, 23, 25};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            int lvl = d.armorLevels[i];
            ItemStack it = plugin.picks().armorPiece(PickaxeManager.SLOTS[i], skin, d.hasPack, d);
            List<String> lore = new ArrayList<>();
            double[] now = pieceBonus(i, lvl), nx = pieceBonus(i, Math.min(max, lvl + 1));
            lore.add("<gray>Livello <" + Txt.QUANTUM + ">" + lvl + "</" + Txt.QUANTUM + "><dark_gray>/" + max);
            lore.add("");
            lore.add("<#E8D7FF><b>Vantaggi:</b>");
            lore.add(line(Txt.SOLDI, "Soldi", now[SOLDI], nx[SOLDI], lvl < max));
            lore.add(line("#FFB86B", "Esperienza piccone", now[XP], nx[XP], lvl < max));
            lore.add(line(Txt.PEPITE, "Pepite", now[PEPITE], nx[PEPITE], lvl < max));
            if (i == 3) lore.add(line(Txt.QUANTUM, "Quantum", now[QUANTUM], nx[QUANTUM], lvl < max));
            lore.add("");
            if (lvl >= max) lore.add("<gold>✔ Livello massimo");
            else {
                lore.add("<gray>Costo: " + Txt.quantum(cost(lvl)));
                lore.add("<#B98CFF>▶ Clicca per migliorare la corazza");
                lore.add("<#B98CFF>▶ Shift + click: migliora al massimo possibile");
            }
            it.editMeta(meta -> meta.lore(Txt.lore(lore)));
            m.set(slots[i], it, e -> {
                upgrade(p, d, idx, e.getClick() == ClickType.SHIFT_LEFT || e.getClick() == ClickType.SHIFT_RIGHT);
                open(p);
            });
        }
        double[] tot = {bonus(d, SOLDI), bonus(d, XP), bonus(d, PEPITE), bonus(d, QUANTUM)};
        m.set(31, Gui.button(p, "gui_info", Material.BOOK).name("<#E8D7FF><b>Totale della corazza</b>")
                .lore("<" + Txt.SOLDI + ">+" + Fmt.pct(tot[SOLDI]) + " <gray>Soldi",
                        "<#FFB86B>+" + Fmt.pct(tot[XP]) + " <gray>Esperienza piccone",
                        "<" + Txt.PEPITE + ">+" + Fmt.pct(tot[PEPITE]) + " <gray>Pepite",
                        "<" + Txt.QUANTUM + ">+" + Fmt.pct(tot[QUANTUM]) + " <gray>Quantum",
                        "", "<gray>Aspetto: <white>" + skin.display, "<dark_gray>Cambialo con /skin").build());
        m.set(39, Gui.back(p), e -> plugin.menus().main(p));
        m.set(41, Gui.button(p, "gui_skin", Material.LEATHER_CHESTPLATE).name("<#FF7BD1><b>Aspetto della corazza</b>")
                .lore("<gray>Scegli uno dei set.", "", "<#FFD54A>▶ Click per aprire").build(), e -> plugin.menus().armorSkins(p));
        m.frame();
        m.open(p);
    }

    private static String line(String color, String what, double now, double next, boolean showNext) {
        return "  <" + color + ">+" + Fmt.pct(now) + " <gray>" + what + (showNext ? " <dark_gray>→ <" + color + ">+" + Fmt.pct(next) : "");
    }

    public void upgrade(Player p, PlayerData d, int piece, boolean many) {
        int done = 0;
        double spent = 0;
        while (d.armorLevels[piece] < max) {
            double c = cost(d.armorLevels[piece]);
            if (!plugin.eco().take(d, Economy.Cur.QUANTUM, c, "corazza")) break;
            spent += c;
            d.armorLevels[piece]++;
            done++;
            if (!many) break;
        }
        if (done == 0) {
            if (d.armorLevels[piece] >= max) Txt.send(p, "Questo pezzo è già al livello massimo.");
            else Txt.send(p, "Ti servono " + Txt.quantum(cost(d.armorLevels[piece])) + " <gray>(hai " + Txt.quantum(d.quantum)
                    + "<gray>). I Quantum si trovano nella <red>Miniera PvP</red>.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            return;
        }
        d.dirty = true;
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.6f);
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 25, 0.4, 0.8, 0.4, 0.05);
        Txt.send(p, "<white>" + PickaxeManager.PIECE_NAMES[piece] + "</white> della corazza → livello <" + Txt.QUANTUM + ">"
                + d.armorLevels[piece] + "</" + Txt.QUANTUM + "> <gray>(-" + Txt.quantum(spent) + "<gray>)");
        plugin.picks().applyArmor(p, d);
    }

    // =====================================================================
    //  Click sulla corazza indossata
    // =====================================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getInventory().getHolder() instanceof Menu) return;
        boolean armorSlot = e.getSlotType() == InventoryType.SlotType.ARMOR;
        ItemStack cur = e.getCurrentItem();
        if (armorSlot && ItemBuilder.is(cur, PickaxeManager.ARMOR)) {
            e.setCancelled(true);
            if (p.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
            Bukkit.getScheduler().runTask(plugin, () -> open(p));
            return;
        }
        // niente spostamenti della corazza con shift o tasti numerici
        if (ItemBuilder.is(cur, PickaxeManager.ARMOR) || (e.getClick() == ClickType.NUMBER_KEY && armorSlot)) e.setCancelled(true);
        if (e.getHotbarButton() >= 0) {
            ItemStack hb = p.getInventory().getItem(e.getHotbarButton());
            if (ItemBuilder.is(hb, PickaxeManager.ARMOR)) e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) return;
        if (e.getView().getType() != InventoryType.CRAFTING) return;
        for (int raw : e.getRawSlots()) if (raw >= 5 && raw <= 8) {
            e.setCancelled(true);
            return;
        }
    }

    public static EquipmentSlot slotOf(int piece) {
        return PickaxeManager.SLOTS[piece];
    }
}

package it.pepita.core.crate;

import it.pepita.core.PepitaCore;
import it.pepita.core.cosmetics.ArmorSkin;
import it.pepita.core.cosmetics.PickaxeSkin;
import it.pepita.core.data.PlayerData;
import it.pepita.core.gui.Menu;
import it.pepita.core.pickaxe.Enchant;
import it.pepita.core.rank.RankManager;
import it.pepita.core.util.Fmt;
import it.pepita.core.util.ItemBuilder;
import it.pepita.core.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Premi, apertura animata e menu delle casse. */
public final class CrateManager {
    private final PepitaCore plugin;
    private final Map<Crate, List<Reward>> rewards = new EnumMap<>(Crate.class);

    public CrateManager(PepitaCore plugin) {
        this.plugin = plugin;
        // SOLDI = minuti di scavo del giocatore (vedi ECONOMIA.md)
        rewards.put(Crate.COMUNE, List.of(
                Reward.soldi(3, 30), Reward.soldi(6, 15), Reward.pepite(100, 20), Reward.pepite(300, 10),
                Reward.bomba("normale", 2, 10), Reward.chiave("rara", 1, 8), Reward.booster(1.5, 10, 5), Reward.gemme(2, 2),
                Reward.skinPiccone("caramella", 1), Reward.skinPiccone("baguette", 1)));
        rewards.put(Crate.RARA, List.of(
                Reward.soldi(12, 22), Reward.pepite(750, 20), Reward.pepite(2000, 10), Reward.bomba("grande", 1, 10),
                Reward.incantesimo("esplosivo", 3, 8), Reward.incantesimo("fortuna", 25, 10), Reward.chiave("leggendaria", 1, 7),
                Reward.booster(2, 15, 6), Reward.gemme(5, 4), Reward.skinPiccone("ghiaccio", 2), Reward.skinPiccone("drago", 1),
                Reward.skinPiccone("sakura", 2), Reward.quantum(10, 3)));
        rewards.put(Crate.LEGGENDARIA, List.of(
                Reward.soldi(30, 20), Reward.pepite(5000, 18), Reward.pepite(15000, 8), Reward.bomba("nucleare", 1, 8),
                Reward.incantesimo("cercapepite", 20, 10), Reward.incantesimo("martello", 2, 8), Reward.chiave("pepita", 1, 6),
                Reward.booster(2, 60, 6), Reward.gemme(15, 5), Reward.skinPiccone("neon", 3), Reward.skinPiccone("lava", 3),
                Reward.skinPiccone("arcobaleno", 3), Reward.skinPiccone("smeraldo", 2), Reward.skinPiccone("glaciale", 2),
                Reward.skinPiccone("ametista", 2), Reward.skinArmatura("righe", 3), Reward.skinArmatura("sakura", 2), Reward.quantum(40, 4)));
        rewards.put(Crate.PEPITA, List.of(
                Reward.soldi(75, 18), Reward.pepite(25000, 15), Reward.pepite(75000, 6), Reward.bomba("nucleare", 3, 10),
                Reward.incantesimo("esplosivo", 10, 8), Reward.incantesimo("fortuna", 150, 8), Reward.booster(3, 30, 6),
                Reward.gemme(40, 6), Reward.pepitaOro(3), Reward.skinPiccone("pepita", 2), Reward.skinPiccone("galassia", 3),
                Reward.skinPiccone("aureo", 2), Reward.skinPiccone("inferno", 2), Reward.skinPiccone("faraone", 2),
                Reward.skinPiccone("abisso", 1), Reward.skinPiccone("tempesta", 1), Reward.skinPiccone("eclisse", 1),
                Reward.skinArmatura("pepita", 2), Reward.skinArmatura("galassia", 3), Reward.skinArmatura("neon", 3),
                Reward.skinArmatura("aureo", 2), Reward.skinArmatura("inferno", 2), Reward.quantum(120, 4)));
    }

    public List<Reward> rewards(Crate c) {
        return rewards.get(c);
    }

    public Reward roll(Crate c) {
        List<Reward> list = rewards.get(c);
        int tot = 0;
        for (Reward r : list) tot += r.weight();
        int roll = ThreadLocalRandom.current().nextInt(tot);
        for (Reward r : list) {
            roll -= r.weight();
            if (roll < 0) return r;
        }
        return list.getLast();
    }

    public double chance(Crate c, Reward r) {
        int tot = 0;
        for (Reward x : rewards.get(c)) tot += x.weight();
        return (double) r.weight() / tot;
    }

    // --------------- Descrizione e consegna ---------------

    public String describe(Reward r, PlayerData d) {
        return switch (r.type()) {
            case SOLDI -> d == null ? "<" + Txt.SOLDI + ">" + Fmt.num(r.amount()) + " minuti di scavo"
                    : Txt.soldi(moneyFor(r, d)) + " <dark_gray>(" + Fmt.num(r.amount()) + " min)";
            case QUANTUM -> Txt.quantum(r.amount());
            case PEPITE -> Txt.pepite(r.amount());
            case GEMME -> Txt.gemme(r.amount());
            case CHIAVE -> {
                Crate c = Crate.byId(r.arg());
                yield (int) r.amount() + "x " + c.color + "Chiave " + c.display + "<gray>";
            }
            case BOMBA -> (int) r.amount() + "x <#FF6B6B>Bomba " + r.arg() + "<gray>";
            case SKIN_PICCONE -> PickaxeSkin.byId(r.arg()).rar.color + "Skin piccone: " + PickaxeSkin.byId(r.arg()).display;
            case SKIN_ARMATURA -> "<white>Skin armatura: " + ArmorSkin.byId(r.arg()).display;
            case BOOSTER -> "<gold>Booster soldi x" + r.arg() + "</gold> <gray>per " + (int) r.amount() + " min";
            case INCANTESIMO -> "<#FF7B7B>+" + (int) r.amount() + " " + Enchant.byId(r.arg()).display + "<gray>";
            case PEPITA_ORO -> "<gradient:#FFF6B7:#FFA726><b>Pepita d'Oro</b></gradient>";
        };
    }

    private double moneyFor(Reward r, PlayerData d) {
        return plugin.eco().minutes(d, r.amount());
    }

    public ItemStack icon(Reward r) {
        return switch (r.type()) {
            case SOLDI -> new ItemBuilder(Material.EMERALD).name("<" + Txt.SOLDI + "><b>Soldi</b>").build();
            case PEPITE -> new ItemBuilder(Material.GOLD_NUGGET).name("<" + Txt.PEPITE + "><b>" + Fmt.num(r.amount()) + " Pepite</b>").build();
            case GEMME -> new ItemBuilder(Material.AMETHYST_SHARD).name("<" + Txt.GEMME + "><b>" + (int) r.amount() + " Gemme</b>").model("gemma").build();
            case CHIAVE -> {
                Crate c = Crate.byId(r.arg());
                yield new ItemBuilder(Material.TRIPWIRE_HOOK, (int) r.amount()).name(c.color + "<b>Chiave " + c.display).model(c.keyModel).build();
            }
            case BOMBA -> plugin.picks().bomb(r.arg(), (int) r.amount());
            case SKIN_PICCONE -> plugin.picks().skinItem(PickaxeSkin.byId(r.arg()), 1);
            case QUANTUM -> new ItemBuilder(Material.ECHO_SHARD).model("quantum").name("<" + Txt.QUANTUM + "><b>" + Fmt.num(r.amount()) + " Quantum</b>").build();
            case SKIN_ARMATURA -> {
                ArmorSkin s = ArmorSkin.byId(r.arg());
                yield new ItemBuilder(Material.LEATHER_CHESTPLATE).name("<white><b>" + s.display).lore(s.rarity).dye(s.color).build();
            }
            case BOOSTER -> new ItemBuilder(Material.BLAZE_POWDER).name("<gold><b>Booster x" + r.arg()).glow().build();
            case INCANTESIMO -> new ItemBuilder(Enchant.byId(r.arg()).icon).name("<#FF7B7B><b>+" + (int) r.amount() + " " + Enchant.byId(r.arg()).display).glow().build();
            case PEPITA_ORO -> plugin.picks().pepitaOro();
        };
    }

    public void give(Player p, Reward r) {
        PlayerData d = plugin.data().get(p);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        String text = describe(r, d);
        switch (r.type()) {
            case SOLDI -> plugin.eco().give(d, it.pepita.core.economy.Economy.Cur.SOLDI, moneyFor(r, d), "casse");
            case PEPITE -> plugin.eco().give(d, it.pepita.core.economy.Economy.Cur.PEPITE, r.amount(), "casse");
            case GEMME -> plugin.eco().give(d, it.pepita.core.economy.Economy.Cur.GEMME, r.amount(), "casse");
            case QUANTUM -> plugin.eco().give(d, it.pepita.core.economy.Economy.Cur.QUANTUM, r.amount(), "casse");
            case CHIAVE -> d.addKeys(r.arg(), (int) r.amount());
            case BOMBA -> giveItem(p, plugin.picks().bomb(r.arg(), (int) r.amount()));
            case SKIN_PICCONE -> plugin.picks().giveSkin(p, d, PickaxeSkin.byId(r.arg()));
            case SKIN_ARMATURA -> {
                if (!d.armorSkins.add(r.arg())) {
                    double comp = 3000;
                    plugin.eco().give(d, it.pepita.core.economy.Economy.Cur.PEPITE, comp, "casse");
                    text = "<gray>(skin già posseduta) " + Txt.pepite(comp);
                }
            }
            case BOOSTER -> {
                double mult = Double.parseDouble(r.arg());
                long base = Math.max(System.currentTimeMillis(), d.boostUntil);
                if (d.boostUntil < System.currentTimeMillis() || mult > d.boostMult) d.boostMult = mult;
                d.boostUntil = base + (long) (r.amount() * 60_000);
            }
            case INCANTESIMO -> {
                Enchant e = Enchant.byId(r.arg());
                int cur = d.ench(e.id);
                int add = (int) Math.min(r.amount(), e.max - cur);
                if (add <= 0) {
                    double comp = e.cost(Math.max(0, cur - 1)) * r.amount();
                    plugin.eco().give(d, it.pepita.core.economy.Economy.Cur.PEPITE, comp, "casse");
                    text = "<gray>(" + e.display + " già al massimo) " + Txt.pepite(comp);
                } else {
                    d.enchants.put(e.id, cur + add);
                    plugin.picks().refresh(p);
                }
            }
            case PEPITA_ORO -> giveItem(p, plugin.picks().pepitaOro());
        }
        d.dirty = true;
        plugin.pass().progress(p, d, it.pepita.core.pass.BattlePass.Mission.CASSE, 1);
        plugin.tutorial().onEvent(p, it.pepita.core.tutorial.Tutorial.Ev.CRATE, 1);
        Txt.send(p, "Hai vinto: " + text);
        if (rnd.nextBoolean()) p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.2);
    }

    private void giveItem(Player p, ItemStack it) {
        for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItem(p.getLocation(), left);
    }

    // --------------- Menu ---------------

    public void openHub(Player p) {
        PlayerData d = plugin.data().get(p);
        Menu m = new Menu(3, "Casse di Pepita").emblem(it.pepita.core.gui.Gui.Emblem.CASSE);
        int[] slots = {10, 12, 14, 16};
        Crate[] cs = Crate.values();
        for (int i = 0; i < cs.length; i++) {
            Crate c = cs[i];
            int k = d.keys(c.id);
            m.set(slots[i], new ItemBuilder(c.block).name(c.title())
                    .lore("<gray>Chiavi: <white>" + k, "",
                            "<#FFD54A>▶ Sinistro: apri 1", "<#FFD54A>▶ Shift + sinistro: apri tutte (max 64)", "<#FFD54A>▶ Destro: guarda i premi")
                    .build(), e -> {
                if (e.getClick() == ClickType.RIGHT || e.getClick() == ClickType.SHIFT_RIGHT) preview(p, c);
                else if (e.getClick() == ClickType.SHIFT_LEFT) openMany(p, c, 64);
                else openAnimated(p, c);
            });
        }
        m.set(22, it.pepita.core.gui.Gui.back(p), e -> plugin.menus().main(p));
        m.fill(Material.BLACK_STAINED_GLASS_PANE);
        m.open(p);
    }

    public void preview(Player p, Crate c) {
        PlayerData d = plugin.data().get(p);
        Menu m = new Menu(6, "Premi • Cassa " + c.display).emblem(it.pepita.core.gui.Gui.Emblem.CASSE);
        int slot = 10;
        for (Reward r : rewards(c)) {
            ItemStack ic = icon(r);
            ic.editMeta(meta -> meta.lore(Txt.lore(List.of(describe(r, d), "", "<gray>Probabilità: <white>" + Fmt.pct(chance(c, r))))));
            m.set(slot, ic);
            slot++;
            if (slot % 9 == 8) slot += 2;
        }
        m.set(48, it.pepita.core.gui.Gui.back(p), e -> openHub(p));
        m.set(50, new ItemBuilder(Material.TRIPWIRE_HOOK).name("<green><b>Apri</b>").model(c.keyModel)
                .lore("<gray>Chiavi: <white>" + d.keys(c.id)).build(), e -> openAnimated(p, c));
        m.border(Material.BLACK_STAINED_GLASS_PANE);
        m.open(p);
    }

    private boolean takeKey(Player p, Crate c) {
        PlayerData d = plugin.data().get(p);
        if (d.keys(c.id) <= 0) {
            Txt.send(p, "Non hai chiavi " + c.color + c.display + "<gray>. Trovale scavando, nel <yellow>/giornaliero</yellow> o nel <yellow>/negozio</yellow>.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            return false;
        }
        d.addKeys(c.id, -1);
        return true;
    }

    public void openMany(Player p, Crate c, int max) {
        PlayerData d = plugin.data().get(p);
        int n = 0;
        if (d.keys(c.id) <= 0) {
            takeKey(p, c);
            return;
        }
        while (n < max && d.keys(c.id) > 0) {
            d.addKeys(c.id, -1);
            give(p, roll(c));
            n++;
        }
        if (n > 0) {
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
            Txt.send(p, "Hai aperto <white>" + n + "</white> casse " + c.color + c.display + "<gray>.");
            p.closeInventory();
        }
    }

    public void openAnimated(Player p, Crate c) {
        if (!takeKey(p, c)) return;
        final Reward win = roll(c);
        final List<Reward> reel = new ArrayList<>();
        for (int i = 0; i < 40; i++) reel.add(roll(c));
        final int stopAt = 30; // l'elemento al centro alla fine
        reel.set(stopAt, win);
        Menu m = new Menu(3, "Apertura • Cassa " + c.display).emblem(it.pepita.core.gui.Gui.Emblem.CASSE);
        ItemStack marker = new ItemBuilder(Material.HOPPER).name("<gold>▼").build();
        m.set(4, marker);
        m.set(22, new ItemBuilder(Material.GOLD_NUGGET).name("<gold>▲").model("pepita_oro").build());
        m.fill(Material.GRAY_STAINED_GLASS_PANE);
        final boolean[] done = {false};
        m.onClose(() -> {
            if (!done[0]) {
                done[0] = true;
                give(p, win);
            }
        });
        m.open(p);
        new BukkitRunnable() {
            int offset = 0, wait = 0, ticks = 0;

            @Override
            public void run() {
                if (done[0] || !p.isOnline()) {
                    cancel();
                    return;
                }
                if (wait-- > 0) return;
                // centro = slot 13 = offset + 4
                for (int s = 0; s < 9; s++) m.getInventory().setItem(9 + s, icon(reel.get(offset + s)));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.2f + offset * 0.02f);
                if (offset + 4 >= stopAt) {
                    done[0] = true;
                    cancel();
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                    m.fill(Material.LIME_STAINED_GLASS_PANE);
                    give(p, win);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (p.getOpenInventory().getTopInventory().getHolder() == m) preview(p, c);
                    }, 40L);
                    return;
                }
                offset++;
                ticks++;
                int left = stopAt - (offset + 4);
                wait = left > 15 ? 0 : left > 8 ? 1 : left > 4 ? 3 : 6;
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }
}

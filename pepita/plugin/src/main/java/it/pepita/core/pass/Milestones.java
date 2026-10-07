package it.pepita.core.pass;

import it.pepita.core.PepitaCore;
import it.pepita.core.cosmetics.PickaxeSkin;
import it.pepita.core.crate.Crate;
import it.pepita.core.data.PlayerData;
import it.pepita.core.economy.Economy;
import it.pepita.core.gui.Gui;
import it.pepita.core.gui.Menu;
import it.pepita.core.util.Fmt;
import it.pepita.core.util.ItemBuilder;
import it.pepita.core.util.Txt;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Traguardi dei blocchi rotti: premi una tantum a 1k, 5k, 10k ... 10M blocchi. */
public final class Milestones {
    public static final long[] STEPS = {1_000, 5_000, 10_000, 25_000, 50_000, 100_000, 250_000, 500_000, 1_000_000,
            2_500_000, 5_000_000, 10_000_000};
    private static final double[] MINUTES = {5, 10, 15, 20, 30, 40, 60, 80, 120, 160, 240, 360};
    private static final double[] PEPITE = {500, 1500, 3000, 6000, 12000, 25000, 50000, 100000, 200000, 400000, 800000, 1600000};
    private static final String[] KEY = {"comune", "comune", "rara", "rara", "rara", "leggendaria", "leggendaria", "leggendaria",
            "pepita", "pepita", "pepita", "pepita"};
    private static final int[] KEYN = {2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3, 5};
    private static final int[] GEMS = {0, 0, 0, 0, 2, 5, 8, 12, 20, 30, 45, 75};
    private static final String[] SKIN = {null, null, null, null, null, "pizza", null, "sakura", "faraone", null, "tempesta", "abisso"};

    private final PepitaCore plugin;

    public Milestones(PepitaCore plugin) {
        this.plugin = plugin;
    }

    /** Dopo aver aggiunto blocchi: avvisa se si è superato un traguardo. */
    public void check(Player p, PlayerData d, long before) {
        for (long s : STEPS) {
            if (before < s && d.blocchi >= s) {
                p.showTitle(Title.title(Txt.mm("<gradient:#FFE259:#FFA751><b>TRAGUARDO!</b></gradient>"),
                        Txt.mm("<white>" + Fmt.num(s) + "</white> <gray>blocchi rotti • ritira il premio: <yellow>/traguardi"),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2200), Duration.ofMillis(500))));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
                if (s >= 1_000_000) Bukkit.broadcast(Txt.mm(Txt.PREFIX + "<white>" + Txt.esc(p.getName()) + "</white> ha rotto <gold>"
                        + Fmt.num(s) + "</gold> blocchi!"));
            }
        }
    }

    public List<String> rewardLines(PlayerData d, int i) {
        List<String> l = new ArrayList<>();
        l.add("<" + Txt.SOLDI + ">" + Fmt.num(MINUTES[i]) + " minuti di scavo <dark_gray>(" + Txt.soldi(plugin.eco().minutes(d, MINUTES[i])) + "<dark_gray>)");
        l.add(Txt.pepite(PEPITE[i]));
        Crate c = Crate.byId(KEY[i]);
        l.add(KEYN[i] + "x " + c.color + "Chiave " + c.display);
        if (GEMS[i] > 0) l.add(Txt.gemme(GEMS[i]));
        if (SKIN[i] != null) {
            PickaxeSkin s = PickaxeSkin.byId(SKIN[i]);
            l.add(s.rar.color + "Skin " + s.display);
        }
        return l;
    }

    public int claimable(PlayerData d) {
        int n = 0;
        for (long s : STEPS) if (d.blocchi >= s && !d.traguardi.contains(s)) n++;
        return n;
    }

    public void open(Player p) {
        PlayerData d = plugin.data().get(p);
        Menu m = new Menu(4, "Traguardi dei blocchi").emblem(Gui.Emblem.TRAGUARDI);
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 20, 21, 22, 23, 24};
        for (int i = 0; i < STEPS.length; i++) {
            final int idx = i;
            long s = STEPS[i];
            boolean got = d.traguardi.contains(s), ok = d.blocchi >= s;
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Rompi <white>" + Fmt.num(s) + "</white> blocchi.");
            lore.add(Txt.bar(Math.min(1, d.blocchi / (double) s), 12, "#6BE36B", "#4A4A4A") + " <gray>" + Fmt.pct(Math.min(1, d.blocchi / (double) s)));
            lore.add("");
            lore.add("<gray>Premi:");
            for (String r : rewardLines(d, i)) lore.add(" <dark_gray>▸ " + r);
            lore.add("");
            lore.add(got ? "<green>✔ Ritirato" : ok ? "<#FFD54A>▶ Click per ritirare" : "<red>🔒 Non ancora");
            ItemBuilder b = got ? new ItemBuilder(Material.LIME_DYE) : ok ? Gui.button(p, "gui_traguardo", Material.GOLD_BLOCK).glow()
                    : Gui.button(p, "gui_lucchetto", Material.GRAY_DYE);
            m.set(slots[i], b.name((got ? "<green>" : ok ? "<gold>" : "<gray>") + "<b>" + Fmt.num(s) + " blocchi").lore(lore).build(), e -> {
                if (got || !ok) return;
                claim(p, d, idx);
                open(p);
            });
        }
        m.set(31, Gui.back(p), e -> plugin.menus().main(p));
        m.frame();
        m.open(p);
    }

    private void claim(Player p, PlayerData d, int i) {
        if (!d.traguardi.add(STEPS[i])) return;
        Economy eco = plugin.eco();
        eco.give(d, Economy.Cur.SOLDI, eco.minutes(d, MINUTES[i]), "traguardi");
        eco.give(d, Economy.Cur.PEPITE, PEPITE[i], "traguardi");
        d.addKeys(KEY[i], KEYN[i]);
        if (GEMS[i] > 0) eco.give(d, Economy.Cur.GEMME, GEMS[i], "traguardi");
        if (SKIN[i] != null) plugin.picks().giveSkin(p, d, PickaxeSkin.byId(SKIN[i]));
        d.dirty = true;
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        Txt.send(p, "Traguardo <white>" + Fmt.num(STEPS[i]) + " blocchi</white> ritirato: " + String.join("<gray>, ", rewardLines(d, i)));
    }
}

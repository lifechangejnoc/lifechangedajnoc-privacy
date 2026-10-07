package it.pepita.core.pass;

import it.pepita.core.PepitaCore;
import it.pepita.core.cosmetics.PickaxeSkin;
import it.pepita.core.crate.Crate;
import it.pepita.core.data.PlayerData;
import it.pepita.core.economy.Economy;
import it.pepita.core.gui.Gui;
import it.pepita.core.gui.Menu;
import it.pepita.core.tutorial.Tutorial;
import it.pepita.core.util.Fmt;
import it.pepita.core.util.ItemBuilder;
import it.pepita.core.util.Txt;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Battle pass stagionale: 50 livelli, XP dai blocchi e da 3 missioni giornaliere + 3 settimanali,
 * traccia gratuita e premium (acquistabile con le Gemme). Premi in "minuti di scavo".
 */
public final class BattlePass {
    public enum Mission {
        BLOCCHI("Rompi %s blocchi"), INCANTESIMI("Attiva %s incantesimi"), LUCKY("Apri %s Lucky Block"),
        CASSE("Apri %s casse"), QUANTUM("Raccogli %s Quantum"), RANKUP("Sali di %s rank"), BOMBE("Lancia %s bombe in miniera");
        public final String text;

        Mission(String text) {
            this.text = text;
        }
    }

    public record Task(String id, Mission type, double target, double xp) {
        public String describe() {
            return String.format(type.text, Fmt.num(target));
        }
    }

    public enum RType { SOLDI, PEPITE, GEMME, QUANTUM, CHIAVE, SKIN, BOOSTER }

    public record Reward(RType type, double amount, String arg) {}

    private static final Task[] DAILY = {
            new Task("d_blocchi", Mission.BLOCCHI, 3000, 300), new Task("d_blocchi2", Mission.BLOCCHI, 8000, 600),
            new Task("d_incantesimi", Mission.INCANTESIMI, 60, 300), new Task("d_lucky", Mission.LUCKY, 3, 350),
            new Task("d_casse", Mission.CASSE, 3, 300), new Task("d_rankup", Mission.RANKUP, 2, 350),
            new Task("d_bombe", Mission.BOMBE, 5, 250), new Task("d_quantum", Mission.QUANTUM, 30, 450)};
    private static final Task[] WEEKLY = {
            new Task("w_blocchi", Mission.BLOCCHI, 60000, 2500), new Task("w_incantesimi", Mission.INCANTESIMI, 1200, 2000),
            new Task("w_lucky", Mission.LUCKY, 25, 2200), new Task("w_casse", Mission.CASSE, 25, 2000),
            new Task("w_quantum", Mission.QUANTUM, 400, 3000), new Task("w_rankup", Mission.RANKUP, 15, 2200)};

    private final PepitaCore plugin;
    private int season, levels, price;
    private double xpLevel, xpBlock;
    private String name;

    public BattlePass(PepitaCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        season = c.getInt("battlepass.stagione", 1);
        name = c.getString("battlepass.nome", "Stagione " + season);
        levels = Math.max(1, c.getInt("battlepass.livelli", 50));
        xpLevel = c.getDouble("battlepass.xp-livello", 1000);
        xpBlock = c.getDouble("battlepass.xp-per-blocco", 0.05);
        price = c.getInt("battlepass.prezzo-premium", 1200);
    }

    public double xpPerBlock() {
        return xpBlock;
    }

    // =====================================================================
    //  Stato del giocatore
    // =====================================================================

    private static long today() {
        return LocalDate.now().toEpochDay();
    }

    private static long week() {
        return Math.floorDiv(today() + 3, 7); // settimane da lunedì
    }

    private void sync(PlayerData d) {
        if (d.bpSeason != season) {
            d.bpSeason = season;
            d.bpXp = 0;
            d.bpPremium = false;
            d.bpFree.clear();
            d.bpPrem.clear();
            d.dirty = true;
        }
        long t = today(), w = week();
        if (d.bpDay != t) {
            d.bpDay = t;
            d.bpDaily.clear();
            d.bpDailyDone.clear();
            d.dirty = true;
        }
        if (d.bpWeek != w) {
            d.bpWeek = w;
            d.bpWeekly.clear();
            d.bpWeeklyDone.clear();
            d.dirty = true;
        }
    }

    private List<Task> pick(Task[] pool, long seed) {
        List<Task> l = new ArrayList<>(List.of(pool));
        Random r = new Random(seed * 7919L + season * 104729L);
        List<Task> out = new ArrayList<>();
        // al massimo una missione per tipo
        while (out.size() < 3 && !l.isEmpty()) {
            Task t = l.remove(r.nextInt(l.size()));
            if (out.stream().noneMatch(o -> o.type == t.type)) out.add(t);
        }
        return out;
    }

    public List<Task> daily() {
        return pick(DAILY, today());
    }

    public List<Task> weekly() {
        return pick(WEEKLY, week() + 100000);
    }

    public int level(PlayerData d) {
        sync(d);
        return (int) Math.min(levels, Math.floor(d.bpXp / xpLevel));
    }

    public void addXp(Player p, PlayerData d, double xp) {
        if (xp <= 0) return;
        sync(d);
        int before = level(d);
        if (before >= levels) return;
        d.bpXp += xp;
        d.dirty = true;
        int after = level(d);
        if (after > before) {
            p.showTitle(Title.title(Txt.mm("<gradient:#FFE259:#FFA751><b>BATTLE PASS " + after + "</b></gradient>"),
                    Txt.mm("<gray>Nuovi premi da ritirare: <yellow>/battlepass"),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1600), Duration.ofMillis(400))));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.5f);
        }
    }

    public void progress(Player p, PlayerData d, Mission type, double amount) {
        if (amount <= 0) return;
        sync(d);
        for (Task t : daily()) bump(p, d, t, type, amount, d.bpDaily, d.bpDailyDone, "giornaliera");
        for (Task t : weekly()) bump(p, d, t, type, amount, d.bpWeekly, d.bpWeeklyDone, "settimanale");
    }

    private void bump(Player p, PlayerData d, Task t, Mission type, double amount, java.util.Map<String, Double> prog,
                      java.util.Set<String> done, String kind) {
        if (t.type != type || done.contains(t.id)) return;
        double v = prog.merge(t.id, amount, Double::sum);
        if (v >= t.target) {
            done.add(t.id);
            Txt.send(p, "<gold>Missione " + kind + " completata:</gold> <white>" + t.describe() + "</white> <gray>(+" + Fmt.num(t.xp) + " XP pass)");
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);
            addXp(p, d, t.xp);
        }
        d.dirty = true;
    }

    // =====================================================================
    //  Premi
    // =====================================================================

    public Reward free(int lvl) {
        if (lvl == levels) return new Reward(RType.SKIN, 1, "sakura");
        if (lvl == 25) return new Reward(RType.CHIAVE, 1, "leggendaria");
        if (lvl % 10 == 0) return new Reward(RType.CHIAVE, 1, "rara");
        if (lvl % 5 == 0) return new Reward(RType.CHIAVE, 2, "comune");
        if (lvl % 2 == 0) return new Reward(RType.PEPITE, 200 + lvl * 40, null);
        return new Reward(RType.SOLDI, 3 + lvl / 10.0, null);
    }

    public Reward premium(int lvl) {
        if (lvl == levels) return new Reward(RType.SKIN, 1, "eclisse");
        if (lvl == 35) return new Reward(RType.SKIN, 1, "inferno");
        if (lvl == 20) return new Reward(RType.SKIN, 1, "smeraldo");
        if (lvl == 30 || lvl == 45) return new Reward(RType.CHIAVE, 1, "pepita");
        if (lvl == 15 || lvl == 40) return new Reward(RType.BOOSTER, 30, "2");
        if (lvl % 10 == 0) return new Reward(RType.GEMME, 10 + lvl / 2.0, null);
        if (lvl % 10 == 5) return new Reward(RType.CHIAVE, 1, "leggendaria");
        if (lvl % 7 == 0) return new Reward(RType.QUANTUM, 20 + lvl, null);
        if (lvl % 2 == 0) return new Reward(RType.PEPITE, 600 + lvl * 120, null);
        return new Reward(RType.SOLDI, 6 + lvl / 5.0, null);
    }

    public String describe(Reward r) {
        return switch (r.type) {
            case SOLDI -> "<" + Txt.SOLDI + ">" + Fmt.num(r.amount) + " minuti di scavo in soldi";
            case PEPITE -> Txt.pepite(r.amount);
            case GEMME -> Txt.gemme(r.amount);
            case QUANTUM -> Txt.quantum(r.amount);
            case CHIAVE -> {
                Crate c = Crate.byId(r.arg);
                yield (int) r.amount + "x " + c.color + "Chiave " + c.display;
            }
            case SKIN -> {
                PickaxeSkin s = PickaxeSkin.byId(r.arg);
                yield s.rar.color + "Skin " + s.display;
            }
            case BOOSTER -> "<gold>Booster soldi x" + r.arg + " per " + (int) r.amount + " min";
        };
    }

    private ItemStack icon(Player p, Reward r) {
        return switch (r.type) {
            case SOLDI -> Gui.button(p, "gui_soldi", Material.EMERALD).build();
            case PEPITE -> new ItemBuilder(Material.GOLD_NUGGET).model("pepita_oro").build();
            case GEMME -> new ItemBuilder(Material.AMETHYST_SHARD).model("gemma").build();
            case QUANTUM -> Gui.button(p, "quantum", Material.ECHO_SHARD).build();
            case CHIAVE -> new ItemBuilder(Material.TRIPWIRE_HOOK, (int) r.amount).model(Crate.byId(r.arg).keyModel).build();
            case SKIN -> plugin.picks().skinItem(PickaxeSkin.byId(r.arg), 1);
            case BOOSTER -> new ItemBuilder(Material.BLAZE_POWDER).glow().build();
        };
    }

    private void give(Player p, PlayerData d, Reward r) {
        Economy eco = plugin.eco();
        switch (r.type) {
            case SOLDI -> eco.give(d, Economy.Cur.SOLDI, eco.minutes(d, r.amount), "battlepass");
            case PEPITE -> eco.give(d, Economy.Cur.PEPITE, r.amount, "battlepass");
            case GEMME -> eco.give(d, Economy.Cur.GEMME, r.amount, "battlepass");
            case QUANTUM -> eco.give(d, Economy.Cur.QUANTUM, r.amount, "battlepass");
            case CHIAVE -> d.addKeys(r.arg, (int) r.amount);
            case SKIN -> plugin.picks().giveSkin(p, d, PickaxeSkin.byId(r.arg));
            case BOOSTER -> {
                double mult = Double.parseDouble(r.arg);
                long base = Math.max(System.currentTimeMillis(), d.boostUntil);
                if (d.boostUntil < System.currentTimeMillis() || mult > d.boostMult) d.boostMult = mult;
                d.boostUntil = base + (long) (r.amount * 60_000);
            }
        }
        d.dirty = true;
        Txt.send(p, "Battle pass: hai ritirato " + describe(r) + "<gray>.");
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.3f);
    }

    // =====================================================================
    //  Menu a pagine
    // =====================================================================

    public void open(Player p, int page) {
        PlayerData d = plugin.data().get(p);
        sync(d);
        plugin.tutorial().onEvent(p, Tutorial.Ev.PASS, 1);
        int pages = (levels + 8) / 9;
        page = Math.max(0, Math.min(pages - 1, page));
        final int pg = page;
        int lvl = level(d);
        Menu m = new Menu(6, "Battle Pass • " + Txt.esc(name)).emblem(Gui.Emblem.BATTLEPASS);
        double into = lvl >= levels ? 1 : (d.bpXp - lvl * xpLevel) / xpLevel;
        m.set(4, Gui.button(p, "gui_battlepass", Material.NETHER_STAR).name("<gradient:#FFE259:#FFA751><b>" + Txt.esc(name) + "</b></gradient>")
                .lore("<gray>Livello <gold>" + lvl + "</gold><dark_gray>/" + levels,
                        Txt.bar(into, 14, "#FFA726", "#4A4A4A") + " <gray>" + Fmt.num(d.bpXp - lvl * xpLevel) + "/" + Fmt.num(xpLevel) + " XP",
                        "", "<gray>XP: blocchi scavati e missioni.",
                        "<gray>Premium: " + (d.bpPremium ? "<green>attivo ✔" : "<red>no")).build());
        if (page > 0) m.set(0, Gui.prev(p), e -> open(p, pg - 1));
        if (page < pages - 1) m.set(8, Gui.next(p), e -> open(p, pg + 1));
        for (int c = 0; c < 9; c++) {
            int L = page * 9 + c + 1;
            if (L > levels) break;
            boolean reached = lvl >= L;
            m.set(9 + c, new ItemBuilder(reached ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE, Math.min(64, L))
                    .name((reached ? "<green>" : "<gray>") + "Livello " + L).lore(reached ? "<green>Raggiunto" : "<gray>Servono "
                            + Fmt.num(Math.max(0, L * xpLevel - d.bpXp)) + " XP").build());
            Reward fr = free(L), pr = premium(L);
            boolean fGot = d.bpFree.contains(L), pGot = d.bpPrem.contains(L);
            ItemStack fi = icon(p, fr);
            fi.editMeta(meta -> {
                meta.itemName(Txt.item("<white><b>Gratis • Livello " + L));
                meta.lore(Txt.lore(List.of(describe(fr), "", fGot ? "<green>✔ Ritirato" : reached ? "<#FFD54A>▶ Click per ritirare" : "<red>🔒 Bloccato")));
            });
            m.set(18 + c, fi, e -> {
                if (d.bpFree.contains(L) || lvl < L) return;
                d.bpFree.add(L);
                give(p, d, fr);
                open(p, pg);
            });
            ItemStack pi = icon(p, pr);
            pi.editMeta(meta -> {
                meta.itemName(Txt.item("<gradient:#FFE259:#FFA751><b>Premium • Livello " + L + "</b></gradient>"));
                meta.lore(Txt.lore(List.of(describe(pr), "", pGot ? "<green>✔ Ritirato" : !d.bpPremium ? "<red>🔒 Serve il Pass Premium"
                        : reached ? "<#FFD54A>▶ Click per ritirare" : "<red>🔒 Bloccato")));
                meta.setEnchantmentGlintOverride(d.bpPremium && reached && !pGot);
            });
            m.set(27 + c, pi, e -> {
                if (!d.bpPremium || d.bpPrem.contains(L) || lvl < L) return;
                d.bpPrem.add(L);
                give(p, d, pr);
                open(p, pg);
            });
        }
        int[] ds = {36, 37, 38}, ws = {42, 43, 44};
        List<Task> dl = daily(), wl = weekly();
        for (int i = 0; i < dl.size(); i++) m.set(ds[i], taskItem(p, dl.get(i), d.bpDaily, d.bpDailyDone, "Giornaliera"));
        for (int i = 0; i < wl.size(); i++) m.set(ws[i], taskItem(p, wl.get(i), d.bpWeekly, d.bpWeeklyDone, "Settimanale"));
        if (!d.bpPremium) {
            m.set(40, Gui.button(p, "gui_premium", Material.GOLD_BLOCK).name("<gradient:#FFE259:#FFA751><b>Pass Premium</b></gradient>")
                    .lore("<gray>Sblocca la traccia premium:", "<gray>skin esclusive, chiavi Pepita,", "<gray>gemme, Quantum e booster.", "",
                            "<gray>Prezzo: " + Txt.gemme(price), "<#FFD54A>▶ Click per acquistare").glow().build(),
                    e -> plugin.menus().purchase(p, "Pass Premium (" + name + ")", price, () -> d.bpPremium = true, () -> open(p, pg)));
        } else {
            m.set(40, Gui.button(p, "gui_premium", Material.GOLD_BLOCK).name("<gradient:#FFE259:#FFA751><b>Pass Premium attivo</b></gradient>")
                    .lore("<gray>Grazie per il supporto ❤").glow().build());
        }
        m.set(45, Gui.back(p), e -> plugin.menus().main(p));
        m.set(49, new ItemBuilder(Material.PAPER).name("<gray>Pagina <white>" + (page + 1) + "<gray>/" + pages).build());
        m.set(53, Gui.close(p), e -> p.closeInventory());
        m.fill();
        m.open(p);
    }

    private ItemStack taskItem(Player p, Task t, java.util.Map<String, Double> prog, java.util.Set<String> done, String kind) {
        double v = Math.min(t.target, prog.getOrDefault(t.id, 0.0));
        boolean ok = done.contains(t.id);
        return new ItemBuilder(ok ? Material.LIME_DYE : kind.startsWith("G") ? Material.PAPER : Material.BOOK)
                .name((ok ? "<green>" : "<white>") + "<b>Missione " + kind + "</b>")
                .lore("<gray>" + t.describe(), Txt.bar(v / t.target, 12, ok ? "#6BE36B" : "#FFA726", "#4A4A4A") + " <gray>" + Fmt.num(v) + "/" + Fmt.num(t.target),
                        "<gray>Premio: <gold>" + Fmt.num(t.xp) + " XP pass", ok ? "<green>✔ Completata" : "")
                .glow(ok).build();
    }
}

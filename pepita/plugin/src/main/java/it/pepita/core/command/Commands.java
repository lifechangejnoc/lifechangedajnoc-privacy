package it.pepita.core.command;

import it.pepita.core.PepitaCore;
import it.pepita.core.cosmetics.ArmorSkin;
import it.pepita.core.cosmetics.PickaxeSkin;
import it.pepita.core.crate.Crate;
import it.pepita.core.data.DataManager;
import it.pepita.core.data.PlayerData;
import it.pepita.core.mine.Mine;
import it.pepita.core.pickaxe.Enchant;
import it.pepita.core.rank.RankManager;
import it.pepita.core.util.Fmt;
import it.pepita.core.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Tutti i comandi di Pepita. */
public final class Commands implements TabExecutor {
    private final PepitaCore plugin;

    public Commands(PepitaCore plugin) {
        this.plugin = plugin;
    }

    private Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        Txt.send(s, "Comando solo per giocatori.");
        return null;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, String @NotNull [] a) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        if (name.equals("pepitaadmin")) return admin(s, a);
        if (name.equals("gemme") && a.length >= 1 && (a[0].equalsIgnoreCase("dai") || a[0].equalsIgnoreCase("give")) && s.hasPermission("pepita.admin"))
            return admin(s, new String[]{"dai", a.length > 1 ? a[1] : "", "gemme", a.length > 2 ? a[2] : "0"});
        Player p = player(s);
        if (p == null) return true;
        PlayerData d = plugin.data().get(p);
        switch (name) {
            case "menu" -> plugin.menus().main(p);
            case "miniere" -> {
                if (a.length == 0) plugin.menus().mines(p);
                else {
                    Mine m = plugin.mines().get(a[0]);
                    if (m == null) Txt.send(p, "Miniera non trovata. Usa <yellow>/miniere");
                    else plugin.mines().teleport(p, m);
                }
            }
            case "miniera" -> {
                Mine m = a.length > 0 ? plugin.mines().get(a[0]) : plugin.mines().best(d);
                if (m == null) Txt.send(p, "Miniera non trovata.");
                else plugin.mines().teleport(p, m);
            }
            case "spawn" -> plugin.menus().travel(p, "hub");
            case "prigione" -> plugin.menus().travel(p, "prigione");
            case "pvp" -> plugin.menus().travel(p, "pvp");
            case "cella" -> plugin.cells().command(p, a);
            case "selettore" -> plugin.menus().selector(p);
            case "tutorial" -> {
                String sub = a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : "";
                switch (sub) {
                    case "salta", "skip" -> plugin.tutorial().skip(p);
                    case "ricomincia", "restart" -> {
                        plugin.menus().travel(p, "tutorial");
                        plugin.tutorial().start(p);
                    }
                    default -> plugin.menus().travel(p, "tutorial");
                }
            }
            case "battlepass" -> plugin.pass().open(p, 0);
            case "traguardi" -> plugin.milestones().open(p);
            case "quantum" -> Txt.send(p, "Hai " + Txt.quantum(d.quantum) + (d.quantumTasca > 0 ? " <gray>(+" + Txt.quantum(d.quantumTasca)
                    + " <gray>in tasca, da riportare nella zona sicura)" : "") + "<gray>. Resa attuale: <white>" + Fmt.pct(plugin.quantum().resa(d))
                    + "</white>. <gray>Si trovano solo nella <red>Miniera PvP</red> (<yellow>/pvp</yellow>).");
            case "corazza" -> plugin.armor().open(p);
            case "tier" -> plugin.menus().pickaxe(p);
            case "keyall" -> Txt.send(p, "Prossimo <gradient:#D17BFF:#FFD54A><b>Keyall</b></gradient> tra circa <white>" + Fmt.time(plugin.keyAll().secondsToNext())
                    + "</white>: tutti gli online ricevono chiavi!");
            case "rankup" -> plugin.ranks().rankup(p, false);
            case "rankupmax" -> plugin.ranks().rankupMax(p);
            case "prestigio" -> {
                if (a.length > 0 && a[0].equalsIgnoreCase("conferma")) plugin.ranks().prestige(p);
                else {
                    RankManager rm = plugin.ranks();
                    Txt.send(p, "Il prestigio azzera rank e soldi ma ti dà <white>+10% soldi</white> per sempre.");
                    Txt.send(p, "Costo: " + Txt.soldi(rm.prestigeCost(d)) + " <gray>• Rank richiesto: <gold>Z");
                    Txt.raw(p, "<click:run_command:'/prestigio conferma'><green><b>[CLICCA PER CONFERMARE]</b></green></click>");
                }
            }
            case "evasione" -> {
                if (a.length > 0 && a[0].equalsIgnoreCase("conferma")) plugin.ranks().evasione(p);
                else {
                    Txt.send(p, "L'evasione azzera rank, soldi e prestigio ma dà bonus permanenti enormi.");
                    Txt.send(p, "Richiede il <light_purple>Prestigio " + plugin.ranks().evasionePrestige() + "</light_purple>.");
                    Txt.raw(p, "<click:run_command:'/evasione conferma'><red><b>[CLICCA PER EVADERE]</b></red></click>");
                }
            }
            case "piccone" -> {
                plugin.picks().ensureKit(p);
                Txt.send(p, "Ecco il tuo piccone e la tua divisa.");
            }
            case "incantesimi" -> plugin.menus().enchants(p);
            case "soldi" -> {
                PlayerData t = a.length > 0 ? plugin.data().getAny(a[0]) : d;
                if (t == null) Txt.send(p, "Giocatore non trovato.");
                else Txt.send(p, "<white>" + Txt.esc(t.name) + "</white>: " + Txt.soldi(t.soldi) + " <dark_gray>• " + Txt.pepite(t.pepite) + " <dark_gray>• " + Txt.gemme(t.gemme));
            }
            case "pepite" -> {
                if (a.length >= 3 && a[0].equalsIgnoreCase("paga")) pay(p, d, a[1], a[2], true);
                else Txt.send(p, "Hai " + Txt.pepite(d.pepite) + ". <gray>Regalale con <yellow>/pepite paga <giocatore> <quantità>");
            }
            case "gemme" -> Txt.send(p, "Hai " + Txt.gemme(d.gemme) + ". <gray>Spendile nel <yellow>/negozio</yellow>. Acquistale su <white>"
                    + plugin.getConfig().getString("negozio.link", "negozio.pepitamc.it"));
            case "paga" -> {
                if (a.length < 2) Txt.send(p, "Uso: <yellow>/paga <giocatore> <quantità>");
                else pay(p, d, a[0], a[1], false);
            }
            case "top" -> {
                if (a.length == 0) plugin.menus().top(p);
                else {
                    List<DataManager.TopEntry> list = switch (a[0].toLowerCase()) {
                        case "blocchi" -> plugin.data().topBlocchi();
                        case "prestigio" -> plugin.data().topPrestigio();
                        default -> plugin.data().topSoldi();
                    };
                    Txt.raw(p, "<gradient:#FFE259:#FFA751><b>━━ Classifica " + a[0] + " ━━</b></gradient>");
                    for (int i = 0; i < list.size(); i++)
                        Txt.raw(p, "<gold>" + (i + 1) + ".</gold> <white>" + Txt.esc(list.get(i).name()) + " <dark_gray>- <gray>"
                                + (list.get(i).extra().isEmpty() ? Fmt.num(list.get(i).value()) : list.get(i).extra()));
                }
            }
            case "casse" -> plugin.crates().openHub(p);
            case "negozio" -> plugin.menus().shop(p);
            case "skin" -> plugin.menus().skins(p);
            case "giornaliero" -> plugin.menus().daily(p);
            case "booster" -> {
                Txt.send(p, "Booster globale: " + (plugin.boosters().active() ? "<gold>x" + Fmt.num(plugin.boosters().globalMult()) : "<gray>nessuno"));
                Txt.send(p, "Booster personale: " + (d.boostUntil > System.currentTimeMillis() ? "<gold>x" + Fmt.num(d.boostMult) + " <gray>per <white>"
                        + Fmt.time((d.boostUntil - System.currentTimeMillis()) / 1000) : "<gray>nessuno"));
                Txt.send(p, "Moltiplicatore totale: <gold>" + Fmt.mult(plugin.ranks().soldiMult(d)));
            }
            case "fly" -> {
                boolean can = d.ench("volo") > 0 || d.vip >= 2 || p.hasPermission("pepita.fly");
                if (!can) {
                    Txt.send(p, "Ti serve l'incantesimo <white>Volo</white> o il grado <#55E6FF>VIP+</#55E6FF>.");
                    return true;
                }
                d.fly = !d.fly;
                d.dirty = true;
                Txt.send(p, "Volo " + (d.fly ? "<green>attivato</green> (spawn e miniere)" : "<red>disattivato"));
            }
            case "gang" -> plugin.gangs().handle(p, a);
            case "pepita" -> help(p);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void pay(Player p, PlayerData d, String target, String amount, boolean pepite) {
        Player t = Bukkit.getPlayerExact(target);
        if (t == null || t.equals(p)) {
            Txt.send(p, "Giocatore non online.");
            return;
        }
        double v = Fmt.parse(amount);
        if (v <= 0) {
            Txt.send(p, "Quantità non valida.");
            return;
        }
        PlayerData td = plugin.data().get(t);
        it.pepita.core.economy.Economy eco = plugin.eco();
        it.pepita.core.economy.Economy.Cur cur = pepite ? it.pepita.core.economy.Economy.Cur.PEPITE : it.pepita.core.economy.Economy.Cur.SOLDI;
        if (!eco.has(d, cur, v)) {
            Txt.send(p, pepite ? "Non hai abbastanza pepite." : "Non hai abbastanza soldi.");
            return;
        }
        // tassa sui trasferimenti (sink dell'economia): chi riceve ottiene il netto
        double tax = v * eco.payTax();
        double net = v - tax;
        eco.take(d, cur, v, "paga");
        eco.record(cur, tax, "tassa-paga", false);
        if (pepite) td.pepite += net;
        else td.soldi += net;
        td.dirty = true;
        String fv = pepite ? Txt.pepite(v) : Txt.soldi(v), fn = pepite ? Txt.pepite(net) : Txt.soldi(net);
        Txt.send(p, "Hai inviato " + fv + " <gray>a <white>" + t.getName() + "</white> (tassa " + Fmt.pct(eco.payTax()) + ", riceve " + fn + "<gray>)");
        Txt.send(t, "<white>" + p.getName() + "</white> ti ha inviato " + fn);
    }

    private void help(Player p) {
        Txt.raw(p, "<gradient:#FFE259:#FFA751><b>━━━━━━━━━━ PEPITA ━━━━━━━━━━</b></gradient>");
        String[][] cmds = {
                {"/menu", "tutto in un menu"}, {"/miniere", "scegli la miniera"}, {"/miniera", "vai alla tua miniera migliore"},
                {"/rankup  /rankupmax", "sali di rank"}, {"/prestigio", "ricomincia con bonus"}, {"/evasione", "il rebirth di Pepita"},
                {"/incantesimi", "potenzia il piccone"}, {"/casse", "apri le casse"}, {"/negozio", "negozio gemme"},
                {"/skin", "skin di piccone e armatura"}, {"/giornaliero", "premio ogni 24h"}, {"/gang", "crea o entra in una gang"},
                {"/paga  /pepite paga", "dai soldi o pepite (tassa 5%)"}, {"/top", "classifiche"}, {"/fly", "vola (VIP+ o incantesimo)"},
                {"/piccone", "riprendi piccone, bussola e corazza"}, {"/hub  /prigione  /pvp", "viaggia"}, {"/selettore", "menu dei viaggi"},
                {"/cella", "le celle del colosseo"}, {"/battlepass", "missioni e premi"}, {"/traguardi", "premi per i blocchi rotti"},
                {"/corazza", "potenzia la corazza con i Quantum"}, {"/tier", "potenzia il piccone"}, {"/quantum", "i tuoi Quantum"},
                {"/tutorial", "il tutorial di Beppe"}, {"/keyall", "quando arriva il prossimo keyall"}};
        for (String[] c : cmds) Txt.raw(p, "<yellow>" + c[0] + "</yellow> <dark_gray>»</dark_gray> <gray>" + c[1]);
    }

    // ============================ ADMIN ============================

    private boolean admin(CommandSender s, String[] a) {
        if (!s.hasPermission("pepita.admin")) {
            Txt.send(s, "Non hai il permesso.");
            return true;
        }
        if (a.length == 0) {
            Txt.raw(s, "<gradient:#FFE259:#FFA751><b>━━ Pepita Admin ━━</b></gradient>");
            Txt.raw(s, "<yellow>/pa dai <giocatore> <soldi|pepite|gemme|quantum> <n>");
            Txt.raw(s, "<yellow>/pa togli <giocatore> <soldi|pepite|gemme|quantum> <n>");
            Txt.raw(s, "<yellow>/pa chiave <giocatore> <comune|rara|leggendaria|pepita> <n>");
            Txt.raw(s, "<yellow>/pa vip <giocatore> <0-4>   /pa rank <giocatore> <A-Z>   /pa prestigio <giocatore> <n>");
            Txt.raw(s, "<yellow>/pa incantesimo <giocatore> <id> <livello>   /pa skin <giocatore> <id>");
            Txt.raw(s, "<yellow>/pa oggetto <giocatore> <pepita|bomba|grande|nucleare> [n]");
            Txt.raw(s, "<yellow>/pa booster <moltiplicatore> <minuti>   /pa corsa [secondi]");
            Txt.raw(s, "<yellow>/pa reset <miniera|tutte>   /pa ricostruisci <hub|prigione|miniere|pvp|celle|tutti>");
            Txt.raw(s, "<yellow>/pa ologrammi   /pa salva   /pa render [spawn|miniera]   /pa economia   /pa keyall");
            Txt.raw(s, "<yellow>/pa tier <giocatore> <0-7>   /pa corazza <giocatore> <0-3> <livello>   /pa battlepass <giocatore> <xp|premium>");
            Txt.raw(s, "<yellow>/pa tutorial <giocatore> [reset]   /pa cella libera <piano> <settore>   /pa cella reset <piano> <settore>");
            Txt.raw(s, "<yellow>/pa info <giocatore>   /pa reload");
            return true;
        }
        String sub = a[0].toLowerCase();
        switch (sub) {
            case "dai", "togli" -> {
                if (a.length < 4) { Txt.send(s, "Uso: /pa " + sub + " <giocatore> <soldi|pepite|gemme> <n>"); return true; }
                PlayerData t = plugin.data().getAny(a[1]);
                if (t == null) { Txt.send(s, "Giocatore mai entrato: " + a[1]); return true; }
                double v = Fmt.parse(a[3]);
                if (v < 0) { Txt.send(s, "Quantità non valida."); return true; }
                if (sub.equals("togli")) v = -v;
                switch (a[2].toLowerCase()) {
                    case "soldi" -> t.soldi = Math.max(0, t.soldi + v);
                    case "pepite" -> t.pepite = Math.max(0, t.pepite + v);
                    case "gemme" -> t.gemme = Math.max(0, t.gemme + (long) v);
                    case "quantum" -> t.quantum = Math.max(0, t.quantum + v);
                    default -> { Txt.send(s, "Valuta: soldi, pepite, gemme o quantum."); return true; }
                }
                t.dirty = true;
                it.pepita.core.economy.Economy.Cur ec = switch (a[2].toLowerCase()) {
                    case "pepite" -> it.pepita.core.economy.Economy.Cur.PEPITE;
                    case "gemme" -> it.pepita.core.economy.Economy.Cur.GEMME;
                    case "quantum" -> it.pepita.core.economy.Economy.Cur.QUANTUM;
                    default -> it.pepita.core.economy.Economy.Cur.SOLDI;
                };
                plugin.eco().record(ec, Math.abs(v), "admin", v >= 0);
                plugin.data().saveAny(t);
                Txt.send(s, "Fatto: " + a[2] + " " + (v >= 0 ? "+" : "") + Fmt.num(v) + " a " + t.name);
                Player online = Bukkit.getPlayerExact(t.name);
                if (online != null && v > 0 && a[2].equalsIgnoreCase("gemme"))
                    Txt.send(online, "Hai ricevuto " + Txt.gemme(v) + "<gray>! Grazie per il supporto ❤");
                plugin.getLogger().info("[Admin] " + s.getName() + " " + sub + " " + a[2] + " " + v + " a " + t.name);
            }
            case "chiave" -> {
                if (a.length < 4) { Txt.send(s, "Uso: /pa chiave <giocatore> <cassa> <n>"); return true; }
                PlayerData t = plugin.data().getAny(a[1]);
                Crate c = Crate.byId(a[2]);
                if (t == null || c == null) { Txt.send(s, "Giocatore o cassa non validi."); return true; }
                t.addKeys(c.id, (int) Math.max(1, Fmt.parse(a[3])));
                plugin.data().saveAny(t);
                Txt.send(s, "Chiavi date.");
            }
            case "vip" -> {
                if (a.length < 3) return true;
                PlayerData t = plugin.data().getAny(a[1]);
                if (t == null) { Txt.send(s, "Giocatore non trovato."); return true; }
                t.vip = Math.max(0, Math.min(4, (int) Fmt.parse(a[2])));
                t.dirty = true;
                plugin.data().saveAny(t);
                Txt.send(s, "VIP impostato a " + t.vip);
            }
            case "rank" -> {
                if (a.length < 3) return true;
                PlayerData t = plugin.data().getAny(a[1]);
                if (t == null) return true;
                char c = Character.toUpperCase(a[2].charAt(0));
                t.rank = Math.max(0, Math.min(25, c - 'A'));
                plugin.data().saveAny(t);
                Txt.send(s, "Rank impostato a " + RankManager.letter(t.rank));
            }
            case "prestigio" -> {
                if (a.length < 3) return true;
                PlayerData t = plugin.data().getAny(a[1]);
                if (t == null) return true;
                t.prestige = Math.max(0, (int) Fmt.parse(a[2]));
                plugin.data().saveAny(t);
                Txt.send(s, "Prestigio impostato a " + t.prestige);
            }
            case "incantesimo" -> {
                if (a.length < 4) return true;
                PlayerData t = plugin.data().getAny(a[1]);
                Enchant e = Enchant.byId(a[2]);
                if (t == null || e == null) { Txt.send(s, "Giocatore o incantesimo non validi."); return true; }
                t.enchants.put(e.id, Math.max(0, Math.min(e.max, (int) Fmt.parse(a[3]))));
                plugin.data().saveAny(t);
                Player online = Bukkit.getPlayerExact(t.name);
                if (online != null) plugin.picks().refresh(online);
                Txt.send(s, e.display + " impostato.");
            }
            case "skin" -> {
                if (a.length < 3) return true;
                PlayerData t = plugin.data().getAny(a[1]);
                if (t == null) return true;
                Player op = Bukkit.getPlayerExact(t.name);
                if (PickaxeSkin.byIdOrNull(a[2]) != null) plugin.picks().giveSkin(op, t, PickaxeSkin.byIdOrNull(a[2]));
                else if (ArmorSkin.byIdOrNull(a[2]) != null) t.armorSkins.add(a[2].toLowerCase());
                else { Txt.send(s, "Skin non trovata."); return true; }
                plugin.data().saveAny(t);
                Txt.send(s, "Skin data.");
            }
            case "oggetto" -> {
                if (a.length < 3) return true;
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { Txt.send(s, "Giocatore non online."); return true; }
                int n = a.length > 3 ? (int) Math.max(1, Fmt.parse(a[3])) : 1;
                switch (a[2].toLowerCase()) {
                    case "pepita" -> { for (int i = 0; i < n; i++) t.getInventory().addItem(plugin.picks().pepitaOro()); }
                    case "bomba" -> t.getInventory().addItem(plugin.picks().bomb("normale", n));
                    case "grande" -> t.getInventory().addItem(plugin.picks().bomb("grande", n));
                    case "nucleare" -> t.getInventory().addItem(plugin.picks().bomb("nucleare", n));
                    default -> Txt.send(s, "Oggetti: pepita, bomba, grande, nucleare");
                }
            }
            case "booster" -> {
                double m = a.length > 1 ? Fmt.parse(a[1]) : 2;
                long min = a.length > 2 ? (long) Fmt.parse(a[2]) : 60;
                plugin.boosters().start(Math.max(1, m), Math.max(1, min) * 60, s.getName());
            }
            case "corsa" -> plugin.goldRush().start(a.length > 1 ? (int) Math.max(10, Fmt.parse(a[1])) : 300, "<gray>Evento avviato dallo staff!");
            case "reset" -> {
                if (a.length < 2 || a[1].equalsIgnoreCase("tutte")) {
                    plugin.mines().resetAll();
                    Txt.send(s, "Tutte le miniere in coda per il reset.");
                } else {
                    Mine m = plugin.mines().get(a[1]);
                    if (m == null) Txt.send(s, "Miniera non trovata.");
                    else {
                        plugin.mines().reset(m);
                        Txt.send(s, "Miniera resettata.");
                    }
                }
            }
            case "ricostruisci" -> {
                String which = a.length > 1 ? a[1] : "";
                if (which.isEmpty()) {
                    Txt.send(s, "Uso: /pa ricostruisci <hub|prigione|miniere|pvp|celle|tutti>");
                    return true;
                }
                boolean ok = plugin.world().rebuild(which, () -> Txt.send(s, "Ricostruzione di <white>" + which + "</white> completata."));
                Txt.send(s, ok ? "Ricostruzione di <white>" + which + "</white> in coda (i blocchi si piazzano a lotti)." : "Mondo sconosciuto.");
            }
            case "economia" -> {
                for (String line : plugin.eco().report()) Txt.raw(s, line);
            }
            case "keyall" -> plugin.keyAll().fire();
            case "tier" -> {
                if (a.length < 3) return true;
                PlayerData t = plugin.data().getAny(a[1]);
                if (t == null) return true;
                t.pickTier = Math.max(0, Math.min(it.pepita.core.pickaxe.PickTier.values().length - 1, (int) Fmt.parse(a[2])));
                plugin.data().saveAny(t);
                Player online = Bukkit.getPlayerExact(t.name);
                if (online != null) plugin.picks().refresh(online);
                Txt.send(s, "Tier impostato a " + it.pepita.core.pickaxe.PickTier.of(t.pickTier).display);
            }
            case "corazza" -> {
                if (a.length < 4) return true;
                PlayerData t = plugin.data().getAny(a[1]);
                if (t == null) return true;
                int piece = Math.max(0, Math.min(3, (int) Fmt.parse(a[2])));
                t.armorLevels[piece] = Math.max(0, Math.min(plugin.armor().maxLevel(), (int) Fmt.parse(a[3])));
                plugin.data().saveAny(t);
                Player online = Bukkit.getPlayerExact(t.name);
                if (online != null) plugin.picks().applyArmor(online, t);
                Txt.send(s, "Livello della corazza impostato.");
            }
            case "battlepass" -> {
                if (a.length < 3) return true;
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { Txt.send(s, "Giocatore non online."); return true; }
                PlayerData td = plugin.data().get(t);
                if (a[2].equalsIgnoreCase("premium")) {
                    plugin.pass().level(td);
                    td.bpPremium = true;
                    td.dirty = true;
                    Txt.send(s, "Pass premium attivato per " + t.getName());
                } else {
                    plugin.pass().addXp(t, td, Math.max(0, Fmt.parse(a[2])));
                    Txt.send(s, "XP del pass date.");
                }
            }
            case "tutorial" -> {
                if (a.length < 2) return true;
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { Txt.send(s, "Giocatore non online."); return true; }
                plugin.tutorial().start(t);
                Txt.send(s, "Tutorial ricominciato per " + t.getName());
            }
            case "cella" -> {
                if (a.length < 4) { Txt.send(s, "Uso: /pa cella <libera|reset> <piano 1-10> <settore 1-32>"); return true; }
                int f = (int) Fmt.parse(a[2]) - 1, sec = (int) Fmt.parse(a[3]) - 1;
                if (f < 0 || f >= it.pepita.core.world.Layout.CELL_FLOORS || sec < 0 || sec >= it.pepita.core.world.Layout.CELL_SECTORS) {
                    Txt.send(s, "Piano o settore non validi.");
                    return true;
                }
                int id = it.pepita.core.world.CellGeometry.id(f, sec);
                if (a[1].equalsIgnoreCase("libera")) Txt.send(s, plugin.cells().adminRelease(id) ? "Cella liberata." : "La cella era già libera.");
                else plugin.cells().resetCell(id, () -> Txt.send(s, "Cella resettata."));
            }
            case "ologrammi", "npc" -> {
                plugin.holograms().spawnAll();
                Txt.send(s, "Ologrammi, logo e NPC ricreati.");
            }
            case "salva" -> {
                plugin.data().saveAll(true);
                plugin.gangs().save(true);
                Txt.send(s, "Dati salvati.");
            }
            case "render" -> {
                String what = a.length > 1 ? a[1] : "spawn";
                java.io.File out = plugin.renderer().render(what);
                Txt.send(s, "Render salvato in " + out.getPath());
            }
            case "info" -> {
                if (a.length < 2) return true;
                PlayerData t = plugin.data().getAny(a[1]);
                if (t == null) { Txt.send(s, "Giocatore non trovato."); return true; }
                Txt.raw(s, "<gold>" + t.name + "</gold> <gray>rank " + RankManager.tag(t) + " vip " + t.vip
                        + " • " + Txt.soldi(t.soldi) + " • " + Txt.pepite(t.pepite) + " • " + Txt.gemme(t.gemme)
                        + " • " + Txt.quantum(t.quantum) + " • blocchi " + Fmt.num(t.blocchi) + " • piccone lv " + t.pickLevel
                        + " tier " + it.pepita.core.pickaxe.PickTier.of(Math.max(0, t.pickTier)).display + " • gemme spese " + t.gemmeSpese
                        + " • corazza " + java.util.Arrays.toString(t.armorLevels));
            }
            case "reload" -> {
                plugin.reloadConfig();
                plugin.ranks().reload();
                plugin.eco().reload();
                plugin.armor().reload();
                plugin.pass().reload();
                plugin.mines().reloadSettings();
                Txt.send(s, "Config ricaricata (per miniere.yml serve un riavvio).");
            }
            default -> Txt.send(s, "Sottocomando sconosciuto. Usa /pa");
        }
        return true;
    }

    // ============================ TAB ============================

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, String @NotNull [] a) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        switch (name) {
            case "miniere", "miniera" -> {
                if (a.length == 1) for (Mine m : plugin.mines().all()) out.add(m.id);
            }
            case "gang" -> {
                if (a.length == 1) out.addAll(List.of("crea", "invita", "accetta", "esci", "caccia", "chat", "info", "top"));
                else if (a.length == 2) return null;
            }
            case "top" -> {
                if (a.length == 1) out.addAll(List.of("soldi", "blocchi", "prestigio"));
            }
            case "cella" -> {
                if (a.length == 1) out.addAll(List.of("menu", "fidati", "unisci", "dividi", "visita", "reset", "lascia", "info"));
                else if (a.length == 2 && (a[0].equalsIgnoreCase("unisci") || a[0].equalsIgnoreCase("dividi"))) out.addAll(List.of("sinistra", "destra"));
                else if (a.length == 2) return null;
            }
            case "tutorial" -> {
                if (a.length == 1) out.addAll(List.of("salta", "ricomincia"));
            }
            case "paga", "soldi" -> {
                return a.length == 1 ? null : List.of();
            }
            case "pepite" -> {
                if (a.length == 1) out.add("paga");
                else if (a.length == 2) return null;
            }
            case "pepitaadmin" -> {
                if (!s.hasPermission("pepita.admin")) return List.of();
                if (a.length == 1) out.addAll(List.of("dai", "togli", "chiave", "vip", "rank", "prestigio", "incantesimo", "skin", "oggetto",
                        "booster", "corsa", "reset", "ricostruisci", "ologrammi", "salva", "render", "info", "reload", "economia", "keyall",
                        "tier", "corazza", "battlepass", "tutorial", "cella"));
                else if (a.length == 2 && a[0].equalsIgnoreCase("ricostruisci")) out.addAll(List.of("hub", "prigione", "miniere", "pvp", "celle", "tutti"));
                else if (a.length == 2 && a[0].equalsIgnoreCase("cella")) out.addAll(List.of("libera", "reset"));
                else if (a.length == 2 && !List.of("booster", "corsa", "reset", "ricostruisci", "ologrammi", "salva", "render", "reload", "economia", "keyall").contains(a[0].toLowerCase()))
                    return null;
                else if (a.length == 2 && a[0].equalsIgnoreCase("reset")) {
                    out.add("tutte");
                    for (Mine m : plugin.mines().all()) out.add(m.id);
                } else if (a.length == 3) {
                    switch (a[0].toLowerCase()) {
                        case "dai", "togli" -> out.addAll(List.of("soldi", "pepite", "gemme", "quantum"));
                        case "battlepass" -> out.addAll(List.of("premium", "1000"));
                        case "chiave" -> Arrays.stream(Crate.values()).forEach(c -> out.add(c.id));
                        case "incantesimo" -> Arrays.stream(Enchant.values()).forEach(e -> out.add(e.id));
                        case "skin" -> {
                            Arrays.stream(PickaxeSkin.values()).forEach(x -> out.add(x.id));
                            Arrays.stream(ArmorSkin.values()).forEach(x -> out.add(x.id));
                        }
                        case "oggetto" -> out.addAll(List.of("pepita", "bomba", "grande", "nucleare"));
                        default -> {
                        }
                    }
                }
            }
            default -> {
            }
        }
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase();
        return out.stream().filter(x -> x.toLowerCase().startsWith(last)).collect(Collectors.toList());
    }
}

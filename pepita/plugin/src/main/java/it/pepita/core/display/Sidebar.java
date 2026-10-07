package it.pepita.core.display;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import it.pepita.core.PepitaCore;
import it.pepita.core.data.PlayerData;
import it.pepita.core.gang.GangManager;
import it.pepita.core.pickaxe.Enchant;
import it.pepita.core.pickaxe.MiningService;
import it.pepita.core.pickaxe.PickTier;
import it.pepita.core.rank.RankManager;
import it.pepita.core.rank.VipTier;
import it.pepita.core.util.Fmt;
import it.pepita.core.util.Txt;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Scoreboard laterale, tab (logo + incantesimi attivi del piccone in mano) e nomi in lista. */
public final class Sidebar {
    private static final String[] ENTRIES = {"§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7", "§8", "§9", "§a", "§b", "§c", "§d", "§e"};
    private final PepitaCore plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, Integer> lineCount = new HashMap<>();
    private final String ip;

    public Sidebar(PepitaCore plugin) {
        this.plugin = plugin;
        this.ip = plugin.getConfig().getString("server.ip-visualizzato", "pepitamc.it");
    }

    public void create(Player p) {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective o = sb.registerNewObjective("pepita", Criteria.DUMMY, Txt.mm("<gradient:#FFE259:#FFA751><b>⛏ PEPITA ⛏</b></gradient>"));
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        o.numberFormat(NumberFormat.blank());
        p.setScoreboard(sb);
        boards.put(p.getUniqueId(), sb);
        update(p);
    }

    public void remove(Player p) {
        boards.remove(p.getUniqueId());
        lineCount.remove(p.getUniqueId());
    }

    public void update(Player p) {
        Scoreboard sb = boards.get(p.getUniqueId());
        if (sb == null) return;
        Objective o = sb.getObjective("pepita");
        if (o == null) return;
        PlayerData d = plugin.data().get(p);
        boolean pk = d.hasPack;
        RankManager rm = plugin.ranks();
        if (plugin.lobby().in(p)) {
            write(p, sb, o, lobbyLines(p, d));
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add(" ");
        if (d.rank >= RankManager.MAX_RANK) {
            lines.add("<gray>Rank <gold><b>Z</b></gold> <dark_gray>» <light_purple>/prestigio <dark_gray>(<yellow>" + Fmt.pct(rm.progress(d)) + "<dark_gray>)");
        } else {
            lines.add("<gray>Rank " + RankManager.tag(d) + " <dark_gray>» <" + RankManager.color(d.rank + 1) + ">"
                    + RankManager.letter(d.rank + 1) + " <dark_gray>(<yellow>" + Fmt.pct(rm.progress(d)) + "<dark_gray>)");
        }
        lines.add("<gray>Prestigio <#D17BFF>" + d.prestige + "</#D17BFF> <dark_gray>• <gray>Evasioni <#FF5555>" + d.evasioni);
        VipTier vt = VipTier.of(d.vip);
        if (vt != VipTier.NESSUNO) lines.add("<gray>Grado " + vt.tag);
        lines.add("  ");
        lines.add(Txt.icon(pk, "soldi") + " <gray>Soldi <white>" + Fmt.num(d.soldi));
        lines.add(Txt.icon(pk, "pepite") + " <gray>Pepite <white>" + Fmt.num(d.pepite));
        lines.add(Txt.icon(pk, "gemme") + " <gray>Gemme <white>" + Fmt.num(d.gemme));
        lines.add(Txt.icon(pk, "quantum") + " <gray>Quantum <white>" + Fmt.num(d.quantum)
                + (d.quantumTasca > 0 ? " <" + Txt.QUANTUM + ">+" + Fmt.num(d.quantumTasca) + "</" + Txt.QUANTUM + "> <dark_gray>in tasca" : ""));
        lines.add("   ");
        lines.add(Txt.icon(pk, "piccone") + " <gray>Blocchi <white>" + Fmt.num(d.blocchi) + " <dark_gray>• " + PickTier.of(Math.max(0, d.pickTier)).label());
        lines.add("<gold>✖</gold> <gray>Moltiplicatore <gold>" + Fmt.mult(rm.soldiMult(d)));
        GangManager.Gang g = plugin.gangs().get(d.gang);
        if (g != null) lines.add("<#FF7B7B>⚑</#FF7B7B> <gray>Gang <#FF7B7B>" + g.name);
        if (plugin.goldRush().active()) lines.add("<gold>☀ Corsa all'Oro <white>" + Fmt.time(plugin.goldRush().secondsLeft()));
        else if (d.boostUntil > System.currentTimeMillis())
            lines.add("<gold>⚡ Booster x" + Fmt.num(d.boostMult) + " <white>" + Fmt.time((d.boostUntil - System.currentTimeMillis()) / 1000));
        lines.add("    ");
        lines.add("<gradient:#FFE259:#FFA751>" + ip + "</gradient>");
        write(p, sb, o, lines);
    }

    /** Scoreboard della lobby: niente del prison, solo le modalità e il server. */
    private List<String> lobbyLines(Player p, PlayerData d) {
        List<String> lines = new ArrayList<>();
        lines.add(" ");
        lines.add("<gray>Ciao, <white>" + Txt.esc(p.getName()));
        VipTier vt = VipTier.of(d.vip);
        lines.add("<gray>Grado " + (vt != VipTier.NESSUNO ? vt.tag : "<white>Giocatore"));
        lines.add("  ");
        lines.add("<gold><b>Modalità</b></gold>");
        lines.add("<gray>⛓ Prison <dark_gray>» <white>" + plugin.lobby().prisonPlayers() + " <gray>in gioco");
        lines.add("   ");
        lines.add("<gray>Online <white>" + Bukkit.getOnlinePlayers().size());
        lines.add("<yellow>Bussola</yellow> <gray>per giocare");
        lines.add("    ");
        lines.add("<gradient:#FFE259:#FFA751>" + ip + "</gradient>");
        return lines;
    }

    private void write(Player p, Scoreboard sb, Objective o, List<String> lines) {
        if (lines.size() > ENTRIES.length) lines = lines.subList(0, ENTRIES.length);

        int n = lines.size();
        for (int i = 0; i < n; i++) {
            var score = o.getScore(ENTRIES[i]);
            score.setScore(n - i);
            score.customName(Txt.mm(lines.get(i)));
        }
        int prev = lineCount.getOrDefault(p.getUniqueId(), 0);
        for (int i = n; i < prev; i++) sb.resetScores(ENTRIES[i]);
        lineCount.put(p.getUniqueId(), n);
    }

    /** Incantesimi a probabilità del piccone in mano, con la % di attivazione per blocco. */
    private List<String> enchantLines(PlayerData d) {
        List<String> parts = new ArrayList<>();
        for (Enchant e : Enchant.values()) {
            if (e.cat == Enchant.Cat.UTILITA || e == Enchant.FRANTUMAZIONE || e == Enchant.RISONANZA) continue;
            int l = d.active(e.id);
            if (l <= 0) continue;
            double c = MiningService.chance(d, e);
            if (c <= 0) continue;
            String col = e.cat == Enchant.Cat.QUANTUM ? Txt.QUANTUM : e.cat == Enchant.Cat.AREA ? "#FF7B7B" : e.cat == Enchant.Cat.SPECIALE ? "#D17BFF" : "#6BE36B";
            parts.add("<" + col + ">" + e.display + "</" + col + "> <white>" + Fmt.num(l) + " <dark_gray>• <gray>" + pct(c));
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < parts.size(); i += 2)
            out.add(parts.get(i) + (i + 1 < parts.size() ? "   <dark_gray>|</dark_gray>   " + parts.get(i + 1) : ""));
        return out;
    }

    private static String pct(double c) {
        if (c >= 0.01) return Fmt.pct(c);
        return String.format(java.util.Locale.US, "%.3f%%", c * 100);
    }

    public void updateTab(Player p) {
        PlayerData d = plugin.data().get(p);
        String logo = d.hasPack ? "<white><font:pepita:logo></font></white>" : "<gradient:#FFE259:#FFA751><b>⛏  P E P I T A  ⛏</b></gradient>";
        if (plugin.lobby().in(p)) {
            updateLobbyTab(p, d, logo);
            return;
        }
        // il logo del pack è alto 40px con ascent 34: servono righe vuote sopra perché non venga tagliato
        Component header = Txt.mm((d.hasPack ? "\n\n\n\n" : "\n") + logo + "\n" + (d.hasPack ? "\n" : "") + "<gray>Il prison della <gold>Corsa all'Oro</gold>\n");
        StringBuilder f = new StringBuilder("\n");
        if (plugin.picks().holdingPickaxe(p)) {
            List<String> en = enchantLines(d);
            f.append("<gold><b>Incantesimi attivi</b></gold> <dark_gray>(probabilità per blocco");
            double res = MiningService.resonance(d);
            if (res > 1) f.append(", Risonanza <" + Txt.QUANTUM + ">+").append(Fmt.pct(res - 1)).append("</" + Txt.QUANTUM + ">");
            f.append(")\n");
            if (en.isEmpty()) f.append("<dark_gray>nessuno: compra gli incantesimi col tasto destro\n");
            for (String s : en) f.append(s).append("\n");
            f.append("\n");
        }
        f.append(Txt.icon(d.hasPack, "soldi")).append(" <white>").append(Fmt.num(d.soldi)).append("  ")
                .append(Txt.icon(d.hasPack, "pepite")).append(" <white>").append(Fmt.num(d.pepite)).append("  ")
                .append(Txt.icon(d.hasPack, "gemme")).append(" <white>").append(Fmt.num(d.gemme)).append("  ")
                .append(Txt.icon(d.hasPack, "quantum")).append(" <white>").append(Fmt.num(d.quantum)).append("\n");
        List<String> boost = new ArrayList<>();
        if (plugin.boosters().active()) boost.add("<gold>Globale x" + Fmt.num(plugin.boosters().globalMult()));
        if (d.boostUntil > System.currentTimeMillis())
            boost.add("<gold>Personale x" + Fmt.num(d.boostMult) + " <white>" + Fmt.time((d.boostUntil - System.currentTimeMillis()) / 1000));
        if (plugin.goldRush().active()) boost.add("<gold>☀ Corsa all'Oro x2 <white>" + Fmt.time(plugin.goldRush().secondsLeft()));
        f.append("<gray>Booster: ").append(boost.isEmpty() ? "<dark_gray>nessuno" : String.join(" <dark_gray>• ", boost)).append("\n");
        f.append("<gray>Prossima Corsa all'Oro <white>").append(Fmt.time(plugin.goldRush().secondsToNext()))
                .append(" <dark_gray>• <gray>Keyall <white>").append(Fmt.time(plugin.keyAll().secondsToNext())).append("\n");
        f.append("<gray>Online <white>").append(Bukkit.getOnlinePlayers().size()).append(" <dark_gray>• <gray>Ping <white>").append(p.getPing())
                .append("ms\n<gradient:#FFE259:#FFA751>").append(ip).append("</gradient>\n");
        p.sendPlayerListHeaderAndFooter(header, Txt.mm(f.toString()));
        VipTier vt = VipTier.of(d.vip);
        p.playerListName(Txt.mm("<dark_gray>[</dark_gray>" + RankManager.tag(d) + "<dark_gray>]</dark_gray> "
                + (vt != VipTier.NESSUNO ? vt.tag + " " : "") + "<white>" + Txt.esc(p.getName())));
    }

    /** Tab della lobby: logo, modalità e server, senza nulla del prison. */
    private void updateLobbyTab(Player p, PlayerData d, String logo) {
        // il logo del pack è alto 40px con ascent 34: servono righe vuote sopra perché non venga tagliato
        Component header = Txt.mm((d.hasPack ? "\n\n\n\n" : "\n") + logo + "\n" + (d.hasPack ? "\n" : "") + "<gray>Lobby\n");
        String f = "\n<gold><b>Modalità</b></gold>\n<gray>⛓ Prison <dark_gray>» <white>" + plugin.lobby().prisonPlayers() + " <gray>in gioco\n\n"
                + "<gray>Usa la <yellow>bussola</yellow> per scegliere a cosa giocare\n\n"
                + "<gray>Online <white>" + Bukkit.getOnlinePlayers().size() + " <dark_gray>• <gray>Ping <white>" + p.getPing()
                + "ms\n<gradient:#FFE259:#FFA751>" + ip + "</gradient>\n";
        p.sendPlayerListHeaderAndFooter(header, Txt.mm(f));
        VipTier vt = VipTier.of(d.vip);
        p.playerListName(Txt.mm("<dark_gray>[<gray>Lobby</gray>]</dark_gray> " + (vt != VipTier.NESSUNO ? vt.tag + " " : "")
                + "<white>" + Txt.esc(p.getName())));
    }
}

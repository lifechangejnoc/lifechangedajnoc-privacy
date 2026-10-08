package it.pepita.lobby;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import it.pepita.lobby.util.Txt;
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

/** Scoreboard laterale e tab della lobby: modalità, giocatori della rete e indirizzo del server. */
public final class Board {
    private static final String[] ENTRIES = {"§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7", "§8", "§9", "§a", "§b", "§c", "§d", "§e"};
    private final PepitaLobby plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, Integer> lineCount = new HashMap<>();

    public Board(PepitaLobby plugin) {
        this.plugin = plugin;
    }

    private String ip() {
        return plugin.getConfig().getString("server.ip-visualizzato", "pepitamc.it");
    }

    public void create(Player p) {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective o = sb.registerNewObjective("lobby", Criteria.DUMMY, Txt.mm("<gradient:#FFE259:#FFA751><b>⛏ PEPITA ⛏</b></gradient>"));
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

    public void updateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) update(p);
    }

    public void update(Player p) {
        Scoreboard sb = boards.get(p.getUniqueId());
        Objective o = sb == null ? null : sb.getObjective("lobby");
        if (o != null) {
            List<String> lines = new ArrayList<>();
            lines.add(" ");
            lines.add("<gray>Ciao, <white>" + Txt.esc(p.getName()));
            lines.add("  ");
            lines.add("<gold><b>Modalità</b></gold>");
            for (Modes.Mode m : plugin.modes().all()) {
                String name = Txt.plain(Txt.mm(m.name())).replace("⛓", "").trim();
                int n = plugin.network().count(m.server());
                lines.add("<gray>" + Txt.esc(name) + " <dark_gray>» " + (m.ready() ? "<white>" + (n < 0 ? "?" : n) + " <gray>in gioco" : "<dark_gray>in arrivo"));
            }
            lines.add("   ");
            lines.add("<gray>Online <white>" + plugin.network().total());
            lines.add("<yellow>Bussola</yellow> <gray>per giocare");
            lines.add("    ");
            lines.add("<gradient:#FFE259:#FFA751>" + ip() + "</gradient>");
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
        updateTab(p);
    }

    private void updateTab(Player p) {
        boolean pack = plugin.hasPack(p);
        String logo = pack ? "<white><font:pepita:logo></font></white>" : "<gradient:#FFE259:#FFA751><b>⛏  P E P I T A  ⛏</b></gradient>";
        // il logo del pack è alto 40px con ascent 34: servono righe vuote sopra perché non venga tagliato
        Component header = Txt.mm((pack ? "\n\n\n\n" : "\n") + logo + "\n" + (pack ? "\n" : "") + "<gray>Lobby\n");
        StringBuilder f = new StringBuilder("\n<gold><b>Modalità</b></gold>\n");
        for (Modes.Mode m : plugin.modes().all()) {
            int n = plugin.network().count(m.server());
            f.append(m.name()).append(" <dark_gray>» ").append(m.ready() ? "<white>" + (n < 0 ? "?" : n) + " <gray>in gioco" : "<dark_gray>in arrivo").append("\n");
        }
        f.append("\n<gray>Usa la <yellow>bussola</yellow> per scegliere a cosa giocare\n\n")
                .append("<gray>Online <white>").append(plugin.network().total()).append(" <dark_gray>• <gray>Ping <white>").append(p.getPing())
                .append("ms\n<gradient:#FFE259:#FFA751>").append(ip()).append("</gradient>\n");
        p.sendPlayerListHeaderAndFooter(header, Txt.mm(f.toString()));
        p.playerListName(Txt.mm("<dark_gray>[<gray>Lobby</gray>]</dark_gray> <white>" + Txt.esc(p.getName())));
    }
}

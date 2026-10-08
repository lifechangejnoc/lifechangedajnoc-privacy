package it.pepita.lobby.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/** Helper per i testi (MiniMessage) con lo stile di Pepita. */
public final class Txt {
    private Txt() {}

    public static final MiniMessage MM = MiniMessage.miniMessage();

    public static final String GOLD = "#FFD54A";
    public static final String ORANGE = "#FFA726";
    public static final String SOLDI = "#6BE36B";
    public static final String PEPITE = "#FFD54A";
    public static final String GEMME = "#D17BFF";
    public static final String QUANTUM = "#3FE0F0";

    public static final String LOGO = "<gradient:#FFE259:#FFA751><b>PEPITA</b></gradient>";
    public static final String PREFIX = LOGO + " <dark_gray>»</dark_gray> <gray>";

    public static final String S_SOLDI = "<" + SOLDI + ">$</" + SOLDI + ">";
    public static final String S_PEPITE = "<" + PEPITE + ">✦</" + PEPITE + ">";
    public static final String S_GEMME = "<" + GEMME + ">◆</" + GEMME + ">";
    public static final String S_QUANTUM = "<" + QUANTUM + ">⚛</" + QUANTUM + ">";

    public static Component mm(String s) {
        return MM.deserialize(s);
    }

    /** Testo per nomi e lore degli oggetti (senza corsivo). */
    public static Component item(String s) {
        return MM.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    public static List<Component> lore(List<String> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String l : lines) out.add(item(l));
        return out;
    }

    public static void send(CommandSender s, String msg) {
        s.sendMessage(mm(PREFIX + msg));
    }

    public static void raw(CommandSender s, String msg) {
        s.sendMessage(mm(msg));
    }

    public static String esc(String s) {
        return MM.escapeTags(s);
    }

    public static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    /**
     * Icona 8x8 del font pepita:icons (solo con il resource pack, glifo bianco), altrimenti il simbolo testuale.
     * nomi: soldi, pepite, gemme, quantum, chiave, lucky, battlepass, spada, piccone, stella
     */
    public static String icon(boolean pack, String name) {
        if (pack) {
            int i = switch (name) {
                case "soldi" -> 0;
                case "pepite" -> 1;
                case "gemme" -> 2;
                case "quantum" -> 3;
                case "chiave" -> 4;
                case "lucky" -> 5;
                case "battlepass" -> 6;
                case "spada" -> 7;
                case "piccone" -> 8;
                default -> 9;
            };
            return "<white><font:pepita:icons>" + (char) (0xE100 + i) + "</font></white>";
        }
        return switch (name) {
            case "soldi" -> S_SOLDI;
            case "pepite" -> S_PEPITE;
            case "gemme" -> S_GEMME;
            case "quantum" -> S_QUANTUM;
            case "chiave" -> "<#FFD54A>🗝</#FFD54A>";
            case "lucky" -> "<#FFD54A>?</#FFD54A>";
            case "battlepass" -> "<#FFA726>✪</#FFA726>";
            case "spada" -> "<#FF5555>⚔</#FF5555>";
            case "piccone" -> "<#55E6FF>⛏</#55E6FF>";
            default -> "<#FFD54A>★</#FFD54A>";
        };
    }

    /** Barra di avanzamento testuale. */
    public static String bar(double pct, int len, String full, String empty) {
        pct = Math.max(0, Math.min(1, pct));
        int f = (int) Math.round(pct * len);
        StringBuilder sb = new StringBuilder("<" + full + ">");
        sb.append("■".repeat(f)).append("</" + full + "><" + empty + ">");
        sb.append("■".repeat(len - f)).append("</" + empty + ">");
        return sb.toString();
    }
}

package it.pepita.lobby;

import it.pepita.lobby.util.Txt;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /lobby, /modalita, /prigione, /fly e i comandi staff /pepitalobby. */
public final class LobbyCommands implements CommandExecutor, TabCompleter {
    private final PepitaLobby plugin;

    public LobbyCommands(PepitaLobby plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, String @NotNull [] a) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        if (name.equals("pepitalobby")) return admin(s, a);
        if (!(s instanceof Player p)) {
            Txt.send(s, "Solo in gioco.");
            return true;
        }
        switch (name) {
            case "lobby" -> {
                p.teleport(plugin.spawn());
                plugin.prepare(p);
            }
            case "modalita" -> plugin.modes().open(p);
            case "prigione" -> {
                Modes.Mode m = plugin.modes().get("prison");
                if (m == null) m = plugin.modes().first();
                if (m == null) Txt.send(p, "Nessuna modalità disponibile.");
                else plugin.modes().send(p, m);
            }
            case "fly" -> {
                boolean on = !plugin.flying().remove(p.getUniqueId());
                if (on) plugin.flying().add(p.getUniqueId());
                p.setAllowFlight(on);
                if (!on) p.setFlying(false);
                Txt.send(p, on ? "Volo <green>attivo</green>." : "Volo <red>spento</red>.");
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean admin(CommandSender s, String[] a) {
        if (!s.hasPermission("pepita.admin")) {
            Txt.send(s, "Non hai il permesso.");
            return true;
        }
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "spawn" -> {
                if (!(s instanceof Player p)) return true;
                plugin.setSpawn(p.getLocation());
                Txt.send(s, "Spawn della lobby impostato qui.");
            }
            case "npc" -> {
                if (!(s instanceof Player p)) return true;
                Modes.Mode m = plugin.modes().get(a.length > 1 ? a[1] : "prison");
                if (m == null) {
                    Txt.send(s, "Modalità sconosciuta. Quelle in config: " + plugin.modes().all().stream().map(Modes.Mode::id).toList());
                    return true;
                }
                plugin.modes().setNpc(m, p.getLocation());
                plugin.reload();
                Txt.send(s, "NPC della modalità <white>" + m.id() + "</white> spostato qui.");
            }
            case "reload" -> {
                plugin.reload();
                Txt.send(s, "Config ricaricata.");
            }
            case "info" -> {
                var sp = plugin.spawn();
                Txt.raw(s, "<gold>Lobby</gold> <gray>spawn <white>" + sp.getWorld().getName() + " " + sp.getX() + " " + sp.getY() + " " + sp.getZ()
                        + "</white> • online in rete <white>" + plugin.network().total());
                for (Modes.Mode m : plugin.modes().all())
                    Txt.raw(s, "<gray> - <white>" + m.id() + "</white> → server <white>" + m.server() + "</white> ("
                            + (m.ready() ? plugin.network().count(m.server()) + " in gioco" : "in arrivo") + ")");
            }
            default -> {
                Txt.raw(s, "<gradient:#FFE259:#FFA751><b>━━ Pepita Lobby ━━</b></gradient>");
                Txt.raw(s, "<yellow>/pepitalobby spawn</yellow> <gray>imposta lo spawn dove sei");
                Txt.raw(s, "<yellow>/pepitalobby npc [modalità]</yellow> <gray>sposta l'NPC della modalità dove sei");
                Txt.raw(s, "<yellow>/pepitalobby info</yellow> <gray>spawn, modalità e giocatori");
                Txt.raw(s, "<yellow>/pepitalobby reload</yellow> <gray>ricarica config.yml");
                Txt.raw(s, "<gray>Per costruire: <white>/gamemode creative</white> (lo staff in creativa può toccare tutto).");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, String @NotNull [] a) {
        List<String> out = new ArrayList<>();
        if (cmd.getName().equalsIgnoreCase("pepitalobby") && s.hasPermission("pepita.admin")) {
            if (a.length == 1) out.addAll(List.of("spawn", "npc", "info", "reload"));
            else if (a.length == 2 && a[0].equalsIgnoreCase("npc")) plugin.modes().all().forEach(m -> out.add(m.id()));
        }
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}

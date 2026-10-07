package it.pepita.core.gang;

import it.pepita.core.PepitaCore;
import it.pepita.core.data.PlayerData;
import it.pepita.core.util.Fmt;
import it.pepita.core.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Gang: gruppi di detenuti con tag in chat e classifica per blocchi scavati. */
public final class GangManager {
    public static final class Gang {
        public String name;
        public UUID leader;
        public final Set<UUID> members = new LinkedHashSet<>();
        public final Map<UUID, String> names = new HashMap<>();
        public long blocchi;
        public long created;
    }

    private final PepitaCore plugin;
    private final File file;
    private final Map<String, Gang> gangs = new HashMap<>();
    private final Map<UUID, String> invites = new HashMap<>();
    private boolean dirty;

    public GangManager(PepitaCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "gang.yml");
        load();
    }

    public Gang get(String name) {
        return name == null ? null : gangs.get(name.toLowerCase());
    }

    public void addBlocks(String gang, long n) {
        Gang g = get(gang);
        if (g != null) {
            g.blocchi += n;
            dirty = true;
        }
    }

    public List<Gang> top() {
        List<Gang> l = new ArrayList<>(gangs.values());
        l.sort(Comparator.comparingLong((Gang g) -> g.blocchi).reversed());
        return l;
    }

    public void handle(Player p, String[] a) {
        PlayerData d = plugin.data().get(p);
        String sub = a.length == 0 ? "info" : a[0].toLowerCase();
        switch (sub) {
            case "crea" -> {
                if (a.length < 2) { Txt.send(p, "Uso: <yellow>/gang crea <nome>"); return; }
                if (d.gang != null) { Txt.send(p, "Sei già in una gang. Esci prima con <yellow>/gang esci"); return; }
                String name = a[1];
                if (!name.matches("[A-Za-z0-9_]{3,10}")) { Txt.send(p, "Il nome deve avere 3-10 caratteri (lettere, numeri, _)."); return; }
                if (get(name) != null) { Txt.send(p, "Esiste già una gang con quel nome."); return; }
                double cost = plugin.getConfig().getDouble("gang.costo-creazione", 25000);
                if (d.soldi < cost) { Txt.send(p, "Creare una gang costa " + Txt.soldi(cost) + "."); return; }
                d.soldi -= cost;
                Gang g = new Gang();
                g.name = name;
                g.leader = p.getUniqueId();
                g.members.add(p.getUniqueId());
                g.names.put(p.getUniqueId(), p.getName());
                g.created = System.currentTimeMillis();
                gangs.put(name.toLowerCase(), g);
                d.gang = name;
                d.dirty = true;
                dirty = true;
                Bukkit.broadcast(Txt.mm(Txt.PREFIX + "<white>" + Txt.esc(p.getName()) + "</white> ha fondato la gang <#FF7B7B>" + name + "</#FF7B7B>!"));
            }
            case "invita" -> {
                Gang g = get(d.gang);
                if (g == null || !g.leader.equals(p.getUniqueId())) { Txt.send(p, "Solo il capo della gang può invitare."); return; }
                if (a.length < 2) { Txt.send(p, "Uso: <yellow>/gang invita <giocatore>"); return; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { Txt.send(p, "Giocatore non online."); return; }
                if (g.members.size() >= plugin.getConfig().getInt("gang.max-membri", 8)) { Txt.send(p, "La gang è piena."); return; }
                invites.put(t.getUniqueId(), g.name.toLowerCase());
                Txt.send(p, "Invito inviato a <white>" + t.getName());
                Txt.send(t, "<white>" + Txt.esc(p.getName()) + "</white> ti invita nella gang <#FF7B7B>" + g.name
                        + "</#FF7B7B>. <click:run_command:'/gang accetta'><green><b>[ACCETTA]</b></green></click>");
            }
            case "accetta" -> {
                String gn = invites.remove(p.getUniqueId());
                Gang g = get(gn);
                if (g == null) { Txt.send(p, "Non hai inviti."); return; }
                if (d.gang != null) { Txt.send(p, "Sei già in una gang."); return; }
                g.members.add(p.getUniqueId());
                g.names.put(p.getUniqueId(), p.getName());
                d.gang = g.name;
                d.dirty = true;
                dirty = true;
                broadcast(g, "<white>" + Txt.esc(p.getName()) + "</white> è entrato nella gang!");
            }
            case "esci" -> {
                Gang g = get(d.gang);
                if (g == null) { Txt.send(p, "Non sei in una gang."); return; }
                leave(g, p.getUniqueId());
                d.gang = null;
                d.dirty = true;
                Txt.send(p, "Hai lasciato la gang.");
            }
            case "caccia" -> {
                Gang g = get(d.gang);
                if (g == null || !g.leader.equals(p.getUniqueId())) { Txt.send(p, "Solo il capo può cacciare."); return; }
                if (a.length < 2) { Txt.send(p, "Uso: <yellow>/gang caccia <giocatore>"); return; }
                UUID target = null;
                for (Map.Entry<UUID, String> en : g.names.entrySet()) if (en.getValue().equalsIgnoreCase(a[1])) target = en.getKey();
                if (target == null || target.equals(g.leader)) { Txt.send(p, "Membro non trovato."); return; }
                leave(g, target);
                PlayerData td = plugin.data().cached(target);
                if (td != null) td.gang = null;
                else {
                    PlayerData off = plugin.data().load(target, a[1]);
                    off.gang = null;
                    plugin.data().save(off, true);
                }
                broadcast(g, "<white>" + Txt.esc(a[1]) + "</white> è stato cacciato.");
            }
            case "chat", "c" -> {
                Gang g = get(d.gang);
                if (g == null) { Txt.send(p, "Non sei in una gang."); return; }
                String msg = String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length));
                for (UUID u : g.members) {
                    Player o = Bukkit.getPlayer(u);
                    if (o != null) Txt.raw(o, "<#FF7B7B>[Gang]</#FF7B7B> <white>" + Txt.esc(p.getName()) + "</white><gray>: " + Txt.esc(msg));
                }
            }
            case "top" -> {
                Txt.raw(p, "<gradient:#FFE259:#FFA751><b>━━ Top Gang ━━</b></gradient>");
                List<Gang> t = top();
                for (int i = 0; i < Math.min(10, t.size()); i++)
                    Txt.raw(p, "<gold>" + (i + 1) + ".</gold> <#FF7B7B>" + t.get(i).name + "</#FF7B7B> <gray>— <white>" + Fmt.num(t.get(i).blocchi) + " blocchi");
                if (t.isEmpty()) Txt.raw(p, "<gray>Nessuna gang. Fondane una con <yellow>/gang crea <nome>");
            }
            default -> {
                Gang g = a.length >= 2 && sub.equals("info") ? get(a[1]) : get(d.gang);
                if (g == null) {
                    Txt.raw(p, "<gradient:#FFE259:#FFA751><b>━━ Gang ━━</b></gradient>");
                    Txt.raw(p, "<yellow>/gang crea <nome></yellow> <gray>— fonda una gang (" + Txt.soldi(plugin.getConfig().getDouble("gang.costo-creazione", 25000)) + "<gray>)");
                    Txt.raw(p, "<yellow>/gang invita <giocatore></yellow> <gray>— invita");
                    Txt.raw(p, "<yellow>/gang accetta</yellow> <gray>— accetta un invito");
                    Txt.raw(p, "<yellow>/gang chat <msg></yellow> <gray>— chat della gang");
                    Txt.raw(p, "<yellow>/gang esci</yellow>, <yellow>/gang caccia</yellow>, <yellow>/gang top");
                    return;
                }
                Txt.raw(p, "<gradient:#FFE259:#FFA751><b>━━ Gang " + g.name + " ━━</b></gradient>");
                Txt.raw(p, "<gray>Capo: <white>" + g.names.getOrDefault(g.leader, "?"));
                Txt.raw(p, "<gray>Membri (" + g.members.size() + "): <white>" + String.join(", ", g.names.values()));
                Txt.raw(p, "<gray>Blocchi scavati: <white>" + Fmt.num(g.blocchi));
            }
        }
    }

    private void leave(Gang g, UUID u) {
        g.members.remove(u);
        g.names.remove(u);
        dirty = true;
        if (g.members.isEmpty()) {
            gangs.remove(g.name.toLowerCase());
        } else if (g.leader.equals(u)) {
            g.leader = g.members.iterator().next();
            broadcast(g, "Il nuovo capo è <white>" + g.names.get(g.leader));
        }
    }

    private void broadcast(Gang g, String msg) {
        for (UUID u : g.members) {
            Player o = Bukkit.getPlayer(u);
            if (o != null) Txt.send(o, "<#FF7B7B>[" + g.name + "]</#FF7B7B> " + msg);
        }
    }

    public void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String k : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(k);
            if (s == null) continue;
            Gang g = new Gang();
            g.name = s.getString("nome", k);
            try {
                g.leader = UUID.fromString(s.getString("capo", ""));
            } catch (Exception e) {
                continue;
            }
            ConfigurationSection mem = s.getConfigurationSection("membri");
            if (mem != null) for (String u : mem.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(u);
                    g.members.add(id);
                    g.names.put(id, mem.getString(u, "?"));
                } catch (Exception ignored) {
                }
            }
            g.blocchi = s.getLong("blocchi");
            g.created = s.getLong("creata");
            gangs.put(g.name.toLowerCase(), g);
        }
    }

    public void save(boolean force) {
        if (!dirty && !force) return;
        YamlConfiguration y = new YamlConfiguration();
        for (Gang g : gangs.values()) {
            String p = g.name.toLowerCase() + ".";
            y.set(p + "nome", g.name);
            y.set(p + "capo", g.leader.toString());
            for (UUID u : g.members) y.set(p + "membri." + u, g.names.getOrDefault(u, "?"));
            y.set(p + "blocchi", g.blocchi);
            y.set(p + "creata", g.created);
        }
        try {
            y.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().warning("gang.yml: " + e.getMessage());
        }
    }
}

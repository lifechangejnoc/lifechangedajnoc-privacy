package it.pepita.lobby;

import it.pepita.lobby.gui.Gui;
import it.pepita.lobby.gui.Menu;
import it.pepita.lobby.util.Txt;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Le modalità della rete (config "modalita"): carta nel menu della bussola, server di destinazione, NPC. */
public final class Modes {
    /** Una modalità. npc = null se non ha il personaggio nella lobby. */
    public record Mode(String id, String server, boolean ready, int slot, String name, Material icon, String model,
                       List<String> desc, double[] npc, String skin) {}

    private final PepitaLobby plugin;
    private final Map<String, Mode> modes = new LinkedHashMap<>();

    public Modes(PepitaLobby plugin) {
        this.plugin = plugin;
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("modalita");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            Material icon = Material.matchMaterial(s.getString("icona", "COMPASS"));
            double[] npc = null;
            ConfigurationSection n = s.getConfigurationSection("npc");
            if (n != null) npc = new double[]{n.getDouble("x"), n.getDouble("y"), n.getDouble("z"), n.getDouble("yaw")};
            modes.put(id, new Mode(id, s.getString("server", id), s.getBoolean("pronta", true), s.getInt("slot", 13),
                    s.getString("nome", id), icon == null ? Material.COMPASS : icon, s.getString("modello", ""),
                    s.getStringList("descrizione"), npc, n == null ? "" : n.getString("skin", "")));
        }
    }

    public Collection<Mode> all() {
        return modes.values();
    }

    public Mode get(String id) {
        return modes.get(id);
    }

    /** La prima modalità pronta (per /prigione, i portali della mappa ecc.). */
    public Mode first() {
        for (Mode m : modes.values()) if (m.ready()) return m;
        return null;
    }

    /** Il menu della bussola. */
    public void open(Player p) {
        Menu m = new Menu(3, "Scegli la modalità").emblem(Gui.Emblem.SELETTORE);
        for (Mode mode : modes.values()) {
            List<String> lore = new ArrayList<>(mode.desc());
            lore.add("");
            if (mode.ready()) {
                int n = plugin.network().count(mode.server());
                lore.add("<gray>In gioco: <white>" + (n < 0 ? "?" : n) + "</white> " + (n == 1 ? "giocatore" : "giocatori"));
                lore.add("");
                lore.add("<#FFD54A>▶ Click per entrare");
            } else lore.add("<dark_gray>✦ In arrivo ✦");
            var b = Gui.button(p, mode.model().isBlank() ? "gui_selettore" : mode.model(), mode.icon()).name(mode.name()).lore(lore);
            if (mode.ready()) b.glow();
            m.set(mode.slot(), b.build(), e -> send(p, mode));
        }
        m.set(22, Gui.close(p), e -> p.closeInventory());
        m.frame();
        m.open(p);
    }

    /** Manda il giocatore nel server della modalità, passando dal proxy. */
    public void send(Player p, Mode mode) {
        p.closeInventory();
        if (!mode.ready()) {
            Txt.send(p, plugin.getConfig().getString("messaggi.in-arrivo", "<gray>Questa modalità è in arrivo!"));
            return;
        }
        Txt.send(p, plugin.getConfig().getString("messaggi.connessione", "<gray>Ti porto in %modalita%...")
                .replace("%modalita%", Txt.plain(Txt.mm(mode.name()))));
        p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 0.8f, 0.8f);
        plugin.network().connect(p, mode.server());
    }

    /** Posizione dell'NPC di una modalità nel mondo dello spawn. */
    public Location npcLocation(Mode mode) {
        double[] n = mode.npc();
        return new Location(plugin.spawn().getWorld(), n[0], n[1], n[2], (float) n[3], 0);
    }

    public void setNpc(Mode mode, Location l) {
        String k = "modalita." + mode.id() + ".npc.";
        plugin.getConfig().set(k + "x", Math.floor(l.getX() * 2) / 2);
        plugin.getConfig().set(k + "y", Math.floor(l.getY() * 2) / 2);
        plugin.getConfig().set(k + "z", Math.floor(l.getZ() * 2) / 2);
        plugin.getConfig().set(k + "yaw", (double) Math.round(l.getYaw() / 15f) * 15);
        if (!plugin.getConfig().isString(k + "skin")) plugin.getConfig().set(k + "skin", "pepita:entity/npc/guardiano");
        plugin.saveConfig();
    }
}

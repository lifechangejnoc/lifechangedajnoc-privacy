package it.pepita.lobby;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import it.pepita.lobby.util.Keys;
import it.pepita.lobby.util.Txt;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerTextures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Personaggi delle modalità (Mannequin) con la scritta sopra: cliccandoli si entra nella modalità.
 * Ogni NPC esiste in due versioni (skin del resource pack o skin di default) e ognuno vede quella giusta.
 */
public final class Npcs implements Listener {
    private record Npc(Mannequin pack, Mannequin plain, TextDisplay label, Modes.Mode mode) {}

    private final PepitaLobby plugin;
    private final List<Npc> npcs = new ArrayList<>();
    private final List<Entity> extra = new ArrayList<>();
    private final Map<java.util.UUID, Long> cooldown = new HashMap<>();

    public Npcs(PepitaLobby plugin) {
        this.plugin = plugin;
    }

    public void spawnAll() {
        removeAll();
        // resti di un avvio precedente (le entità non sono persistenti, ma per sicurezza)
        for (World w : Bukkit.getWorlds())
            for (Entity e : w.getEntities()) if (e.getPersistentDataContainer().has(Keys.HOLO, PersistentDataType.STRING)) e.remove();
        boolean welcome = false;
        for (Modes.Mode m : plugin.modes().all()) {
            if (m.npc() == null) continue;
            Location l = plugin.modes().npcLocation(m);
            Mannequin pack = mannequin(l, m, true), plain = mannequin(l, m, false);
            TextDisplay label = text(l.clone().add(0, 2.35, 0), label(m), 1f, "npc_label");
            npcs.add(new Npc(pack, plain, label, m));
            if (!welcome) {
                extra.add(text(l.clone().add(0, 3.4, 0), plugin.getConfig().getString("messaggi.ologramma", ""), 1.1f, "benvenuto"));
                welcome = true;
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) refreshView(p);
    }

    public void removeAll() {
        for (Npc n : npcs) {
            n.pack().remove();
            n.plain().remove();
            n.label().remove();
        }
        npcs.clear();
        for (Entity e : extra) e.remove();
        extra.clear();
    }

    private String label(Modes.Mode m) {
        int n = plugin.network().count(m.server());
        return m.name() + "\n" + (m.ready() ? "<gray>Clicca per entrare\n<white>" + (n < 0 ? "?" : n) + "</white> <gray>in gioco" : "<dark_gray>In arrivo");
    }

    public void updateLabels() {
        for (Npc n : npcs) if (n.label().isValid()) n.label().text(Txt.mm(label(n.mode())));
    }

    private Mannequin mannequin(Location l, Modes.Mode m, boolean pack) {
        return l.getWorld().spawn(l, Mannequin.class, e -> {
            String skin = m.skin();
            ResolvableProfile prof = pack && skin != null && !skin.isBlank()
                    ? ResolvableProfile.resolvableProfile().skinPatch(sp -> sp.body(Key.key(skin)).model(PlayerTextures.SkinModel.CLASSIC)).build()
                    : Mannequin.defaultProfile();
            e.setProfile(prof);
            e.setImmovable(true);
            e.setInvulnerable(true);
            e.setSilent(true);
            e.setGravity(false);
            e.setPersistent(false);
            e.setDescription(Txt.mm("<gray>Modalità"));
            e.customName(Txt.mm(m.name()));
            e.setCustomNameVisible(false);
            e.setRotation(l.getYaw(), 0);
            e.setVisibleByDefault(false);
            e.getPersistentDataContainer().set(Keys.NPC, PersistentDataType.STRING, m.id());
            e.getPersistentDataContainer().set(Keys.HOLO, PersistentDataType.STRING, pack ? "npc_pack" : "npc");
        });
    }

    private TextDisplay text(Location l, String mm, float scale, String tag) {
        return l.getWorld().spawn(l, TextDisplay.class, t -> {
            t.text(Txt.mm(mm));
            t.setBillboard(Display.Billboard.CENTER);
            t.setShadowed(true);
            t.setDefaultBackground(false);
            t.setBackgroundColor(Color.fromARGB(80, 0, 0, 0));
            t.setPersistent(false);
            if (scale != 1f) t.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(),
                    new org.joml.AxisAngle4f(), new org.joml.Vector3f(scale, scale, scale), new org.joml.AxisAngle4f()));
            t.getPersistentDataContainer().set(Keys.HOLO, PersistentDataType.STRING, tag);
        });
    }

    /** Versione dell'NPC con o senza resource pack. */
    public void refreshView(Player p) {
        boolean pack = plugin.hasPack(p);
        for (Npc n : npcs) {
            p.showEntity(plugin, pack ? n.pack() : n.plain());
            p.hideEntity(plugin, pack ? n.plain() : n.pack());
        }
    }

    private Modes.Mode modeOf(Entity e) {
        String id = e.getPersistentDataContainer().get(Keys.NPC, PersistentDataType.STRING);
        return id == null ? null : plugin.modes().get(id);
    }

    private void use(Player p, Modes.Mode m) {
        long now = System.currentTimeMillis();
        if (now - cooldown.getOrDefault(p.getUniqueId(), 0L) < 1500) return;
        cooldown.put(p.getUniqueId(), now);
        plugin.modes().send(p, m);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(PlayerInteractEntityEvent e) {
        Modes.Mode m = modeOf(e.getRightClicked());
        if (m == null) return;
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND) use(e.getPlayer(), m);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClickAt(PlayerInteractAtEntityEvent e) {
        if (modeOf(e.getRightClicked()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHit(EntityDamageByEntityEvent e) {
        Modes.Mode m = modeOf(e.getEntity());
        if (m == null) return;
        e.setCancelled(true);
        if (e.getDamager() instanceof Player p) use(p, m);
    }
}

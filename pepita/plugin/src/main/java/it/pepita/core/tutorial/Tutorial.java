package it.pepita.core.tutorial;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import it.pepita.core.PepitaCore;
import it.pepita.core.cosmetics.PickaxeSkin;
import it.pepita.core.data.PlayerData;
import it.pepita.core.economy.Economy;
import it.pepita.core.rank.RankManager;
import it.pepita.core.util.Keys;
import it.pepita.core.util.Txt;
import it.pepita.core.world.Layout;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tutorial interattivo guidato da "Beppe il Secondino" (Mannequin nell'hub): passi legati alle azioni del giocatore,
 * fumetto personale sopra l'NPC, titoli e ricompensa finale. Parte al primo accesso e con /tutorial.
 */
public final class Tutorial implements Listener {
    public enum Ev { MENU, MINE, BLOCK, RANKUP, ENCHANT, CRATE, SKIN, PVP, CELLS, PASS }

    private record Step(Ev ev, int need, String title, String text) {}

    private static final Step[] STEPS = {
            new Step(Ev.MENU, 1, "Il menu", "Apri il <gold>menu</gold>: tasto destro con la <yellow>stella</yellow> nell'ultimo slot (o <yellow>/menu</yellow>)."),
            new Step(Ev.MINE, 1, "In miniera", "Entra nel portale delle <aqua>Miniere</aqua> qui nell'hub (o usa la <yellow>bussola</yellow>)."),
            new Step(Ev.BLOCK, 20, "Scava!", "Rompi <white>20 blocchi</white> col tuo piccone: si vendono da soli."),
            new Step(Ev.RANKUP, 1, "Sali di rank", "Scrivi <yellow>/rankup</yellow>: ogni rank sblocca una miniera migliore. Il primo te lo offro io!"),
            new Step(Ev.ENCHANT, 1, "Incantesimi", "Tasto destro col piccone e compra un <gold>incantesimo</gold> con le Pepite (ti ho dato qualche Pepita)."),
            new Step(Ev.CRATE, 1, "Le casse", "Apri una <gold>cassa</gold> con <yellow>/casse</yellow>: hai già delle chiavi."),
            new Step(Ev.SKIN, 1, "Le skin", "Le skin sono oggetti: apri <yellow>/skin</yellow> o trascina una skin sul piccone. Eccone una in regalo!"),
            new Step(Ev.PVP, 1, "Quantum e PvP", "Visita la <red>Miniera PvP</red> (portale rosso nell'hub): lì si trovano i <aqua>Quantum</aqua>. Attento agli altri!"),
            new Step(Ev.CELLS, 1, "Le celle", "Visita il <gold>Colosseo delle Celle</gold> (portale nell'hub o <yellow>/cella</yellow>): puoi comprarne una e arredarla."),
            new Step(Ev.PASS, 1, "Battle pass", "Apri il <gold>Battle Pass</gold> con <yellow>/battlepass</yellow>: missioni giornaliere e premi a ogni livello."),
    };

    private final PepitaCore plugin;
    private final Map<UUID, TextDisplay> bubbles = new HashMap<>();
    private Mannequin npcPack, npcPlain;
    private TextDisplay npcLabel;

    public Tutorial(PepitaCore plugin) {
        this.plugin = plugin;
    }

    public int steps() {
        return STEPS.length;
    }

    public boolean active(PlayerData d) {
        return !d.tutorialDone && d.tutorialStep >= 0 && d.tutorialStep < STEPS.length;
    }

    // =====================================================================
    //  NPC
    // =====================================================================

    public void spawnNpc() {
        removeNpc();
        World w = plugin.world().hub();
        if (w == null) return;
        double[] n = Layout.HUB_NPC;
        Location l = new Location(w, n[0], n[1], n[2], (float) n[3], 0);
        npcPack = spawnMannequin(l, true);
        npcPlain = spawnMannequin(l, false);
        npcLabel = w.spawn(l.clone().add(0, 2.35, 0), TextDisplay.class, t -> {
            t.text(Txt.mm("<gradient:#FFE259:#FFA751><b>Beppe il Secondino</b></gradient>\n<gray>Clicca per il <white>tutorial"));
            t.setBillboard(Display.Billboard.CENTER);
            t.setShadowed(true);
            t.setDefaultBackground(false);
            t.setBackgroundColor(Color.fromARGB(80, 0, 0, 0));
            t.setPersistent(false);
            t.getPersistentDataContainer().set(Keys.HOLO, PersistentDataType.STRING, "npc_label");
        });
        for (Player p : Bukkit.getOnlinePlayers()) refreshView(p);
    }

    private Mannequin spawnMannequin(Location l, boolean pack) {
        return l.getWorld().spawn(l, Mannequin.class, m -> {
            ResolvableProfile prof = pack
                    ? ResolvableProfile.resolvableProfile().skinPatch(sp -> sp.body(Key.key("pepita", "entity/npc/guardiano"))
                    .model(PlayerTextures.SkinModel.CLASSIC)).build()
                    : Mannequin.defaultProfile();
            m.setProfile(prof);
            m.setImmovable(true);
            m.setInvulnerable(true);
            m.setSilent(true);
            m.setGravity(false);
            m.setPersistent(false);
            m.setDescription(Txt.mm("<gray>Tutorial"));
            m.customName(Txt.mm("<gold>Beppe il Secondino"));
            m.setCustomNameVisible(false);
            m.setRotation(l.getYaw(), 0);
            m.setVisibleByDefault(false);
            m.getPersistentDataContainer().set(Keys.NPC, PersistentDataType.STRING, "beppe");
            m.getPersistentDataContainer().set(Keys.HOLO, PersistentDataType.STRING, pack ? "npc_pack" : "npc");
        });
    }

    public void removeNpc() {
        if (npcPack != null) npcPack.remove();
        if (npcPlain != null) npcPlain.remove();
        if (npcLabel != null) npcLabel.remove();
        for (TextDisplay t : bubbles.values()) t.remove();
        bubbles.clear();
        npcPack = npcPlain = null;
        npcLabel = null;
    }

    /** Mostra la versione dell'NPC giusta (con o senza resource pack) e il fumetto personale. */
    public void refreshView(Player p) {
        PlayerData d = plugin.data().get(p);
        if (npcPack != null && npcPlain != null) {
            if (d.hasPack) {
                p.showEntity(plugin, npcPack);
                p.hideEntity(plugin, npcPlain);
            } else {
                p.showEntity(plugin, npcPlain);
                p.hideEntity(plugin, npcPack);
            }
        }
        updateBubble(p, d);
    }

    private boolean isNpc(Entity e) {
        return e.getPersistentDataContainer().has(Keys.NPC, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(PlayerInteractEntityEvent e) {
        if (!isNpc(e.getRightClicked())) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        talk(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClickAt(PlayerInteractAtEntityEvent e) {
        if (isNpc(e.getRightClicked())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!isNpc(e.getEntity())) return;
        e.setCancelled(true);
        if (e.getDamager() instanceof Player p) talk(p);
    }

    private final Map<UUID, Long> talkCd = new HashMap<>();

    private void talk(Player p) {
        long now = System.currentTimeMillis();
        if (now - talkCd.getOrDefault(p.getUniqueId(), 0L) < 1000) return;
        talkCd.put(p.getUniqueId(), now);
        PlayerData d = plugin.data().get(p);
        if (active(d)) announce(p, d, false);
        else if (d.tutorialDone) {
            say(p, "Bentornato, detenuto! Il tutorial l'hai già finito. Se vuoi rifarlo: <yellow>/tutorial ricomincia</yellow>.");
        } else start(p);
    }

    // =====================================================================
    //  Passi
    // =====================================================================

    public void start(Player p) {
        PlayerData d = plugin.data().get(p);
        d.tutorialStep = 0;
        d.tutorialCount = 0;
        d.tutorialDone = false;
        d.dirty = true;
        say(p, "Benvenuto a <gold>Pepita</gold>! Sono <gold>Beppe</gold>, il secondino. Ti spiego come funziona la prigione: segui i miei consigli.");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) announce(p, d, true);
        }, 40L);
    }

    public void skip(Player p) {
        PlayerData d = plugin.data().get(p);
        d.tutorialDone = true;
        d.tutorialStep = STEPS.length;
        d.dirty = true;
        updateBubble(p, d);
        Txt.send(p, "Tutorial saltato. Puoi rifarlo quando vuoi con <yellow>/tutorial ricomincia</yellow>.");
    }

    private void say(Player p, String msg) {
        Txt.raw(p, "<dark_gray>[<gold>Beppe</gold>]</dark_gray> <gray>" + msg);
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, 0.7f, 0.8f);
    }

    private void announce(Player p, PlayerData d, boolean title) {
        Step s = STEPS[d.tutorialStep];
        prepare(p, d, s);
        if (title) p.showTitle(Title.title(Txt.mm("<gold><b>" + (d.tutorialStep + 1) + "/" + STEPS.length + " • " + s.title + "</b>"),
                Txt.mm("<gray>" + Txt.plain(Txt.mm(s.text))),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(3500), Duration.ofMillis(500))));
        say(p, "<white>(" + (d.tutorialStep + 1) + "/" + STEPS.length + ")</white> " + s.text);
        updateBubble(p, d);
    }

    /** Piccoli aiuti perché il passo sia fattibile subito. */
    private void prepare(Player p, PlayerData d, Step s) {
        Economy eco = plugin.eco();
        switch (s.ev) {
            case RANKUP -> {
                if (d.rank < RankManager.MAX_RANK) {
                    double need = plugin.ranks().nextCost(d) - d.soldi;
                    if (need > 0) eco.give(d, Economy.Cur.SOLDI, need, "tutorial");
                }
            }
            case ENCHANT -> {
                if (d.pepite < 200) eco.give(d, Economy.Cur.PEPITE, 200 - d.pepite, "tutorial");
            }
            case CRATE -> {
                if (d.keys("comune") <= 0) d.addKeys("comune", 1);
            }
            case SKIN -> {
                if (d.tutorialCount == 0) {
                    plugin.picks().giveSkin(p, d, PickaxeSkin.BAGUETTE);
                    d.tutorialCount = -1; // regalo già fatto per questo passo
                }
            }
            default -> {
            }
        }
    }

    /** Chiamato dai vari sistemi quando il giocatore fa qualcosa. */
    public void onEvent(Player p, Ev ev, int amount) {
        if (p == null) return;
        PlayerData d = plugin.data().get(p);
        if (!active(d)) return;
        Step s = STEPS[d.tutorialStep];
        if (s.ev != ev) return;
        d.tutorialCount = Math.max(0, d.tutorialCount) + amount;
        d.dirty = true;
        if (d.tutorialCount < s.need) {
            if (s.need > 1) p.sendActionBar(Txt.mm("<gold>Tutorial:</gold> <white>" + d.tutorialCount + "/" + s.need + " <gray>blocchi"));
            return;
        }
        d.tutorialStep++;
        d.tutorialCount = 0;
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.6f);
        p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1.5, 0), 15, 0.4, 0.4, 0.4, 0);
        if (d.tutorialStep >= STEPS.length) {
            complete(p, d);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && active(d)) announce(p, d, true);
        }, 30L);
    }

    private void complete(Player p, PlayerData d) {
        d.tutorialDone = true;
        d.dirty = true;
        Economy eco = plugin.eco();
        double money = eco.minutes(d, plugin.getConfig().getDouble("tutorial.premio-minuti", 20));
        double pep = plugin.getConfig().getDouble("tutorial.premio-pepite", 1500);
        eco.give(d, Economy.Cur.SOLDI, money, "tutorial");
        eco.give(d, Economy.Cur.PEPITE, pep, "tutorial");
        d.addKeys("rara", 2);
        d.addKeys("leggendaria", 1);
        plugin.picks().giveSkin(p, d, PickaxeSkin.SAKURA);
        p.showTitle(Title.title(Txt.mm("<gradient:#FFE259:#FFA751><b>TUTORIAL COMPLETATO!</b></gradient>"),
                Txt.mm("<gray>Ora sei un vero detenuto di Pepita."),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(3500), Duration.ofMillis(700))));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        say(p, "Bravo! Ecco il tuo premio: " + Txt.soldi(money) + "<gray>, " + Txt.pepite(pep)
                + "<gray>, 2 Chiavi Rare, 1 Chiave Leggendaria e la skin <#55E6FF>Sakura</#55E6FF>. Buona evasione!");
        updateBubble(p, d);
    }

    // =====================================================================
    //  Fumetto personale
    // =====================================================================

    private void updateBubble(Player p, PlayerData d) {
        TextDisplay old = bubbles.get(p.getUniqueId());
        boolean want = active(d) && plugin.world().isHub(p.getWorld()) && npcPack != null;
        if (!want) {
            if (old != null) {
                old.remove();
                bubbles.remove(p.getUniqueId());
            }
            return;
        }
        Step s = STEPS[d.tutorialStep];
        String text = "<gold><b>" + (d.tutorialStep + 1) + "/" + STEPS.length + " • " + s.title + "</b></gold>\n<white>" + s.text;
        if (old != null && old.isValid()) {
            old.text(Txt.mm(text));
            return;
        }
        double[] n = Layout.HUB_NPC;
        Location l = new Location(p.getWorld(), n[0], n[1] + 3.1, n[2]);
        TextDisplay td = p.getWorld().spawn(l, TextDisplay.class, t -> {
            t.text(Txt.mm(text));
            t.setBillboard(Display.Billboard.CENTER);
            t.setLineWidth(180);
            t.setDefaultBackground(false);
            t.setBackgroundColor(Color.fromARGB(200, 20, 14, 6));
            t.setShadowed(false);
            t.setPersistent(false);
            t.setVisibleByDefault(false);
            t.getPersistentDataContainer().set(Keys.HOLO, PersistentDataType.STRING, "fumetto");
        });
        p.showEntity(plugin, td);
        bubbles.put(p.getUniqueId(), td);
    }

    public void onWorldChange(Player p) {
        PlayerData d = plugin.data().get(p);
        updateBubble(p, d);
        if (plugin.world().isPvp(p.getWorld())) onEvent(p, Ev.PVP, 1);
        if (plugin.world().isCells(p.getWorld())) onEvent(p, Ev.CELLS, 1);
    }

    public void onQuit(Player p) {
        TextDisplay t = bubbles.remove(p.getUniqueId());
        if (t != null) t.remove();
        talkCd.remove(p.getUniqueId());
    }

    /** Ogni secondo: l'NPC guarda il giocatore più vicino. */
    public void tick() {
        if (npcPack == null || !npcPack.isValid()) return;
        Location base = npcPack.getLocation();
        Player near = null;
        double bd = 64;
        for (Player p : base.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(base);
            if (d < bd) {
                bd = d;
                near = p;
            }
        }
        float yaw = (float) Layout.HUB_NPC[3], pitch = 0;
        if (near != null) {
            Location e = near.getEyeLocation();
            double dx = e.getX() - base.getX(), dz = e.getZ() - base.getZ(), dy = e.getY() - (base.getY() + 1.62);
            yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        }
        npcPack.setRotation(yaw, pitch);
        if (npcPlain != null) npcPlain.setRotation(yaw, pitch);
    }
}

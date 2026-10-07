package it.pepita.core.lobby;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import it.pepita.core.PepitaCore;
import it.pepita.core.data.PlayerData;
import it.pepita.core.gui.Gui;
import it.pepita.core.gui.Menu;
import it.pepita.core.util.ItemBuilder;
import it.pepita.core.util.Keys;
import it.pepita.core.util.Txt;
import it.pepita.core.world.Layout;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.profile.PlayerTextures;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/**
 * Lobby (mondo pepita_hub) separata dalle modalità: chi è qui ha solo la bussola delle modalità, niente piccone,
 * corazza, scoreboard o bossbar del prison. Entrando nella lobby l'inventario del prison viene salvato nei dati
 * del giocatore e svuotato; entrando nel prison (qualunque suo mondo) viene rimesso com'era.
 */
public final class Lobby implements Listener {
    /** Id della bussola delle modalità. */
    public static final String COMPASS = "modalita";

    /** Comandi che hanno senso solo dentro il prison (in lobby rispondono con un suggerimento). */
    private static final Set<String> PRISON_ONLY = Set.of("menu", "tutorial", "battlepass", "traguardi", "quantum", "corazza", "tier",
            "keyall", "rankup", "rankupmax", "prestigio", "evasione", "piccone", "incantesimi", "soldi", "pepite", "paga", "top",
            "casse", "skin", "giornaliero", "booster", "gang");

    private final PepitaCore plugin;
    private Mannequin npcPack, npcPlain;
    private TextDisplay npcLabel;

    public Lobby(PepitaCore plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateLabel, 40L, 40L);
    }

    public boolean in(Player p) {
        return plugin.world().isHub(p.getWorld());
    }

    /** true se il comando va usato solo nel prison e il giocatore è nella lobby. */
    public boolean blocks(Player p, String command) {
        return in(p) && PRISON_ONLY.contains(command);
    }

    /** Giocatori nel prison (tutti i mondi tranne la lobby). */
    public int prisonPlayers() {
        int n = 0;
        for (Player p : Bukkit.getOnlinePlayers()) if (plugin.world().managed(p.getWorld()) && !in(p)) n++;
        return n;
    }

    // =====================================================================
    //  Inventario: lobby ↔ prison
    // =====================================================================

    /** Allinea inventario, modalità di gioco e HUD al mondo in cui si trova il giocatore. */
    public void sync(Player p) {
        if (!p.isOnline()) return;
        boolean free = p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR;
        if (in(p)) {
            if (!free) enterLobby(p);
        } else if (plugin.world().managed(p.getWorld())) {
            if (!free) enterPrison(p);
        }
        refreshHud(p);
    }

    public ItemStack compass() {
        return new ItemBuilder(Material.COMPASS)
                .name("<gradient:#FFE259:#FFA751><b>Modalità</b></gradient> <gray>(tasto destro)")
                .lore("<gray>Scegli a cosa giocare.", "", "<#FFD54A>▶ Tasto destro per aprire")
                .model("selettore").id(COMPASS).glow().build();
    }

    private static boolean isLobbyItem(ItemStack it) {
        return ItemBuilder.is(it, COMPASS);
    }

    private void enterLobby(Player p) {
        PlayerData d = plugin.data().get(p);
        PlayerInventory inv = p.getInventory();
        List<ItemStack> leftovers = List.of();
        if (!d.inLobby) {
            boolean lobbyKit = false;
            for (ItemStack it : inv.getContents()) if (isLobbyItem(it)) lobbyKit = true;
            // non sovrascrivere mai un inventario salvato con il kit della lobby
            if (!lobbyKit || d.prisonInv == null) {
                ItemStack[] contents = inv.getContents();
                for (int i = 0; i < contents.length; i++)
                    if (contents[i] != null && (contents[i].getType().isAir() || isLobbyItem(contents[i]))) contents[i] = null;
                d.prisonInv = Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(contents));
            }
            d.inLobby = true;
            d.dirty = true;
            plugin.data().save(d, false); // subito su disco: l'inventario sta per essere svuotato
        } else {
            // già nella lobby: oggetti ricevuti nel frattempo vanno nell'inventario del prison salvato
            List<ItemStack> extra = new ArrayList<>();
            for (ItemStack it : inv.getContents())
                if (it != null && !it.getType().isAir() && !isLobbyItem(it)) extra.add(it.clone());
            if (!extra.isEmpty()) leftovers = storeInSaved(d, extra);
        }
        inv.clear();
        inv.setArmorContents(new ItemStack[4]);
        inv.setItemInOffHand(null);
        inv.setItem(4, compass());
        // se l'inventario del prison era pieno restano qui finché non si entra nel prison
        for (ItemStack it : leftovers) inv.addItem(it);
        inv.setHeldItemSlot(4);
        p.setItemOnCursor(null);
        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
        if (p.getGameMode() != GameMode.ADVENTURE) p.setGameMode(GameMode.ADVENTURE);
    }

    /** Aggiunge oggetti agli slot liberi dell'inventario del prison salvato; restituisce quelli che non ci stanno. */
    private List<ItemStack> storeInSaved(PlayerData d, List<ItemStack> items) {
        ItemStack[] saved;
        try {
            saved = d.prisonInv == null ? new ItemStack[41] : ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(d.prisonInv));
        } catch (Exception ex) {
            return items;
        }
        if (saved.length < 41) saved = java.util.Arrays.copyOf(saved, 41);
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack it : items) {
            int slot = -1;
            for (int i = 0; i < 36; i++) if (saved[i] == null || saved[i].getType().isAir()) { slot = i; break; }
            if (slot < 0) left.add(it);
            else saved[slot] = it;
        }
        d.prisonInv = Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(saved));
        d.dirty = true;
        plugin.data().save(d, false);
        return left;
    }

    private void enterPrison(Player p) {
        PlayerData d = plugin.data().get(p);
        if (d.inLobby) {
            PlayerInventory inv = p.getInventory();
            // oggetti arrivati mentre era nella lobby (premi, skin...): non vanno persi
            List<ItemStack> extra = new ArrayList<>();
            for (ItemStack it : inv.getContents())
                if (it != null && !it.getType().isAir() && !isLobbyItem(it)) extra.add(it);
            inv.clear();
            inv.setArmorContents(new ItemStack[4]);
            inv.setItemInOffHand(null);
            if (d.prisonInv != null) {
                try {
                    ItemStack[] saved = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(d.prisonInv));
                    ItemStack[] contents = new ItemStack[inv.getSize()];
                    System.arraycopy(saved, 0, contents, 0, Math.min(saved.length, contents.length));
                    for (int i = 0; i < contents.length; i++) if (isLobbyItem(contents[i])) contents[i] = null;
                    inv.setContents(contents);
                } catch (Exception ex) {
                    plugin.getLogger().severe("Inventario del prison di " + p.getName() + " non ripristinato: " + ex.getMessage()
                            + " (resta salvato nel file del giocatore)");
                    d.prisonInvBackup = d.prisonInv;
                }
            }
            for (ItemStack it : extra)
                for (ItemStack left : inv.addItem(it).values()) p.getWorld().dropItem(p.getLocation(), left);
            d.prisonInv = null;
            d.inLobby = false;
            d.dirty = true;
            plugin.data().save(d, false);
        }
        // la bussola delle modalità resta solo nella lobby
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) if (isLobbyItem(inv.getItem(i))) inv.setItem(i, null);
        if (p.getGameMode() == GameMode.ADVENTURE) p.setGameMode(GameMode.SURVIVAL);
        plugin.picks().ensureKit(p);
    }

    /** Scoreboard, tab e bossbar giusti per lobby o prison. */
    public void refreshHud(Player p) {
        plugin.sidebar().update(p);
        plugin.sidebar().updateTab(p);
        plugin.keyAll().refresh(p);
        plugin.boosters().refresh(p);
        plugin.goldRush().refresh(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> sync(p));
    }

    // =====================================================================
    //  Entrare nel prison
    // =====================================================================

    /** Dalla lobby al cortile della prigione (la prima volta parte il tutorial). */
    public void enterPrisonMode(Player p) {
        p.closeInventory();
        p.teleport(plugin.world().prisonSpawn());
        p.showTitle(Title.title(Txt.mm("<gradient:#FFE259:#FFA751><b>⛓ PRISON ⛓</b></gradient>"),
                Txt.mm("<gray>Scava, guadagna e scala i rank"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2000), Duration.ofMillis(600))));
        p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 0.8f, 0.8f);
        PlayerData d = plugin.data().get(p);
        if (!d.tutorialDone && d.tutorialStep < 0 && plugin.getConfig().getBoolean("tutorial.automatico", true))
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && !in(p)) plugin.tutorial().start(p);
            }, 40L);
    }

    /** Menu delle modalità (per ora solo il Prison). */
    public void openModes(Player p) {
        Menu m = new Menu(3, "Scegli la modalità").emblem(Gui.Emblem.SELETTORE);
        int n = prisonPlayers();
        m.set(13, Gui.button(p, "gui_prigione", Material.IRON_BARS)
                .name("<gradient:#FFE259:#FFA751><b>⛓ PRISON ⛓</b></gradient>")
                .lore("<gray>Sei un detenuto: scava, guadagna", "<gray>e scala i rank da <white>A</white> a <gold>Z</gold>.",
                        "<gray>Miniere, incantesimi, Quantum,", "<gray>celle e battle pass.", "",
                        "<gray>In gioco: <white>" + n + "</white> " + (n == 1 ? "giocatore" : "giocatori"), "",
                        "<#FFD54A>▶ Click per entrare").glow().build(), e -> enterPrisonMode(p));
        m.set(22, Gui.close(p), e -> p.closeInventory());
        m.frame();
        m.open(p);
    }

    // =====================================================================
    //  NPC della modalità Prison
    // =====================================================================

    public void spawnNpc() {
        removeNpc();
        World w = plugin.world().hub();
        if (w == null) return;
        Location l = plugin.world().hubNpc();
        npcPack = spawnMannequin(l, true);
        npcPlain = spawnMannequin(l, false);
        npcLabel = w.spawn(l.clone().add(0, 2.35, 0), TextDisplay.class, t -> {
            t.text(Txt.mm(labelText()));
            t.setBillboard(Display.Billboard.CENTER);
            t.setShadowed(true);
            t.setDefaultBackground(false);
            t.setBackgroundColor(Color.fromARGB(80, 0, 0, 0));
            t.setPersistent(false);
            t.getPersistentDataContainer().set(Keys.HOLO, PersistentDataType.STRING, "lobby_npc_label");
        });
        for (Player p : Bukkit.getOnlinePlayers()) refreshView(p);
    }

    private String labelText() {
        int n = prisonPlayers();
        return "<gradient:#FFE259:#FFA751><b>⛓ PRISON ⛓</b></gradient>\n<gray>Clicca per entrare\n<white>" + n + "</white> <gray>in gioco";
    }

    private void updateLabel() {
        if (npcLabel != null && npcLabel.isValid()) npcLabel.text(Txt.mm(labelText()));
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
            m.setDescription(Txt.mm("<gray>Modalità"));
            m.customName(Txt.mm("<gold>Prison"));
            m.setCustomNameVisible(false);
            m.setRotation(l.getYaw(), 0);
            m.setVisibleByDefault(false);
            m.getPersistentDataContainer().set(Keys.NPC, PersistentDataType.STRING, "prison");
            m.getPersistentDataContainer().set(Keys.HOLO, PersistentDataType.STRING, pack ? "lobby_npc_pack" : "lobby_npc");
        });
    }

    public void removeNpc() {
        if (npcPack != null) npcPack.remove();
        if (npcPlain != null) npcPlain.remove();
        if (npcLabel != null) npcLabel.remove();
        npcPack = npcPlain = null;
        npcLabel = null;
    }

    /** Versione dell'NPC con o senza resource pack. */
    public void refreshView(Player p) {
        if (npcPack == null || npcPlain == null) return;
        boolean pack = plugin.data().get(p).hasPack;
        p.showEntity(plugin, pack ? npcPack : npcPlain);
        p.hideEntity(plugin, pack ? npcPlain : npcPack);
    }

    private static boolean isModeNpc(Entity e) {
        return "prison".equals(e.getPersistentDataContainer().get(Keys.NPC, PersistentDataType.STRING));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onNpc(PlayerInteractEntityEvent e) {
        if (!isModeNpc(e.getRightClicked())) return;
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND) enterPrisonMode(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onNpcAt(PlayerInteractAtEntityEvent e) {
        if (isModeNpc(e.getRightClicked())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onNpcHit(EntityDamageByEntityEvent e) {
        if (!isModeNpc(e.getEntity())) return;
        e.setCancelled(true);
        if (e.getDamager() instanceof Player p) enterPrisonMode(p);
    }

    // =====================================================================
    //  Oggetti della lobby
    // =====================================================================

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent e) {
        if (!isLobbyItem(e.getItem())) return;
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND && e.getAction().isRightClick()) openModes(e.getPlayer());
    }

    private boolean locked(Player p) {
        return in(p) && p.getGameMode() != GameMode.CREATIVE;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || e.getInventory().getHolder() instanceof Menu) return;
        if (locked(p) || isLobbyItem(e.getCurrentItem()) || isLobbyItem(e.getCursor())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && locked(p) && !(e.getInventory().getHolder() instanceof Menu)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent e) {
        if (locked(e.getPlayer()) || isLobbyItem(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (locked(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && locked(p)) e.setCancelled(true);
    }
}

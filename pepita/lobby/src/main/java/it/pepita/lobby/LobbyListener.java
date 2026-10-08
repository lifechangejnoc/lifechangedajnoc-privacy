package it.pepita.lobby;

import io.papermc.paper.event.player.AsyncChatEvent;
import it.pepita.lobby.gui.Menu;
import it.pepita.lobby.util.Txt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Ingresso nella lobby, bussola, chat e protezioni: nella lobby non si rompe e non si tocca niente. */
public final class LobbyListener implements Listener {
    private final PepitaLobby plugin;
    private final Map<UUID, Long> portalCd = new HashMap<>();

    public LobbyListener(PepitaLobby plugin) {
        this.plugin = plugin;
    }

    /** Lo staff in creativa può costruire e toccare tutto. */
    private static boolean builder(Player p) {
        return p.getGameMode() == GameMode.CREATIVE && p.hasPermission("pepita.admin");
    }

    // ---------------- Ingresso e uscita ----------------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        e.joinMessage(null);
        p.teleport(plugin.spawn());
        // la mappa può avere la creativa come modalità predefinita: nella lobby solo lo staff può restarci
        if (p.getGameMode() != GameMode.ADVENTURE && !p.hasPermission("pepita.admin")) p.setGameMode(GameMode.ADVENTURE);
        plugin.prepare(p);
        plugin.board().create(p);
        plugin.npcs().refreshView(p);
        plugin.packs().send(p);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            p.showTitle(Title.title(Txt.mm(Txt.LOGO), Txt.mm("<gray>Scegli la modalità con la <yellow>bussola</yellow>"),
                    Title.Times.times(Duration.ofMillis(400), Duration.ofMillis(2500), Duration.ofMillis(700))));
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
            Txt.raw(p, plugin.getConfig().getString("messaggi.benvenuto", ""));
        }, 30L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        e.quitMessage(null);
        plugin.board().remove(e.getPlayer());
        plugin.setPack(e.getPlayer(), false);
        portalCd.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPack(PlayerResourcePackStatusEvent e) {
        Player p = e.getPlayer();
        if (e.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            plugin.setPack(p, true);
            plugin.npcs().refreshView(p);
            plugin.board().update(p);
        } else if (e.getStatus() == PlayerResourcePackStatusEvent.Status.DECLINED || e.getStatus() == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD) {
            plugin.setPack(p, false);
            plugin.npcs().refreshView(p);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        e.setRespawnLocation(plugin.spawn());
        Bukkit.getScheduler().runTask(plugin, () -> plugin.prepare(e.getPlayer()));
    }

    // ---------------- Bussola ----------------

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent e) {
        if (!PepitaLobby.isCompass(e.getItem())) return;
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND && e.getAction().isRightClick()) plugin.modes().open(e.getPlayer());
    }

    // ---------------- Chat (solo la lobby: ogni server ha la sua) ----------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Component pre = Txt.mm("<dark_gray>[<gray>Lobby</gray>]</dark_gray> <white>" + Txt.esc(e.getPlayer().getName()) + " <dark_gray>»</dark_gray> ");
        boolean staff = e.getPlayer().hasPermission("pepita.admin");
        e.renderer((source, name, message, viewer) -> pre.append(message.color(staff ? NamedTextColor.WHITE : NamedTextColor.GRAY)));
    }

    // ---------------- Vuoto e portali ----------------

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo().getBlockY() == e.getFrom().getBlockY()) return;
        if (e.getTo().getY() < plugin.getConfig().getInt("vuoto-y", -60)) {
            e.getPlayer().setFallDistance(0);
            e.getPlayer().teleport(plugin.spawn());
        }
    }

    @EventHandler
    public void onPortalEnter(EntityPortalEnterEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        long now = System.currentTimeMillis();
        if (now - portalCd.getOrDefault(p.getUniqueId(), 0L) < 3000) return;
        portalCd.put(p.getUniqueId(), now);
        // un portale della mappa porta alla prima modalità pronta
        Modes.Mode m = plugin.modes().first();
        if (m != null) Bukkit.getScheduler().runTask(plugin, () -> plugin.modes().send(p, m));
    }

    @EventHandler
    public void onPortal(PlayerPortalEvent e) {
        e.setCancelled(true);
    }

    // ---------------- Protezioni ----------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (!builder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        if (!builder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (!builder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (!builder(e.getPlayer())) e.setCancelled(true);
    }

    /** Porte, bauli, leve, pulsanti, piastre della mappa: per i giocatori non si toccano. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (builder(e.getPlayer()) || e.getClickedBlock() == null) return;
        if (e.getAction() == org.bukkit.event.block.Action.PHYSICAL || e.getClickedBlock().getType().isInteractable()) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityInteract(PlayerInteractEntityEvent e) {
        if (!builder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (!builder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHanging(HangingBreakEvent e) {
        if (e instanceof org.bukkit.event.hanging.HangingBreakByEntityEvent be && be.getRemover() instanceof Player p && builder(p)) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player) {
            e.setCancelled(true);
            return;
        }
        // cornici, armor stand e altre entità della mappa
        if (e instanceof EntityDamageByEntityEvent be && be.getDamager() instanceof Player p && builder(p)) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        e.setCancelled(true);
        e.getEntity().setFoodLevel(20);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent e) {
        if (!builder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && !builder(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (!builder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) return;
        if (e.getWhoClicked() instanceof Player p && p.getGameMode() != GameMode.CREATIVE) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) return;
        if (e.getWhoClicked() instanceof Player p && p.getGameMode() != GameMode.CREATIVE) e.setCancelled(true);
    }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent e) {
        // solo le entità create dai plugin (gli NPC delle modalità)
        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) e.setCancelled(true);
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().clear();
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().clear();
    }

    @EventHandler
    public void onChangeBlock(EntityChangeBlockEvent e) {
        if (!(e.getEntity() instanceof Player p && builder(p))) e.setCancelled(true);
    }

    @EventHandler
    public void onBurn(BlockBurnEvent e) {
        e.setCancelled(true);
    }

    @EventHandler
    public void onSpread(BlockSpreadEvent e) {
        e.setCancelled(true);
    }

    @EventHandler
    public void onFade(BlockFadeEvent e) {
        e.setCancelled(true);
    }

    @EventHandler
    public void onForm(BlockFormEvent e) {
        e.setCancelled(true);
    }

    @EventHandler
    public void onLeaves(LeavesDecayEvent e) {
        e.setCancelled(true);
    }

    @EventHandler
    public void onFlow(BlockFromToEvent e) {
        e.setCancelled(true);
    }

    @EventHandler
    public void onWeather(WeatherChangeEvent e) {
        if (e.toWeatherState()) e.setCancelled(true);
    }
}

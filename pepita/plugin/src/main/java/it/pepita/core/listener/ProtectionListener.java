package it.pepita.core.listener;

import it.pepita.core.PepitaCore;
import it.pepita.core.mine.Mine;
import it.pepita.core.util.Keys;
import it.pepita.core.world.CellGeometry;
import it.pepita.core.world.WorldService;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Protegge tutti i mondi di Pepita (hub, prigione, miniere, pvp, celle): niente griefing, niente fame,
 * si rompe solo nelle miniere, si costruisce solo dentro le proprie celle, PvP solo nella miniera PvP fuori dalla zona sicura.
 */
public final class ProtectionListener implements Listener {
    private final PepitaCore plugin;

    public ProtectionListener(PepitaCore plugin) {
        this.plugin = plugin;
    }

    private boolean managed(World w) {
        return plugin.world().managed(w);
    }

    private boolean inWorld(Entity e) {
        return managed(e.getWorld());
    }

    private boolean builder(Player p) {
        return p.getGameMode() == GameMode.CREATIVE && p.hasPermission("pepita.admin");
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        World w = b.getWorld();
        if (!managed(w) || builder(e.getPlayer())) return;
        WorldService ws = plugin.world();
        if (ws.isCells(w)) {
            if (!plugin.cells().canBuild(e.getPlayer(), b)) e.setCancelled(true);
            return;
        }
        if ((ws.isMines(w) || ws.isPvp(w)) && plugin.mines().at(b) != null) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!inWorld(e.getPlayer()) || builder(e.getPlayer())) return;
        if (plugin.world().isCells(e.getBlock().getWorld()) && plugin.cells().canBuild(e.getPlayer(), e.getBlock())) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (inWorld(e.getPlayer()) && !builder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (inWorld(e.getPlayer()) && !builder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent e) {
        if (inWorld(e.getPlayer()) && !builder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (inWorld(e.getPlayer()) && !builder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHanging(HangingBreakEvent e) {
        if (inWorld(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent e) {
        if (inWorld(e.getEntity()) && (e.getPlayer() == null || !builder(e.getPlayer()))) e.setCancelled(true);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (inWorld(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !inWorld(p)) return;
        if (e.getCause() == EntityDamageEvent.DamageCause.KILL) return;
        if (e instanceof EntityDamageByEntityEvent ev && plugin.world().isPvp(p.getWorld())) {
            if (ev.getDamager().getPersistentDataContainer().has(Keys.LUCKY, PersistentDataType.STRING)) {
                e.setCancelled(true);
                return;
            }
            Player attacker = null;
            if (ev.getDamager() instanceof Player a) attacker = a;
            else if (ev.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player a) attacker = a;
            if (attacker != null && !attacker.equals(p)) {
                Mine mv = plugin.mines().areaOf(p.getLocation());
                Mine ma = plugin.mines().areaOf(attacker.getLocation());
                boolean safe = plugin.world().inPvpSafe(p.getLocation()) || plugin.world().inPvpSafe(attacker.getLocation());
                if (mv != null && mv.pvp && ma == mv && !safe) return; // PvP consentito
            }
        }
        e.setCancelled(true);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (inWorld(e.getEntity())) {
            e.setCancelled(true);
            e.getEntity().setFoodLevel(20);
        }
    }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent e) {
        if (!inWorld(e.getEntity())) return;
        switch (e.getSpawnReason()) {
            case CUSTOM, COMMAND, SPAWNER_EGG -> {
            }
            default -> e.setCancelled(true);
        }
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        if (inWorld(e.getEntity())) e.blockList().clear();
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        if (managed(e.getBlock().getWorld())) e.blockList().clear();
    }

    @EventHandler
    public void onChangeBlock(EntityChangeBlockEvent e) {
        if (inWorld(e.getEntity()) && !(e.getEntity() instanceof Player)) e.setCancelled(true);
    }

    @EventHandler
    public void onBurn(BlockBurnEvent e) {
        if (managed(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler
    public void onSpread(BlockSpreadEvent e) {
        if (managed(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler
    public void onFade(BlockFadeEvent e) {
        if (managed(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler
    public void onForm(BlockFormEvent e) {
        if (managed(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler
    public void onLeaves(LeavesDecayEvent e) {
        if (managed(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler
    public void onFlow(BlockFromToEvent e) {
        if (managed(e.getBlock().getWorld())) e.setCancelled(true);
    }

    /**
     * Evita che portali, lanterne e decorazioni si rompano per aggiornamenti dei blocchi vicini:
     * fisica bloccata in hub e prigione, fuori dalle buche nelle miniere, fuori dagli interni delle celle.
     */
    @EventHandler
    public void onPhysics(BlockPhysicsEvent e) {
        Block b = e.getBlock();
        World w = b.getWorld();
        WorldService ws = plugin.world();
        if (!ws.managed(w)) return;
        if (ws.isHub(w) || ws.isPrison(w)) {
            e.setCancelled(true);
            return;
        }
        if (ws.isMines(w) || ws.isPvp(w)) {
            if (plugin.mines().at(b) == null) e.setCancelled(true);
            return;
        }
        if (ws.isCells(w) && CellGeometry.classify(b.getX(), b.getY(), b.getZ()).kind() != CellGeometry.Kind.INTERIOR) e.setCancelled(true);
    }

    @EventHandler
    public void onWeather(WeatherChangeEvent e) {
        if (managed(e.getWorld()) && e.toWeatherState()) e.setCancelled(true);
    }
}

package it.pepita.core.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import it.pepita.core.PepitaCore;
import it.pepita.core.cosmetics.PickaxeSkin;
import it.pepita.core.crate.Crate;
import it.pepita.core.data.PlayerData;
import it.pepita.core.gang.GangManager;
import it.pepita.core.gui.Menu;
import it.pepita.core.mine.Mine;
import it.pepita.core.pickaxe.MiningService;
import it.pepita.core.pickaxe.PickaxeManager;
import it.pepita.core.rank.RankManager;
import it.pepita.core.rank.VipTier;
import it.pepita.core.util.ItemBuilder;
import it.pepita.core.util.Txt;
import it.pepita.core.world.Layout;
import it.pepita.core.world.WorldService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Ingresso, uscita, chat, portali, interazioni e skin trascinate sul piccone. */
public final class PlayerListener implements Listener {
    private final PepitaCore plugin;
    private final Map<UUID, Long> portalCooldown = new HashMap<>();

    public PlayerListener(PepitaCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.data().get(p);
        boolean first = d.firstJoin == 0;
        if (first) {
            d.firstJoin = System.currentTimeMillis();
            d.addKeys("comune", 3);
            d.dirty = true;
        }
        e.joinMessage(Txt.mm(first
                ? "<dark_gray>[<gold>✦</gold>]</dark_gray> <gray>Un nuovo detenuto è arrivato: <gold>" + Txt.esc(p.getName()) + "</gold>!"
                : "<dark_gray>[<green>+</green>]</dark_gray> <gray>" + Txt.esc(p.getName())));
        if (first || !plugin.world().managed(p.getWorld()) || plugin.getConfig().getBoolean("mondo.spawn-ad-ogni-accesso", true))
            p.teleport(plugin.world().hubSpawn());
        if (p.getGameMode() != GameMode.CREATIVE) p.setGameMode(GameMode.SURVIVAL);
        plugin.picks().ensureKit(p);
        plugin.sidebar().create(p);
        plugin.sidebar().updateTab(p);
        p.setFoodLevel(20);
        var hp = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (hp != null) p.setHealth(hp.getValue());
        plugin.keyAll().onJoin(p);
        plugin.billboard().refreshView(p);
        plugin.tutorial().refreshView(p);

        plugin.packs().send(p);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            p.showTitle(Title.title(Txt.mm(Txt.LOGO),
                    Txt.mm(first ? "<gray>Benvenuto in prigione, <white>" + Txt.esc(p.getName()) + "</white>!" : "<gray>Bentornato, <white>" + Txt.esc(p.getName())),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(2500), Duration.ofMillis(800))));
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
            if (first) {
                Txt.raw(p, "");
                Txt.raw(p, "<gradient:#FFE259:#FFA751><b>━━━━━━━━ BENVENUTO SU PEPITA ━━━━━━━━</b></gradient>");
                Txt.raw(p, "<gray>Sei un detenuto: <white>scava</white>, guadagna e scala i rank da <white>A</white> a <gold>Z</gold>.");
                Txt.raw(p, "<gray>Parla con <gold>Beppe il Secondino</gold> davanti a te: ti guida passo passo.");
                Txt.raw(p, "<gray>La <yellow>bussola</yellow> ti porta ovunque: prigione, miniere, PvP, celle.");
                Txt.raw(p, "<gray>Hai ricevuto <white>3 chiavi Comuni</white>: aprile in <yellow>/casse</yellow>.");
                Txt.raw(p, "");
            }
            if (first && plugin.getConfig().getBoolean("tutorial.automatico", true)) plugin.tutorial().start(p);
            if (d.skinDeposito.size() > 0)
                Txt.send(p, "Hai <white>" + d.skinDeposito.size() + "</white> skin nel deposito: ritirale da <yellow>/skin</yellow>.");
            int t = plugin.milestones().claimable(d);
            if (t > 0) Txt.send(p, "Hai <white>" + t + "</white> traguardi da ritirare: <yellow>/traguardi</yellow>.");
        }, 40L);
    }

    @EventHandler
    public void onPack(PlayerResourcePackStatusEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.data().get(p);
        if (e.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            d.hasPack = true;
            plugin.sidebar().updateTab(p);
            plugin.picks().applyArmor(p, d);
            plugin.billboard().refreshView(p);
            plugin.tutorial().refreshView(p);
        } else if (e.getStatus() == PlayerResourcePackStatusEvent.Status.DECLINED || e.getStatus() == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD) {
            d.hasPack = false;
            Txt.send(p, "Senza resource pack non vedrai skin, menu decorati e logo. Puoi attivarlo dalla lista server.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        e.quitMessage(Txt.mm("<dark_gray>[<red>-</red>]</dark_gray> <gray>" + Txt.esc(p.getName())));
        plugin.sidebar().remove(p);
        plugin.tutorial().onQuit(p);
        plugin.data().unload(p.getUniqueId());
        portalCooldown.remove(p.getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        World w = e.getPlayer().getWorld();
        e.setRespawnLocation(plugin.world().isPvp(w) ? plugin.world().pvpSpawn() : plugin.world().isCells(w) ? plugin.world().cellsSpawn()
                : plugin.world().hubSpawn());
        Bukkit.getScheduler().runTask(plugin, () -> plugin.picks().ensureKit(e.getPlayer()));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
        Player p = e.getEntity();
        Player k = p.getKiller();
        if (k != null && !k.equals(p)) {
            PlayerData kd = plugin.data().get(k);
            double reward = 200.0 * (1 + plugin.data().get(p).prestige);
            plugin.eco().give(kd, it.pepita.core.economy.Economy.Cur.PEPITE, reward, "pvp-uccisioni");
            e.deathMessage(Txt.mm("<red>⚔</red> <white>" + Txt.esc(k.getName()) + "</white> <gray>ha eliminato <white>" + Txt.esc(p.getName())
                    + "</white> <gray>nella Miniera PvP " + Txt.pepite(reward)));
        } else e.deathMessage(null);
    }

    private static boolean soulbound(String id) {
        return id != null && (id.equals(PickaxeManager.PICKAXE) || id.equals(PickaxeManager.MENU) || id.equals(PickaxeManager.ARMOR)
                || id.equals(PickaxeManager.SELECTOR));
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (soulbound(ItemBuilder.idOf(e.getItemDrop().getItemStack()))) {
            e.setCancelled(true);
            Txt.send(e.getPlayer(), "Questo oggetto è legato a te e non si può buttare.");
        }
    }

    // ---------------- Skin trascinate sul piccone ----------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onInvClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getInventory().getHolder() instanceof Menu) return;
        ItemStack cursor = e.getCursor();
        ItemStack cur = e.getCurrentItem();
        PickaxeSkin s = PickaxeManager.skinOf(cursor);
        if (s != null && PickaxeManager.isPickaxe(cur)) {
            e.setCancelled(true);
            PlayerData d = plugin.data().get(p);
            if (s.id.equals(d.pickSkin)) {
                Txt.send(p, "Questa skin è già montata.");
                return;
            }
            cursor.setAmount(cursor.getAmount() - 1);
            p.setItemOnCursor(cursor.getAmount() <= 0 ? null : cursor);
            Bukkit.getScheduler().runTask(plugin, () -> plugin.picks().mount(p, d, s));
            return;
        }
        // gli oggetti legati non escono dall'inventario del giocatore
        if (e.getView().getTopInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING
                && (soulbound(ItemBuilder.idOf(cur)) || soulbound(ItemBuilder.idOf(cursor)))) {
            if (e.getClickedInventory() != null && e.getClickedInventory().equals(e.getView().getTopInventory()) || e.isShiftClick())
                e.setCancelled(true);
        }
    }

    // ---------------- Chat ----------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.data().get(p);
        GangManager.Gang g = plugin.gangs().get(d.gang);
        VipTier vt = VipTier.of(d.vip);
        String prefix = (g != null ? "<#FF7B7B>[" + g.name + "]</#FF7B7B> " : "")
                + "<dark_gray>[</dark_gray>" + RankManager.tag(d) + "<dark_gray>]</dark_gray> "
                + (vt != VipTier.NESSUNO ? vt.tag + " " : "");
        String nameColor = vt == VipTier.NESSUNO ? "<gray>" : "<white>";
        Component pre = Txt.mm(prefix + nameColor + Txt.esc(p.getName()) + " <dark_gray>»</dark_gray> ");
        boolean colored = vt.level >= 2;
        e.renderer((source, displayName, message, viewer) -> pre.append(message.color(colored ? net.kyori.adventure.text.format.NamedTextColor.WHITE : net.kyori.adventure.text.format.NamedTextColor.GRAY)));
    }

    // ---------------- Interazioni ----------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        WorldService ws = plugin.world();
        if (!ws.managed(p.getWorld())) return;
        if (e.getHand() == EquipmentSlot.OFF_HAND) {
            if (e.getAction().isRightClick()) e.setCancelled(true);
            return;
        }
        Block b = e.getClickedBlock();
        Action a = e.getAction();
        boolean admin = p.hasPermission("pepita.admin") && p.getGameMode() == GameMode.CREATIVE;

        // Colosseo: piastre degli ascensori e porte delle celle
        if (b != null && ws.isCells(b.getWorld())) {
            if (a == Action.PHYSICAL && b.getType() == Material.LIGHT_WEIGHTED_PRESSURE_PLATE) {
                e.setCancelled(true);
                plugin.cells().stepPad(p, b);
                return;
            }
            if (a == Action.RIGHT_CLICK_BLOCK && b.getType() == Material.IRON_DOOR && !admin) {
                if (plugin.cells().clickDoor(p, b)) {
                    e.setCancelled(true);
                    return;
                }
            }
        }

        if (a == Action.RIGHT_CLICK_BLOCK && b != null && ws.isPrison(b.getWorld())) {
            Crate c = ws.crateAt(b);
            if (c != null) {
                e.setCancelled(true);
                plugin.crates().preview(p, c);
                return;
            }
            if (ws.is(b, Layout.PRISON_ENCHANT)) {
                e.setCancelled(true);
                plugin.menus().enchants(p);
                return;
            }
            if (ws.is(b, Layout.PRISON_SHOP)) {
                e.setCancelled(true);
                plugin.menus().shop(p);
                return;
            }
            if (ws.is(b, Layout.PRISON_DAILY)) {
                e.setCancelled(true);
                plugin.menus().daily(p);
                return;
            }
            if (ws.is(b, Layout.PRISON_ARMOR)) {
                e.setCancelled(true);
                plugin.armor().open(p);
                return;
            }
            if (ws.is(b, Layout.PRISON_PASS)) {
                e.setCancelled(true);
                plugin.pass().open(p, 0);
                return;
            }
        }

        ItemStack hand = e.getItem();
        if (a.isRightClick() && hand != null) {
            String id = ItemBuilder.idOf(hand);
            if (id != null) {
                switch (id) {
                    case PickaxeManager.PICKAXE -> {
                        e.setCancelled(true);
                        if (p.isSneaking()) plugin.menus().pickaxe(p);
                        else plugin.menus().enchants(p);
                        return;
                    }
                    case PickaxeManager.MENU -> {
                        e.setCancelled(true);
                        plugin.menus().main(p);
                        return;
                    }
                    case PickaxeManager.SELECTOR -> {
                        e.setCancelled(true);
                        plugin.menus().selector(p);
                        return;
                    }
                    case PickaxeManager.PEPITA_ORO -> {
                        e.setCancelled(true);
                        hand.setAmount(hand.getAmount() - 1);
                        plugin.redeemPepita(p);
                        return;
                    }
                    default -> {
                        if (MiningService.isBomb(hand)) {
                            e.setCancelled(true);
                            plugin.mining().throwBomb(p, hand, id.substring(id.indexOf(':') + 1));
                            return;
                        }
                        PickaxeSkin s = PickaxeManager.skinOf(hand);
                        if (s != null) {
                            e.setCancelled(true);
                            PlayerData d = plugin.data().get(p);
                            if (s.id.equals(d.pickSkin)) {
                                Txt.send(p, "Questa skin è già montata sul piccone.");
                                return;
                            }
                            hand.setAmount(hand.getAmount() - 1);
                            plugin.picks().mount(p, d, s);
                            return;
                        }
                    }
                }
            }
        }
        // Blocca porte, bauli, leve ecc. ai non-admin (nelle proprie celle sì)
        if (b != null && !admin && (a == Action.RIGHT_CLICK_BLOCK || a == Action.PHYSICAL)) {
            if (ws.isCells(b.getWorld()) && plugin.cells().canBuild(p, b)) return;
            Material t = b.getType();
            if (t.isInteractable() || a == Action.PHYSICAL) e.setCancelled(true);
        }
    }

    // ---------------- Portali e vuoto ----------------

    private static boolean inBox(Object[] box, Location l) {
        int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
        int x1 = (Integer) box[1], y1 = (Integer) box[2], z1 = (Integer) box[3], x2 = (Integer) box[4], y2 = (Integer) box[5], z2 = (Integer) box[6];
        return x >= Math.min(x1, x2) - 1 && x <= Math.max(x1, x2) + 1 && y >= Math.min(y1, y2) - 1 && y <= Math.max(y1, y2) + 1
                && z >= Math.min(z1, z2) - 1 && z <= Math.max(z1, z2) + 1;
    }

    @EventHandler
    public void onPortalEnter(EntityPortalEnterEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        WorldService ws = plugin.world();
        World w = p.getWorld();
        if (!ws.managed(w)) return;
        long now = System.currentTimeMillis();
        if (now - portalCooldown.getOrDefault(p.getUniqueId(), 0L) < 2500) return;
        Location l = e.getLocation();
        String dest = null;
        if (ws.isHub(w)) {
            for (Object[] box : Layout.HUB_PORTALS) if (inBox(box, l)) dest = (String) box[0];
        } else if (ws.isPrison(w)) dest = "miniere";
        if (dest == null) return;
        portalCooldown.put(p.getUniqueId(), now);
        final String to = dest;
        Bukkit.getScheduler().runTask(plugin, () -> plugin.menus().travel(p, to));
    }

    @EventHandler
    public void onPortal(PlayerPortalEvent e) {
        if (plugin.world().managed(e.getFrom().getWorld())) e.setCancelled(true);
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent e) {
        plugin.tutorial().onWorldChange(e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo().getBlockY() == e.getFrom().getBlockY()) return;
        Player p = e.getPlayer();
        World w = p.getWorld();
        if (!plugin.world().managed(w)) return;
        if (e.getTo().getY() < 40) {
            p.setFallDistance(0);
            Location to;
            if (plugin.world().isMines(w) || plugin.world().isPvp(w)) {
                Mine m = plugin.mines().nearest(e.getTo());
                to = m != null ? m.spawn(w) : plugin.world().spawnOf(w);
            } else to = plugin.world().spawnOf(w);
            p.teleport(to);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1f);
        }
    }
}

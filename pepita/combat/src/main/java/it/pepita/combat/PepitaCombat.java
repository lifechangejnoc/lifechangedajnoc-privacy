package it.pepita.combat;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BlocksAttacks;
import io.papermc.paper.datacomponent.item.blocksattacks.DamageReduction;
import io.papermc.paper.datacomponent.item.blocksattacks.ItemDamageFunction;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Combattimento della 1.8.9 sul server 26.2:
 * <ul>
 *     <li>niente tempo di ricarica (attributo attack_speed altissimo: si colpisce a ogni click);</li>
 *     <li>knockback calcolato con la formula della 1.8 (profili configurabili) e invulnerabilità regolabile per le combo;</li>
 *     <li>danni delle armi, Affilatezza e critici come in 1.8 (critici anche correndo);</li>
 *     <li>niente colpo a spazzata, parata con la spada (componente blocks_attacks), niente scudi, canna da pesca 1.8.</li>
 * </ul>
 * I suoni nuovi degli attacchi li riproduce il client: li toglie il resource pack PepitaCombat-pack.zip.
 */
public final class PepitaCombat extends JavaPlugin implements Listener {
    /** Valore della 26.2 dell'attributo attack_speed dei giocatori. */
    private static final double DEFAULT_ATTACK_SPEED = 4.0;

    /** Un profilo di knockback. */
    record Profile(String name, double horizontal, double vertical, double verticalLimit, double sprintHorizontal,
                   double sprintVertical, double friction, int hitDelay) {}

    /** Spinta calcolata per un giocatore colpito, da applicare quando il server gli manda la velocità. */
    private record Pending(Vector velocity, long at) {}

    private Profile profile;
    private final Map<Material, Double> damage = new EnumMap<>(Material.class);
    private final Map<UUID, Pending> pending = new HashMap<>();
    private PackSender packs;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        load();
        getServer().getPluginManager().registerEvents(this, this);
        for (Player p : Bukkit.getOnlinePlayers()) apply(p);
        // le spade che arrivano da casse, negozi, comandi ecc. diventano parabili
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!getConfig().getBoolean("parata-spada", true)) return;
            for (Player p : Bukkit.getOnlinePlayers()) swords(p);
        }, 40L, 40L);
        getLogger().info("Combattimento 1.8 attivo, profilo \"" + profile.name() + "\".");
    }

    @Override
    public void onDisable() {
        // senza il plugin si torna al combattimento della 26.2
        for (Player p : Bukkit.getOnlinePlayers()) reset(p);
    }

    private void load() {
        reloadConfig();
        String name = getConfig().getString("profilo", "pepita");
        ConfigurationSection s = getConfig().getConfigurationSection("profili." + name);
        if (s == null) {
            getLogger().warning("Profilo \"" + name + "\" non trovato: uso quello della 1.8.");
            s = getConfig().getConfigurationSection("profili.1.8");
            name = "1.8";
        }
        profile = s == null ? new Profile("1.8", 0.4, 0.4, 0.4, 0.5, 0.1, 2.0, 20)
                : new Profile(name, s.getDouble("orizzontale", 0.4), s.getDouble("verticale", 0.4), s.getDouble("limite-verticale", 0.4),
                s.getDouble("sprint-orizzontale", 0.5), s.getDouble("sprint-verticale", 0.1), Math.max(1, s.getDouble("attrito", 2.0)),
                Math.max(1, s.getInt("ritardo-colpi", 20)));
        damage.clear();
        ConfigurationSection w = getConfig().getConfigurationSection("danni-1.8.armi");
        if (w != null) for (String k : w.getKeys(false)) {
            Material m = Material.matchMaterial(k);
            if (m != null) damage.put(m, w.getDouble(k));
            else getLogger().warning("danni-1.8: oggetto sconosciuto " + k);
        }
        packs = new PackSender(this);
    }

    // =====================================================================
    //  Giocatori: velocità d'attacco e invulnerabilità
    // =====================================================================

    private void apply(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.ATTACK_SPEED);
        if (a != null) a.setBaseValue(getConfig().getDouble("velocita-attacco", 1024));
        p.setMaximumNoDamageTicks(profile.hitDelay());
        if (getConfig().getBoolean("parata-spada", true)) swords(p);
    }

    private void reset(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.ATTACK_SPEED);
        if (a != null) a.setBaseValue(DEFAULT_ATTACK_SPEED);
        p.setMaximumNoDamageTicks(20);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        apply(e.getPlayer());
        // dopo il pack di Pepita (che viene mandato all'ingresso), il pack dei suoni 1.8 si aggiunge sopra
        Bukkit.getScheduler().runTaskLater(this, () -> packs.send(e.getPlayer()), 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // l'attributo resta salvato nel giocatore: lo rimettiamo normale, all'ingresso torna quello della 1.8
        reset(e.getPlayer());
        pending.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Bukkit.getScheduler().runTask(this, () -> apply(e.getPlayer()));
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        apply(e.getPlayer());
    }

    // =====================================================================
    //  Danni e critici 1.8
    // =====================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!(e.getDamager() instanceof Player a) || !(e.getEntity() instanceof Player v)) return;
        boolean oldDamage = getConfig().getBoolean("danni-1.8.attivi", true);
        boolean oldCrit = getConfig().getBoolean("critici-1.8.attivi", true);
        if (!oldDamage && !oldCrit) return;
        ItemStack hand = a.getInventory().getItemInMainHand();
        boolean crit = oldCrit ? crit18(a) : e.isCritical();
        if (oldDamage) {
            Double base = hand.getType().isAir() ? (Double) getConfig().getDouble("danni-1.8.pugno", 1.0) : damage.get(hand.getType());
            if (base == null) return; // armi non elencate: danno della 26.2
            double d = base;
            PotionEffect str = a.getPotionEffect(PotionEffectType.STRENGTH);
            if (str != null) d += getConfig().getDouble("danni-1.8.forza", 3.0) * (str.getAmplifier() + 1);
            PotionEffect weak = a.getPotionEffect(PotionEffectType.WEAKNESS);
            if (weak != null) d -= getConfig().getDouble("danni-1.8.debolezza", 4.0) * (weak.getAmplifier() + 1);
            d = Math.max(0, d);
            if (crit) d *= getConfig().getDouble("critici-1.8.moltiplicatore", 1.5);
            d += hand.getEnchantmentLevel(Enchantment.SHARPNESS) * getConfig().getDouble("danni-1.8.affilatezza", 1.25);
            e.setDamage(d);
        } else if (crit && !e.isCritical()) {
            e.setDamage(e.getDamage() * getConfig().getDouble("critici-1.8.moltiplicatore", 1.5));
        }
        if (crit && !e.isCritical())
            v.getWorld().spawnParticle(Particle.CRIT, v.getLocation().add(0, v.getHeight() * 0.6, 0), 14, 0.3, 0.4, 0.3, 0.25);
    }

    /** Critico della 1.8: cadendo, non a terra, non in acqua, non su scale/rampicanti, non accecato, non a cavallo. Anche correndo. */
    @SuppressWarnings("deprecation")
    private static boolean crit18(Player a) {
        return a.getFallDistance() > 0 && !a.isOnGround() && !a.isClimbing() && !a.isInWater() && a.getVehicle() == null
                && !a.hasPotionEffect(PotionEffectType.BLINDNESS);
    }

    // =====================================================================
    //  Knockback 1.8
    // =====================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!(e.getEntity() instanceof Player v) || !(e.getDamager() instanceof Player a)) return;
        // 1.8: la resistenza al contraccolpo (es. netherite) è una probabilità di non essere spinti
        AttributeInstance res = v.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (res != null && ThreadLocalRandom.current().nextDouble() < res.getValue()) return;
        Profile pr = profile;
        Vector now = v.getVelocity();
        double dx = a.getX() - v.getX(), dz = a.getZ() - v.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 1.0E-4) {
            double yaw = Math.toRadians(a.getYaw());
            dx = Math.sin(yaw);
            dz = -Math.cos(yaw);
            dist = 1;
        }
        double kx = now.getX() / pr.friction() - dx / dist * pr.horizontal();
        double ky = Math.min(pr.verticalLimit(), now.getY() / pr.friction() + pr.vertical());
        double kz = now.getZ() / pr.friction() - dz / dist * pr.horizontal();
        // spinta in più correndo e per livello di Contraccolpo (verso dove guarda chi colpisce)
        int level = a.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.KNOCKBACK) + (a.isSprinting() ? 1 : 0);
        if (level > 0) {
            double yaw = Math.toRadians(a.getYaw());
            kx += -Math.sin(yaw) * level * pr.sprintHorizontal();
            kz += Math.cos(yaw) * level * pr.sprintHorizontal();
            ky += pr.sprintVertical();
        }
        pending.put(v.getUniqueId(), new Pending(new Vector(kx, ky, kz), System.currentTimeMillis()));
    }

    /** Il server sta mandando al colpito la spinta della 26.2: la sostituiamo con quella della 1.8. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent e) {
        Pending p = pending.remove(e.getPlayer().getUniqueId());
        if (p != null && System.currentTimeMillis() - p.at() < 150) e.setVelocity(p.velocity());
    }

    /** Spazzata (1.9+) e spinta della parata contro chi attacca: non esistevano in 1.8. */
    @EventHandler(ignoreCancelled = true)
    public void onKnockback(EntityKnockbackEvent e) {
        if (e.getCause() == EntityKnockbackEvent.Cause.SWEEP_ATTACK && !getConfig().getBoolean("spazzata", false)) e.setCancelled(true);
        if (e.getCause() == EntityKnockbackEvent.Cause.SHIELD_BLOCK && getConfig().getBoolean("parata-spada", true)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSweep(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK && !getConfig().getBoolean("spazzata", false)) e.setCancelled(true);
    }

    // =====================================================================
    //  Parata con la spada, scudi, canna da pesca
    // =====================================================================

    private BlocksAttacks swordBlock() {
        return BlocksAttacks.blocksAttacks()
                .blockDelaySeconds(0)
                .disableCooldownScale(0)
                // come in 1.8: danno preso = (danno + 1) / 2, da qualunque direzione
                .addDamageReduction(DamageReduction.damageReduction().horizontalBlockingAngle(180).base(-0.5f).factor(0.5f).build())
                .itemDamage(ItemDamageFunction.itemDamageFunction().threshold(0).base(0).factor(0).build())
                .blockSound(null)
                .disableSound(null)
                .build();
    }

    private void swords(Player p) {
        PlayerInventory inv = p.getInventory();
        BlocksAttacks ba = null;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || !Tag.ITEMS_SWORDS.isTagged(it.getType()) || it.hasData(DataComponentTypes.BLOCKS_ATTACKS)) continue;
            if (ba == null) ba = swordBlock();
            it.setData(DataComponentTypes.BLOCKS_ATTACKS, ba);
            inv.setItem(i, it);
        }
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent e) {
        if (getConfig().getBoolean("parata-spada", true)) swords(e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player) || !getConfig().getBoolean("parata-spada", true)) return;
        ItemStack it = e.getItem().getItemStack();
        if (Tag.ITEMS_SWORDS.isTagged(it.getType()) && !it.hasData(DataComponentTypes.BLOCKS_ATTACKS)) {
            it.setData(DataComponentTypes.BLOCKS_ATTACKS, swordBlock());
            e.getItem().setItemStack(it);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onShield(PlayerInteractEvent e) {
        if (getConfig().getBoolean("scudi", false) || !e.getAction().isRightClick()) return;
        ItemStack it = e.getItem();
        if (it != null && it.getType() == Material.SHIELD) {
            e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            e.setCancelled(true);
        }
    }

    /** Canna da pesca della 1.8: l'amo che colpisce un giocatore lo spinge (e passa dalle protezioni come un colpo). */
    @EventHandler(ignoreCancelled = true)
    public void onRod(ProjectileHitEvent e) {
        if (!getConfig().getBoolean("canna-da-pesca", true)) return;
        if (!(e.getEntity() instanceof FishHook hook) || !(e.getHitEntity() instanceof Player v)) return;
        if (!(hook.getShooter() instanceof Player shooter) || shooter.equals(v)) return;
        if (v.getNoDamageTicks() > v.getMaximumNoDamageTicks() / 2) return;
        v.damage(0.01, shooter); // conta come colpo: invulnerabilità, protezioni del server e knockback 1.8
    }

    // =====================================================================
    //  Comandi
    // =====================================================================

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, String @NotNull [] a) {
        MiniMessage mm = MiniMessage.miniMessage();
        String sub = a.length == 0 ? "" : a[0].toLowerCase();
        switch (sub) {
            case "reload" -> {
                load();
                for (Player p : Bukkit.getOnlinePlayers()) apply(p);
                s.sendMessage(mm.deserialize("<gold>Combattimento 1.8</gold> <gray>ricaricato, profilo <white>" + profile.name()));
            }
            case "profilo" -> {
                if (a.length < 2 || !getConfig().isConfigurationSection("profili." + a[1])) {
                    s.sendMessage(mm.deserialize("<gray>Profili: <white>" + String.join(", ", getConfig().getConfigurationSection("profili").getKeys(false))));
                    return true;
                }
                getConfig().set("profilo", a[1]);
                saveConfig();
                load();
                for (Player p : Bukkit.getOnlinePlayers()) apply(p);
                s.sendMessage(mm.deserialize("<gold>Combattimento 1.8</gold> <gray>profilo <white>" + profile.name()));
            }
            default -> {
                Profile p = profile;
                s.sendMessage(mm.deserialize("<gold><b>Combattimento 1.8</b></gold> <gray>profilo <white>" + p.name()
                        + "</white> • orizzontale <white>" + p.horizontal() + "</white> • verticale <white>" + p.vertical()
                        + "</white> • sprint <white>" + p.sprintHorizontal() + "</white> • ritardo <white>" + p.hitDelay() + " tick"));
                s.sendMessage(mm.deserialize("<yellow>/pepitacombat profilo <nome></yellow> <gray>cambia profilo • <yellow>/pepitacombat reload"));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, String @NotNull [] a) {
        if (a.length == 1) return List.of("reload", "profilo");
        if (a.length == 2 && a[0].equalsIgnoreCase("profilo") && getConfig().isConfigurationSection("profili"))
            return List.copyOf(getConfig().getConfigurationSection("profili").getKeys(false));
        return List.of();
    }
}

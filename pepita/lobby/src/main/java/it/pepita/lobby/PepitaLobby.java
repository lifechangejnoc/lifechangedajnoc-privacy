package it.pepita.lobby;

import it.pepita.lobby.gui.MenuListener;
import it.pepita.lobby.util.ItemBuilder;
import it.pepita.lobby.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server della lobby della rete Pepita: si arriva dal proxy, si sceglie la modalità con la bussola
 * (o con l'NPC) e si viene mandati al server di quella modalità. Niente del Prison qui.
 */
public final class PepitaLobby extends JavaPlugin {
    /** Id della bussola delle modalità. */
    public static final String COMPASS = "modalita";

    private static PepitaLobby instance;
    private Modes modes;
    private Network network;
    private PackService packs;
    private Board board;
    private Npcs npcs;
    private final Set<UUID> withPack = ConcurrentHashMap.newKeySet();
    private final Set<UUID> flying = ConcurrentHashMap.newKeySet();

    public static PepitaLobby get() { return instance; }
    public Modes modes() { return modes; }
    public Network network() { return network; }
    public PackService packs() { return packs; }
    public Board board() { return board; }
    public Npcs npcs() { return npcs; }

    public boolean hasPack(Player p) {
        return withPack.contains(p.getUniqueId());
    }

    void setPack(Player p, boolean v) {
        if (v) withPack.add(p.getUniqueId());
        else withPack.remove(p.getUniqueId());
    }

    Set<UUID> flying() {
        return flying;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        Keys.init(this);
        modes = new Modes(this);
        network = new Network(this);
        packs = new PackService(this);
        board = new Board(this);
        npcs = new Npcs(this);

        getServer().getMessenger().registerOutgoingPluginChannel(this, Network.CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, Network.CHANNEL, network);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(new LobbyListener(this), this);
        pm.registerEvents(npcs, this);
        LobbyCommands cmds = new LobbyCommands(this);
        for (String c : getDescription().getCommands().keySet()) {
            PluginCommand pc = getCommand(c);
            if (pc != null) {
                pc.setExecutor(cmds);
                pc.setTabCompleter(cmds);
            }
        }

        for (World w : Bukkit.getWorlds()) setupWorld(w);
        Bukkit.getScheduler().runTask(this, () -> {
            replaceBlocks();
            npcs.spawnAll();
            for (Player p : Bukkit.getOnlinePlayers()) {
                board.create(p);
                prepare(p);
            }
        });
        // ogni 2 secondi: giocatori per modalità (dal proxy), scoreboard, tab e scritte degli NPC
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            network.refresh();
            board.updateAll();
            npcs.updateLabels();
        }, 40L, 40L);
        getLogger().info("Lobby di Pepita pronta: " + modes.all().size() + " modalità nel menu.");
    }

    @Override
    public void onDisable() {
        if (npcs != null) npcs.removeAll();
    }

    /** Ricarica config.yml (modalità, spawn, NPC, pack). */
    public void reload() {
        reloadConfig();
        modes = new Modes(this);
        packs = new PackService(this);
        npcs.spawnAll();
        for (World w : Bukkit.getWorlds()) setupWorld(w);
    }

    // =====================================================================
    //  Spawn e kit della lobby
    // =====================================================================

    public Location spawn() {
        String wn = getConfig().getString("spawn.mondo", "");
        World w = wn == null || wn.isBlank() ? Bukkit.getWorlds().getFirst() : Bukkit.getWorld(wn);
        if (w == null) w = Bukkit.getWorlds().getFirst();
        return new Location(w, getConfig().getDouble("spawn.x", 0.5), getConfig().getDouble("spawn.y", 100),
                getConfig().getDouble("spawn.z", 0.5), (float) getConfig().getDouble("spawn.yaw", 0), (float) getConfig().getDouble("spawn.pitch", 0));
    }

    public void setSpawn(Location l) {
        getConfig().set("spawn.mondo", l.getWorld().equals(Bukkit.getWorlds().getFirst()) ? "" : l.getWorld().getName());
        getConfig().set("spawn.x", Math.floor(l.getX() * 2) / 2);
        getConfig().set("spawn.y", Math.floor(l.getY() * 2) / 2);
        getConfig().set("spawn.z", Math.floor(l.getZ() * 2) / 2);
        getConfig().set("spawn.yaw", (double) Math.round(l.getYaw() / 15f) * 15);
        getConfig().set("spawn.pitch", 0.0);
        saveConfig();
        l.getWorld().setSpawnLocation(spawn());
    }

    public ItemStack compass() {
        return new ItemBuilder(Material.COMPASS)
                .name("<gradient:#FFE259:#FFA751><b>Modalità</b></gradient> <gray>(tasto destro)")
                .lore("<gray>Scegli a cosa giocare.", "", "<#FFD54A>▶ Tasto destro per aprire")
                .model("selettore").id(COMPASS).glow().build();
    }

    public static boolean isCompass(ItemStack it) {
        return ItemBuilder.is(it, COMPASS);
    }

    /** Inventario della lobby: solo la bussola. Chi è in creativa (staff che costruisce) non viene toccato. */
    public void prepare(Player p) {
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setArmorContents(new ItemStack[4]);
        inv.setItemInOffHand(null);
        inv.setItem(4, compass());
        inv.setHeldItemSlot(4);
        p.setGameMode(GameMode.ADVENTURE);
        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
        var hp = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (hp != null) p.setHealth(hp.getValue());
        p.setFoodLevel(20);
        p.setSaturation(20);
        p.setFireTicks(0);
        p.setLevel(0);
        p.setExp(0);
        boolean fly = flying.contains(p.getUniqueId()) && p.hasPermission("pepita.fly");
        p.setAllowFlight(fly);
        p.setFlying(fly);
    }

    // =====================================================================
    //  Mondi
    // =====================================================================

    private void setupWorld(World w) {
        w.setDifficulty(Difficulty.PEACEFUL);
        rule(w, GameRules.ADVANCE_TIME, false);
        rule(w, GameRules.ADVANCE_WEATHER, false);
        rule(w, GameRules.SPAWN_MOBS, false);
        rule(w, GameRules.SPAWN_MONSTERS, false);
        rule(w, GameRules.SPAWN_PHANTOMS, false);
        rule(w, GameRules.SPAWN_PATROLS, false);
        rule(w, GameRules.SPAWN_WANDERING_TRADERS, false);
        rule(w, GameRules.SPAWN_WARDENS, false);
        rule(w, GameRules.KEEP_INVENTORY, true);
        rule(w, GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        rule(w, GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        rule(w, GameRules.FALL_DAMAGE, false);
        rule(w, GameRules.IMMEDIATE_RESPAWN, true);
        rule(w, GameRules.ALLOW_ENTERING_NETHER_USING_PORTALS, false);
        rule(w, GameRules.MOB_GRIEFING, false);
        rule(w, GameRules.RANDOM_TICK_SPEED, 0);
        rule(w, GameRules.RESPAWN_RADIUS, 0);
        rule(w, GameRules.LOCATOR_BAR, false);
        rule(w, GameRules.SPREAD_VINES, false);
        rule(w, GameRules.BLOCK_DROPS, false);
        rule(w, GameRules.PVP, false);
        w.setTime(getConfig().getLong("ora", 6000));
        w.setStorm(false);
        w.setThundering(false);
        if (w.equals(spawn().getWorld())) w.setSpawnLocation(spawn());
    }

    private static <T> void rule(World w, GameRule<T> r, T v) {
        try {
            w.setGameRule(r, v);
        } catch (Exception ignored) {
        }
    }

    /** "x y z blocco" da sostituisci-blocchi (es. lodestone della mappa che col pack sembrano nuclei Quantum). */
    private void replaceBlocks() {
        World w = spawn().getWorld();
        int n = 0;
        for (String line : getConfig().getStringList("sostituisci-blocchi")) {
            String[] t = line.trim().split("\\s+", 4);
            if (t.length < 4) continue;
            try {
                var b = w.getBlockAt(Integer.parseInt(t[0]), Integer.parseInt(t[1]), Integer.parseInt(t[2]));
                var data = Bukkit.createBlockData(t[3]);
                if (!b.getBlockData().matches(data)) {
                    b.setBlockData(data, false);
                    n++;
                }
            } catch (IllegalArgumentException ex) {
                getLogger().warning("sostituisci-blocchi, riga non valida: " + line);
            }
        }
        if (n > 0) getLogger().info("Sostituiti " + n + " blocchi della mappa.");
    }
}

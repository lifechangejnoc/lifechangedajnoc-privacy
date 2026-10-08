package it.pepita.core;

import it.pepita.core.armor.ArmorService;
import it.pepita.core.cell.CellManager;
import it.pepita.core.command.Commands;
import it.pepita.core.crate.CrateManager;
import it.pepita.core.data.DataManager;
import it.pepita.core.data.PlayerData;
import it.pepita.core.display.Sidebar;
import it.pepita.core.economy.Economy;
import it.pepita.core.event.BoosterManager;
import it.pepita.core.event.GoldRush;
import it.pepita.core.event.KeyAll;
import it.pepita.core.gang.GangManager;
import it.pepita.core.gui.MenuListener;
import it.pepita.core.gui.Menus;
import it.pepita.core.listener.PlayerListener;
import it.pepita.core.listener.ProtectionListener;
import it.pepita.core.lucky.LuckyBlocks;
import it.pepita.core.mine.Mine;
import it.pepita.core.mine.MineManager;
import it.pepita.core.mine.Prices;
import it.pepita.core.pass.BattlePass;
import it.pepita.core.pass.Milestones;
import it.pepita.core.pickaxe.MiningService;
import it.pepita.core.pickaxe.PickaxeManager;
import it.pepita.core.quantum.QuantumService;
import it.pepita.core.rank.RankManager;
import it.pepita.core.tutorial.Tutorial;
import it.pepita.core.util.Fmt;
import it.pepita.core.util.Keys;
import it.pepita.core.util.Txt;
import it.pepita.core.world.Billboard;
import it.pepita.core.world.Holograms;
import it.pepita.core.world.Layout;
import it.pepita.core.world.Renderer;
import it.pepita.core.world.WorldService;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/** PepitaCore — il plugin tutto-in-uno del server prison Pepita. */
public final class PepitaCore extends JavaPlugin implements Listener {
    private static PepitaCore instance;

    private DataManager data;
    private Economy eco;
    private RankManager ranks;
    private MineManager mines;
    private PickaxeManager picks;
    private ArmorService armor;
    private MiningService mining;
    private QuantumService quantum;
    private LuckyBlocks lucky;
    private CrateManager crates;
    private BoosterManager boosters;
    private GoldRush goldRush;
    private KeyAll keyAll;
    private BattlePass pass;
    private Milestones milestones;
    private GangManager gangs;
    private WorldService world;
    private CellManager cells;
    private Holograms holograms;
    private Billboard billboard;
    private Tutorial tutorial;
    private it.pepita.core.lobby.Lobby lobby;
    private Sidebar sidebar;
    private Menus menus;
    private Renderer renderer;
    private it.pepita.core.listener.ResourcePackService packs;

    public static PepitaCore get() { return instance; }

    /**
     * Rete Velocity: questo server è solo il Prison, la lobby è un server a parte (plugin PepitaLobby).
     * Si attiva con rete.attiva: true in config.yml.
     */
    public boolean network() { return getConfig().getBoolean("rete.attiva", false); }

    /** Manda un giocatore a un altro server della rete (canale BungeeCord, supportato da Velocity). */
    public void connect(Player p, String server) {
        try {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(b);
            out.writeUTF("Connect");
            out.writeUTF(server);
            p.sendPluginMessage(this, "BungeeCord", b.toByteArray());
        } catch (java.io.IOException ex) {
            getLogger().warning("Impossibile mandare " + p.getName() + " al server " + server + ": " + ex.getMessage());
        }
    }

    /** Torna alla lobby: in rete va al server della lobby, altrimenti al mondo della lobby. */
    public void toLobby(Player p) {
        if (network()) {
            Txt.send(p, "Torno alla <gold>lobby</gold>...");
            connect(p, getConfig().getString("rete.server-lobby", "lobby"));
        } else {
            p.teleport(world.hubSpawn());
            p.sendActionBar(Txt.mm("<gradient:#FFE259:#FFA751>Lobby di Pepita"));
        }
    }
    public DataManager data() { return data; }
    public Economy eco() { return eco; }
    public RankManager ranks() { return ranks; }
    public MineManager mines() { return mines; }
    public PickaxeManager picks() { return picks; }
    public ArmorService armor() { return armor; }
    public MiningService mining() { return mining; }
    public QuantumService quantum() { return quantum; }
    public LuckyBlocks lucky() { return lucky; }
    public CrateManager crates() { return crates; }
    public BoosterManager boosters() { return boosters; }
    public GoldRush goldRush() { return goldRush; }
    public KeyAll keyAll() { return keyAll; }
    public BattlePass pass() { return pass; }
    public Milestones milestones() { return milestones; }
    public GangManager gangs() { return gangs; }
    public WorldService world() { return world; }
    public CellManager cells() { return cells; }
    public Holograms holograms() { return holograms; }
    public Billboard billboard() { return billboard; }
    public Tutorial tutorial() { return tutorial; }
    public it.pepita.core.lobby.Lobby lobby() { return lobby; }
    public Sidebar sidebar() { return sidebar; }
    public Menus menus() { return menus; }
    public Renderer renderer() { return renderer; }
    public it.pepita.core.listener.ResourcePackService packs() { return packs; }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        // v1 → v2: il vecchio spawn della prigione era nello stesso mondo "pepita"
        boolean migratePrison = getConfig().getBoolean("mondo.costruito", false)
                && !getConfig().contains("mondi.costruiti." + Layout.W_PRISON, true);
        getConfig().options().copyDefaults(true);
        saveConfig();
        Keys.init(this);
        Prices.load(getConfig().getConfigurationSection("prezzi-blocchi"));

        data = new DataManager(this);
        eco = new Economy(this);
        ranks = new RankManager(this);
        mines = new MineManager(this);
        mines.load();
        picks = new PickaxeManager(this);
        armor = new ArmorService(this);
        mining = new MiningService(this);
        quantum = new QuantumService(this);
        lucky = new LuckyBlocks(this);
        crates = new CrateManager(this);
        boosters = new BoosterManager(this);
        goldRush = new GoldRush(this);
        keyAll = new KeyAll(this);
        pass = new BattlePass(this);
        milestones = new Milestones(this);
        gangs = new GangManager(this);
        world = new WorldService(this);
        world.setMigrateOldPrison(migratePrison);
        world.load();
        world.keepMinesLoaded();
        cells = new CellManager(this);
        holograms = new Holograms(this);
        billboard = new Billboard(this);
        tutorial = new Tutorial(this);
        lobby = new it.pepita.core.lobby.Lobby(this);
        sidebar = new Sidebar(this);
        menus = new Menus(this);
        renderer = new Renderer(this);
        packs = new it.pepita.core.listener.ResourcePackService(this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        var pm = Bukkit.getPluginManager();
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(tutorial, this);
        pm.registerEvents(lobby, this);
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new ProtectionListener(this), this);
        pm.registerEvents(mining, this);
        pm.registerEvents(quantum, this);
        pm.registerEvents(armor, this);
        pm.registerEvents(this, this);

        Commands cmds = new Commands(this);
        for (String c : getDescription().getCommands().keySet()) {
            PluginCommand pc = getCommand(c);
            if (pc != null) {
                pc.setExecutor(cmds);
                pc.setTabCompleter(cmds);
            }
        }

        // Costruisce i mondi non ancora costruiti (prima volta, o nuovi mondi della v2), poi resetta le miniere
        world.buildMissing(() -> {
            holograms.spawnAll();
            getLogger().info("Tutti i mondi di Pepita sono pronti.");
        });
        mines.resetAll();
        Bukkit.getScheduler().runTaskLater(this, holograms::spawnAll, 5L);

        for (Player p : Bukkit.getOnlinePlayers()) {
            data.get(p);
            sidebar.create(p);
            lobby.sync(p);
        }
        data.refreshTops();
        startTasks();
        getLogger().info("Pepita è pronta! ⛏");
    }

    /** Chiamato da WorldService quando un mondo è stato (ri)costruito. */
    public void onWorldBuilt(String w) {
        switch (w) {
            case Layout.W_MINES, Layout.W_PVP -> mines.resetWorld(w);
            default -> {
            }
        }
        Bukkit.getScheduler().runTaskLater(this, holograms::spawnAll, 10L);
        for (Player p : Bukkit.getOnlinePlayers())
            if (p.getWorld().getName().equals(w)) p.teleport(world.spawnOf(p.getWorld()));
    }

    @Override
    public void onDisable() {
        if (eco != null) eco.flush();
        if (data != null) data.saveAll(false);
        if (gangs != null) gangs.save(true);
        if (cells != null) cells.save();
        if (goldRush != null) goldRush.hideAll();
        if (boosters != null) boosters.hideAll();
        if (keyAll != null) keyAll.hideAll();
        if (tutorial != null) tutorial.removeNpc();
        if (lobby != null) lobby.removeNpc();
        if (billboard != null) billboard.removeAll();
        if (holograms != null && world != null) holograms.removeAll();
        if (quantum != null) for (Player p : Bukkit.getOnlinePlayers()) quantum.removeSpeed(p);
    }

    private void startTasks() {
        // Ogni tick: reset delle miniere a lotti
        Bukkit.getScheduler().runTaskTimer(this, mines::tickResets, 1L, 1L);

        // Ogni secondo: miniere, eventi, scoreboard, action bar, effetti del piccone, volo, quantum
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            mines.tick();
            goldRush.tick();
            boosters.tick();
            keyAll.tick();
            quantum.tick();
            cells.tick();
            tutorial.tick();
            long now = System.currentTimeMillis();
            for (Player p : Bukkit.getOnlinePlayers()) {
                PlayerData d = data.get(p);
                d.playtime++;
                sidebar.update(p);
                if (d.secSoldi > 0 || d.secPepite > 0) {
                    String combo = d.active("furia") > 0 && d.combo > 10 ? " <dark_gray>| <#FF7B7B>Furia " + Fmt.num(d.combo) : "";
                    if (!world.isPvp(p.getWorld()) || d.quantumTasca <= 0)
                        p.sendActionBar(Txt.mm("<" + Txt.SOLDI + ">+$" + Fmt.num(d.secSoldi) + " <dark_gray>| <" + Txt.PEPITE + ">+"
                                + Fmt.num(d.secPepite) + " ✦" + combo));
                    d.secSoldi = 0;
                    d.secPepite = 0;
                }
                if (d.pickDirty && now - d.lastPickRefresh > 15000) {
                    d.pickDirty = false;
                    d.lastPickRefresh = now;
                    picks.refresh(p);
                }
                applyEffects(p, d);
            }
        }, 20L, 20L);

        // Ogni 2 secondi: logo animato
        Bukkit.getScheduler().runTaskTimer(this, billboard::tick, 40L, 40L);

        // Ogni 3 secondi: tab ed etichette delle miniere
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) sidebar.updateTab(p);
            holograms.updateMines();
        }, 60L, 60L);

        // Ogni minuto: contatori dell'economia
        Bukkit.getScheduler().runTaskTimer(this, eco::tick, 1200L, 1200L);

        // Ogni 2 minuti: salvataggio
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (PlayerData d : data.online()) if (d.dirty) data.save(d, true);
            gangs.save(false);
        }, 2400L, 2400L);

        // Ogni 5 minuti: classifiche
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            data.refreshTops();
            Bukkit.getScheduler().runTaskLater(this, holograms::updateBoards, 60L);
        }, 200L, 6000L);

        // Ogni 10 minuti: tasse delle celle
        Bukkit.getScheduler().runTaskTimer(this, cells::taxTick, 2400L, 12000L);

        // Annunci
        String[] tips = {
                "Tasto destro col piccone per potenziarlo con le <gold>Pepite</gold>!",
                "Cerca la <gold>Pepita d'Oro</gold>: scatena la Corsa all'Oro per tutti.",
                "Ritira il premio <yellow>/giornaliero</yellow> ogni 24 ore.",
                "Fonda una gang con <yellow>/gang crea</yellow> e scalate la classifica insieme.",
                "Al rank <gold>Z</gold> usa <light_purple>/prestigio</light_purple> per ricominciare più forte.",
                "Le skin del piccone sono oggetti: <white>trascinale</white> sul piccone per montarle!",
                "I <aqua>Quantum</aqua> si trovano solo nella <red>Miniera PvP</red>: riportali nella zona sicura.",
                "Compra una cella nel <gold>Colosseo</gold> con <yellow>/cella menu</yellow> e arredala.",
                "Completa le missioni del <gold>/battlepass</gold> per salire di livello.",
                "Ogni 35-45 minuti c'è un <light_purple>Keyall</light_purple>: chiavi per tutti gli online!",
                "Rompi i <gold>Lucky Block</gold> nelle miniere: premi... o scherzetti!"};
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) return;
            Txt.broadcastPrison(Txt.mm(Txt.PREFIX + "<gray>" + tips[ThreadLocalRandom.current().nextInt(tips.length)]));
        }, 6000L, 6000L);
    }

    private void applyEffects(Player p, PlayerData d) {
        boolean holding = picks.holdingPickaxe(p);
        if (holding) {
            effect(p, PotionEffectType.HASTE, d.active("rapidita"));
            effect(p, PotionEffectType.SPEED, d.active("velocita"));
            effect(p, PotionEffectType.JUMP_BOOST, d.active("salto"));
            if (d.active("visione") > 0)
                p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 400, 0, true, false, false));
        }
        if (p.getGameMode() == GameMode.SURVIVAL) {
            boolean canFly = d.fly && (d.ench("volo") > 0 || d.vip >= 2 || p.hasPermission("pepita.fly"));
            Mine area = mines.areaOf(p.getLocation());
            boolean zone = world.inSpawn(p.getLocation()) || (area != null && !area.pvp);
            if (canFly && zone && !p.getAllowFlight()) p.setAllowFlight(true);
            else if (!(canFly && zone) && p.getAllowFlight()) {
                p.setFlying(false);
                p.setAllowFlight(false);
            }
        }
    }

    private void effect(Player p, PotionEffectType t, int level) {
        if (level <= 0) return;
        p.addPotionEffect(new PotionEffect(t, 60, level - 1, true, false, false));
    }

    /** Riscatto della Pepita d'Oro: premio grande + Corsa all'Oro per tutti. */
    public void redeemPepita(Player p) {
        PlayerData d = data.get(p);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double pep = (2000 + r.nextInt(6000)) * (1 + d.prestige * 0.2 + d.evasioni);
        double money = eco.minutes(d, getConfig().getDouble("corsa-all-oro.pepita-minuti", 45));
        long gems = 5 + r.nextInt(16);
        eco.give(d, Economy.Cur.PEPITE, pep, "pepita-oro");
        eco.give(d, Economy.Cur.SOLDI, money, "pepita-oro");
        eco.give(d, Economy.Cur.GEMME, gems, "pepita-oro");
        p.showTitle(Title.title(Txt.mm("<gradient:#FFF6B7:#FFD54A:#FFA726><b>PEPITA RISCATTATA!</b></gradient>"),
                Txt.mm(Txt.pepite(pep) + " <gray>• " + Txt.soldi(money) + " <gray>• " + Txt.gemme(gems)),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(3000), Duration.ofMillis(600))));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        goldRush.start(getConfig().getInt("corsa-all-oro.durata-pepita-secondi", 180),
                "<white>" + Txt.esc(p.getName()) + "</white> ha riscattato una <gold>Pepita d'Oro</gold>!");
    }

    @EventHandler
    public void onPing(ServerListPingEvent e) {
        String l1 = getConfig().getString("server.motd-1", "<gradient:#FFE259:#FFA751><b>⛏ PEPITA</b></gradient> <dark_gray>»</dark_gray> <white>Prison italiano</white> <gray>[26.x]");
        String l2 = goldRush != null && goldRush.active()
                ? "<gold><b>☀ CORSA ALL'ORO IN CORSO!</b></gold> <gray>Entra ora!"
                : getConfig().getString("server.motd-2", "<gray>Incantesimi folli • Casse • Gang • <gold>Corsa all'Oro");
        e.motd(Txt.mm(l1 + "\n" + l2));
    }
}

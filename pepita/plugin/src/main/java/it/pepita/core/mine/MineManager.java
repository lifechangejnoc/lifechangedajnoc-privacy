package it.pepita.core.mine;

import it.pepita.core.PepitaCore;
import it.pepita.core.data.PlayerData;
import it.pepita.core.rank.RankManager;
import it.pepita.core.util.Fmt;
import it.pepita.core.util.Txt;
import it.pepita.core.world.Layout;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Gestisce tutte le miniere: definizione, reset automatico a lotti, lucky block, accessi. */
public final class MineManager {
    public static final int[] WEIGHTS = {45, 30, 17, 8};
    public static final int VERSION = 2;

    private final PepitaCore plugin;
    private final File file;
    private final Map<String, Mine> mines = new LinkedHashMap<>();
    private final Deque<Mine> resetQueue = new ArrayDeque<>();
    private final Map<String, Boolean> stale = new HashMap<>();
    private int resetSeconds;
    private double resetPercent;
    private int perTick;
    private int luckyEvery;
    private ResetJob job;

    public MineManager(PepitaCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "miniere.yml");
        reloadSettings();
    }

    public void reloadSettings() {
        resetSeconds = plugin.getConfig().getInt("miniere.reset-secondi", 300);
        resetPercent = plugin.getConfig().getDouble("miniere.reset-percentuale", 0.30);
        perTick = Math.max(2000, plugin.getConfig().getInt("miniere.blocchi-reset-per-tick", 15000));
        luckyEvery = plugin.getConfig().getInt("lucky.ogni-blocchi", 4000);
    }

    public Collection<Mine> all() {
        return mines.values();
    }

    public Mine get(String id) {
        return mines.get(id.toLowerCase());
    }

    public Mine pvpMine() {
        for (Mine m : mines.values()) if (m.pvp) return m;
        return null;
    }

    // ------------------ Definizione ------------------

    public void load() {
        mines.clear();
        if (!file.exists()) {
            createDefaults();
            save();
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        boolean old = y.getInt("versione", 1) < VERSION;
        for (String id : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(id);
            if (s == null) continue;
            Mine m = new Mine(id);
            m.name = s.getString("nome", id);
            m.icon = Material.matchMaterial(s.getString("icona", "STONE"));
            if (m.icon == null) m.icon = Material.STONE;
            m.theme = s.getString("tema", "pietra");
            m.world = s.getString("mondo", Layout.W_MINES);
            List<Integer> a = s.getIntegerList("area");
            if (a.size() != 6) continue;
            m.minX = a.get(0); m.minY = a.get(1); m.minZ = a.get(2);
            m.maxX = a.get(3); m.maxY = a.get(4); m.maxZ = a.get(5);
            List<Double> sp = s.getDoubleList("spawn");
            if (sp.size() >= 4) {
                m.spawnX = sp.get(0); m.spawnY = sp.get(1); m.spawnZ = sp.get(2); m.spawnYaw = sp.get(3).floatValue();
            }
            List<Double> lb = s.getDoubleList("etichetta");
            if (lb.size() >= 3) {
                m.labelX = lb.get(0); m.labelY = lb.get(1); m.labelZ = lb.get(2);
            }
            ConfigurationSection comp = s.getConfigurationSection("blocchi");
            if (comp != null) for (String k : comp.getKeys(false)) {
                Material mat = Material.matchMaterial(k);
                if (mat != null && mat.isBlock()) {
                    m.mats.add(mat);
                    m.weights.add(comp.getInt(k));
                }
            }
            if (m.mats.isEmpty()) {
                m.mats.add(Material.STONE);
                m.weights.add(1);
            }
            m.reqRank = s.getInt("richiede.rank");
            m.reqPrestige = s.getInt("richiede.prestigio");
            m.reqEvasioni = s.getInt("richiede.evasioni");
            m.reqVip = s.getInt("richiede.vip");
            m.pvp = s.getBoolean("pvp");
            mines.put(id, m);
        }
        if (old) migrate();
    }

    /** Vecchio miniere.yml (miniere nel mondo della prigione): tiene nomi, blocchi e requisiti, rifà le coordinate. */
    private void migrate() {
        try {
            Files.copy(file.toPath(), new File(plugin.getDataFolder(), "miniere_v1.yml").toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().warning("Backup di miniere.yml non riuscito: " + e.getMessage());
        }
        Map<String, Mine> oldMines = new LinkedHashMap<>(mines);
        mines.clear();
        createDefaults();
        for (Mine m : mines.values()) {
            Mine o = oldMines.get(m.id);
            if (o == null) continue;
            m.name = o.name;
            if (!m.pvp) {
                m.mats.clear();
                m.weights.clear();
                m.mats.addAll(o.mats);
                m.weights.addAll(o.weights);
                m.icon = o.icon;
            }
            m.reqRank = o.reqRank;
            m.reqPrestige = o.reqPrestige;
            m.reqEvasioni = o.reqEvasioni;
            m.reqVip = o.reqVip;
        }
        // miniere aggiunte a mano dall'utente: le teniamo nel mondo delle miniere, in coda alla griglia
        int extra = 0;
        for (Mine o : oldMines.values()) {
            if (mines.containsKey(o.id)) continue;
            int col = extra % 6, row = 5 + extra / 6;
            Mine m = make(o.id, o.name, 0, col, row, o.theme == null ? "pietra" : o.theme);
            m.mats.clear();
            m.weights.clear();
            m.mats.addAll(o.mats);
            m.weights.addAll(o.weights);
            m.icon = o.icon;
            m.reqRank = o.reqRank;
            m.reqPrestige = o.reqPrestige;
            m.reqEvasioni = o.reqEvasioni;
            m.reqVip = o.reqVip;
            extra++;
        }
        save();
        plugin.getLogger().info("miniere.yml aggiornato alla versione " + VERSION + " (mondi separati, miniere 29x29). Backup: miniere_v1.yml");
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of(
                "Miniere di Pepita. Puoi cambiare nomi, blocchi (con il loro peso) e requisiti.",
                "Dopo aver modificato le coordinate usa /pa ricostruisci miniere (o pvp)."));
        y.set("versione", VERSION);
        for (Mine m : mines.values()) {
            String p = m.id + ".";
            y.set(p + "nome", m.name);
            y.set(p + "icona", m.icon.name());
            y.set(p + "tema", m.theme);
            y.set(p + "mondo", m.world);
            y.set(p + "area", List.of(m.minX, m.minY, m.minZ, m.maxX, m.maxY, m.maxZ));
            y.set(p + "spawn", List.of(m.spawnX, m.spawnY, m.spawnZ, (double) m.spawnYaw));
            y.set(p + "etichetta", List.of(m.labelX, m.labelY, m.labelZ));
            for (int i = 0; i < m.mats.size(); i++) y.set(p + "blocchi." + m.mats.get(i).name(), m.weights.get(i));
            y.set(p + "richiede.rank", m.reqRank);
            y.set(p + "richiede.prestigio", m.reqPrestige);
            y.set(p + "richiede.evasioni", m.reqEvasioni);
            y.set(p + "richiede.vip", m.reqVip);
            y.set(p + "pvp", m.pvp);
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossibile salvare miniere.yml: " + e.getMessage());
        }
    }

    private void createDefaults() {
        for (int i = 0; i < 26; i++) {
            Mine m = make(String.valueOf((char) ('a' + i)), "Miniera " + RankManager.letter(i), i, i % 6, i / 6, themeFor(i));
            m.reqRank = i;
        }
        Mine pr = make("prestigio", "Miniera del Prestigio", 14, 2, 4, "prestigio");
        pr.reqPrestige = 1;
        Mine vip = make("vip", "Miniera VIP", 16, 3, 4, "vip");
        vip.reqVip = 3;
        Mine ev = make("evasione", "Miniera dell'Evasione", 33, 4, 4, "evasione");
        ev.reqEvasioni = 1;
        makePvp();
    }

    private static String themeFor(int i) {
        if (i < 5) return "pietra";
        if (i < 10) return "ardesia";
        if (i < 15) return "nera";
        if (i < 20) return "prismarino";
        return "end";
    }

    private Mine make(String id, String name, int ladderStart, int col, int row, String theme) {
        Mine m = new Mine(id);
        m.name = name;
        m.theme = theme;
        m.world = Layout.W_MINES;
        int cx = Layout.mineCenterX(col), cz = Layout.mineCenterZ(row);
        int half = Layout.MINE_HALF, top = Layout.MINE_TOP;
        m.minX = cx - half; m.maxX = cx + half;
        m.minZ = cz - half; m.maxZ = cz + half;
        m.maxY = top; m.minY = top - Layout.MINE_DEPTH + 1;
        m.spawnX = cx + 0.5; m.spawnY = top + 2; m.spawnZ = cz + half + 7.5; m.spawnYaw = 180f;
        m.labelX = cx + 0.5; m.labelY = top + 7.4; m.labelZ = cz - half - 4 + 0.5;
        for (int k = 0; k < 4; k++) {
            int idx = Math.min(Prices.LADDER.size() - 1, ladderStart + k);
            m.mats.add(Prices.LADDER.get(idx));
            m.weights.add(WEIGHTS[k]);
        }
        m.icon = m.mats.get(3);
        mines.put(id, m);
        return m;
    }

    /** La Miniera PvP dei Quantum (mondo pepita_pvp). */
    private void makePvp() {
        Mine m = new Mine("pvp");
        m.name = "Miniera PvP dei Quantum";
        m.theme = "pvp";
        m.world = Layout.W_PVP;
        m.pvp = true;
        int half = Layout.PVP_HALF, top = Layout.PVP_TOP;
        m.minX = -half; m.maxX = half;
        m.minZ = -half; m.maxZ = half;
        m.maxY = top; m.minY = top - Layout.PVP_DEPTH + 1;
        m.spawnX = Layout.PVP_SPAWN[0]; m.spawnY = Layout.PVP_SPAWN[1]; m.spawnZ = Layout.PVP_SPAWN[2];
        m.spawnYaw = (float) Layout.PVP_SPAWN[3];
        m.labelX = 0.5; m.labelY = top + 9.4; m.labelZ = -half - 4 + 0.5;
        Object[][] comp = {{Material.NETHERRACK, 38}, {Material.BLACKSTONE, 18}, {Material.BASALT, 12},
                {Prices.MINERALE, 24}, {Prices.NUCLEO, 8}};
        for (Object[] c : comp) {
            m.mats.add((Material) c[0]);
            m.weights.add((Integer) c[1]);
        }
        m.icon = Prices.MINERALE;
        m.reqRank = 10;
        mines.put(m.id, m);
    }

    // ------------------ Ricerca ------------------

    public Mine at(World w, int x, int y, int z) {
        if (w == null) return null;
        String n = w.getName();
        for (Mine m : mines.values()) if (m.world.equals(n) && m.contains(x, y, z)) return m;
        return null;
    }

    public Mine at(Block b) {
        return at(b.getWorld(), b.getX(), b.getY(), b.getZ());
    }

    public Mine at(Location l) {
        return at(l.getWorld(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    public Mine pitOf(Location l) {
        for (Mine m : mines.values()) if (m.inPit(l)) return m;
        return null;
    }

    public Mine areaOf(Location l) {
        for (Mine m : mines.values()) if (m.inArea(l)) return m;
        return null;
    }

    /** La miniera più vicina nello stesso mondo (per chi cade nel vuoto). */
    public Mine nearest(Location l) {
        Mine best = null;
        double bd = Double.MAX_VALUE;
        for (Mine m : mines.values()) {
            if (!m.in(l.getWorld())) continue;
            double dx = m.centerX() - l.getX(), dz = m.centerZ() - l.getZ();
            double d = dx * dx + dz * dz;
            if (d < bd) {
                bd = d;
                best = m;
            }
        }
        return best;
    }

    /** La miniera migliore a cui il giocatore ha accesso (esclusa la PvP). */
    public Mine best(PlayerData d) {
        Mine best = null;
        double bestVal = -1;
        for (Mine m : mines.values()) {
            if (m.pvp || !m.canAccess(d)) continue;
            double v = m.avgValue();
            if (v > bestVal) {
                bestVal = v;
                best = m;
            }
        }
        return best;
    }

    public World worldOf(Mine m) {
        return Bukkit.getWorld(m.world);
    }

    public boolean teleport(Player p, Mine m) {
        PlayerData d = plugin.data().get(p);
        if (!m.canAccess(d) && !p.hasPermission("pepita.admin")) {
            Txt.send(p, "<red>Non puoi entrare qui.</red> Requisito: <white>" + m.requirement());
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            return false;
        }
        World w = worldOf(m);
        if (w == null) return false;
        if (needsReset(m)) queueFirst(m);
        p.teleport(m.spawn(w));
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.2f);
        p.sendActionBar(Txt.mm("<gold>⛏ <white>" + m.name + (m.pvp ? " <red>• PvP attivo!" : "")));
        plugin.tutorial().onEvent(p, it.pepita.core.tutorial.Tutorial.Ev.MINE, 1);
        return true;
    }

    // ------------------ Reset ------------------

    private boolean needsReset(Mine m) {
        if (m.total <= 0) return true;
        if (Boolean.TRUE.equals(stale.get(m.id))) return true;
        return System.currentTimeMillis() - m.lastReset >= resetSeconds * 1000L || m.remaining <= m.total * resetPercent;
    }

    public void resetAll() {
        for (Mine m : mines.values()) queueReset(m);
    }

    /** Resetta subito le miniere con giocatori vicini, le altre al prossimo ingresso. */
    public void refreshActive() {
        for (Mine m : mines.values()) {
            if (hasPlayersNear(m)) queueReset(m);
            else stale.put(m.id, true);
        }
    }

    public void resetWorld(String world) {
        for (Mine m : mines.values()) if (m.world.equals(world)) queueReset(m);
    }

    public void queueReset(Mine m) {
        if (!resetQueue.contains(m) && (job == null || job.m != m)) resetQueue.add(m);
    }

    private void queueFirst(Mine m) {
        if (job != null && job.m == m) return;
        resetQueue.remove(m);
        resetQueue.addFirst(m);
    }

    /** Reset immediato (comando admin): mette la miniera in testa alla coda. */
    public void reset(Mine m) {
        queueFirst(m);
    }

    public boolean resetting(Mine m) {
        return job != null && job.m == m;
    }

    private boolean hasPlayersNear(Mine m) {
        World w = worldOf(m);
        if (w == null) return false;
        for (Player p : w.getPlayers()) if (m.inArea(p.getLocation())) return true;
        return false;
    }

    /** Reset in corso, a lotti di blocchi per tick. */
    private final class ResetJob {
        final Mine m;
        final World w;
        final boolean gold;
        int x, y, z;
        int lucky;

        ResetJob(Mine m, World w) {
            this.m = m;
            this.w = w;
            this.gold = plugin.goldRush().active();
            x = m.minX;
            y = m.minY;
            z = m.minZ;
        }

        /** @return true se finito */
        boolean step(int budget) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            BlockData luckyData = Prices.LUCKY.createBlockData();
            for (int n = 0; n < budget; n++) {
                BlockData bd = luckyEvery > 0 && r.nextInt(luckyEvery) == 0 ? luckyData : m.pick(gold);
                if (bd == luckyData) lucky++;
                w.getBlockAt(x, y, z).setBlockData(bd, false);
                if (++z > m.maxZ) {
                    z = m.minZ;
                    if (++x > m.maxX) {
                        x = m.minX;
                        if (++y > m.maxY) return true;
                    }
                }
            }
            return false;
        }
    }

    private void start(Mine m) {
        World w = worldOf(m);
        if (w == null) return;
        for (Player p : w.getPlayers()) {
            if (m.inPit(p.getLocation())) {
                p.teleport(m.spawn(w));
                p.showTitle(Title.title(Txt.mm("<gold>⛏"), Txt.mm("<gray>Miniera <white>" + m.name + "</white> ricaricata!"),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(300))));
            }
        }
        m.total = m.volume();
        m.remaining = m.total;
        m.lastReset = System.currentTimeMillis();
        stale.remove(m.id);
        job = new ResetJob(m, w);
    }

    private void finish(ResetJob j) {
        for (Player p : j.w.getPlayers()) {
            Location l = p.getLocation();
            if (j.m.inPit(l) && !l.getBlock().getType().isAir()) p.teleport(j.m.spawn(j.w));
        }
        job = null;
    }

    /** Ogni tick: avanza il reset in corso. */
    public void tickResets() {
        int budget = perTick;
        while (budget > 0) {
            if (job == null) {
                Mine next = resetQueue.poll();
                if (next == null) return;
                start(next);
                if (job == null) continue;
            }
            int step = Math.min(budget, job.m.volume());
            budget -= step;
            if (job.step(step)) finish(job);
        }
    }

    /** Ogni secondo: reset per tempo o per blocchi rimasti. Le miniere senza giocatori vicini aspettano. */
    public void tick() {
        long now = System.currentTimeMillis();
        for (Mine m : mines.values()) {
            if (m.total <= 0 || resetting(m) || resetQueue.contains(m)) continue;
            boolean byBlocks = m.remaining <= m.total * resetPercent;
            boolean byTime = now - m.lastReset >= resetSeconds * 1000L;
            if (byBlocks) queueReset(m);
            else if (byTime) {
                if (hasPlayersNear(m)) queueReset(m);
                else stale.put(m.id, true);
            }
        }
    }

    /** Se la miniera è quasi vuota la mette in coda per il reset. */
    public void checkLow(Mine m) {
        if (m.total > 0 && m.remaining <= m.total * resetPercent) queueReset(m);
    }

    public long secondsToReset(Mine m) {
        return Math.max(0, (m.lastReset + resetSeconds * 1000L - System.currentTimeMillis()) / 1000);
    }

    public String labelText(Mine m) {
        double pct = m.total <= 0 ? 0 : (double) m.remaining / m.total;
        String col = pct > 0.6 ? "<green>" : pct > 0.4 ? "<yellow>" : "<red>";
        StringBuilder sb = new StringBuilder();
        sb.append("<gradient:#FFE259:#FFA751><b>⛏ ").append(m.name.toUpperCase()).append(" ⛏</b></gradient>\n");
        if (m.pvp) sb.append("<red><b>⚔ PvP ATTIVO ⚔</b></red> <dark_gray>• ").append(Txt.S_QUANTUM).append(" <" + Txt.QUANTUM + ">Quantum</" + Txt.QUANTUM + ">\n");
        sb.append("<gray>Requisito: <white>").append(m.requirement()).append("\n");
        if (!m.pvp) sb.append("<gray>Valore medio: ").append(Txt.soldi(m.avgValue())).append("<gray>/blocco\n");
        else sb.append("<gray>Minerale e Nucleo Quantico: <white>scava e torna allo spawn!\n");
        sb.append("<gray>Blocchi: ").append(col).append(Fmt.pct(pct)).append("</gray> <dark_gray>•</dark_gray> <gray>Reset tra <white>")
                .append(Fmt.time(secondsToReset(m)));
        return sb.toString();
    }

    public List<Mine> list() {
        return new ArrayList<>(mines.values());
    }

    public void announceIfInside(Mine m, String msg) {
        for (Player p : Bukkit.getOnlinePlayers()) if (m.inArea(p.getLocation())) Txt.send(p, msg);
    }
}

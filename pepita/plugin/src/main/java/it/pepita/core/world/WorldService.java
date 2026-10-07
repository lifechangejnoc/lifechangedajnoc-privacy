package it.pepita.core.world;

import it.pepita.core.PepitaCore;
import it.pepita.core.crate.Crate;
import it.pepita.core.mine.Mine;
import it.pepita.core.world.build.ColosseumBuild;
import it.pepita.core.world.build.HubBuild;
import it.pepita.core.world.build.MineBuild;
import it.pepita.core.world.build.PrisonBuild;
import it.pepita.core.world.build.PvpBuild;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static it.pepita.core.world.Layout.*;

/**
 * I cinque mondi vuoti di Pepita (hub, prigione, miniere, pvp, celle) e la loro costruzione.
 * Le costruzioni sono classi pure (package world.build): il piano si calcola in asincrono e si piazza a lotti.
 */
public final class WorldService {
    public static final List<String> WORLDS = List.of(W_HUB, W_PRISON, W_MINES, W_PVP, W_CELLS);

    private final PepitaCore plugin;
    private final LobbyMap lobbyMap;
    private final Map<String, World> worlds = new LinkedHashMap<>();
    private final Map<Long, Crate> crateBlocks = new HashMap<>();
    private final Deque<Job> jobs = new ArrayDeque<>();
    private boolean running;
    /** Server aggiornato dalla v1: lo spawn vecchio della prigione va svuotato prima di costruire quello nuovo. */
    private boolean migrateOldPrison;

    public void setMigrateOldPrison(boolean v) {
        migrateOldPrison = v;
    }

    /** Un pezzo di costruzione: un piano (calcolato in asincrono) da piazzare in un mondo. */
    private record Job(String world, String label, Supplier<BuildPlan> plan, int[] clearBox, Runnable after) {}

    public WorldService(PepitaCore plugin) {
        this.plugin = plugin;
        this.lobbyMap = new LobbyMap(plugin);
        Crate[] cs = Crate.values();
        for (int i = 0; i < cs.length; i++)
            crateBlocks.put(bk(PRISON_CRATES[i][0], PRISON_CRATES[i][1], PRISON_CRATES[i][2]), cs[i]);
    }

    private static long bk(int x, int y, int z) {
        return ((long) x & 0xFFFFF) << 40 | ((long) z & 0xFFFFF) << 16 | (y & 0xFFFF);
    }

    // =====================================================================
    //  Mondi
    // =====================================================================

    /** Mappa esterna della lobby (se installata). */
    public LobbyMap lobbyMap() {
        return lobbyMap;
    }

    public void load() {
        // una mappa nuova per la lobby va installata prima di creare il mondo
        lobbyMap.prepare(() -> create(W_HUB));
        for (String n : WORLDS) worlds.put(n, create(n));
        lobbyMap.afterLoad(hub());
        double[] ls = lobbyMap.spawn();
        keepLoaded(hub(), (int) Math.floor(ls[0]), (int) Math.floor(ls[2]) - 8, 3);
        keepLoaded(prison(), 0, 0, 3);
        keepLoaded(pvp(), 0, 0, 3);
        keepLoaded(cells(), 0, 0, 2);
    }

    private void keepLoaded(World w, int bx, int bz, int r) {
        int cx = bx >> 4, cz = bz >> 4;
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) w.addPluginChunkTicket(cx + x, cz + z, plugin);
    }

    private World create(String name) {
        double[] sp = name.equals(W_HUB) ? lobbyMap.spawn() : spawnArray(name);
        Biome biome = switch (name) {
            case W_PVP -> Biome.BASALT_DELTAS;
            case W_CELLS -> Biome.SAVANNA;
            default -> Biome.MEADOW;
        };
        World w = Bukkit.getWorld(name);
        if (w == null) {
            w = new WorldCreator(name).generator(new VoidGenerator(sp[0], sp[1], sp[2], biome))
                    .environment(World.Environment.NORMAL).type(WorldType.FLAT).generateStructures(false).createWorld();
        }
        if (w == null) throw new IllegalStateException("Impossibile creare il mondo " + name);
        w.setSpawnLocation(new Location(w, sp[0], sp[1], sp[2], (float) sp[3], 0));
        w.setDifficulty(name.equals(W_PVP) ? Difficulty.EASY : Difficulty.PEACEFUL);
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
        rule(w, GameRules.BLOCK_DROPS, name.equals(W_CELLS)); // nelle celle i blocchi rotti tornano come oggetti
        rule(w, GameRules.PVP, name.equals(W_PVP));
        rule(w, GameRules.NATURAL_HEALTH_REGENERATION, true);
        long time = switch (name) {
            case W_PVP -> 18000L;
            case W_CELLS -> 11500L;
            default -> plugin.getConfig().getLong("mondo.ora", 6000);
        };
        w.setTime(time);
        w.setStorm(false);
        w.setThundering(false);
        w.setAutoSave(true);
        return w;
    }

    private static <T> void rule(World w, GameRule<T> r, T v) {
        try {
            w.setGameRule(r, v);
        } catch (Exception ignored) {
        }
    }

    private static double[] spawnArray(String name) {
        return switch (name) {
            case W_HUB -> HUB_SPAWN;
            case W_PVP -> PVP_SPAWN;
            case W_CELLS -> CELLS_SPAWN;
            case W_MINES -> new double[]{0.5, MINE_TOP + 2, MINE_HALF + 7.5, 180};
            default -> PRISON_SPAWN;
        };
    }

    public World get(String name) { return worlds.get(name); }
    public World hub() { return worlds.get(W_HUB); }
    public World prison() { return worlds.get(W_PRISON); }
    public World minesWorld() { return worlds.get(W_MINES); }
    public World pvp() { return worlds.get(W_PVP); }
    public World cells() { return worlds.get(W_CELLS); }

    /** Mondo della prigione (compatibilità con il codice vecchio). */
    public World world() { return prison(); }

    public boolean managed(World w) {
        return w != null && worlds.containsKey(w.getName());
    }

    public boolean isHub(World w) { return w != null && w.getName().equals(W_HUB); }
    public boolean isPrison(World w) { return w != null && w.getName().equals(W_PRISON); }
    public boolean isMines(World w) { return w != null && w.getName().equals(W_MINES); }
    public boolean isPvp(World w) { return w != null && w.getName().equals(W_PVP); }
    public boolean isCells(World w) { return w != null && w.getName().equals(W_CELLS); }

    private static Location loc(World w, double[] a) {
        return new Location(w, a[0], a[1], a[2], (float) a[3], 0);
    }

    public Location hubSpawn() { return loc(hub(), lobbyMap.spawn()); }

    /** Dove sta il secondino della modalità Prison nella lobby. */
    public Location hubNpc() { return loc(hub(), lobbyMap.npc()); }

    /** Sotto questa altezza si torna allo spawn del mondo. */
    public int voidY(World w) { return isHub(w) ? lobbyMap.voidY() : 40; }
    public Location prisonSpawn() { return loc(prison(), PRISON_SPAWN); }
    public Location pvpSpawn() { return loc(pvp(), PVP_SPAWN); }
    public Location cellsSpawn() { return loc(cells(), CELLS_SPAWN); }

    /** Spawn principale (hub). */
    public Location spawn() { return hubSpawn(); }

    /** Dove riportare chi cade nel vuoto in un certo mondo. */
    public Location spawnOf(World w) {
        if (w == null) return hubSpawn();
        return switch (w.getName()) {
            case W_PRISON, W_MINES -> prisonSpawn(); // chi gioca al Prison resta nel Prison
            case W_PVP -> pvpSpawn();
            case W_CELLS -> cellsSpawn();
            default -> hubSpawn();
        };
    }

    public Crate crateAt(Block b) {
        if (!isPrison(b.getWorld())) return null;
        return crateBlocks.get(bk(b.getX(), b.getY(), b.getZ()));
    }

    /** Il blocco è nella prigione alla posizione indicata (un blocco interattivo di Layout). */
    public boolean is(Block b, int[] pos) {
        return isPrison(b.getWorld()) && b.getX() == pos[0] && b.getY() == pos[1] && b.getZ() == pos[2];
    }

    /** Zone "di spawn" (hub e piazza della prigione): volo consentito e fisica bloccata. */
    public boolean inSpawn(Location l) {
        World w = l.getWorld();
        if (isHub(w)) return true; // tutta la lobby
        if (w == null || l.getY() < 50) return false;
        double dx = l.getX(), dz = l.getZ();
        if (isPrison(w)) return dx * dx + dz * dz < (PRISON_RADIUS + 10) * (PRISON_RADIUS + 10);
        return false;
    }

    /** Zona sicura della miniera PvP. */
    public boolean inPvpSafe(Location l) {
        if (!isPvp(l.getWorld())) return false;
        double dx = l.getX() - PVP_SPAWN[0], dz = l.getZ() - PVP_SPAWN[2];
        return dx * dx + dz * dz <= PVP_SAFE_RADIUS * PVP_SAFE_RADIUS && Math.abs(l.getY() - PVP_SPAWN[1]) < 6;
    }

    public void keepMinesLoaded() {
        for (Mine m : plugin.mines().all()) {
            World w = get(m.world);
            if (w == null) continue;
            w.addPluginChunkTicket((int) Math.floor(m.labelX) >> 4, (int) Math.floor(m.labelZ) >> 4, plugin);
            w.addPluginChunkTicket((int) Math.floor(m.spawnX) >> 4, (int) Math.floor(m.spawnZ) >> 4, plugin);
        }
    }

    // =====================================================================
    //  Costruzione
    // =====================================================================

    private String flag(String world) {
        return "mondi.costruiti." + world;
    }

    public boolean built(String world) {
        return plugin.getConfig().getBoolean(flag(world), false);
    }

    public boolean building() {
        return running || !jobs.isEmpty();
    }

    /** Costruisce tutti i mondi non ancora costruiti (flag in config). */
    public void buildMissing(Runnable done) {
        boolean oldPrison = migrateOldPrison;
        List<String> todo = new ArrayList<>();
        for (String w : WORLDS) if (!built(w) && !(w.equals(W_HUB) && lobbyMap.active())) todo.add(w);
        if (todo.isEmpty()) {
            if (done != null) done.run();
            return;
        }
        plugin.getLogger().info("Mondi da costruire: " + String.join(", ", todo));
        for (int i = 0; i < todo.size(); i++) {
            String w = todo.get(i);
            boolean last = i == todo.size() - 1;
            queueWorld(w, w.equals(W_PRISON) && oldPrison, () -> {
                plugin.getConfig().set(flag(w), true);
                plugin.saveConfig();
                plugin.onWorldBuilt(w);
                if (last && done != null) done.run();
            });
        }
        runNext();
    }

    /** Ricostruisce un mondo (o "tutti"). */
    public boolean rebuild(String which, Runnable done) {
        which = which.toLowerCase(Locale.ROOT);
        List<String> list = new ArrayList<>();
        if (which.equals("tutti") || which.equals("all")) list.addAll(WORLDS);
        else {
            String w = alias(which);
            if (w == null) return false;
            list.add(w);
        }
        // la lobby con una mappa esterna non si ricostruisce: la cambierebbe con quella del plugin
        if (lobbyMap.active() && list.remove(W_HUB)) plugin.getLogger().info("La lobby usa una mappa esterna: non viene ricostruita.");
        if (list.isEmpty()) {
            if (done != null) done.run();
            return true;
        }
        for (int i = 0; i < list.size(); i++) {
            String w = list.get(i);
            boolean last = i == list.size() - 1;
            queueWorld(w, false, () -> {
                plugin.getConfig().set(flag(w), true);
                plugin.saveConfig();
                plugin.onWorldBuilt(w);
                if (last && done != null) done.run();
            });
        }
        runNext();
        return true;
    }

    public static String alias(String s) {
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "hub", "lobby", W_HUB -> W_HUB;
            case "prigione", "spawn", W_PRISON -> W_PRISON;
            case "miniere", "mine", W_MINES -> W_MINES;
            case "pvp", W_PVP -> W_PVP;
            case "celle", "colosseo", W_CELLS -> W_CELLS;
            default -> null;
        };
    }

    private void queueWorld(String w, boolean clearOld, Runnable after) {
        switch (w) {
            case W_HUB -> jobs.add(new Job(w, "hub", () -> plan(HubBuild::build), null, after));
            case W_PRISON -> jobs.add(new Job(w, "prigione", () -> plan(PrisonBuild::build),
                    clearOld ? new int[]{-44, 70, -44, 44, 135, 44} : null, after));
            case W_MINES -> {
                List<Mine> ms = new ArrayList<>();
                for (Mine m : plugin.mines().all()) if (W_MINES.equals(m.world)) ms.add(m);
                for (int i = 0; i < ms.size(); i++) {
                    Mine m = ms.get(i);
                    jobs.add(new Job(w, "miniera " + m.id, () -> plan(p -> MineBuild.build(p, m)), null,
                            i == ms.size() - 1 ? after : null));
                }
                if (ms.isEmpty() && after != null) after.run();
            }
            case W_PVP -> {
                Mine pm = null;
                for (Mine m : plugin.mines().all()) if (m.pvp) pm = m;
                final Mine fm = pm;
                if (fm == null) {
                    if (after != null) after.run();
                } else jobs.add(new Job(w, "pvp", () -> plan(p -> PvpBuild.build(p, fm)), null, after));
            }
            case W_CELLS -> jobs.add(new Job(w, "colosseo", () -> plan(ColosseumBuild::build), null, after));
            default -> {
            }
        }
    }

    private static BuildPlan plan(Consumer<BuildPlan> builder) {
        BuildPlan p = new BuildPlan();
        builder.accept(p);
        return p;
    }

    private void runNext() {
        if (running) return;
        Job j = jobs.poll();
        if (j == null) return;
        running = true;
        World w = get(j.world);
        long t0 = System.currentTimeMillis();
        int perTick = Math.max(1000, plugin.getConfig().getInt("mondo.blocchi-per-tick", 20000));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            BuildPlan plan;
            try {
                plan = j.plan.get();
            } catch (Throwable t) {
                plugin.getLogger().severe("Errore nel calcolo della costruzione " + j.label + ": " + t);
                t.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    running = false;
                    if (j.after != null) j.after.run();
                    runNext();
                });
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getLogger().info("Costruzione " + j.label + " (" + j.world + "): " + plan.size() + " blocchi...");
                Runnable place = () -> plan.place(plugin, w, perTick, () -> {
                    plugin.getLogger().info("Costruzione " + j.label + " completata in "
                            + (System.currentTimeMillis() - t0) / 1000.0 + "s.");
                    running = false;
                    if (j.after != null) j.after.run();
                    runNext();
                });
                if (j.clearBox != null) clearBox(w, j.clearBox, perTick * 2, place);
                else place.run();
            });
        });
    }

    /** Svuota (aria) un box del mondo a lotti, toccando solo i blocchi non vuoti. */
    private void clearBox(World w, int[] b, int perTick, Runnable done) {
        new BukkitRunnable() {
            int x = b[0], y = b[1], z = b[2];

            @Override
            public void run() {
                int n = 0;
                while (n < perTick) {
                    Block bl = w.getBlockAt(x, y, z);
                    if (!bl.getType().isAir()) bl.setType(Material.AIR, false);
                    n++;
                    if (++y > b[4]) {
                        y = b[1];
                        if (++z > b[5]) {
                            z = b[2];
                            if (++x > b[3]) {
                                cancel();
                                done.run();
                                return;
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Piazza un piano qualsiasi (reset delle celle ecc.). */
    public void place(World w, BuildPlan plan, Runnable done) {
        plan.place(plugin, w, Math.max(1000, plugin.getConfig().getInt("mondo.blocchi-per-tick", 20000)), done);
    }
}

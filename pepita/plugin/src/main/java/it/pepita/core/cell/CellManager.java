package it.pepita.core.cell;

import it.pepita.core.PepitaCore;
import it.pepita.core.data.PlayerData;
import it.pepita.core.economy.Economy;
import it.pepita.core.gui.Gui;
import it.pepita.core.gui.Menu;
import it.pepita.core.tutorial.Tutorial;
import it.pepita.core.util.Fmt;
import it.pepita.core.util.ItemBuilder;
import it.pepita.core.util.Txt;
import it.pepita.core.world.BuildPlan;
import it.pepita.core.world.CellGeometry;
import it.pepita.core.world.Layout;
import it.pepita.core.world.build.ColosseumBuild;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static it.pepita.core.world.Layout.*;

/**
 * Celle del colosseo (mondo pepita_celle): acquisto, fidati, unione di celle adiacenti (abbattendo il muro laterale),
 * porte di ferro aperte via codice, ascensori, tassa giornaliera e reset. La geometria è {@link CellGeometry#classify}.
 */
public final class CellManager {
    public static final class Cell {
        public final int id;
        public UUID owner;
        public String ownerName = "?";
        public final Set<UUID> trusted = new LinkedHashSet<>();
        public final Map<UUID, String> trustedNames = new HashMap<>();
        public long bought, lastTax;

        Cell(int id) {
            this.id = id;
        }

        public int floor() { return CellGeometry.floorOf(id); }
        public int sector() { return CellGeometry.sectorOf(id); }
    }

    private final PepitaCore plugin;
    private final File file;
    private final Map<Integer, Cell> cells = new HashMap<>();
    private final Set<Integer> openWalls = new HashSet<>(); // floor*SECTORS + indice del muro laterale
    private final Map<Long, int[]> pads = new HashMap<>();  // blocco -> {ascensore, livello}
    private final List<int[]> padList;
    private final Map<UUID, Long> lastPad = new HashMap<>();
    private final Map<UUID, Long> doorMsg = new HashMap<>();

    public CellManager(PepitaCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "celle.yml");
        padList = ColosseumBuild.elevatorPads();
        for (int[] p : padList) pads.put(key(p[0], p[1], p[2]), new int[]{elevatorOf(p[0], p[2]), p[3]});
        load();
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0xFFFFF) << 40 | ((long) z & 0xFFFFF) << 16 | (y & 0xFFFF);
    }

    private static int elevatorOf(int x, int z) {
        double a = Math.toDegrees(Math.atan2(z + 0.5, x + 0.5));
        if (a < 0) a += 360;
        int best = 0;
        double bd = 999;
        for (int i = 0; i < ELEVATOR_ANGLES.length; i++) {
            double d = Math.abs(((a - ELEVATOR_ANGLES[i]) % 360 + 540) % 360 - 180);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    // =====================================================================
    //  Dati
    // =====================================================================

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("celle");
        if (s != null) for (String k : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(k);
            if (c == null) continue;
            try {
                Cell cell = new Cell(Integer.parseInt(k));
                cell.owner = UUID.fromString(c.getString("proprietario", ""));
                cell.ownerName = c.getString("nome", "?");
                cell.bought = c.getLong("comprata");
                cell.lastTax = c.getLong("tassa-pagata", System.currentTimeMillis());
                ConfigurationSection f = c.getConfigurationSection("fidati");
                if (f != null) for (String u : f.getKeys(false)) {
                    UUID id = UUID.fromString(u);
                    cell.trusted.add(id);
                    cell.trustedNames.put(id, f.getString(u, "?"));
                }
                cells.put(cell.id, cell);
            } catch (Exception ignored) {
            }
        }
        for (Object o : y.getList("muri-aperti", List.of())) if (o instanceof Number n) openWalls.add(n.intValue());
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("Celle del colosseo di Pepita. id = piano*" + CELL_SECTORS + " + settore."));
        for (Cell c : cells.values()) {
            String p = "celle." + c.id + ".";
            y.set(p + "proprietario", c.owner.toString());
            y.set(p + "nome", c.ownerName);
            y.set(p + "comprata", c.bought);
            y.set(p + "tassa-pagata", c.lastTax);
            for (UUID u : c.trusted) y.set(p + "fidati." + u, c.trustedNames.getOrDefault(u, "?"));
        }
        y.set("muri-aperti", new ArrayList<>(openWalls));
        final String data = y.saveToString();
        Runnable w = () -> {
            try {
                java.nio.file.Files.writeString(file.toPath(), data, java.nio.charset.StandardCharsets.UTF_8);
            } catch (IOException e) {
                plugin.getLogger().warning("celle.yml: " + e.getMessage());
            }
        };
        if (plugin.isEnabled()) Bukkit.getScheduler().runTaskAsynchronously(plugin, w);
        else w.run();
    }

    // =====================================================================
    //  Interrogazioni
    // =====================================================================

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    public static String label(int id) {
        return "Cella " + (CellGeometry.floorOf(id) + 1) + "-" + (CellGeometry.sectorOf(id) + 1);
    }

    public Cell cell(int id) {
        return cells.get(id);
    }

    public List<Cell> owned(UUID u) {
        List<Cell> l = new ArrayList<>();
        for (Cell c : cells.values()) if (u.equals(c.owner)) l.add(c);
        l.sort((a, b) -> Integer.compare(a.id, b.id));
        return l;
    }

    public boolean canUse(UUID u, int id) {
        Cell c = cells.get(id);
        return c != null && (u.equals(c.owner) || c.trusted.contains(u));
    }

    private World world() {
        return plugin.world().cells();
    }

    private boolean inWorld(Block b) {
        return plugin.world().isCells(b.getWorld());
    }

    /** Il giocatore può piazzare/rompere questo blocco (interno delle proprie celle o muri abbattuti tra le proprie celle). */
    public boolean canBuild(Player p, Block b) {
        if (!inWorld(b)) return false;
        CellGeometry.Info i = CellGeometry.classify(b.getX(), b.getY(), b.getZ());
        UUID u = p.getUniqueId();
        if (i.kind() == CellGeometry.Kind.INTERIOR) return canUse(u, CellGeometry.id(i.floor(), i.sector()));
        if (i.kind() == CellGeometry.Kind.WALL_SIDE && openWalls.contains(CellGeometry.id(i.floor(), i.sector()))) {
            int right = CellGeometry.id(i.floor(), i.sector()), left = CellGeometry.id(i.floor(), i.sector() - 1);
            return canUse(u, right) && canUse(u, left);
        }
        return false;
    }

    public double price(PlayerData d, int floor) {
        double mins = cfg().getDouble("celle.costo-minuti-base", 45) + floor * cfg().getDouble("celle.costo-minuti-piano", 15);
        return plugin.eco().minutes(d, mins);
    }

    public double tax(PlayerData d) {
        return plugin.eco().minutes(d, cfg().getDouble("celle.tassa-minuti-giorno", 3));
    }

    public Location home(int id) {
        double[] h = CellGeometry.home(CellGeometry.floorOf(id), CellGeometry.sectorOf(id));
        return new Location(world(), h[0], h[1], h[2], (float) h[3], 0);
    }

    public Location doorstep(int id) {
        double[] h = CellGeometry.doorstep(CellGeometry.floorOf(id), CellGeometry.sectorOf(id));
        return new Location(world(), h[0], h[1], h[2], (float) h[3], 0);
    }

    /** Celle unite alla cella id (muri aperti), compresa se stessa. */
    public Set<Integer> group(int id) {
        Set<Integer> out = new LinkedHashSet<>();
        Deque<Integer> q = new ArrayDeque<>();
        q.add(id);
        while (!q.isEmpty()) {
            int c = q.poll();
            if (!out.add(c)) continue;
            int f = CellGeometry.floorOf(c), s = CellGeometry.sectorOf(c);
            if (openWalls.contains(CellGeometry.id(f, s))) q.add(CellGeometry.id(f, s - 1));
            if (openWalls.contains(CellGeometry.id(f, s + 1))) q.add(CellGeometry.id(f, s + 1));
        }
        return out;
    }

    // =====================================================================
    //  Blocchi delle celle
    // =====================================================================

    private interface KindFilter {
        boolean ok(CellGeometry.Info i);
    }

    /** Posizioni (x,y,z) di un piano che soddisfano il filtro. */
    private List<int[]> positions(int floor, KindFilter f) {
        List<int[]> out = new ArrayList<>();
        int R = (int) Math.ceil(CELL_ROUT) + 1;
        int y0 = CELL_Y0 + floor * CELL_FH;
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++) {
                double r = CellGeometry.radius(x, z);
                if (r < CELL_RIN || r >= CELL_ROUT) continue;
                for (int y = y0; y < y0 + CELL_FH; y++) {
                    CellGeometry.Info i = CellGeometry.classify(x, y, z);
                    if (i.floor() == floor && f.ok(i)) out.add(new int[]{x, y, z});
                }
            }
        return out;
    }

    private void openWall(int floor, int wall) {
        openWalls.add(CellGeometry.id(floor, wall));
        World w = world();
        for (int[] p : positions(floor, i -> i.kind() == CellGeometry.Kind.WALL_SIDE && i.sector() == Math.floorMod(wall, CELL_SECTORS)))
            w.getBlockAt(p[0], p[1], p[2]).setType(Material.AIR, false);
    }

    private void closeWall(int floor, int wall) {
        openWalls.remove(CellGeometry.id(floor, wall));
        BuildPlan plan = new BuildPlan();
        ColosseumBuild.buildCell(plan, floor, Math.floorMod(wall, CELL_SECTORS), false);
        World w = world();
        for (int[] p : positions(floor, i -> i.kind() == CellGeometry.Kind.WALL_SIDE && i.sector() == Math.floorMod(wall, CELL_SECTORS))) {
            String s = plan.get(p[0], p[1], p[2]);
            w.getBlockAt(p[0], p[1], p[2]).setBlockData(Bukkit.createBlockData(s == null ? "minecraft:stone_bricks" : s), false);
        }
    }

    /** Ricostruisce la cella (con arredamento) e riapre i muri ancora uniti. */
    public void resetCell(int id, Runnable done) {
        int f = CellGeometry.floorOf(id), s = CellGeometry.sectorOf(id);
        BuildPlan plan = new BuildPlan();
        ColosseumBuild.buildCell(plan, f, s, true);
        plugin.world().place(world(), plan, () -> {
            if (openWalls.contains(CellGeometry.id(f, s))) openWall(f, s);
            if (openWalls.contains(CellGeometry.id(f, s + 1))) openWall(f, s + 1);
            if (done != null) done.run();
        });
    }

    // =====================================================================
    //  Azioni
    // =====================================================================

    public void buy(Player p, int id) {
        PlayerData d = plugin.data().get(p);
        if (cells.containsKey(id)) {
            Txt.send(p, "Questa cella è già di qualcuno.");
            return;
        }
        int max = cfg().getInt("celle.max-per-giocatore", 4);
        if (owned(p.getUniqueId()).size() >= max) {
            Txt.send(p, "Puoi avere al massimo <white>" + max + "</white> celle.");
            return;
        }
        double price = price(d, CellGeometry.floorOf(id));
        if (!plugin.eco().take(d, Economy.Cur.SOLDI, price, "celle")) {
            Txt.send(p, "Ti servono " + Txt.soldi(price) + " <gray>(hai " + Txt.soldi(d.soldi) + "<gray>).");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            return;
        }
        Cell c = new Cell(id);
        c.owner = p.getUniqueId();
        c.ownerName = p.getName();
        c.bought = c.lastTax = System.currentTimeMillis();
        // i fidati valgono per tutte le celle del proprietario
        for (Cell o : owned(p.getUniqueId())) {
            c.trusted.addAll(o.trusted);
            c.trustedNames.putAll(o.trustedNames);
        }
        cells.put(id, c);
        save();
        p.teleport(home(id));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        Txt.send(p, "Hai comprato la <gold>" + label(id) + "</gold> per " + Txt.soldi(price) + "<gray>! Arredala come vuoi: "
                + "puoi costruire solo dentro le tue celle. Tassa giornaliera: " + Txt.soldi(tax(d)) + "<gray>.");
    }

    public void release(int id, String reason) {
        Cell c = cells.remove(id);
        if (c == null) return;
        int f = CellGeometry.floorOf(id), s = CellGeometry.sectorOf(id);
        if (openWalls.contains(CellGeometry.id(f, s))) closeWall(f, s);
        if (openWalls.contains(CellGeometry.id(f, s + 1))) closeWall(f, s + 1);
        resetCell(id, null);
        save();
        Player o = Bukkit.getPlayer(c.owner);
        if (o != null) Txt.send(o, "La tua <white>" + label(id) + "</white> è stata liberata: " + reason);
        plugin.getLogger().info("[Celle] " + label(id) + " di " + c.ownerName + " liberata: " + reason);
    }

    /** La cella in cui si trova il giocatore (interno o muri), o -1. */
    public int cellHere(Player p) {
        Location l = p.getLocation();
        if (!plugin.world().isCells(l.getWorld())) return -1;
        CellGeometry.Info i = CellGeometry.classify(l.getBlockX(), l.getBlockY(), l.getBlockZ());
        if (i.kind() != CellGeometry.Kind.INTERIOR && i.kind() != CellGeometry.Kind.DOOR) return -1;
        return CellGeometry.id(i.floor(), i.sector());
    }

    public void merge(Player p, String dir, boolean split) {
        int here = cellHere(p);
        if (here < 0 || !p.getUniqueId().equals(cells.containsKey(here) ? cells.get(here).owner : null)) {
            Txt.send(p, "Entra in una delle <white>tue</white> celle per usare questo comando.");
            return;
        }
        int step = switch (dir.toLowerCase(Locale.ROOT)) {
            case "sinistra", "s", "+", "sx" -> 1;
            case "destra", "d", "-", "dx" -> -1;
            default -> 0;
        };
        if (step == 0) {
            Txt.send(p, "Direzione: <yellow>sinistra</yellow> o <yellow>destra</yellow> (guardando la porta dall'interno).");
            return;
        }
        int f = CellGeometry.floorOf(here), s = CellGeometry.sectorOf(here);
        int other = CellGeometry.id(f, s + step);
        int wall = CellGeometry.wallBetween(here, other);
        boolean open = openWalls.contains(CellGeometry.id(f, wall));
        if (split) {
            if (!open) {
                Txt.send(p, "Quel muro è già in piedi.");
                return;
            }
            closeWall(f, wall);
            save();
            Txt.send(p, "Muro ricostruito tra <white>" + label(here) + "</white> e <white>" + label(other) + "</white>.");
            return;
        }
        Cell oc = cells.get(other);
        if (oc == null || !p.getUniqueId().equals(oc.owner)) {
            Txt.send(p, "Per unire devi possedere anche la <white>" + label(other) + "</white> (accanto, stesso piano).");
            return;
        }
        if (!CellGeometry.adjacent(here, other)) return;
        if (open) {
            Txt.send(p, "Queste celle sono già unite.");
            return;
        }
        int max = cfg().getInt("celle.max-unite", 4);
        Set<Integer> g = new HashSet<>(group(here));
        g.addAll(group(other));
        if (g.size() > max) {
            Txt.send(p, "Puoi unire al massimo <white>" + max + "</white> celle.");
            return;
        }
        openWall(f, wall);
        save();
        p.playSound(p.getLocation(), Sound.ENTITY_IRON_GOLEM_DAMAGE, 0.8f, 0.7f);
        Txt.send(p, "<gold>Muro abbattuto!</gold> <white>" + label(here) + "</white> e <white>" + label(other) + "</white> ora sono unite.");
    }

    public void trust(Player p, String name) {
        List<Cell> mine = owned(p.getUniqueId());
        if (mine.isEmpty()) {
            Txt.send(p, "Non hai celle.");
            return;
        }
        if (name == null) {
            Set<String> names = new LinkedHashSet<>(mine.getFirst().trustedNames.values());
            Txt.send(p, "Fidati delle tue celle: <white>" + (names.isEmpty() ? "nessuno" : String.join(", ", names))
                    + "</white>. <gray>Usa <yellow>/cella fidati <giocatore></yellow> per aggiungere o togliere.");
            return;
        }
        OfflinePlayer t = Bukkit.getPlayerExact(name);
        if (t == null) t = Bukkit.getOfflinePlayerIfCached(name);
        if (t == null || t.getUniqueId().equals(p.getUniqueId())) {
            Txt.send(p, "Giocatore non trovato.");
            return;
        }
        boolean add = !mine.getFirst().trusted.contains(t.getUniqueId());
        for (Cell c : mine) {
            if (add) {
                c.trusted.add(t.getUniqueId());
                c.trustedNames.put(t.getUniqueId(), t.getName() == null ? name : t.getName());
            } else {
                c.trusted.remove(t.getUniqueId());
                c.trustedNames.remove(t.getUniqueId());
            }
        }
        save();
        Txt.send(p, (add ? "<green>Aggiunto" : "<red>Tolto") + "</green></red> <white>" + Txt.esc(name) + "</white> <gray>"
                + (add ? "ai fidati: può aprire le porte e costruire nelle tue celle." : "dai fidati."));
        Player op = t.getPlayer();
        if (op != null && add) Txt.send(op, "<white>" + Txt.esc(p.getName()) + "</white> ti ha aggiunto ai fidati delle sue celle.");
    }

    // =====================================================================
    //  Porte e ascensori (chiamati da PlayerListener)
    // =====================================================================

    /** Click su una porta di ferro del colosseo. @return true se gestito */
    public boolean clickDoor(Player p, Block b) {
        if (!inWorld(b) || b.getType() != Material.IRON_DOOR) return false;
        CellGeometry.Info i = CellGeometry.classify(b.getX(), b.getY(), b.getZ());
        if (i.kind() != CellGeometry.Kind.DOOR) {
            // la metà superiore può cadere in ly=2: cerca sotto
            CellGeometry.Info j = CellGeometry.classify(b.getX(), b.getY() - 1, b.getZ());
            if (j.kind() != CellGeometry.Kind.DOOR) return false;
            i = j;
        }
        int id = CellGeometry.id(i.floor(), i.sector());
        Cell c = cells.get(id);
        if (c == null) {
            confirmBuy(p, id);
            return true;
        }
        if (!canUse(p.getUniqueId(), id) && !p.hasPermission("pepita.admin")) {
            long now = System.currentTimeMillis();
            if (now - doorMsg.getOrDefault(p.getUniqueId(), 0L) > 1500) {
                doorMsg.put(p.getUniqueId(), now);
                Txt.send(p, "<white>" + label(id) + "</white> di <gold>" + Txt.esc(c.ownerName) + "</gold>: la porta è chiusa a chiave.");
                p.playSound(b.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 0.6f, 0.6f);
            }
            return true;
        }
        toggleDoors(b, id);
        return true;
    }

    private void toggleDoors(Block clicked, int id) {
        World w = clicked.getWorld();
        Boolean target = null;
        List<Block> doors = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int dy = -2; dy <= 2; dy++) {
                    Block b = w.getBlockAt(clicked.getX() + dx, clicked.getY() + dy, clicked.getZ() + dz);
                    if (b.getType() != Material.IRON_DOOR) continue;
                    CellGeometry.Info i = CellGeometry.classify(b.getX(), b.getY(), b.getZ());
                    CellGeometry.Info i2 = CellGeometry.classify(b.getX(), b.getY() - 1, b.getZ());
                    boolean mine = (i.kind() == CellGeometry.Kind.DOOR && CellGeometry.id(i.floor(), i.sector()) == id)
                            || (i2.kind() == CellGeometry.Kind.DOOR && CellGeometry.id(i2.floor(), i2.sector()) == id);
                    if (mine) doors.add(b);
                }
        for (Block b : doors) {
            BlockData bd = b.getBlockData();
            if (!(bd instanceof Door dr)) continue;
            if (target == null) target = !dr.isOpen();
            dr.setOpen(target);
            b.setBlockData(dr, false);
        }
        if (target != null) w.playSound(clicked.getLocation(), target ? Sound.BLOCK_IRON_DOOR_OPEN : Sound.BLOCK_IRON_DOOR_CLOSE, 0.8f, 1f);
        if (Boolean.TRUE.equals(target))
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Block b : doors) {
                    if (!(b.getBlockData() instanceof Door dr) || !dr.isOpen()) continue;
                    dr.setOpen(false);
                    b.setBlockData(dr, false);
                }
                if (!doors.isEmpty()) w.playSound(doors.getFirst().getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 0.6f, 1f);
            }, 20L * cfg().getInt("celle.porta-secondi", 6));
    }

    /** Il giocatore è su una piastra dell'ascensore. @return true se gestito */
    public boolean stepPad(Player p, Block b) {
        if (!inWorld(b)) return false;
        int[] pad = pads.get(key(b.getX(), b.getY(), b.getZ()));
        if (pad == null) return false;
        long k = key(b.getX(), b.getY(), b.getZ());
        Long last = lastPad.get(p.getUniqueId());
        if (last != null && last == k) return true; // ancora sulla stessa piastra
        lastPad.put(p.getUniqueId(), k);
        elevatorMenu(p, pad[0], pad[1]);
        return true;
    }

    private void elevatorMenu(Player p, int elevator, int current) {
        Menu m = new Menu(3, "Ascensore " + (elevator + 1)).emblem(Gui.Emblem.CELLE);
        int slot = 9;
        for (int lvl = -1; lvl < CELL_FLOORS; lvl++) {
            final int L = lvl;
            boolean here = lvl == current;
            String name = lvl < 0 ? "Arena" : "Piano " + (lvl + 1);
            long mine = lvl < 0 ? 0 : owned(p.getUniqueId()).stream().filter(c -> c.floor() == L).count();
            m.set(slot, new ItemBuilder(here ? Material.LIME_STAINED_GLASS : lvl < 0 ? Material.SMOOTH_SANDSTONE : Material.IRON_BARS,
                    Math.max(1, lvl + 2)).name((here ? "<green>" : "<gold>") + "<b>" + name + "</b>")
                    .lore(here ? "<green>Sei qui" : "<#FFD54A>▶ Click per salire/scendere", mine > 0 ? "<gray>Le tue celle qui: <white>" + mine : "")
                    .build(), e -> {
                p.closeInventory();
                if (!here) ride(p, elevator, L);
            });
            slot++;
        }
        m.fill();
        m.open(p);
    }

    private void ride(Player p, int elevator, int level) {
        for (int[] pd : padList) {
            if (pd[3] != level || elevatorOf(pd[0], pd[2]) != elevator) continue;
            Location to = new Location(world(), pd[0] + 0.5, pd[1] + 0.05, pd[2] + 0.5, p.getLocation().getYaw(), p.getLocation().getPitch());
            lastPad.put(p.getUniqueId(), key(pd[0], pd[1], pd[2]));
            p.teleport(to);
            p.playSound(to, Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 1.8f);
            p.sendActionBar(Txt.mm("<gold>Ascensore</gold> <gray>→ <white>" + (level < 0 ? "Arena" : "Piano " + (level + 1))));
            return;
        }
    }

    // =====================================================================
    //  Tick
    // =====================================================================

    /** Ogni secondo: info sulle porte vicine, uscita dalle piastre. */
    public void tick() {
        World w = world();
        if (w == null) return;
        for (Player p : w.getPlayers()) {
            Location l = p.getLocation();
            Block feet = l.getBlock();
            Long last = lastPad.get(p.getUniqueId());
            if (last != null && last != key(feet.getX(), feet.getY(), feet.getZ())) lastPad.remove(p.getUniqueId());
            double r = Math.hypot(l.getX(), l.getZ());
            int dy = l.getBlockY() - CELL_Y0 - 1;
            if (r < BALCONY_IN || r > CELL_RIN + 0.5 || dy < 0 || dy >= CELL_FLOORS * CELL_FH) continue;
            int floor = dy / CELL_FH;
            double a = Math.atan2(l.getZ(), l.getX());
            if (a < 0) a += Math.PI * 2;
            int sector = Math.min(CELL_SECTORS - 1, (int) Math.floor(a / CellGeometry.STEP));
            double into = a - sector * CellGeometry.STEP;
            if (Math.abs(into - CellGeometry.STEP / 2) * r > 2.2) continue; // non davanti a una porta
            int id = CellGeometry.id(floor, sector);
            Cell c = cells.get(id);
            PlayerData d = plugin.data().get(p);
            if (c == null) p.sendActionBar(Txt.mm("<white>" + label(id) + " <dark_gray>• <green>LIBERA</green> <gray>— " + Txt.soldi(price(d, floor))
                    + " <gray>• clicca la porta per comprarla"));
            else p.sendActionBar(Txt.mm("<white>" + label(id) + " <dark_gray>• <gray>di <gold>" + Txt.esc(c.ownerName)
                    + (canUse(p.getUniqueId(), id) ? " <dark_gray>• <green>puoi entrare" : "")));
        }
    }

    /** Ogni 10 minuti: tassa giornaliera (pagata quando il proprietario è online), celle abbandonate liberate. */
    public void taxTick() {
        long now = System.currentTimeMillis();
        long day = 86_400_000L;
        int grace = cfg().getInt("celle.giorni-abbandono", 14);
        boolean changed = false;
        for (Cell c : new ArrayList<>(cells.values())) {
            if (now - c.lastTax < day) continue;
            Player o = Bukkit.getPlayer(c.owner);
            if (o != null) {
                PlayerData d = plugin.data().get(o);
                long days = (now - c.lastTax) / day;
                double t = tax(d) * days;
                if (plugin.eco().take(d, Economy.Cur.SOLDI, t, "tassa-celle")) {
                    c.lastTax += days * day;
                    Txt.send(o, "Tassa della <white>" + label(c.id) + "</white>: " + Txt.soldi(t) + " <gray>(" + days + " giorno/i).");
                } else {
                    Txt.send(o, "<red>Non hai abbastanza soldi per la tassa della " + label(c.id) + "</red> (" + Txt.soldi(t)
                            + "<red>). Dopo " + grace + " giorni di arretrati la cella viene liberata.");
                    if (now - c.lastTax > grace * day) release(c.id, "tasse non pagate");
                }
                changed = true;
            } else if (now - c.lastTax > grace * day) {
                release(c.id, "proprietario assente da più di " + grace + " giorni");
                changed = true;
            }
        }
        if (changed) save();
    }

    // =====================================================================
    //  Menu
    // =====================================================================

    public void openMain(Player p) {
        plugin.tutorial().onEvent(p, Tutorial.Ev.CELLS, 1);
        PlayerData d = plugin.data().get(p);
        List<Cell> mine = owned(p.getUniqueId());
        Menu m = new Menu(5, "Celle del Colosseo").emblem(Gui.Emblem.CELLE);
        m.set(4, Gui.button(p, "gui_cella", Material.IRON_DOOR).name("<gold><b>Le tue celle</b>")
                .lore("<gray>Possiedi <white>" + mine.size() + "</white>/" + cfg().getInt("celle.max-per-giocatore", 4) + " celle.",
                        "<gray>Tassa giornaliera: " + Txt.soldi(tax(d)) + " <gray>a cella", "",
                        "<gray>Comandi: <yellow>/cella fidati</yellow>, <yellow>/cella unisci</yellow>,",
                        "<yellow>/cella dividi</yellow>, <yellow>/cella reset</yellow>, <yellow>/cella lascia").build());
        int slot = 10;
        for (Cell c : mine) {
            Set<Integer> g = group(c.id);
            m.set(slot++, Gui.button(p, "gui_cella", Material.IRON_BARS).name("<gold><b>" + label(c.id) + "</b>")
                    .lore("<gray>Piano <white>" + (c.floor() + 1) + "</white>, settore <white>" + (c.sector() + 1),
                            g.size() > 1 ? "<gray>Unita con <white>" + (g.size() - 1) + "</white> altre" : "<gray>Non unita",
                            "", "<#FFD54A>▶ Click per andarci").build(), e -> {
                p.closeInventory();
                p.teleport(home(c.id));
            });
        }
        for (int f = 0; f < CELL_FLOORS; f++) {
            final int fl = f;
            int free = 0;
            for (int s = 0; s < CELL_SECTORS; s++) if (!cells.containsKey(CellGeometry.id(f, s))) free++;
            m.set(f < 5 ? 20 + f : 29 + (f - 5), new ItemBuilder(Material.IRON_BARS, f + 1).name("<white><b>Piano " + (f + 1) + "</b>")
                    .lore("<gray>Celle libere: <white>" + free + "</white>/" + CELL_SECTORS, "<gray>Prezzo: " + Txt.soldi(price(d, f)), "",
                            "<#FFD54A>▶ Click per vedere le celle").build(), e -> openFloor(p, fl));
        }
        m.set(40, Gui.button(p, "gui_negozio", Material.CRAFTING_TABLE).name("<#6BE36B><b>Arredamento</b>")
                .lore("<gray>Blocchi per decorare la cella.", "", "<#FFD54A>▶ Click per aprire").build(), e -> openShop(p));
        m.set(36, Gui.back(p), e -> plugin.menus().main(p));
        m.set(44, Gui.button(p, "gui_celle", Material.ENDER_PEARL).name("<gold><b>Vai al Colosseo</b>").build(), e -> {
            p.closeInventory();
            p.teleport(plugin.world().cellsSpawn());
        });
        m.frame();
        m.open(p);
    }

    public void openFloor(Player p, int floor) {
        PlayerData d = plugin.data().get(p);
        Menu m = new Menu(6, "Piano " + (floor + 1) + " • celle").emblem(Gui.Emblem.CELLE);
        double price = price(d, floor);
        for (int s = 0; s < CELL_SECTORS; s++) {
            int id = CellGeometry.id(floor, s);
            Cell c = cells.get(id);
            int slot = 9 + (s / 8) * 9 + (s % 8);
            ItemStack it;
            if (c == null) it = new ItemBuilder(Material.LIME_STAINED_GLASS_PANE).name("<green><b>" + label(id) + "</b>")
                    .lore("<green>Libera", "<gray>Prezzo: " + Txt.soldi(price), "", "<#FFD54A>▶ Click per comprarla").build();
            else if (p.getUniqueId().equals(c.owner)) it = new ItemBuilder(Material.GOLD_BLOCK).name("<gold><b>" + label(id) + "</b>")
                    .lore("<gold>È tua!", "", "<#FFD54A>▶ Click per andarci").glow().build();
            else it = new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name("<red><b>" + label(id) + "</b>")
                    .lore("<gray>Di <white>" + Txt.esc(c.ownerName), "", "<#FFD54A>▶ Click per andare alla porta").build();
            m.set(slot, it, e -> {
                Cell cc = cells.get(id);
                if (cc == null) confirmBuy(p, id);
                else if (p.getUniqueId().equals(cc.owner)) {
                    p.closeInventory();
                    p.teleport(home(id));
                } else {
                    p.closeInventory();
                    p.teleport(doorstep(id));
                }
            });
        }
        m.set(45, Gui.back(p), e -> openMain(p));
        m.fill();
        m.open(p);
    }

    public void confirmBuy(Player p, int id) {
        PlayerData d = plugin.data().get(p);
        double price = price(d, CellGeometry.floorOf(id));
        plugin.menus().confirm(p, "Comprare la " + label(id) + "?", () -> {
            p.closeInventory();
            buy(p, id);
        }, p::closeInventory, List.of("<gray>Prezzo: " + Txt.soldi(price), "<gray>Tassa giornaliera: " + Txt.soldi(tax(d))));
    }

    private static final Object[][] SHOP = {
            {Material.OAK_PLANKS, 16}, {Material.SPRUCE_PLANKS, 16}, {Material.BIRCH_PLANKS, 16}, {Material.DARK_OAK_PLANKS, 16},
            {Material.CHERRY_PLANKS, 16}, {Material.STONE_BRICKS, 16}, {Material.BRICKS, 16}, {Material.QUARTZ_BLOCK, 16},
            {Material.WHITE_WOOL, 16}, {Material.RED_WOOL, 16}, {Material.BLUE_WOOL, 16}, {Material.YELLOW_WOOL, 16},
            {Material.RED_CARPET, 16}, {Material.GRAY_CARPET, 16}, {Material.GLASS, 16}, {Material.LANTERN, 4},
            {Material.TORCH, 16}, {Material.BOOKSHELF, 4}, {Material.CRAFTING_TABLE, 1}, {Material.BARREL, 2},
            {Material.CHEST, 2}, {Material.FURNACE, 1}, {Material.RED_BED, 1}, {Material.FLOWER_POT, 4},
            {Material.POPPY, 8}, {Material.CANDLE, 8}, {Material.GLOWSTONE, 8}, {Material.OAK_STAIRS, 16}};

    public void openShop(Player p) {
        PlayerData d = plugin.data().get(p);
        Menu m = new Menu(5, "Arredamento della cella").emblem(Gui.Emblem.NEGOZIO);
        double unit = plugin.eco().minutes(d, cfg().getDouble("celle.arredo-minuti", 0.5));
        int[] slots = new int[28];
        int k = 0;
        for (int r = 1; r <= 4 && k < 28; r++) for (int c = 1; c <= 7 && k < 28; c++) slots[k++] = r * 9 + c;
        for (int i = 0; i < SHOP.length && i < slots.length; i++) {
            Material mat = (Material) SHOP[i][0];
            int n = (Integer) SHOP[i][1];
            double cost = unit * Math.max(1, n / 8.0);
            m.set(slots[i], new ItemBuilder(mat, n).lore("<gray>Prezzo: " + Txt.soldi(cost), "<#FFD54A>▶ Click per comprare").build(), e -> {
                if (!plugin.eco().take(d, Economy.Cur.SOLDI, cost, "arredo")) {
                    Txt.send(p, "Ti servono " + Txt.soldi(cost) + ".");
                    return;
                }
                for (ItemStack left : p.getInventory().addItem(new ItemStack(mat, n)).values()) p.getWorld().dropItem(p.getLocation(), left);
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
            });
        }
        m.set(40, Gui.back(p), e -> openMain(p));
        m.frame();
        m.open(p);
    }

    /** /cella con argomenti. */
    public void command(Player p, String[] a) {
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "" -> {
                List<Cell> mine = owned(p.getUniqueId());
                if (mine.size() == 1) {
                    p.teleport(home(mine.getFirst().id));
                    Txt.send(p, "Benvenuto nella tua <gold>" + label(mine.getFirst().id) + "</gold>.");
                } else openMain(p);
            }
            case "menu", "compra" -> openMain(p);
            case "fidati", "trust" -> trust(p, a.length > 1 ? a[1] : null);
            case "unisci", "merge" -> merge(p, a.length > 1 ? a[1] : "", false);
            case "dividi", "separa" -> merge(p, a.length > 1 ? a[1] : "", true);
            case "visita" -> {
                if (a.length < 2) {
                    Txt.send(p, "Uso: <yellow>/cella visita <giocatore>");
                    return;
                }
                for (Cell c : cells.values())
                    if (c.ownerName.equalsIgnoreCase(a[1])) {
                        p.teleport(doorstep(c.id));
                        Txt.send(p, "Davanti alla <white>" + label(c.id) + "</white> di <gold>" + Txt.esc(c.ownerName));
                        return;
                    }
                Txt.send(p, "Quel giocatore non ha celle.");
            }
            case "reset" -> {
                int here = cellHere(p);
                Cell c = here < 0 ? null : cells.get(here);
                if (c == null || !p.getUniqueId().equals(c.owner)) {
                    Txt.send(p, "Entra nella tua cella per resettarla.");
                    return;
                }
                plugin.menus().confirm(p, "Resettare la " + label(here) + "?", () -> {
                    p.closeInventory();
                    p.teleport(doorstep(here));
                    resetCell(here, () -> Txt.send(p, "<white>" + label(here) + "</white> resettata."));
                }, p::closeInventory, List.of("<red>Tutto quello che hai costruito", "<red>dentro la cella sparirà."));
            }
            case "lascia", "vendi" -> {
                int here = cellHere(p);
                Cell c = here < 0 ? null : cells.get(here);
                if (c == null || !p.getUniqueId().equals(c.owner)) {
                    Txt.send(p, "Entra nella cella che vuoi lasciare.");
                    return;
                }
                plugin.menus().confirm(p, "Lasciare la " + label(here) + "?", () -> {
                    p.closeInventory();
                    p.teleport(doorstep(here));
                    release(here, "l'hai lasciata tu");
                }, p::closeInventory, List.of("<red>La cella torna libera e viene resettata.", "<red>Non riavrai i soldi."));
            }
            case "info" -> {
                int here = cellHere(p);
                if (here < 0) {
                    Txt.send(p, "Non sei in una cella.");
                    return;
                }
                Cell c = cells.get(here);
                Txt.send(p, "<white>" + label(here) + "</white>: " + (c == null ? "<green>libera" : "di <gold>" + Txt.esc(c.ownerName)
                        + "</gold>, unita con " + (group(here).size() - 1) + " celle"));
            }
            default -> {
                Txt.raw(p, "<gradient:#FFE259:#FFA751><b>━━ Celle ━━</b></gradient>");
                Txt.raw(p, "<yellow>/cella</yellow> <gray>— vai alla tua cella (o menu)");
                Txt.raw(p, "<yellow>/cella menu</yellow> <gray>— compra e gestisci le celle");
                Txt.raw(p, "<yellow>/cella fidati [giocatore]</yellow> <gray>— chi può entrare e costruire");
                Txt.raw(p, "<yellow>/cella unisci <sinistra|destra></yellow> <gray>— abbatti il muro con la cella accanto (max 4)");
                Txt.raw(p, "<yellow>/cella dividi <sinistra|destra></yellow> <gray>— ricostruisci il muro");
                Txt.raw(p, "<yellow>/cella visita <giocatore></yellow>, <yellow>/cella reset</yellow>, <yellow>/cella lascia</yellow>, <yellow>/cella info");
            }
        }
    }

    public int count() {
        return cells.size();
    }

    /** Comando admin: libera una cella. */
    public boolean adminRelease(int id) {
        if (!cells.containsKey(id)) return false;
        release(id, "liberata dallo staff");
        return true;
    }
}

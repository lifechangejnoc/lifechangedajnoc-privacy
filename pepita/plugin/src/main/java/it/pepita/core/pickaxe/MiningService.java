package it.pepita.core.pickaxe;

import it.pepita.core.PepitaCore;
import it.pepita.core.crate.Crate;
import it.pepita.core.data.PlayerData;
import it.pepita.core.economy.Economy;
import it.pepita.core.mine.Mine;
import it.pepita.core.mine.Prices;
import it.pepita.core.pass.BattlePass;
import it.pepita.core.tutorial.Tutorial;
import it.pepita.core.util.Fmt;
import it.pepita.core.util.ItemBuilder;
import it.pepita.core.util.Keys;
import it.pepita.core.util.Txt;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Il cuore del prison: scavo, vendita automatica e incantesimi. */
public final class MiningService implements Listener {
    private final PepitaCore plugin;
    private static final Enchant[] AREA = {Enchant.ESPLOSIVO, Enchant.MARTELLO, Enchant.LASER, Enchant.TRIVELLA,
            Enchant.FULMINE, Enchant.METEORA};

    public MiningService(PepitaCore plugin) {
        this.plugin = plugin;
    }

    /** Totale di un'operazione di scavo. */
    private static final class Haul {
        final PlayerData d;
        final Mine m;
        final double avg; // valore medio di riferimento (nella PvP: miniera migliore del giocatore)
        double value;     // valore base (prima dei moltiplicatori)
        long blocks;
        double quantum;   // quantum base
        double mult = 1;  // superposizione
        int procs;
        final List<Location> lucky = new ArrayList<>();

        Haul(PlayerData d, Mine m, double avg) {
            this.d = d;
            this.m = m;
            this.avg = avg;
        }
    }

    private double refAvg(PlayerData d, Mine m) {
        if (!m.pvp) return m.avgValue();
        Mine best = plugin.mines().best(d);
        return best == null ? 12 : best.avgValue();
    }

    private double value(Material t, Haul h) {
        if (h.m.pvp) return t == Prices.LUCKY ? h.avg * 5 : h.avg * Prices.pvpFactor(t);
        return Prices.of(t, h.m);
    }

    private double quantumOf(Material t) {
        if (t == Prices.MINERALE) return plugin.getConfig().getDouble("quantum.minerale", 1.0);
        if (t == Prices.NUCLEO) return plugin.getConfig().getDouble("quantum.nucleo", 4.0);
        return 0;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        World w = b.getWorld();
        if (!plugin.world().isMines(w) && !plugin.world().isPvp(w)) return;
        Mine m = plugin.mines().at(b);
        if (m == null) return;
        boolean builder = p.getGameMode() == GameMode.CREATIVE && p.hasPermission("pepita.admin");
        if (builder) return;
        PlayerData d = plugin.data().get(p);
        if (!m.canAccess(d)) {
            e.setCancelled(true);
            deny(p, d, "Non hai accesso a questa miniera. Requisito: <white>" + m.requirement());
            return;
        }
        if (!PickaxeManager.isPickaxe(p.getInventory().getItemInMainHand())) {
            e.setCancelled(true);
            deny(p, d, "Usa il tuo <gold>Piccone di Pepita</gold>! Se l'hai perso: <yellow>/piccone");
            return;
        }
        if (plugin.mines().resetting(m)) {
            e.setCancelled(true);
            return;
        }
        e.setDropItems(false);
        e.setExpToDrop(0);

        Haul h = new Haul(d, m, refAvg(d, m));
        Material type = b.getType();
        h.value = value(type, h);
        h.quantum = m.pvp ? quantumOf(type) : 0;
        h.blocks = 1;
        if (type == Prices.LUCKY) h.lucky.add(b.getLocation());
        m.remaining--;

        // Combo per l'incantesimo Furia
        long now = System.currentTimeMillis();
        d.combo = now - d.lastBreak < 2000 ? Math.min(d.combo + 1, 100000) : 1;
        d.lastBreak = now;

        procAll(p, d, m, b, h);
        payout(p, d, m, h);
        rareFinds(p, d);
        for (int i = 0; i < Math.min(3, h.lucky.size()); i++) plugin.lucky().open(p, d, m, h.lucky.get(i));
        plugin.mines().checkLow(m);
    }

    private void deny(Player p, PlayerData d, String msg) {
        long now = System.currentTimeMillis();
        if (now - d.lastDeniedMsg > 2500) {
            d.lastDeniedMsg = now;
            Txt.send(p, msg);
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
        }
    }

    /** Applica moltiplicatori e accredita soldi, pepite, quantum, XP, blocchi. */
    private void payout(Player p, PlayerData d, Mine m, Haul h) {
        Economy eco = plugin.eco();
        double fortune = 1 + d.active("fortuna") * 0.02;
        double furia = 1;
        int fl = d.active("furia");
        if (fl > 0) furia = 1 + Math.min(1.0, d.combo / 500.0) * fl * 0.10;
        double money = h.value * fortune * furia * plugin.ranks().soldiMult(d) * h.mult;
        eco.give(d, Economy.Cur.SOLDI, money, m.pvp ? "scavo-pvp" : "scavo");
        d.secSoldi += money;

        int ct = d.active("cercapepite");
        double chance = Math.min(1, 0.25 + ct * 0.0002);
        double per = 1 + ct * 0.05;
        double expected = h.blocks * chance * per * plugin.ranks().pepiteMult(d) * h.mult;
        double pep = Math.floor(expected);
        if (ThreadLocalRandom.current().nextDouble() < expected - pep) pep++;
        if (pep > 0) eco.give(d, Economy.Cur.PEPITE, pep, "scavo");
        d.secPepite += pep;

        if (h.quantum > 0) plugin.quantum().gain(p, d, h.quantum * h.mult, m.pvp);

        long before = d.blocchi;
        d.blocchi += h.blocks;
        plugin.milestones().check(p, d, before);
        if (d.gang != null) plugin.gangs().addBlocks(d.gang, h.blocks);
        d.dirty = true;
        d.pickDirty = true;
        plugin.picks().addXp(p, d, h.blocks * (1 + d.active("saggezza") * 0.05) * h.mult);
        plugin.pass().addXp(p, d, h.blocks * plugin.pass().xpPerBlock());
        plugin.pass().progress(p, d, BattlePass.Mission.BLOCCHI, h.blocks);
        if (h.procs > 0) plugin.pass().progress(p, d, BattlePass.Mission.INCANTESIMI, h.procs);
        plugin.tutorial().onEvent(p, Tutorial.Ev.BLOCK, (int) Math.min(Integer.MAX_VALUE, h.blocks));
    }

    // ---------------- Probabilità ----------------

    /** Moltiplicatore di tutte le probabilità (incantesimo Risonanza). */
    public static double resonance(PlayerData d) {
        return 1 + d.active("risonanza") * 0.025;
    }

    /** Probabilità base per blocco di un incantesimo al livello attuale del giocatore (0 se non si attiva così). */
    public static double chance(PlayerData d, Enchant e) {
        int l = d.active(e.id);
        if (l <= 0) return 0;
        double c = switch (e) {
            case ESPLOSIVO -> 0.002 + l * 0.0006;
            case MARTELLO -> l * 0.0004;
            case LASER -> l * 0.0006;
            case TRIVELLA -> l * 0.0005;
            case FULMINE -> l * 0.0005;
            case METEORA -> l * 0.0004;
            case NUKE -> l * 0.00002;
            case CAOS -> l * 0.0002;
            case SINERGIA -> l * 0.03;
            case ECO -> l * 0.02;
            case MERCANTE -> l * 0.0003;
            case CERCACHIAVI -> l * 0.0001;
            case FORTUNATO -> l * 0.0001;
            case BENEFICENZA -> l * 0.00005;
            case BENEDIZIONE -> l * 0.00005;
            case MOLTIPLICATORE -> l * 0.00004;
            case CERCABOMBE -> l * 0.0001;
            case FIUTO -> 1.0 / 30000 + l * 0.00001;
            case CERCAPEPITE -> Math.min(1, 0.25 + l * 0.0002);
            case SUPERPOSIZIONE -> l * 0.004;
            case OSSERVATORE -> l * 0.00002;
            case TUNNEL -> l * 0.0008;
            case COLLASSO -> l * 0.00003;
            default -> 0;
        };
        if (e == Enchant.CERCAPEPITE) return c;
        return Math.min(1, c * resonance(d));
    }

    private boolean roll(PlayerData d, Enchant e) {
        double c = chance(d, e);
        return c > 0 && ThreadLocalRandom.current().nextDouble() < c;
    }

    // ---------------- Incantesimi ----------------

    private void procAll(Player p, PlayerData d, Mine m, Block b, Haul h) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Enchant fired = null;
        boolean pvp = m.pvp;

        if (!pvp) {
            // Distruzione (non nella miniera PvP: i Quantum vanno sudati)
            for (Enchant en : AREA) {
                int l = d.active(en.id);
                if (l <= 0) continue;
                if (roll(d, en)) {
                    fireArea(p, d, m, b, h, en, l);
                    fired = en;
                    break; // al massimo un incantesimo di area per blocco
                }
            }
            int nuke = d.active("nuke");
            if (nuke > 0 && roll(d, Enchant.NUKE)) {
                fireArea(p, d, m, b, h, Enchant.NUKE, nuke);
                fired = Enchant.NUKE;
            }
            int caos = d.active("caos");
            if (fired == null && caos > 0 && roll(d, Enchant.CAOS)) {
                Enchant en = AREA[r.nextInt(AREA.length)];
                procMsg(p, d, "<#D17BFF>Caos</#D17BFF> ha evocato <white>" + en.display + "</white>!");
                fireArea(p, d, m, b, h, en, Math.max(10, en.max / 3));
                fired = en;
            }
            if (fired != null && fired != Enchant.NUKE) {
                if (d.active("sinergia") > 0 && roll(d, Enchant.SINERGIA)) {
                    Enchant other = AREA[r.nextInt(AREA.length)];
                    int lvl = Math.max(1, d.ench(other.id));
                    procMsg(p, d, "<#D17BFF>Sinergia!</#D17BFF> " + fired.display + " → <white>" + other.display);
                    fireArea(p, d, m, b, h, other, lvl);
                }
                if (d.active("eco") > 0 && roll(d, Enchant.ECO)) {
                    procMsg(p, d, "<#D17BFF>Eco!</#D17BFF> " + fired.display + " si ripete.");
                    fireArea(p, d, m, b, h, fired, Math.max(1, d.ench(fired.id)));
                }
            }
            // Quantum: Tunnel e Collasso
            if (d.active("tunnel") > 0 && roll(d, Enchant.TUNNEL)) {
                box(b.getWorld(), m, b.getX() - 2, b.getY() - 2, b.getZ() - 2, b.getX() + 2, b.getY() + 2, b.getZ() + 2, h);
                b.getWorld().spawnParticle(Particle.PORTAL, b.getLocation().add(0.5, 0.5, 0.5), 80, 1.5, 1.5, 1.5, 0.3);
                b.getWorld().playSound(b.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.6f, 1.6f);
                procMsg(p, d, "<" + Txt.QUANTUM + ">Tunnel!</" + Txt.QUANTUM + "> Un cubo 5x5x5 è sparito.");
                h.procs++;
            }
            if (d.active("collasso") > 0 && roll(d, Enchant.COLLASSO)) {
                collapse(b.getWorld(), m, h);
                Bukkit.broadcast(Txt.mm(Txt.PREFIX + "<" + Txt.QUANTUM + "><b>COLLASSO!</b></" + Txt.QUANTUM + "> <white>" + Txt.esc(p.getName())
                        + "</white> ha fatto crollare la <white>" + m.name + "</white>!"));
                h.procs++;
            }
            // Osservatore: piccola possibilità di Quantum nelle miniere normali
            if (d.active("osservatore") > 0 && roll(d, Enchant.OSSERVATORE)) {
                h.quantum += 1;
                procMsg(p, d, "<" + Txt.QUANTUM + ">Osservatore!</" + Txt.QUANTUM + "> Hai trovato un frammento di Quantum.");
                p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.6f);
                h.procs++;
            }
        }
        if (fired != null) h.procs++;

        // Superposizione: raddoppia tutto il bottino del blocco
        if (d.active("superposizione") > 0 && roll(d, Enchant.SUPERPOSIZIONE)) {
            h.mult *= 2;
            h.procs++;
            if (h.value > 0 && r.nextInt(5) == 0)
                procMsg(p, d, "<" + Txt.QUANTUM + ">Superposizione!</" + Txt.QUANTUM + "> Ricompense raddoppiate.");
            p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, b.getLocation().add(0.5, 0.5, 0.5), 12, 0.3, 0.3, 0.3, 0.05);
        }

        // Economia
        if (d.active("mercante") > 0 && roll(d, Enchant.MERCANTE)) {
            h.value += h.avg * 200;
            procMsg(p, d, "<#6BE36B>Mercante!</#6BE36B> Hai venduto 200 blocchi in un colpo.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 0.6f, 1.3f);
            h.procs++;
        }
        if (d.active("cercachiavi") > 0 && roll(d, Enchant.CERCACHIAVI)) {
            Crate c = Crate.randomKey(r);
            d.addKeys(c.id, 1);
            procMsg(p, d, "<#6BE36B>Cercachiavi!</#6BE36B> Hai trovato una " + c.color + "Chiave " + c.display + "<gray>.");
            p.playSound(p.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1f, 1.4f);
            h.procs++;
        }
        if (d.active("fortunato") > 0 && roll(d, Enchant.FORTUNATO)) {
            fortunato(p, d);
            h.procs++;
        }
        if (d.active("beneficenza") > 0 && roll(d, Enchant.BENEFICENZA)) {
            for (Player o : Bukkit.getOnlinePlayers()) {
                PlayerData od = plugin.data().get(o);
                plugin.eco().give(od, Economy.Cur.SOLDI, plugin.eco().minutes(od, 1), "beneficenza");
            }
            Bukkit.broadcast(Txt.mm(Txt.PREFIX + "<#6BE36B>Beneficenza!</#6BE36B> <white>" + Txt.esc(p.getName())
                    + "</white> ha regalato un minuto di scavo a tutti i detenuti!"));
            h.procs++;
        }
        int bd = d.active("benedizione");
        if (bd > 0 && roll(d, Enchant.BENEDIZIONE)) {
            double gift = 25.0 * bd;
            for (Player o : Bukkit.getOnlinePlayers()) plugin.eco().give(plugin.data().get(o), Economy.Cur.PEPITE, gift, "benedizione");
            Bukkit.broadcast(Txt.mm(Txt.PREFIX + "<" + Txt.PEPITE + ">Benedizione!</" + Txt.PEPITE + "> <white>"
                    + Txt.esc(p.getName()) + "</white> ha donato " + Txt.pepite(gift) + " <gray>a tutti!"));
            h.procs++;
        }
        if (d.active("moltiplicatore") > 0 && roll(d, Enchant.MOLTIPLICATORE)) {
            long base = Math.max(System.currentTimeMillis(), d.boostUntil);
            if (d.boostMult < 2) d.boostMult = 2;
            d.boostUntil = base + 60_000;
            procMsg(p, d, "<#6BE36B>Moltiplicatore!</#6BE36B> Soldi <gold>x2</gold> per 60 secondi.");
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.6f);
            h.procs++;
        }
        if (!pvp && d.active("cercabombe") > 0 && roll(d, Enchant.CERCABOMBE)) {
            int roll = r.nextInt(100);
            String tipo = roll < 1 ? "nucleare" : roll < 10 ? "grande" : "normale";
            give(p, plugin.picks().bomb(tipo, 1));
            procMsg(p, d, "<#6BE36B>Cercabombe!</#6BE36B> Hai trovato una bomba (" + tipo + ").");
            h.procs++;
        }
    }

    private void fireArea(Player p, PlayerData d, Mine m, Block b, Haul h, Enchant en, int l) {
        World w = b.getWorld();
        int x = b.getX(), y = b.getY(), z = b.getZ();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location fx = b.getLocation().add(0.5, 0.5, 0.5);
        switch (en) {
            case ESPLOSIVO -> {
                int rad = 2 + l / 10;
                sphere(w, m, x, y, z, rad, h);
                w.spawnParticle(Particle.EXPLOSION, fx, 3, rad / 2.0, rad / 2.0, rad / 2.0, 0);
                w.playSound(fx, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.2f);
            }
            case MARTELLO -> {
                box(w, m, m.minX, y, m.minZ, m.maxX, y, m.maxZ, h);
                w.spawnParticle(Particle.CLOUD, fx, 40, 6, 0.2, 6, 0.02);
                w.playSound(fx, Sound.BLOCK_ANVIL_LAND, 0.5f, 0.8f);
                procMsg(p, d, "<#FF7B7B>Martello Pneumatico!</#FF7B7B> Strato distrutto.");
            }
            case LASER -> {
                BlockFace f = p.getFacing();
                boolean alongX = f == BlockFace.EAST || f == BlockFace.WEST;
                if (alongX) box(w, m, m.minX, y - 1, z - 1, m.maxX, y + 1, z + 1, h);
                else box(w, m, x - 1, y - 1, m.minZ, x + 1, y + 1, m.maxZ, h);
                for (int i = 0; i < 30; i++) {
                    double t = i / 30.0;
                    Location pl = alongX ? new Location(w, m.minX + t * (m.maxX - m.minX + 1), y + 0.5, z + 0.5)
                            : new Location(w, x + 0.5, y + 0.5, m.minZ + t * (m.maxZ - m.minZ + 1));
                    w.spawnParticle(Particle.END_ROD, pl, 1, 0, 0, 0, 0);
                }
                w.playSound(fx, Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 2f);
            }
            case TRIVELLA -> {
                box(w, m, x - 1, m.minY, z - 1, x + 1, y, z + 1, h);
                w.spawnParticle(Particle.ELECTRIC_SPARK, fx, 40, 0.6, 3, 0.6, 0.05);
                w.playSound(fx, Sound.BLOCK_GRINDSTONE_USE, 0.6f, 0.8f);
            }
            case FULMINE -> {
                int tx = r.nextInt(m.minX, m.maxX + 1), tz = r.nextInt(m.minZ, m.maxZ + 1);
                int ty = topY(w, m, tx, tz);
                w.strikeLightningEffect(new Location(w, tx + 0.5, ty + 1, tz + 0.5));
                sphere(w, m, tx, ty, tz, 3 + l / 15, h);
            }
            case METEORA -> {
                for (int i = 0; i < 4 + l / 8; i++) {
                    int tx = r.nextInt(m.minX, m.maxX + 1), tz = r.nextInt(m.minZ, m.maxZ + 1);
                    int ty = topY(w, m, tx, tz);
                    sphere(w, m, tx, ty, tz, 3, h);
                    Location ml = new Location(w, tx + 0.5, ty + 1, tz + 0.5);
                    w.spawnParticle(Particle.FLAME, ml, 30, 1, 1, 1, 0.05);
                    w.spawnParticle(Particle.LAVA, ml, 8, 1, 0.5, 1, 0);
                }
                w.playSound(fx, Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.6f);
                procMsg(p, d, "<#FF7B7B>Meteora!</#FF7B7B> Pioggia di fuoco sulla miniera.");
            }
            case NUKE -> {
                box(w, m, m.minX, m.minY, m.minZ, m.maxX, m.maxY, m.maxZ, h);
                w.spawnParticle(Particle.EXPLOSION_EMITTER, fx, 5, 5, 3, 5, 0);
                w.playSound(fx, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);
                Bukkit.broadcast(Txt.mm(Txt.PREFIX + "<red><b>☢ NUKE!</b></red> <white>" + Txt.esc(p.getName())
                        + "</white> ha raso al suolo la <white>" + m.name + "</white>!"));
                plugin.mines().queueReset(m);
            }
            default -> {
            }
        }
    }

    /** Collasso: crolla circa il 30% superiore della miniera, con un bordo frastagliato. */
    private void collapse(World w, Mine m, Haul h) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int depth = m.maxY - m.minY + 1;
        for (int x = m.minX; x <= m.maxX; x++)
            for (int z = m.minZ; z <= m.maxZ; z++) {
                int cut = (int) Math.round(depth * 0.3 + (r.nextDouble() - 0.5) * 3);
                for (int y = m.maxY; y > m.maxY - cut && y >= m.minY; y--) take(w.getBlockAt(x, y, z), h);
            }
        Location c = new Location(w, m.centerX() + 0.5, m.maxY - depth * 0.15, m.centerZ() + 0.5);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, c, 6, 6, 2, 6, 0);
        w.spawnParticle(Particle.REVERSE_PORTAL, c, 300, 8, 3, 8, 0.2);
        w.playSound(c, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.6f);
        plugin.mines().checkLow(m);
    }

    private int topY(World w, Mine m, int x, int z) {
        for (int y = m.maxY; y >= m.minY; y--) if (!w.getBlockAt(x, y, z).getType().isAir()) return y;
        return m.minY;
    }

    private void sphere(World w, Mine m, int cx, int cy, int cz, int r, Haul h) {
        int r2 = r * r;
        for (int x = Math.max(m.minX, cx - r); x <= Math.min(m.maxX, cx + r); x++)
            for (int y = Math.max(m.minY, cy - r); y <= Math.min(m.maxY, cy + r); y++)
                for (int z = Math.max(m.minZ, cz - r); z <= Math.min(m.maxZ, cz + r); z++) {
                    int dx = x - cx, dy = y - cy, dz = z - cz;
                    if (dx * dx + dy * dy + dz * dz <= r2) take(w.getBlockAt(x, y, z), h);
                }
    }

    private void box(World w, Mine m, int x1, int y1, int z1, int x2, int y2, int z2, Haul h) {
        for (int x = Math.max(m.minX, x1); x <= Math.min(m.maxX, x2); x++)
            for (int y = Math.max(m.minY, y1); y <= Math.min(m.maxY, y2); y++)
                for (int z = Math.max(m.minZ, z1); z <= Math.min(m.maxZ, z2); z++)
                    take(w.getBlockAt(x, y, z), h);
    }

    private void take(Block bl, Haul h) {
        Material t = bl.getType();
        if (t.isAir()) return;
        h.value += value(t, h);
        if (h.m.pvp) h.quantum += quantumOf(t);
        if (t == Prices.LUCKY) h.lucky.add(bl.getLocation());
        h.blocks++;
        h.m.remaining--;
        bl.setType(Material.AIR, false);
    }

    private void fortunato(Player p, PlayerData d) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int roll = r.nextInt(100);
        String what;
        if (roll < 45) {
            double v = plugin.eco().minutes(d, 4);
            plugin.eco().give(d, Economy.Cur.SOLDI, v, "fortunato");
            what = Txt.soldi(v);
        } else if (roll < 80) {
            double v = 50 + r.nextInt(450);
            plugin.eco().give(d, Economy.Cur.PEPITE, v, "fortunato");
            what = Txt.pepite(v);
        } else if (roll < 95) {
            Crate c = Crate.randomKey(r);
            d.addKeys(c.id, 1);
            what = c.color + "Chiave " + c.display + "<gray>";
        } else {
            int g = 1 + r.nextInt(3);
            plugin.eco().give(d, Economy.Cur.GEMME, g, "fortunato");
            what = Txt.gemme(g);
        }
        procMsg(p, d, "<#6BE36B>Fortunato!</#6BE36B> Hai trovato " + what + "<gray>.");
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.6f);
    }

    private void rareFinds(Player p, PlayerData d) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double chance = 1.0 / 30000 + d.active("fiuto") * 0.00001;
        if (r.nextDouble() < chance * resonance(d)) {
            give(p, plugin.picks().pepitaOro());
            d.pepiteTrovate++;
            p.showTitle(Title.title(Txt.mm("<gradient:#FFF6B7:#FFD54A:#FFA726><b>✦ PEPITA D'ORO ✦</b></gradient>"),
                    Txt.mm("<gray>Hai trovato la pepita leggendaria!"),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2500), Duration.ofMillis(500))));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.4f);
            Bukkit.broadcast(Txt.mm(Txt.PREFIX + "<white>" + Txt.esc(p.getName())
                    + "</white> ha trovato una <gradient:#FFF6B7:#FFA726><b>PEPITA D'ORO</b></gradient>!"));
        }
    }

    private void procMsg(Player p, PlayerData d, String msg) {
        if (d.procMessages) Txt.send(p, msg);
    }

    private void give(Player p, ItemStack it) {
        for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItem(p.getLocation(), left);
    }

    // ---------------- Bombe ----------------

    public void throwBomb(Player p, ItemStack hand, String tipo) {
        Snowball s = p.launchProjectile(Snowball.class);
        ItemStack vis = hand.clone();
        vis.setAmount(1);
        s.setItem(vis);
        s.getPersistentDataContainer().set(Keys.BOMB, PersistentDataType.STRING, tipo);
        hand.setAmount(hand.getAmount() - 1);
        p.playSound(p.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1f, 0.6f);
    }

    @EventHandler
    public void onBombHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Snowball s)) return;
        String tipo = s.getPersistentDataContainer().get(Keys.BOMB, PersistentDataType.STRING);
        if (tipo == null || !(s.getShooter() instanceof Player p)) return;
        Location at = e.getHitBlock() != null ? e.getHitBlock().getLocation() : s.getLocation();
        Mine m = plugin.mines().at(at);
        if (m == null) m = plugin.mines().pitOf(at);
        World w = at.getWorld();
        if (m == null || m.pvp) {
            w.spawnParticle(Particle.SMOKE, at, 20, 0.3, 0.3, 0.3, 0.02);
            Txt.send(p, m == null ? "La bomba funziona solo dentro una miniera." : "Nella miniera PvP le bombe non funzionano.");
            for (ItemStack left : p.getInventory().addItem(plugin.picks().bomb(tipo, 1)).values()) w.dropItem(p.getLocation(), left);
            return;
        }
        PlayerData d = plugin.data().get(p);
        if (!m.canAccess(d)) {
            Txt.send(p, "Non hai accesso a questa miniera.");
            return;
        }
        int r = PickaxeManager.radius(tipo);
        Haul h = new Haul(d, m, m.avgValue());
        int cx = Math.max(m.minX, Math.min(m.maxX, at.getBlockX()));
        int cy = Math.max(m.minY, Math.min(m.maxY, at.getBlockY()));
        int cz = Math.max(m.minZ, Math.min(m.maxZ, at.getBlockZ()));
        sphere(w, m, cx, cy, cz, r, h);
        w.spawnParticle(r >= 10 ? Particle.EXPLOSION_EMITTER : Particle.EXPLOSION, at, r >= 10 ? 4 : 6, r / 2.0, r / 2.0, r / 2.0, 0);
        w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, r >= 10 ? 0.5f : 0.9f);
        if (h.blocks > 0) {
            payout(p, d, m, h);
            plugin.pass().progress(p, d, BattlePass.Mission.BOMBE, 1);
            Txt.send(p, "<#FF7B7B>BOOM!</#FF7B7B> <white>" + Fmt.num(h.blocks) + "</white> blocchi distrutti.");
            for (int i = 0; i < Math.min(3, h.lucky.size()); i++) plugin.lucky().open(p, d, m, h.lucky.get(i));
            plugin.mines().checkLow(m);
        }
    }

    /** Usato dalle casse per controllare i tipi di bomba. */
    public static boolean isBomb(ItemStack it) {
        return ItemBuilder.startsWith(it, PickaxeManager.BOMB + ":");
    }

    public static List<String> bombTypes() {
        return new ArrayList<>(List.of("normale", "grande", "nucleare"));
    }
}

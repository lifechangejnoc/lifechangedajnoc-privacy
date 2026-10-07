package it.pepita.core.rank;

import it.pepita.core.PepitaCore;
import it.pepita.core.data.PlayerData;
import it.pepita.core.util.Fmt;
import it.pepita.core.util.Txt;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.time.Duration;

/** Rank A-Z, Prestigio ed Evasione + calcolo dei moltiplicatori. */
public final class RankManager {
    public static final int MAX_RANK = 25;
    private final PepitaCore plugin;

    private double base, growth, prestigeCostMult, prestigeFactor, evasioneCostMult;
    private int evasionePrestige, maxPrestige;
    private double prestigeSoldi, evasioneSoldi, evasionePepite;

    public RankManager(PepitaCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        base = c.getDouble("rank.costo-base", 4000);
        growth = c.getDouble("rank.crescita", 1.5);
        prestigeCostMult = c.getDouble("prestigio.aumento-costo", 0.5);
        prestigeFactor = c.getDouble("prestigio.costo-moltiplicatore", 2.5);
        maxPrestige = c.getInt("prestigio.massimo", 1000);
        prestigeSoldi = c.getDouble("prestigio.bonus-soldi", 0.10);
        evasionePrestige = c.getInt("evasione.prestigio-richiesto", 10);
        evasioneCostMult = c.getDouble("evasione.aumento-costo", 3.0);
        evasioneSoldi = c.getDouble("evasione.bonus-soldi", 0.25);
        evasionePepite = c.getDouble("evasione.bonus-pepite", 0.50);
    }

    public static String letter(int r) {
        return String.valueOf((char) ('A' + Math.max(0, Math.min(MAX_RANK, r))));
    }

    /** Colore del rank (sfumatura dal grigio all'oro). */
    public static String color(int r) {
        String[] c = {"#B0B0B0", "#C8C8C8", "#9BE39B", "#6BE36B", "#55E6C8", "#55C8FF", "#5599FF", "#9B7BFF", "#D17BFF", "#FF7BD1", "#FF7B7B", "#FFA726", "#FFD54A"};
        return c[Math.min(c.length - 1, r * c.length / 26)];
    }

    public static String tag(PlayerData d) {
        StringBuilder sb = new StringBuilder();
        if (d.evasioni > 0) sb.append("<#FF5555>E").append(d.evasioni).append("</#FF5555> ");
        if (d.prestige > 0) sb.append("<#D17BFF>P").append(d.prestige).append("</#D17BFF> ");
        sb.append("<").append(color(d.rank)).append(">").append(letter(d.rank)).append("</").append(color(d.rank)).append(">");
        return sb.toString();
    }

    /** Costo per passare dal rank "rank" al successivo. */
    public double cost(PlayerData d, int rank) {
        return base * Math.pow(growth, rank) * (1 + d.prestige * prestigeCostMult) * Math.pow(evasioneCostMult, d.evasioni);
    }

    public double nextCost(PlayerData d) {
        return cost(d, d.rank);
    }

    public double prestigeCost(PlayerData d) {
        return cost(d, MAX_RANK) * prestigeFactor;
    }

    public double progress(PlayerData d) {
        double c = d.rank >= MAX_RANK ? prestigeCost(d) : nextCost(d);
        return Math.min(1, d.soldi / c);
    }

    // --------------- Moltiplicatori ---------------

    /** Somma dei bonus permanenti dei soldi (prestigio, evasioni, VIP, livello piccone, skin, corazza). */
    public double soldiBonus(PlayerData d) {
        return d.prestige * prestigeSoldi + d.evasioni * evasioneSoldi + VipTier.of(d.vip).soldiBonus
                + (d.pickLevel - 1) * 0.005 + plugin.picks().skinBonus(d)[0]
                + plugin.armor().bonus(d, it.pepita.core.armor.ArmorService.SOLDI);
    }

    /** Moltiplicatore soldi totale grezzo (con booster e Corsa all'Oro), prima del soft cap. */
    public double soldiMultRaw(PlayerData d) {
        double m = 1 + soldiBonus(d);
        if (d.boostUntil > System.currentTimeMillis()) m *= d.boostMult;
        m *= plugin.boosters().globalMult();
        if (plugin.goldRush().active()) m *= 2;
        return m;
    }

    /** Moltiplicatore soldi effettivo (soft cap, vedi ECONOMIA.md). */
    public double soldiMult(PlayerData d) {
        return plugin.eco().softCap(soldiMultRaw(d));
    }

    /** Moltiplicatore permanente (senza booster temporanei): base dell'unità di reddito. */
    public double soldiMultBase(PlayerData d) {
        return plugin.eco().softCap(1 + soldiBonus(d));
    }

    public double pepiteMult(PlayerData d) {
        double m = 1 + d.evasioni * evasionePepite + VipTier.of(d.vip).pepiteBonus + d.prestige * 0.02
                + plugin.picks().skinBonus(d)[1] + plugin.armor().bonus(d, it.pepita.core.armor.ArmorService.PEPITE);
        if (plugin.goldRush().active()) m *= 2;
        return m;
    }

    public double quantumMult(PlayerData d) {
        return 1 + plugin.picks().skinBonus(d)[2] + plugin.armor().bonus(d, it.pepita.core.armor.ArmorService.QUANTUM);
    }

    // --------------- Azioni ---------------

    public boolean rankup(Player p, boolean silent) {
        PlayerData d = plugin.data().get(p);
        if (d.rank >= MAX_RANK) {
            if (!silent) Txt.send(p, "Sei già al rank <gold>Z</gold>! Usa <yellow>/prestigio</yellow> per ricominciare più forte.");
            return false;
        }
        double c = nextCost(d);
        if (d.soldi < c) {
            if (!silent) Txt.send(p, "Ti servono ancora " + Txt.soldi(c - d.soldi) + " per il rank <white>" + letter(d.rank + 1) + "</white>.");
            return false;
        }
        plugin.eco().take(d, it.pepita.core.economy.Economy.Cur.SOLDI, c, "rankup");
        d.rank++;
        d.dirty = true;
        if (!silent) celebrateRank(p, d);
        plugin.tutorial().onEvent(p, it.pepita.core.tutorial.Tutorial.Ev.RANKUP, 1);
        return true;
    }

    public int rankupMax(Player p) {
        PlayerData d = plugin.data().get(p);
        int n = 0;
        while (d.rank < MAX_RANK && d.soldi >= nextCost(d)) {
            plugin.eco().take(d, it.pepita.core.economy.Economy.Cur.SOLDI, nextCost(d), "rankup");
            d.rank++;
            n++;
        }
        if (n > 0) {
            d.dirty = true;
            celebrateRank(p, d);
            plugin.tutorial().onEvent(p, it.pepita.core.tutorial.Tutorial.Ev.RANKUP, 1);
            Txt.send(p, "Sei salito di <white>" + n + "</white> rank!");
        } else if (d.rank >= MAX_RANK) {
            Txt.send(p, "Sei già al rank <gold>Z</gold>! Usa <yellow>/prestigio</yellow>.");
        } else {
            Txt.send(p, "Ti servono ancora " + Txt.soldi(nextCost(d) - d.soldi) + " per il prossimo rank.");
        }
        return n;
    }

    private void celebrateRank(Player p, PlayerData d) {
        String l = letter(d.rank);
        p.showTitle(Title.title(Txt.mm("<" + color(d.rank) + "><b>RANK " + l + "</b>"),
                Txt.mm("<gray>Nuova miniera sbloccata!"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1500), Duration.ofMillis(400))));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.3);
        if (d.rank == MAX_RANK) {
            Bukkit.broadcast(Txt.mm(Txt.PREFIX + "<white>" + Txt.esc(p.getName()) + "</white> ha raggiunto il rank <gold><b>Z</b></gold>!"));
        }
        plugin.picks().refresh(p);
    }

    public boolean prestige(Player p) {
        PlayerData d = plugin.data().get(p);
        if (d.rank < MAX_RANK) {
            Txt.send(p, "Devi essere al rank <gold>Z</gold> per il prestigio.");
            return false;
        }
        if (d.prestige >= maxPrestige) {
            Txt.send(p, "Hai raggiunto il prestigio massimo! Tenta la <red>/evasione</red>.");
            return false;
        }
        double c = prestigeCost(d);
        if (d.soldi < c) {
            Txt.send(p, "Ti servono " + Txt.soldi(c) + " per il prestigio (hai " + Txt.soldi(d.soldi) + ").");
            return false;
        }
        plugin.eco().wipe(d, it.pepita.core.economy.Economy.Cur.SOLDI, "prestigio");
        d.rank = 0;
        d.prestige++;
        double reward = 500.0 * d.prestige;
        plugin.eco().give(d, it.pepita.core.economy.Economy.Cur.PEPITE, reward, "prestigio");
        long gems = plugin.getConfig().getLong("gemme-guadagnabili.prestigio", 3);
        plugin.eco().give(d, it.pepita.core.economy.Economy.Cur.GEMME, gems, "prestigio");
        d.addKeys("rara", 1);
        d.dirty = true;
        p.showTitle(Title.title(Txt.mm("<#D17BFF><b>PRESTIGIO " + Fmt.roman(d.prestige) + "</b>"),
                Txt.mm("<gray>+" + (int) (prestigeSoldi * 100) + "% soldi per sempre"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2500), Duration.ofMillis(600))));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        Bukkit.broadcast(Txt.mm(Txt.PREFIX + "<white>" + Txt.esc(p.getName()) + "</white> è salito al <#D17BFF><b>Prestigio " + d.prestige + "</b></#D17BFF>!"));
        Txt.send(p, "Ricompense: " + Txt.pepite(reward) + ", " + Txt.gemme(gems) + " e 1 <aqua>Chiave Rara</aqua>.");
        plugin.picks().refresh(p);
        p.teleport(plugin.world().prisonSpawn());
        return true;
    }

    public int evasionePrestige() {
        return evasionePrestige;
    }

    public boolean evasione(Player p) {
        PlayerData d = plugin.data().get(p);
        if (d.prestige < evasionePrestige) {
            Txt.send(p, "Per evadere ti serve il <#D17BFF>Prestigio " + evasionePrestige + "</#D17BFF> (sei al " + d.prestige + ").");
            return false;
        }
        plugin.eco().wipe(d, it.pepita.core.economy.Economy.Cur.SOLDI, "evasione");
        d.rank = 0;
        d.prestige = 0;
        d.evasioni++;
        plugin.eco().give(d, it.pepita.core.economy.Economy.Cur.PEPITE, 25000.0 * d.evasioni, "evasione");
        plugin.eco().give(d, it.pepita.core.economy.Economy.Cur.GEMME, plugin.getConfig().getLong("gemme-guadagnabili.evasione", 25), "evasione");
        d.addKeys("pepita", 2);
        d.dirty = true;
        p.showTitle(Title.title(Txt.mm("<#FF5555><b>EVASIONE RIUSCITA!</b>"),
                Txt.mm("<gray>Evasione n°" + d.evasioni + " • bonus permanenti sbloccati"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(3500), Duration.ofMillis(800))));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
        p.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, p.getLocation(), 2);
        Bukkit.broadcast(Txt.mm(Txt.PREFIX + "<red><b>EVASIONE!</b></red> <white>" + Txt.esc(p.getName())
                + "</white> è fuggito da Pepita per la <red>" + d.evasioni + "ª</red> volta!"));
        plugin.picks().refresh(p);
        p.teleport(plugin.world().prisonSpawn());
        return true;
    }
}

package it.pepita.core.quantum;

import it.pepita.core.PepitaCore;
import it.pepita.core.data.PlayerData;
import it.pepita.core.economy.Economy;
import it.pepita.core.pass.BattlePass;
import it.pepita.core.pickaxe.PickTier;
import it.pepita.core.util.Fmt;
import it.pepita.core.util.Keys;
import it.pepita.core.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Quantum: valuta scarsa e contesa della miniera PvP.
 * <ul>
 *     <li>I Quantum scavati restano "in tasca" finché non si torna nella zona sicura (o si esce dal mondo PvP):
 *     chi muore li perde e metà va a chi l'ha ucciso.</li>
 *     <li>Rendimenti decrescenti per giocatore e per ora: resa = base × D / (D + guadagnati nell'ora).</li>
 *     <li>Blocchi duri: un modificatore dell'attributo block_break_speed porta il Minerale Quantico a ~2,5 s
 *     e il Nucleo Quantico a ~6 s qualunque siano piccone, Efficienza e Rapidità (Frantumazione accorcia).</li>
 * </ul>
 */
public final class QuantumService implements Listener {
    private final PepitaCore plugin;

    public QuantumService(PepitaCore plugin) {
        this.plugin = plugin;
    }

    private void resetHour(PlayerData d) {
        long now = System.currentTimeMillis();
        if (now - d.quantumOraInizio >= 3_600_000L) {
            d.quantumOraInizio = now;
            d.quantumOra = 0;
        }
    }

    /** Resa attuale dei Quantum per il giocatore (1 = piena). */
    public double resa(PlayerData d) {
        resetHour(d);
        double half = plugin.getConfig().getDouble("quantum.dimezzamento-ora", 400);
        return half <= 0 ? 1 : half / (half + d.quantumOra);
    }

    /** Quantum guadagnati scavando. pvp = vanno in tasca (da mettere al sicuro). */
    public void gain(Player p, PlayerData d, double base, boolean pvp) {
        double eff = base * plugin.ranks().quantumMult(d) * resa(d);
        if (eff <= 0) return;
        d.quantumOra += eff;
        if (pvp) {
            d.quantumTasca += eff;
            d.dirty = true;
            p.sendActionBar(Txt.mm(Txt.S_QUANTUM + " <" + Txt.QUANTUM + ">+" + Fmt.num(eff) + " Quantum</" + Txt.QUANTUM
                    + "> <dark_gray>| <gray>in tasca: <white>" + Fmt.num(d.quantumTasca) + " <dark_gray>| <gray>torna allo spawn per salvarli"));
        } else {
            plugin.eco().give(d, Economy.Cur.QUANTUM, eff, "osservatore");
        }
        plugin.pass().progress(p, d, BattlePass.Mission.QUANTUM, eff);
    }

    public void deposit(Player p, PlayerData d) {
        if (d.quantumTasca <= 0) return;
        double v = d.quantumTasca;
        d.quantumTasca = 0;
        plugin.eco().give(d, Economy.Cur.QUANTUM, v, "pvp");
        Txt.send(p, "Hai messo al sicuro " + Txt.quantum(v) + "<gray>. Totale: " + Txt.quantum(d.quantum));
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
    }

    /** Ogni secondo. */
    public void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = plugin.data().get(p);
            boolean inPvp = plugin.world().isPvp(p.getWorld());
            if (inPvp) {
                if (d.quantumTasca > 0 && plugin.world().inPvpSafe(p.getLocation())) deposit(p, d);
                applySpeed(p, d);
            } else {
                removeSpeed(p);
                if (d.quantumTasca > 0) deposit(p, d);
            }
        }
    }

    // ---------------- Lentezza dei blocchi quantici ----------------

    /** Velocità di scavo "vanilla" del giocatore col suo piccone (tool + Efficienza, × Rapidità). */
    private double speed(PlayerData d) {
        PickTier t = plugin.picks().tier(d);
        int eff = d.active("efficienza");
        double s = t.toolSpeed() + (eff > 0 ? eff * eff + 1 : 0);
        int haste = d.active("rapidita");
        return s * (1 + 0.2 * haste);
    }

    /** Moltiplicatore di block_break_speed per avere il tempo desiderato sul Minerale Quantico (durezza 1,5). */
    public double breakMult(PlayerData d) {
        double base = plugin.getConfig().getDouble("quantum.secondi-minerale", 2.5);
        double t = base / (1 + 0.04 * d.active("frantumazione"));
        double required = 30 * 1.5 / (20 * t);
        return Math.min(1, required / speed(d));
    }

    private void applySpeed(Player p, PlayerData d) {
        AttributeInstance a = p.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (a == null) return;
        double amount = breakMult(d) - 1;
        AttributeModifier cur = a.getModifier(Keys.PVP_SPEED);
        if (cur != null && Math.abs(cur.getAmount() - amount) < 1e-6) return;
        if (cur != null) a.removeModifier(Keys.PVP_SPEED);
        a.addTransientModifier(new AttributeModifier(Keys.PVP_SPEED, amount, AttributeModifier.Operation.ADD_NUMBER));
    }

    public void removeSpeed(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (a != null && a.getModifier(Keys.PVP_SPEED) != null) a.removeModifier(Keys.PVP_SPEED);
    }

    // ---------------- Contesa ----------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player v) || !plugin.world().isPvp(v.getWorld())) return;
        Player a = null;
        if (e.getDamager() instanceof Player x) a = x;
        else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player x) a = x;
        if (a == null || a.equals(v)) return;
        PlayerData d = plugin.data().get(v);
        d.lastPvpHit = System.currentTimeMillis();
        d.lastPvpAttacker = a.getUniqueId();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player v = e.getEntity();
        if (!plugin.world().isPvp(v.getWorld())) return;
        PlayerData d = plugin.data().get(v);
        if (d.quantumTasca <= 0) return;
        double lost = d.quantumTasca;
        d.quantumTasca = 0;
        d.dirty = true;
        Player k = v.getKiller();
        if (k != null && !k.equals(v)) {
            PlayerData kd = plugin.data().get(k);
            double half = lost * plugin.getConfig().getDouble("quantum.bottino-uccisione", 0.5);
            kd.quantumTasca += half;
            Txt.send(k, "Hai rubato " + Txt.quantum(half) + " <gray>a <white>" + Txt.esc(v.getName()) + "</white>! Portali allo spawn.");
        }
        Txt.send(v, "<red>Sei morto con " + Txt.quantum(lost) + " <red>in tasca: li hai persi!");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.data().get(p);
        removeSpeed(p);
        if (d.quantumTasca <= 0) return;
        if (plugin.world().isPvp(p.getWorld()) && System.currentTimeMillis() - d.lastPvpHit < 15_000) {
            // uscita in combattimento: i Quantum vanno a chi ti stava attaccando
            double lost = d.quantumTasca;
            d.quantumTasca = 0;
            Player a = d.lastPvpAttacker == null ? null : Bukkit.getPlayer(d.lastPvpAttacker);
            if (a != null) {
                PlayerData ad = plugin.data().get(a);
                ad.quantumTasca += lost * 0.5;
                Txt.send(a, "<white>" + Txt.esc(p.getName()) + "</white> è scappato in combattimento: " + Txt.quantum(lost * 0.5) + " <gray>sono tuoi.");
            }
        } else {
            double v = d.quantumTasca;
            d.quantumTasca = 0;
            plugin.eco().give(d, Economy.Cur.QUANTUM, v, "pvp");
        }
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (!plugin.world().isPvp(p.getWorld())) {
            removeSpeed(p);
            PlayerData d = plugin.data().get(p);
            if (d.quantumTasca > 0) deposit(p, d);
        }
    }
}

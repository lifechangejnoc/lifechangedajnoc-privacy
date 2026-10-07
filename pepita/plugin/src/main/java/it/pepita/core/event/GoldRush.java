package it.pepita.core.event;

import it.pepita.core.PepitaCore;
import it.pepita.core.util.Fmt;
import it.pepita.core.util.Txt;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Corsa all'Oro: l'evento simbolo di Pepita.
 * Soldi e pepite x2 e nelle miniere compaiono le "Pepite Giganti" (blocchi d'oro grezzo che valgono 15 volte la media).
 */
public final class GoldRush {
    private final PepitaCore plugin;
    private long endAt;
    private long startAt;
    private long nextAt;
    private final BossBar bar = BossBar.bossBar(Txt.mm(""), 1f, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10);

    public GoldRush(PepitaCore plugin) {
        this.plugin = plugin;
        scheduleNext();
    }

    public boolean active() {
        return endAt > System.currentTimeMillis();
    }

    public long secondsLeft() {
        return Math.max(0, (endAt - System.currentTimeMillis()) / 1000);
    }

    public long secondsToNext() {
        return Math.max(0, (nextAt - System.currentTimeMillis()) / 1000);
    }

    private void scheduleNext() {
        int min = plugin.getConfig().getInt("corsa-all-oro.ogni-minuti-min", 45);
        int max = plugin.getConfig().getInt("corsa-all-oro.ogni-minuti-max", 75);
        nextAt = System.currentTimeMillis() + ThreadLocalRandom.current().nextLong(min, Math.max(min + 1, max + 1)) * 60_000L;
    }

    public void start(int seconds, String cause) {
        long now = System.currentTimeMillis();
        if (active()) {
            endAt += seconds * 1000L;
            Bukkit.broadcast(Txt.mm(Txt.PREFIX + "<gold>La Corsa all'Oro si allunga!</gold> <gray>(+" + Fmt.time(seconds) + ")"));
            return;
        }
        startAt = now;
        endAt = now + seconds * 1000L;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(Title.title(Txt.mm("<gradient:#FFF6B7:#FFD54A:#FFA726><b>CORSA ALL'ORO!</b></gradient>"),
                    Txt.mm("<gray>Soldi e pepite <gold>x2</gold> • cerca le <gold>Pepite Giganti</gold>!"),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(3000), Duration.ofMillis(700))));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
            p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1f, 1f);
        }
        Bukkit.broadcast(Txt.mm(Txt.PREFIX + "<gold><b>CORSA ALL'ORO!</b></gold> " + cause
                + " <gray>Per <white>" + Fmt.time(seconds) + "</white> soldi e pepite raddoppiano e nelle miniere compaiono le <gold>Pepite Giganti</gold>!"));
        plugin.mines().refreshActive();
    }

    public void tick() {
        long now = System.currentTimeMillis();
        if (active()) {
            float prog = (float) Math.max(0, Math.min(1, (double) (endAt - now) / Math.max(1, endAt - startAt)));
            bar.name(Txt.mm("<gradient:#FFF6B7:#FFD54A:#FFA726><b>☀ CORSA ALL'ORO ☀</b></gradient> <gray>soldi e pepite <gold>x2</gold> • <yellow>" + Fmt.time(secondsLeft())));
            bar.progress(prog);
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showBossBar(bar);
                if (ThreadLocalRandom.current().nextInt(4) == 0)
                    p.getWorld().spawnParticle(Particle.WAX_ON, p.getLocation().add(0, 2.2, 0), 3, 0.6, 0.3, 0.6, 0);
            }
            return;
        }
        if (endAt != 0) {
            endAt = 0;
            for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
            Bukkit.broadcast(Txt.mm(Txt.PREFIX + "La <gold>Corsa all'Oro</gold> è finita. Alla prossima!"));
            scheduleNext();
        }
        if (nextAt > 0 && now >= nextAt && !Bukkit.getOnlinePlayers().isEmpty()) {
            nextAt = Long.MAX_VALUE;
            start(plugin.getConfig().getInt("corsa-all-oro.durata-secondi", 300), "<gray>È stato trovato un nuovo filone d'oro!");
        } else if (nextAt > 0 && now >= nextAt) {
            scheduleNext();
        }
        long warn = secondsToNext();
        if (warn == 60) Bukkit.broadcast(Txt.mm(Txt.PREFIX + "<gold>Corsa all'Oro</gold> tra <white>1 minuto</white>! Preparate i picconi."));
    }

    public void hideAll() {
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
    }
}

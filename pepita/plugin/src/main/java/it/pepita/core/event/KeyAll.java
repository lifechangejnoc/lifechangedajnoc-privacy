package it.pepita.core.event;

import it.pepita.core.PepitaCore;
import it.pepita.core.crate.Crate;
import it.pepita.core.data.PlayerData;
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

/** Keyall casuale: ogni 35-45 minuti tutti gli online ricevono chiavi. Bossbar negli ultimi 60 secondi. */
public final class KeyAll {
    private final PepitaCore plugin;
    private long nextAt;
    private final BossBar bar = BossBar.bossBar(Txt.mm(""), 1f, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_12);
    private boolean showing;

    public KeyAll(PepitaCore plugin) {
        this.plugin = plugin;
        schedule();
    }

    private void schedule() {
        int min = plugin.getConfig().getInt("keyall.minuti-min", 35);
        int max = plugin.getConfig().getInt("keyall.minuti-max", 45);
        nextAt = System.currentTimeMillis() + ThreadLocalRandom.current().nextLong(min * 60L, Math.max(min + 1, max) * 60L + 1) * 1000L;
    }

    public long secondsToNext() {
        return Math.max(0, (nextAt - System.currentTimeMillis()) / 1000);
    }

    public void tick() {
        long left = secondsToNext();
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            if (left < 60) schedule();
            return;
        }
        if (left <= 60 && left > 0) {
            bar.name(Txt.mm("<gradient:#D17BFF:#FFD54A><b>🗝 KEYALL</b></gradient> <gray>tra <white>" + Fmt.time(left) + "</white> <gray>• chiavi per tutti gli online!"));
            bar.progress((float) Math.max(0, Math.min(1, left / 60.0)));
            for (Player p : Bukkit.getOnlinePlayers()) p.showBossBar(bar);
            showing = true;
            if (left == 60 || left == 30 || left <= 5)
                for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, left <= 5 ? 2f : 1.2f);
        }
        if (left == 0) fire();
    }

    public void fire() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int roll = r.nextInt(100);
        Crate c = roll < 50 ? Crate.COMUNE : roll < 85 ? Crate.RARA : roll < 98 ? Crate.LEGGENDARIA : Crate.PEPITA;
        int n = c == Crate.COMUNE ? 2 + r.nextInt(2) : 1;
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = plugin.data().get(p);
            d.addKeys(c.id, n);
            p.hideBossBar(bar);
            p.showTitle(Title.title(Txt.mm("<gradient:#D17BFF:#FFD54A><b>KEYALL!</b></gradient>"),
                    Txt.mm("<gray>Tutti ricevono <white>" + n + "x</white> " + c.color + "Chiave " + c.display),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2500), Duration.ofMillis(500))));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.3f);
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.3);
        }
        Bukkit.broadcast(Txt.mm(Txt.PREFIX + "<gradient:#D17BFF:#FFD54A><b>KEYALL!</b></gradient> <gray>Tutti gli online ricevono <white>" + n + "x</white> "
                + c.color + "Chiave " + c.display + "<gray>. Aprile in <yellow>/casse</yellow>!"));
        showing = false;
        schedule();
    }

    public void hideAll() {
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
    }

    public void onJoin(Player p) {
        if (showing) p.showBossBar(bar);
    }
}

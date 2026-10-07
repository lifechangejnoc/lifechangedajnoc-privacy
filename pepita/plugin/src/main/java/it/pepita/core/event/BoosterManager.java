package it.pepita.core.event;

import it.pepita.core.PepitaCore;
import it.pepita.core.util.Fmt;
import it.pepita.core.util.Txt;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;

/** Booster globale dei soldi (comprato con le gemme o dato dagli admin). */
public final class BoosterManager {
    private final PepitaCore plugin;
    private final File file;
    private double mult = 1;
    private long until;
    private String by = "";
    private final BossBar bar = BossBar.bossBar(Txt.mm(""), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
    private long startedAt;

    public BoosterManager(PepitaCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "booster.yml");
        if (file.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
            mult = y.getDouble("moltiplicatore", 1);
            until = y.getLong("fine");
            by = y.getString("da", "");
            startedAt = y.getLong("inizio", System.currentTimeMillis());
        }
    }

    public double globalMult() {
        return until > System.currentTimeMillis() ? mult : 1;
    }

    public boolean active() {
        return until > System.currentTimeMillis();
    }

    public void start(double m, long seconds, String who) {
        long now = System.currentTimeMillis();
        if (active() && m <= mult) {
            until += seconds * 1000;
        } else {
            mult = m;
            until = Math.max(now, until) + seconds * 1000;
            startedAt = now;
        }
        by = who;
        save();
        Bukkit.broadcast(Txt.mm(Txt.PREFIX + "<gold><b>BOOSTER GLOBALE!</b></gold> <white>" + Txt.esc(who)
                + "</white> ha attivato soldi <gold>x" + Fmt.num(mult) + "</gold> per tutti! <gray>(" + Fmt.time(seconds) + ")"));
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.2f);
    }

    public void tick() {
        long now = System.currentTimeMillis();
        if (until > now) {
            long left = (until - now) / 1000;
            float prog = (float) Math.max(0, Math.min(1, (double) (until - now) / Math.max(1, until - startedAt)));
            bar.name(Txt.mm("<gold><b>Booster globale x" + Fmt.num(mult) + "</b></gold> <gray>di <white>" + Txt.esc(by)
                    + "</white> • <yellow>" + Fmt.time(left)));
            bar.progress(prog);
            for (Player p : Bukkit.getOnlinePlayers()) p.showBossBar(bar);
        } else if (mult != 1) {
            mult = 1;
            save();
            for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
            Bukkit.broadcast(Txt.mm(Txt.PREFIX + "Il booster globale è terminato."));
        }
    }

    public void hideAll() {
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("moltiplicatore", mult);
        y.set("fine", until);
        y.set("da", by);
        y.set("inizio", startedAt);
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("booster.yml: " + e.getMessage());
        }
    }
}

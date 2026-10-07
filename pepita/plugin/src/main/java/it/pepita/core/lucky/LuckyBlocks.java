package it.pepita.core.lucky;

import it.pepita.core.PepitaCore;
import it.pepita.core.cosmetics.PickaxeSkin;
import it.pepita.core.crate.Crate;
import it.pepita.core.data.PlayerData;
import it.pepita.core.economy.Economy;
import it.pepita.core.mine.Mine;
import it.pepita.core.pass.BattlePass;
import it.pepita.core.util.Keys;
import it.pepita.core.util.Txt;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Lucky Block (spugna con texture del pack): sparsi nelle miniere a ogni reset (~1 ogni 4000 blocchi).
 * Premi in "minuti di scavo" a tre rarità + rare trappole innocue. Fuochi d'artificio e titolo.
 */
public final class LuckyBlocks {
    public enum Rarity { COMUNE, RARO, LEGGENDARIO, TRAPPOLA }

    private final PepitaCore plugin;

    public LuckyBlocks(PepitaCore plugin) {
        this.plugin = plugin;
    }

    private Rarity roll() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double trap = plugin.getConfig().getDouble("lucky.probabilita.trappola", 0.08);
        double leg = plugin.getConfig().getDouble("lucky.probabilita.leggendario", 0.05);
        double rare = plugin.getConfig().getDouble("lucky.probabilita.raro", 0.25);
        double x = r.nextDouble();
        if (x < trap) return Rarity.TRAPPOLA;
        x -= trap;
        if (x < leg) return Rarity.LEGGENDARIO;
        x -= leg;
        if (x < rare) return Rarity.RARO;
        return Rarity.COMUNE;
    }

    public void open(Player p, PlayerData d, Mine m, Location at) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Rarity rar = roll();
        d.luckyTrovati++;
        d.dirty = true;
        Location c = at.clone().add(0.5, 0.5, 0.5);
        World w = c.getWorld();
        Economy eco = plugin.eco();
        List<String> got = new ArrayList<>();
        switch (rar) {
            case COMUNE -> {
                int k = r.nextInt(3);
                if (k == 0) {
                    double v = eco.minutes(d, 2 + r.nextInt(4));
                    eco.give(d, Economy.Cur.SOLDI, v, "lucky");
                    got.add(Txt.soldi(v));
                } else if (k == 1) {
                    double v = 100 + r.nextInt(400) * (1 + d.prestige * 0.1);
                    eco.give(d, Economy.Cur.PEPITE, v, "lucky");
                    got.add(Txt.pepite(v));
                } else {
                    d.addKeys("comune", 1);
                    got.add("<white>1 Chiave Comune");
                }
            }
            case RARO -> {
                double v = eco.minutes(d, 8 + r.nextInt(8));
                eco.give(d, Economy.Cur.SOLDI, v, "lucky");
                got.add(Txt.soldi(v));
                if (r.nextBoolean()) {
                    d.addKeys("rara", 1);
                    got.add("<#55E6FF>1 Chiave Rara");
                } else {
                    for (var left : p.getInventory().addItem(plugin.picks().bomb("grande", 2)).values()) w.dropItem(p.getLocation(), left);
                    got.add("<#FF9F43>2 Bombe Grandi");
                }
            }
            case LEGGENDARIO -> {
                double v = eco.minutes(d, 25 + r.nextInt(20));
                eco.give(d, Economy.Cur.SOLDI, v, "lucky");
                got.add(Txt.soldi(v));
                int g = 2 + r.nextInt(4);
                eco.give(d, Economy.Cur.GEMME, g, "lucky");
                got.add(Txt.gemme(g));
                if (r.nextInt(4) == 0) {
                    PickaxeSkin[] pool = {PickaxeSkin.SAKURA, PickaxeSkin.SMERALDO, PickaxeSkin.GLACIALE, PickaxeSkin.AMETISTA, PickaxeSkin.PIZZA};
                    PickaxeSkin s = pool[r.nextInt(pool.length)];
                    plugin.picks().giveSkin(p, d, s);
                    got.add(s.rar.color + "Skin " + s.display);
                } else {
                    d.addKeys("leggendaria", 1);
                    got.add("<#D17BFF>1 Chiave Leggendaria");
                }
                Bukkit.broadcast(Txt.mm(Txt.PREFIX + "<white>" + Txt.esc(p.getName()) + "</white> ha aperto un <gold><b>Lucky Block LEGGENDARIO</b></gold>!"));
            }
            case TRAPPOLA -> trap(p, c);
        }
        if (rar != Rarity.TRAPPOLA) {
            String col = switch (rar) {
                case RARO -> "<#55E6FF>";
                case LEGGENDARIO -> "<gradient:#FFF6B7:#FFA726>";
                default -> "<#FFD54A>";
            };
            String name = switch (rar) {
                case RARO -> "LUCKY BLOCK RARO";
                case LEGGENDARIO -> "LUCKY BLOCK LEGGENDARIO";
                default -> "LUCKY BLOCK";
            };
            p.showTitle(Title.title(Txt.mm(col + "<b>" + name + "</b>" + (rar == Rarity.LEGGENDARIO ? "</gradient>" : "")),
                    Txt.mm("<gray>" + String.join(" <dark_gray>• ", got)),
                    Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1800), Duration.ofMillis(400))));
            Txt.send(p, "Lucky Block: " + String.join("<gray>, ", got));
            firework(c, rar);
            p.playSound(c, rar == Rarity.LEGGENDARIO ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.4f);
        }
        w.spawnParticle(Particle.TOTEM_OF_UNDYING, c, 30, 0.4, 0.4, 0.4, 0.3);
        plugin.pass().progress(p, d, BattlePass.Mission.LUCKY, 1);
    }

    private void firework(Location c, Rarity rar) {
        Color main = switch (rar) {
            case RARO -> Color.fromRGB(0x55E6FF);
            case LEGGENDARIO -> Color.fromRGB(0xD17BFF);
            default -> Color.fromRGB(0xFFD54A);
        };
        Firework fw = c.getWorld().spawn(c.clone().add(0, 1.5, 0), Firework.class, f -> {
            FireworkMeta fm = f.getFireworkMeta();
            fm.addEffect(FireworkEffect.builder().with(rar == Rarity.LEGGENDARIO ? FireworkEffect.Type.STAR : FireworkEffect.Type.BALL_LARGE)
                    .withColor(main, Color.fromRGB(0xFFA726)).withFade(Color.WHITE).flicker(true).trail(true).build());
            fm.setPower(0);
            f.setFireworkMeta(fm);
            f.getPersistentDataContainer().set(Keys.LUCKY, PersistentDataType.STRING, "fuoco");
        });
        Bukkit.getScheduler().runTaskLater(plugin, fw::detonate, 2L);
    }

    /** Trappole innocue (nelle miniere il danno è disattivato). */
    private void trap(Player p, Location c) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        World w = c.getWorld();
        switch (r.nextInt(4)) {
            case 0 -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 40, 1, true, false, true));
                p.showTitle(Title.title(Txt.mm("<#FF7B7B><b>TRAPPOLA!</b>"), Txt.mm("<gray>Si vola... per poco!")));
            }
            case 1 -> {
                for (int i = 0; i < 6; i++) {
                    Chicken ch = w.spawn(c, Chicken.class, x -> {
                        x.setPersistent(false);
                        x.setRemoveWhenFarAway(true);
                        x.getPersistentDataContainer().set(Keys.LUCKY, PersistentDataType.STRING, "pollo");
                        x.setVelocity(new Vector(r.nextDouble(-0.4, 0.4), 0.5, r.nextDouble(-0.4, 0.4)));
                    });
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (ch.isValid()) {
                            ch.getWorld().spawnParticle(Particle.POOF, ch.getLocation(), 6, 0.2, 0.2, 0.2, 0.02);
                            ch.remove();
                        }
                    }, 100L + i * 5);
                }
                p.showTitle(Title.title(Txt.mm("<#FF7B7B><b>COCCODÈ!</b>"), Txt.mm("<gray>Il lucky block era pieno di polli.")));
            }
            case 2 -> {
                w.playSound(c, Sound.ENTITY_CREEPER_PRIMED, 1f, 1f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    w.spawnParticle(Particle.EXPLOSION, c, 3, 0.5, 0.5, 0.5, 0);
                    w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.6f);
                    if (p.isOnline()) p.setVelocity(p.getLocation().toVector().subtract(c.toVector()).normalize().multiply(0.8).setY(0.6));
                }, 30L);
                p.showTitle(Title.title(Txt.mm("<#FF7B7B><b>SSSSS...</b>"), Txt.mm("<gray>Scherzetto!")));
            }
            default -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 0, true, false, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, true, false, false));
                w.playSound(c, Sound.AMBIENT_CAVE, 1f, 1f);
                p.showTitle(Title.title(Txt.mm("<#FF7B7B><b>BUIO!</b>"), Txt.mm("<gray>Qualcuno ha spento la luce...")));
            }
        }
        Txt.send(p, "Lucky Block: era una <#FF7B7B>trappola</#FF7B7B>! (innocua, riprova col prossimo)");
    }
}

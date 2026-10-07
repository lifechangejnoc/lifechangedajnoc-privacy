package it.pepita.core.gui;

import it.pepita.core.PepitaCore;
import it.pepita.core.cosmetics.ArmorSkin;
import it.pepita.core.cosmetics.PickaxeSkin;
import it.pepita.core.crate.Crate;
import it.pepita.core.data.DataManager;
import it.pepita.core.data.PlayerData;
import it.pepita.core.economy.Economy;
import it.pepita.core.gang.GangManager;
import it.pepita.core.mine.Mine;
import it.pepita.core.pickaxe.Enchant;
import it.pepita.core.pickaxe.MiningService;
import it.pepita.core.pickaxe.PickTier;
import it.pepita.core.pickaxe.PickaxeManager;
import it.pepita.core.rank.RankManager;
import it.pepita.core.rank.VipTier;
import it.pepita.core.tutorial.Tutorial;
import it.pepita.core.util.Fmt;
import it.pepita.core.util.ItemBuilder;
import it.pepita.core.util.Txt;
import it.pepita.core.world.Layout;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/** Tutti i menu del server. */
public final class Menus {
    private final PepitaCore plugin;

    public Menus(PepitaCore plugin) {
        this.plugin = plugin;
    }

    private static String click(String what) {
        return "<#FFD54A>▶ " + what;
    }

    // =========================== MENU PRINCIPALE ===========================

    public void main(Player p) {
        PlayerData d = plugin.data().get(p);
        RankManager rm = plugin.ranks();
        plugin.tutorial().onEvent(p, Tutorial.Ev.MENU, 1);
        Menu m = new Menu(6, "Menu di Pepita").emblem(Gui.Emblem.MAIN);

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(meta -> {
            if (meta instanceof SkullMeta sm) sm.setOwningPlayer(p);
            meta.itemName(Txt.item("<gold><b>" + Txt.esc(p.getName())));
            meta.lore(Txt.lore(List.of(
                    "<gray>Rank: " + RankManager.tag(d),
                    "<gray>Grado: " + (d.vip > 0 ? VipTier.of(d.vip).tag : "<white>Detenuto"),
                    "",
                    Txt.S_SOLDI + " <gray>Soldi: <white>" + Fmt.num(d.soldi),
                    Txt.S_PEPITE + " <gray>Pepite: <white>" + Fmt.num(d.pepite),
                    Txt.S_GEMME + " <gray>Gemme: <white>" + Fmt.num(d.gemme),
                    Txt.S_QUANTUM + " <gray>Quantum: <white>" + Fmt.num(d.quantum),
                    "",
                    "<gray>Blocchi scavati: <white>" + Fmt.num(d.blocchi),
                    "<gray>Piccone: " + plugin.picks().tier(d).label() + " <gray>livello <gold>" + d.pickLevel,
                    "<gray>Moltiplicatore soldi: <gold>" + Fmt.mult(rm.soldiMult(d)),
                    "<gray>Un minuto di scavo vale: " + Txt.soldi(plugin.eco().minute(d)),
                    "<gray>Pepite d'Oro trovate: <gold>" + d.pepiteTrovate + " <dark_gray>• <gray>Lucky Block: <gold>" + d.luckyTrovati,
                    "<gray>Tempo di gioco: <white>" + Fmt.time(d.playtime))));
        });
        m.set(4, head);

        m.set(10, Gui.button(p, "gui_miniere", Material.DIAMOND_PICKAXE).name("<#55E6FF><b>Miniere</b>")
                .lore("<gray>Scegli dove scavare.", "", click("Click per aprire")).build(), e -> mines(p));
        m.set(11, Gui.button(p, "gui_incantesimi", Material.ENCHANTED_BOOK).name("<#FFD54A><b>Incantesimi</b>")
                .lore("<gray>Potenzia il tuo piccone", "<gray>con le " + Txt.S_PEPITE + " <gray>Pepite e i " + Txt.S_QUANTUM + " <gray>Quantum.", "", click("Click per aprire")).build(), e -> enchants(p));
        String next = d.rank >= RankManager.MAX_RANK ? "<gray>Sei al rank massimo!" :
                "<gray>Prossimo: <white>" + RankManager.letter(d.rank + 1) + " <gray>per " + Txt.soldi(rm.nextCost(d));
        m.set(12, Gui.button(p, "gui_rankup", Material.EXPERIENCE_BOTTLE).name("<#6BE36B><b>Rankup</b>")
                .lore("<gray>Rank attuale: " + RankManager.tag(d), next, "<gray>Progresso: " + Txt.bar(rm.progress(d), 12, "#6BE36B", "#4A4A4A"), "",
                        click("Sinistro: sali di 1"), click("Destro: sali al massimo")).build(), e -> {
            if (e.isRightClick()) rm.rankupMax(p);
            else rm.rankup(p, false);
            main(p);
        });
        boolean canEv = d.prestige >= rm.evasionePrestige();
        m.set(13, Gui.button(p, "gui_prestigio", canEv ? Material.IRON_BARS : Material.NETHER_STAR)
                .name(canEv ? "<#FF5555><b>Evasione</b>" : "<#D17BFF><b>Prestigio</b>")
                .lore(canEv ? List.of("<gray>Evadi dalla prigione: azzeri rank e", "<gray>prestigio ma ottieni bonus permanenti.", "",
                                "<gray>Evasioni: <white>" + d.evasioni, "", click("Click per evadere"))
                        : List.of("<gray>Dal rank <gold>Z</gold> ricomincia dalla A", "<gray>con <white>+10% soldi</white> per sempre.", "",
                        "<gray>Costo: " + Txt.soldi(rm.prestigeCost(d)), "<gray>Prestigio attuale: <white>" + d.prestige,
                        "<gray>Evasione al prestigio <white>" + rm.evasionePrestige(), "", click("Click per il prestigio")))
                .glow(canEv).build(), e -> confirm(p, canEv ? "Confermi l'evasione?" : "Confermi il prestigio?", () -> {
            if (canEv) rm.evasione(p);
            else rm.prestige(p);
            p.closeInventory();
        }, () -> main(p), List.of()));
        m.set(14, Gui.button(p, "gui_incantesimi", plugin.picks().tier(d).material).name("<gradient:#FFE259:#FFA751><b>Piccone</b></gradient>")
                .lore("<gray>Tier: " + plugin.picks().tier(d).label(), "<gray>Potenzia il tier e monta le skin.", "", click("Click per aprire")).build(), e -> pickaxe(p));
        m.set(15, Gui.button(p, "gui_armatura", Material.LEATHER_CHESTPLATE).name("<gradient:#3FE0F0:#D15BFF><b>Corazza Quantica</b></gradient>")
                .lore("<gray>Potenzia i 4 pezzi con i Quantum:", "<gray>bonus soldi, esperienza e pepite.", "", click("Click per aprire")).build(), e -> plugin.armor().open(p));
        m.set(16, Gui.button(p, "gui_casse", Material.CHEST).name("<#FFA726><b>Casse</b>")
                .lore("<gray>Chiavi: <white>" + d.keys("comune") + " <gray>/ <aqua>" + d.keys("rara") + " <gray>/ <light_purple>"
                        + d.keys("leggendaria") + " <gray>/ <gold>" + d.keys("pepita"), "", click("Click per aprire")).build(), e -> plugin.crates().openHub(p));

        m.set(19, Gui.button(p, "gui_battlepass", Material.NETHER_STAR).name("<gradient:#FFE259:#FFA751><b>Battle Pass</b></gradient>")
                .lore("<gray>Livello <gold>" + plugin.pass().level(d) + "</gold>", "<gray>Missioni giornaliere e settimanali.", "", click("Click per aprire")).build(),
                e -> plugin.pass().open(p, 0));
        int tg = plugin.milestones().claimable(d);
        m.set(20, Gui.button(p, "gui_traguardo", Material.GOLD_BLOCK).name("<gold><b>Traguardi</b>")
                .lore("<gray>Premi per i blocchi rotti.", tg > 0 ? "<green>" + tg + " da ritirare!" : "<gray>Nessuno da ritirare.", "", click("Click per aprire"))
                .glow(tg > 0).build(), e -> plugin.milestones().open(p));
        m.set(21, Gui.button(p, "gui_cella", Material.IRON_DOOR).name("<gold><b>Celle</b>")
                .lore("<gray>Compra e arreda la tua cella", "<gray>nel Colosseo.", "", click("Click per aprire")).build(), e -> plugin.cells().openMain(p));
        m.set(22, Gui.button(p, "gui_negozio", Material.AMETHYST_SHARD).name("<#D17BFF><b>Negozio Gemme</b>")
                .lore("<gray>VIP, chiavi, booster e skin.", "<gray>Hai " + Txt.gemme(d.gemme), "", click("Click per aprire")).build(), e -> shop(p));
        m.set(23, Gui.button(p, "gui_skin", Material.LEATHER_CHESTPLATE).name("<#FF7BD1><b>Skin</b>")
                .lore("<gray>Skin del piccone (oggetti scambiabili)", "<gray>e aspetto della corazza.", "", click("Click per aprire")).build(), e -> skins(p));
        long left = plugin.getConfig().getLong("giornaliero.ore", 24) * 3600_000L - (System.currentTimeMillis() - d.lastDaily);
        m.set(24, Gui.button(p, "gui_giornaliero", Material.BARREL).name("<#6BE36B><b>Ricompensa giornaliera</b>")
                .lore(left <= 0 ? List.of("<green>Pronta da ritirare!", "", click("Click per ritirare"))
                        : List.of("<gray>Torna tra <white>" + Fmt.time(left / 1000))).glow(left <= 0).build(), e -> {
            daily(p);
            main(p);
        });
        GangManager.Gang g = plugin.gangs().get(d.gang);
        m.set(25, Gui.button(p, "gui_gang", Material.RED_BANNER).name("<#FF7B7B><b>Gang</b>")
                .lore(g == null ? List.of("<gray>Non sei in una gang.", "<gray>Fondane una con <yellow>/gang crea <nome>")
                        : List.of("<gray>Gang: <#FF7B7B>" + g.name, "<gray>Membri: <white>" + g.members.size(), "<gray>Blocchi: <white>" + Fmt.num(g.blocchi)))
                .build(), e -> {
            p.closeInventory();
            plugin.gangs().handle(p, new String[]{"info"});
        });

        m.set(28, Gui.button(p, "gui_classifica", Material.GOLD_INGOT).name("<gold><b>Classifiche</b>")
                .lore("<gray>I migliori detenuti di Pepita.", "", click("Click per aprire")).build(), e -> top(p));
        m.set(29, Gui.button(p, "gui_selettore", Material.COMPASS).name("<gradient:#FFE259:#FFA751><b>Viaggi</b></gradient>")
                .lore("<gray>Hub, Prigione, Miniere, PvP, Celle.", "", click("Click per aprire")).build(), e -> selector(p));
        String gr = plugin.goldRush().active() ? "<gold>ATTIVA! <white>" + Fmt.time(plugin.goldRush().secondsLeft()) + " rimasti"
                : "<gray>Prossima tra <white>" + Fmt.time(plugin.goldRush().secondsToNext());
        m.set(30, Gui.button(p, "gui_info", Material.CLOCK).name("<gradient:#FFF6B7:#FFA726><b>Corsa all'Oro</b></gradient>")
                .lore(gr, "", "<gray>Durante la corsa soldi e pepite", "<gray>valgono <gold>x2</gold> e nelle miniere",
                        "<gray>compaiono le <gold>Pepite Giganti</gold>.").glow(plugin.goldRush().active()).build());
        m.set(31, Gui.button(p, "gui_quantum", Material.AMETHYST_CLUSTER).name("<gradient:#3FE0F0:#D15BFF><b>Quantum</b></gradient>")
                .lore("<gray>Hai " + Txt.quantum(d.quantum) + (d.quantumTasca > 0 ? " <gray>(+" + Txt.quantum(d.quantumTasca) + " <gray>in tasca)" : ""),
                        "<gray>Resa attuale: <white>" + Fmt.pct(plugin.quantum().resa(d)) + " <dark_gray>(cala se ne raccogli tanti in un'ora)",
                        "", "<gray>Si trovano solo nella <red>Miniera PvP</red>:", "<gray>Minerale Quantico e Nucleo Quantico.",
                        "<gray>Riportali nella zona sicura!", "", click("Click per andare nella Miniera PvP")).build(), e -> {
            p.closeInventory();
            travel(p, "pvp");
        });
        m.set(32, Gui.button(p, "gui_keyall", Material.TRIPWIRE_HOOK).name("<gradient:#D17BFF:#FFD54A><b>Keyall</b></gradient>")
                .lore("<gray>Ogni 35-45 minuti tutti gli online", "<gray>ricevono chiavi delle casse.", "",
                        "<gray>Prossimo tra circa <white>" + Fmt.time(plugin.keyAll().secondsToNext())).build());
        m.set(33, Gui.button(p, "gui_tutorial", Material.WRITABLE_BOOK).name("<gold><b>Tutorial</b>")
                .lore(d.tutorialDone ? "<green>Completato ✔" : plugin.tutorial().active(d) ? "<yellow>In corso: passo " + (d.tutorialStep + 1) : "<gray>Non iniziato",
                        "", click("Click per parlare con Beppe")).build(), e -> {
            p.closeInventory();
            travel(p, "tutorial");
        });
        m.set(34, Gui.button(p, "gui_impostazioni", Material.COMPARATOR).name("<gray><b>Impostazioni</b>")
                .lore("<gray>Messaggi incantesimi, volo.", "", click("Click per aprire")).build(), e -> settings(p));
        m.set(49, Gui.close(p), e -> p.closeInventory());
        m.frame();
        m.open(p);
    }

    // =========================== SELETTORE ===========================

    public void selector(Player p) {
        PlayerData d = plugin.data().get(p);
        Menu m = new Menu(3, "Dove vuoi andare?").emblem(Gui.Emblem.SELETTORE);
        Mine best = plugin.mines().best(d);
        Mine pvp = plugin.mines().pvpMine();
        m.set(10, Gui.button(p, "gui_hub", Material.COMPASS).name("<gradient:#FFE259:#FFA751><b>Lobby</b></gradient>")
                .lore("<gray>Esci dal Prison e torna", "<gray>alla scelta delle modalità.", "", click("Click per andare")).build(), e -> travel(p, "hub"));
        m.set(11, Gui.button(p, "gui_prigione", Material.IRON_BARS).name("<#C9C9C9><b>Prigione</b>")
                .lore("<gray>Casse, incantesimi, negozio,", "<gray>corazza, battle pass, classifiche.", "", click("Click per andare")).build(), e -> travel(p, "prigione"));
        m.set(12, Gui.button(p, "gui_miniere", Material.DIAMOND_PICKAXE).name("<#55E6FF><b>Miniere</b>")
                .lore("<gray>La tua miniera migliore:", "<white>" + (best == null ? "-" : best.name), "", click("Sinistro: vai"), click("Destro: tutte le miniere")).build(), e -> {
            if (e.isRightClick()) mines(p);
            else travel(p, "miniere");
        });
        m.set(13, Gui.button(p, "gui_pvp", Material.NETHERITE_SWORD).name("<#FF5555><b>Miniera PvP</b>")
                .lore("<gray>L'unico posto con i " + Txt.S_QUANTUM + " <" + Txt.QUANTUM + ">Quantum</" + Txt.QUANTUM + ">.",
                        "<gray>Requisito: <white>" + (pvp == null ? "-" : pvp.requirement()), "<red>⚔ PvP attivo fuori dalla zona sicura", "", click("Click per andare")).build(),
                e -> travel(p, "pvp"));
        m.set(14, Gui.button(p, "gui_cella", Material.IRON_DOOR).name("<gold><b>Colosseo delle Celle</b>")
                .lore("<gray>Compra e arreda la tua cella.", "", click("Sinistro: vai"), click("Destro: menu celle")).build(), e -> {
            if (e.isRightClick()) plugin.cells().openMain(p);
            else travel(p, "celle");
        });
        m.set(15, Gui.button(p, "gui_tutorial", Material.WRITABLE_BOOK).name("<gold><b>Tutorial</b>")
                .lore("<gray>Parla con Beppe il Secondino.", "", click("Click per andare")).build(), e -> travel(p, "tutorial"));
        m.set(16, Gui.button(p, "gui_info", Material.MAP).name("<white><b>Le tue celle</b>")
                .lore(plugin.cells().owned(p.getUniqueId()).isEmpty() ? "<gray>Non hai ancora celle." : "<gray>Vai alla tua cella.", "", click("Click per andare")).build(), e -> {
            p.closeInventory();
            plugin.cells().command(p, new String[0]);
        });
        m.set(22, Gui.close(p), e -> p.closeInventory());
        m.frame();
        m.open(p);
    }

    /** Viaggio verso una destinazione (portali dell'hub, selettore, comandi). */
    public void travel(Player p, String dest) {
        PlayerData d = plugin.data().get(p);
        switch (dest) {
            case "hub" -> {
                p.teleport(plugin.world().hubSpawn());
                p.sendActionBar(Txt.mm("<gradient:#FFE259:#FFA751>Lobby di Pepita"));
            }
            case "prigione" -> {
                p.teleport(plugin.world().prisonSpawn());
                p.sendActionBar(Txt.mm("<#C9C9C9>La Prigione"));
            }
            case "miniere" -> {
                Mine best = plugin.mines().best(d);
                if (best != null) plugin.mines().teleport(p, best);
            }
            case "pvp" -> {
                Mine pm = plugin.mines().pvpMine();
                if (pm != null && !pm.canAccess(d) && !p.hasPermission("pepita.admin")) {
                    Txt.send(p, "La Miniera PvP richiede: <white>" + pm.requirement() + "</white>. Puoi comunque visitare la zona sicura.");
                }
                p.teleport(plugin.world().pvpSpawn());
                p.showTitle(net.kyori.adventure.title.Title.title(Txt.mm("<#FF5555><b>⚔ MINIERA PVP ⚔</b>"),
                        Txt.mm("<gray>Scava i <" + Txt.QUANTUM + ">Quantum</" + Txt.QUANTUM + "> e riportali qui per salvarli")));
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.4f, 1.6f);
            }
            case "celle" -> {
                p.teleport(plugin.world().cellsSpawn());
                p.sendActionBar(Txt.mm("<gold>Colosseo delle Celle <gray>• prendi un ascensore agli angoli"));
            }
            case "tutorial" -> {
                // nel cortile, girati verso Beppe
                double[] s = Layout.PRISON_SPAWN, n = Layout.PRISON_NPC;
                float yaw = (float) Math.toDegrees(Math.atan2(-(n[0] - s[0]), n[2] - s[2]));
                p.teleport(new Location(plugin.world().prison(), s[0], s[1], s[2], yaw, 0));
                if (!plugin.tutorial().active(d) && !d.tutorialDone) plugin.tutorial().start(p);
            }
            default -> {
            }
        }
    }

    // =========================== MINIERE ===========================

    public void mines(Player p) {
        PlayerData d = plugin.data().get(p);
        Menu m = new Menu(6, "Miniere di Pepita").emblem(Gui.Emblem.MINIERE);
        Mine best = plugin.mines().best(d);
        int[] special = {37, 39, 41, 43};
        int si = 0, i = 0;
        for (Mine mine : plugin.mines().all()) {
            int slot;
            if (mine.id.length() == 1 && i < 26) slot = i < 8 ? 1 + i : i < 16 ? 10 + (i - 8) : i < 24 ? 19 + (i - 16) : 28 + (i - 24);
            else if (si < special.length) slot = special[si++];
            else continue;
            if (mine.id.length() == 1) i++;
            boolean ok = mine.canAccess(d);
            double pct = mine.total == 0 ? 0 : (double) mine.remaining / mine.total;
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Requisito: <white>" + mine.requirement());
            if (!mine.pvp) lore.add("<gray>Valore medio: " + Txt.soldi(mine.avgValue()) + "<gray>/blocco");
            lore.add("<gray>Blocchi rimasti: <white>" + Fmt.pct(pct));
            lore.add("<gray>Reset tra: <white>" + Fmt.time(plugin.mines().secondsToReset(mine)));
            if (mine.pvp) lore.add("<red>⚔ PvP attivo: qui ci sono i Quantum!");
            lore.add("");
            lore.add(ok ? click("Click per teletrasportarti") : "<red>🔒 Bloccata");
            ItemBuilder b = new ItemBuilder(ok ? mine.icon : Material.GRAY_STAINED_GLASS)
                    .name((ok ? "<#55E6FF><b>" : "<dark_gray><b>") + mine.name + (mine == best ? " <gold>★" : ""))
                    .lore(lore).glow(mine == best);
            m.set(slot, b.build(), e -> {
                p.closeInventory();
                if (mine.pvp) travel(p, "pvp");
                else plugin.mines().teleport(p, mine);
            });
        }
        m.set(49, Gui.back(p), e -> main(p));
        m.fill();
        m.open(p);
    }

    // =========================== PICCONE (tier e skin) ===========================

    public double tierMoney(PlayerData d, PickTier t) {
        List<Double> l = plugin.getConfig().getDoubleList("tier.costi-minuti");
        int i = t.ordinal();
        double mins = i < l.size() ? l.get(i) : 10 * Math.pow(2, i);
        return plugin.eco().minutes(d, mins);
    }

    public double tierPepite(PickTier t) {
        List<Double> l = plugin.getConfig().getDoubleList("tier.costi-pepite");
        int i = t.ordinal();
        return i < l.size() ? l.get(i) : 500 * Math.pow(5, i);
    }

    public void pickaxe(Player p) {
        PlayerData d = plugin.data().get(p);
        PickTier tier = plugin.picks().tier(d);
        PickTier nx = tier.next();
        Menu m = new Menu(5, "Il tuo piccone").emblem(Gui.Emblem.INCANTESIMI);
        m.set(13, plugin.picks().pickaxe(p, d));
        if (nx != null) {
            double money = tierMoney(d, tier), pep = tierPepite(tier);
            m.set(20, new ItemBuilder(nx.material).model(nx.model).name("<gradient:#FFE259:#FFA751><b>Potenzia il tier</b></gradient>")
                    .lore("<gray>Da " + tier.label() + " <gray>a " + nx.label(),
                            "<gray>Moltiplicatore skin: <white>x" + Fmt.num(tier.mult) + " <dark_gray>→ <white>x" + Fmt.num(nx.mult),
                            "<gray>Il materiale migliore scava più veloce.", "",
                            "<gray>Costo: " + Txt.soldi(money) + " <gray>+ " + Txt.pepite(pep), "", click("Click per potenziare")).glow().build(), e -> {
                upgradeTier(p, d);
                pickaxe(p);
            });
        } else {
            m.set(20, new ItemBuilder(tier.material).model(tier.model).name("<gradient:#3FE0F0:#D15BFF><b>Tier massimo!</b></gradient>")
                    .lore("<gray>Il tuo piccone è " + tier.label()).glow().build());
        }
        PickaxeSkin s = PickaxeSkin.byId(d.pickSkin);
        if (s != PickaxeSkin.CLASSICO) {
            ItemStack it = plugin.picks().skinItem(s, 1);
            it.editMeta(meta -> meta.lore(Txt.lore(List.of("<gray>Skin montata.", "", "<#FFD54A>▶ Click per smontarla"))));
            m.set(22, it, e -> {
                plugin.picks().unmount(p, d);
                pickaxe(p);
            });
        } else {
            m.set(22, Gui.button(p, "gui_skin", Material.PAPER).name("<gray><b>Nessuna skin montata</b>")
                    .lore("<gray>Trascina un oggetto-skin sul piccone", "<gray>nell'inventario per montarlo.").build());
        }
        m.set(24, Gui.button(p, "gui_skin", Material.LEATHER_CHESTPLATE).name("<#FF7BD1><b>Skin</b>").lore("", click("Click per aprire")).build(), e -> skins(p));
        PickTier[] all = PickTier.values();
        for (int i = 0; i < all.length; i++) {
            PickTier t = all[i];
            boolean have = t.ordinal() <= tier.ordinal();
            m.set(27 + i + (i >= 4 ? 1 : 0), new ItemBuilder(have ? t.material : Material.GRAY_DYE).model(have ? t.model : null)
                    .name((have ? "" : "<dark_gray>") + t.label()).lore("<gray>Moltiplicatore skin: <white>x" + Fmt.num(t.mult),
                            t == tier ? "<green>✔ Attuale" : have ? "<gray>Superato" : "<red>🔒").glow(t == tier).build());
        }
        m.set(40, Gui.back(p), e -> main(p));
        m.frame();
        m.open(p);
    }

    public void upgradeTier(Player p, PlayerData d) {
        PickTier tier = plugin.picks().tier(d);
        PickTier nx = tier.next();
        if (nx == null) return;
        double money = tierMoney(d, tier), pep = tierPepite(tier);
        Economy eco = plugin.eco();
        if (!eco.has(d, Economy.Cur.SOLDI, money) || !eco.has(d, Economy.Cur.PEPITE, pep)) {
            Txt.send(p, "Ti servono " + Txt.soldi(money) + " <gray>e " + Txt.pepite(pep) + "<gray>.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            return;
        }
        eco.take(d, Economy.Cur.SOLDI, money, "tier");
        eco.take(d, Economy.Cur.PEPITE, pep, "tier");
        d.pickTier = nx.ordinal();
        d.dirty = true;
        plugin.picks().refresh(p);
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.4f);
        Txt.send(p, "Il tuo piccone è ora " + nx.label() + "<gray>! Moltiplicatore delle skin <white>x" + Fmt.num(nx.mult));
        if (nx.ordinal() >= PickTier.PEPITA.ordinal())
            Txt.broadcastPrison(Txt.mm(Txt.PREFIX + "<white>" + Txt.esc(p.getName()) + "</white> ha forgiato un piccone " + nx.label() + "<gray>!"));
    }

    // =========================== INCANTESIMI ===========================

    public void enchants(Player p) {
        PlayerData d = plugin.data().get(p);
        Menu m = new Menu(6, "Incantesimi del piccone").emblem(Gui.Emblem.INCANTESIMI);
        m.set(4, new ItemBuilder(Material.GOLD_NUGGET).model("pepita_oro").name(Txt.S_PEPITE + " <" + Txt.PEPITE + "><b>" + Fmt.num(d.pepite) + " Pepite</b>")
                .lore("<gray>Le pepite si trovano scavando.", "<gray>Aumentale con <white>Cercapepite</white>.").build());
        int[] slots = new int[28];
        int k = 0;
        for (int row = 1; row <= 4; row++) for (int col = 1; col <= 7; col++) slots[k++] = row * 9 + col;
        int i = 0;
        for (Enchant en : Enchant.values()) {
            if (en.quantum() || i >= slots.length) continue;
            m.set(slots[i++], enchantItem(p, d, en), e -> enchantClick(p, d, en, e.getClick(), () -> enchants(p)));
        }
        m.set(45, Gui.back(p), e -> main(p));
        m.set(47, new ItemBuilder(d.procMessages ? Material.LIME_DYE : Material.GRAY_DYE)
                .name("<gray>Messaggi incantesimi: " + (d.procMessages ? "<green>ON" : "<red>OFF")).build(), e -> {
            d.procMessages = !d.procMessages;
            enchants(p);
        });
        m.set(49, Gui.button(p, "gui_info", Material.BOOK).name("<#FFD54A><b>Come funziona</b>")
                .lore("<gray>Ogni blocco ha una probabilità", "<gray>di attivare gli incantesimi.",
                        "<gray>I livelli si comprano con le " + Txt.S_PEPITE + " <gray>Pepite.",
                        "<gray>Alcuni si sbloccano col prestigio.", "<gray>Le probabilità sono nella <white>TAB</white>.").build());
        m.set(51, Gui.button(p, "gui_quantum", Material.AMETHYST_CLUSTER).name("<gradient:#3FE0F0:#D15BFF><b>Incantesimi Quantum</b></gradient>")
                .lore("<gray>Si pagano in " + Txt.S_QUANTUM + " <gray>Quantum.", "", click("Click per aprire")).glow().build(), e -> enchantsQuantum(p));
        m.set(53, Gui.close(p), e -> p.closeInventory());
        m.fill();
        m.open(p);
    }

    public void enchantsQuantum(Player p) {
        PlayerData d = plugin.data().get(p);
        Menu m = new Menu(4, "Incantesimi Quantum").emblem(Gui.Emblem.QUANTUM);
        m.set(4, Gui.button(p, "quantum", Material.AMETHYST_CLUSTER).name(Txt.S_QUANTUM + " <" + Txt.QUANTUM + "><b>" + Fmt.num(d.quantum) + " Quantum</b>")
                .lore("<gray>Si trovano solo nella <red>Miniera PvP</red>.").build());
        int[] slots = {19, 20, 21, 23, 24, 25};
        int i = 0;
        for (Enchant en : Enchant.values()) {
            if (!en.quantum() || i >= slots.length) continue;
            m.set(slots[i++], enchantItem(p, d, en), e -> enchantClick(p, d, en, e.getClick(), () -> enchantsQuantum(p)));
        }
        m.set(31, Gui.back(p), e -> enchants(p));
        m.frame();
        m.open(p);
    }

    private ItemStack enchantItem(Player p, PlayerData d, Enchant en) {
        int lvl = d.ench(en.id);
        boolean locked = d.prestige < en.minPrestige && d.evasioni == 0;
        boolean maxed = lvl >= en.max;
        boolean off = d.disabledEnchants.contains(en.id);
        List<String> lore = new ArrayList<>();
        lore.add(en.cat.label);
        lore.add("<gray>" + en.desc);
        lore.add("");
        lore.add("<gray>Livello: <white>" + Fmt.num(lvl) + "<dark_gray>/" + Fmt.num(en.max));
        double ch = MiningService.chance(d, en);
        if (lvl > 0 && ch > 0) lore.add("<gray>Probabilità per blocco: <white>" + (ch >= 0.01 ? Fmt.pct(ch) : String.format(java.util.Locale.US, "%.3f%%", ch * 100)));
        if (en == Enchant.FRANTUMAZIONE && lvl > 0) lore.add("<gray>Velocità nei blocchi quantici: <white>+" + Fmt.pct(lvl * 0.04));
        if (locked) {
            lore.add("");
            lore.add("<red>🔒 Richiede Prestigio " + en.minPrestige);
        } else if (maxed) {
            lore.add("<gold>✔ Livello massimo!");
        } else {
            lore.add("<gray>Costo +1: " + price(en, en.cost(lvl)));
            lore.add("<gray>Costo +10: " + price(en, costRange(en, lvl, Math.min(10, en.max - lvl))));
            lore.add("");
            lore.add(click("Sinistro: +1  <dark_gray>|  <#FFD54A>Destro: +10"));
            lore.add(click("Shift + sinistro: compra il massimo"));
        }
        if (lvl > 0) lore.add(click("Shift + destro: " + (off ? "<green>riattiva" : "<red>spegni")));
        return new ItemBuilder(locked ? Material.GRAY_DYE : en.icon)
                .name((off ? "<dark_gray><st>" : maxed ? "<gold><b>" : "<white><b>") + en.display)
                .lore(lore).glow(lvl > 0 && !off).build();
    }

    private static String price(Enchant en, double v) {
        return en.quantum() ? Txt.quantum(v) : Txt.pepite(v);
    }

    private void enchantClick(Player p, PlayerData d, Enchant en, ClickType click, Runnable reopen) {
        boolean locked = d.prestige < en.minPrestige && d.evasioni == 0;
        if (locked) {
            Txt.send(p, "Questo incantesimo si sblocca al <light_purple>Prestigio " + en.minPrestige + "</light_purple>.");
            return;
        }
        if (click == ClickType.SHIFT_RIGHT) {
            if (d.ench(en.id) > 0) {
                if (!d.disabledEnchants.remove(en.id)) d.disabledEnchants.add(en.id);
                d.dirty = true;
                plugin.picks().refresh(p);
            }
        } else {
            int want = click == ClickType.SHIFT_LEFT ? Integer.MAX_VALUE : click.isRightClick() ? 10 : 1;
            buy(p, d, en, want);
        }
        reopen.run();
    }

    private static double costRange(Enchant en, int from, int n) {
        double s = 0;
        for (int i = 0; i < n; i++) s += en.cost(from + i);
        return s;
    }

    private void buy(Player p, PlayerData d, Enchant en, int want) {
        int lvl = d.ench(en.id);
        if (lvl >= en.max) {
            Txt.send(p, en.display + " è già al massimo.");
            return;
        }
        Economy.Cur cur = en.quantum() ? Economy.Cur.QUANTUM : Economy.Cur.PEPITE;
        double have = plugin.eco().balance(d, cur);
        int bought = 0;
        double spent = 0;
        while (bought < want && lvl + bought < en.max) {
            double c = en.cost(lvl + bought);
            if (have - spent < c) break;
            spent += c;
            bought++;
        }
        if (bought == 0) {
            Txt.send(p, "Ti servono " + price(en, en.cost(lvl)) + " <gray>(hai " + price(en, have) + "<gray>).");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            return;
        }
        plugin.eco().take(d, cur, spent, "incantesimi");
        d.enchants.put(en.id, lvl + bought);
        d.dirty = true;
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
        Txt.send(p, "<white>" + en.display + "</white> → livello <gold>" + Fmt.num(lvl + bought) + "</gold> <gray>(-" + price(en, spent) + "<gray>)");
        plugin.picks().refresh(p);
        plugin.tutorial().onEvent(p, Tutorial.Ev.ENCHANT, 1);
    }

    // =========================== NEGOZIO GEMME ===========================

    public void shop(Player p) {
        PlayerData d = plugin.data().get(p);
        Menu m = new Menu(5, "Negozio Gemme ◆").emblem(Gui.Emblem.NEGOZIO);
        String link = plugin.getConfig().getString("negozio.link", "negozio.pepitamc.it");
        m.set(4, new ItemBuilder(Material.AMETHYST_SHARD).model("gemma").name(Txt.S_GEMME + " <" + Txt.GEMME + "><b>" + Fmt.num(d.gemme) + " Gemme</b>")
                .lore("<gray>Acquista gemme su:", "<white>" + link, "", "<gray>Ne trovi anche giocando:",
                        "<gray>giornaliero, prestigio, casse, traguardi.").build());
        m.set(19, new ItemBuilder(Material.GOLDEN_HELMET).name("<gradient:#FFE259:#FFA751><b>Gradi VIP</b></gradient>")
                .lore("<gray>VIP, VIP+, ELITE, LEGGENDA", "<gray>Bonus permanenti e prefisso.", "", click("Click per aprire")).build(), e -> shopVip(p));
        m.set(20, new ItemBuilder(Material.TRIPWIRE_HOOK).model("chiave_leggendaria").name("<#D17BFF><b>Chiavi</b>")
                .lore("<gray>Pacchetti di chiavi per le casse.", "", click("Click per aprire")).build(), e -> shopKeys(p));
        m.set(21, new ItemBuilder(Material.EXPERIENCE_BOTTLE).name("<#6BE36B><b>Salta Rank</b>")
                .lore("<gray>Sali subito di rank.", "", click("Click per aprire")).build(), e -> shopRanks(p));
        m.set(23, new ItemBuilder(Material.BLAZE_POWDER).name("<gold><b>Booster</b>")
                .lore("<gray>Raddoppia i soldi.", "", click("Click per aprire")).build(), e -> shopBoosters(p));
        m.set(24, Gui.button(p, "gui_battlepass", Material.NETHER_STAR).name("<gradient:#FFE259:#FFA751><b>Pass Premium</b></gradient>")
                .lore("<gray>La traccia premium del battle pass.", "", click("Click per aprire")).build(), e -> plugin.pass().open(p, 0));
        m.set(25, Gui.button(p, "gui_skin", Material.LEATHER_CHESTPLATE).dye(ArmorSkin.NEON.color).name("<#FF7BD1><b>Skin</b>")
                .lore("<gray>Picconi folli e corazze.", "", click("Click per aprire")).build(), e -> skins(p));
        int sp = plugin.getConfig().getInt("negozio.starter-pack", 900);
        m.set(31, new ItemBuilder(Material.CHEST).name("<gradient:#FFE259:#FFA751><b>Starter Pack</b></gradient>")
                .lore(d.starterBought ? List.of("<gray>Già acquistato.") : List.of("<gray>Grado <#55FF7F>VIP</#55FF7F>, 5 chiavi Rare", "<gray>e la skin <white>Pizza Margherita</white>.",
                        "<gray>Solo una volta!", "", "<gray>Prezzo: " + Txt.gemme(sp), "", click("Click per acquistare")))
                .glow(!d.starterBought).build(), e -> {
            if (d.starterBought) return;
            purchase(p, "Starter Pack", sp, () -> {
                d.starterBought = true;
                if (d.vip < 1) d.vip = 1;
                d.addKeys("rara", 5);
                plugin.picks().giveSkin(p, d, PickaxeSkin.PIZZA);
            }, () -> shop(p));
        });
        m.set(40, Gui.back(p), e -> main(p));
        m.frame();
        m.open(p);
    }

    private void shopVip(Player p) {
        PlayerData d = plugin.data().get(p);
        Menu m = new Menu(3, "Gradi VIP").emblem(Gui.Emblem.NEGOZIO);
        int[] slots = {10, 12, 14, 16};
        Material[] icons = {Material.IRON_HELMET, Material.GOLDEN_HELMET, Material.DIAMOND_HELMET, Material.NETHERITE_HELMET};
        VipTier[] tiers = {VipTier.VIP, VipTier.VIP_PLUS, VipTier.ELITE, VipTier.LEGGENDA};
        VipTier cur = VipTier.of(d.vip);
        for (int i = 0; i < tiers.length; i++) {
            VipTier t = tiers[i];
            boolean owned = d.vip >= t.level;
            int price = Math.max(0, t.price - cur.price);
            m.set(slots[i], new ItemBuilder(icons[i]).name(t.tag)
                    .lore("<gray>" + t.perks(), "", owned ? "<green>✔ Posseduto" : "<gray>Prezzo: " + Txt.gemme(price)
                            + (cur.level > 0 ? " <dark_gray>(upgrade)" : ""), owned ? "" : click("Click per acquistare"))
                    .glow(owned).build(), e -> {
                if (owned) return;
                purchase(p, "Grado " + t.plain, price, () -> {
                    d.vip = t.level;
                    Txt.broadcastPrison(Txt.mm(Txt.PREFIX + "<white>" + Txt.esc(p.getName()) + "</white> è diventato " + t.tag + "<gray>! Grazie per il supporto ❤"));
                }, () -> shopVip(p));
            });
        }
        m.set(22, Gui.back(p), e -> shop(p));
        m.fill();
        m.open(p);
    }

    private void shopKeys(Player p) {
        PlayerData d = plugin.data().get(p);
        Menu m = new Menu(3, "Chiavi").emblem(Gui.Emblem.CASSE);
        record Pack(String name, int price, int com, int rara, int legg, int pep, Crate icon) {}
        Pack[] packs = {
                new Pack("1 Chiave Pepita", 250, 0, 0, 0, 1, Crate.PEPITA),
                new Pack("3 Chiavi Leggendarie", 300, 0, 0, 3, 0, Crate.LEGGENDARIA),
                new Pack("10 Chiavi Leggendarie", 900, 0, 0, 10, 0, Crate.LEGGENDARIA),
                new Pack("Mega Bundle (25 chiavi)", 2000, 10, 8, 5, 2, Crate.PEPITA)};
        int[] slots = {10, 12, 14, 16};
        for (int i = 0; i < packs.length; i++) {
            Pack pk = packs[i];
            List<String> lore = new ArrayList<>();
            if (pk.com > 0) lore.add("<white>" + pk.com + "x Chiave Comune");
            if (pk.rara > 0) lore.add("<#55E6FF>" + pk.rara + "x Chiave Rara");
            if (pk.legg > 0) lore.add("<#D17BFF>" + pk.legg + "x Chiave Leggendaria");
            if (pk.pep > 0) lore.add("<#FFD54A>" + pk.pep + "x Chiave Pepita");
            lore.add("");
            lore.add("<gray>Prezzo: " + Txt.gemme(pk.price));
            lore.add(click("Click per acquistare"));
            m.set(slots[i], new ItemBuilder(Material.TRIPWIRE_HOOK, Math.max(1, pk.com + pk.rara + pk.legg + pk.pep)).model(pk.icon.keyModel)
                    .name(pk.icon.color + "<b>" + pk.name).lore(lore).build(), e -> purchase(p, pk.name, pk.price, () -> {
                d.addKeys("comune", pk.com);
                d.addKeys("rara", pk.rara);
                d.addKeys("leggendaria", pk.legg);
                d.addKeys("pepita", pk.pep);
            }, () -> shopKeys(p)));
        }
        m.set(22, Gui.back(p), e -> shop(p));
        m.fill();
        m.open(p);
    }

    private void shopRanks(Player p) {
        PlayerData d = plugin.data().get(p);
        Menu m = new Menu(3, "Salta Rank").emblem(Gui.Emblem.NEGOZIO);
        int[][] opts = {{1, 150}, {5, 600}};
        int[] slots = {11, 15};
        for (int i = 0; i < opts.length; i++) {
            int n = opts[i][0], price = opts[i][1];
            m.set(slots[i], new ItemBuilder(Material.EXPERIENCE_BOTTLE, n).name("<#6BE36B><b>+" + n + " Rank")
                    .lore("<gray>Rank attuale: " + RankManager.tag(d), "", "<gray>Prezzo: " + Txt.gemme(price), click("Click per acquistare")).build(), e -> {
                if (d.rank >= RankManager.MAX_RANK) {
                    Txt.send(p, "Sei già al rank massimo. Fai il <light_purple>/prestigio</light_purple>!");
                    return;
                }
                purchase(p, "+" + n + " Rank", price, () -> {
                    d.rank = Math.min(RankManager.MAX_RANK, d.rank + n);
                    plugin.picks().refresh(p);
                }, () -> shopRanks(p));
            });
        }
        m.set(22, Gui.back(p), e -> shop(p));
        m.fill();
        m.open(p);
    }

    private void shopBoosters(Player p) {
        PlayerData d = plugin.data().get(p);
        Menu m = new Menu(3, "Booster").emblem(Gui.Emblem.NEGOZIO);
        m.set(11, new ItemBuilder(Material.BLAZE_POWDER).name("<gold><b>Booster personale x2</b>")
                        .lore("<gray>Soldi x2 solo per te per 1 ora.", "", "<gray>Prezzo: " + Txt.gemme(200), click("Click per acquistare")).build(),
                e -> purchase(p, "Booster personale x2 (1h)", 200, () -> {
                    long base = Math.max(System.currentTimeMillis(), d.boostUntil);
                    d.boostMult = Math.max(2, d.boostMult);
                    d.boostUntil = base + 3600_000L;
                }, () -> shopBoosters(p)));
        m.set(15, new ItemBuilder(Material.BEACON).name("<gold><b>Booster GLOBALE x2</b>")
                        .lore("<gray>Soldi x2 per <white>tutto il server</white> per 1 ora.", "<gray>Tutti vedranno il tuo nome!", "",
                                "<gray>Prezzo: " + Txt.gemme(800), click("Click per acquistare")).glow().build(),
                e -> purchase(p, "Booster globale x2 (1h)", 800, () -> plugin.boosters().start(2, 3600, p.getName()), () -> shopBoosters(p)));
        m.set(22, Gui.back(p), e -> shop(p));
        m.fill();
        m.open(p);
    }

    /** Conferma e scala le gemme. */
    public void purchase(Player p, String what, int price, Runnable give, Runnable back) {
        PlayerData d = plugin.data().get(p);
        confirm(p, "Comprare " + what + "?", () -> {
            if (!plugin.eco().take(d, Economy.Cur.GEMME, price, "negozio")) {
                Txt.send(p, "Ti servono " + Txt.gemme(price) + " <gray>(hai " + Txt.gemme(d.gemme) + "<gray>). Acquistale su <white>"
                        + plugin.getConfig().getString("negozio.link", "negozio.pepitamc.it"));
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
                p.closeInventory();
                return;
            }
            d.gemmeSpese += price;
            give.run();
            d.dirty = true;
            plugin.data().save(d, true);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
            Txt.send(p, "Acquisto completato: <white>" + what + "</white> <gray>(-" + Txt.gemme(price) + "<gray>)");
            plugin.getLogger().info("[Negozio] " + p.getName() + " ha comprato " + what + " per " + price + " gemme");
            back.run();
        }, back, List.of("<gray>Prezzo: " + Txt.gemme(price), "<gray>Hai: " + Txt.gemme(d.gemme)));
    }

    public void confirm(Player p, String title, Runnable yes, Runnable no) {
        confirm(p, title, yes, no, List.of());
    }

    public void confirm(Player p, String title, Runnable yes, Runnable no, List<String> info) {
        Menu m = new Menu(3, title).emblem(Gui.Emblem.CONFERMA);
        m.set(11, Gui.button(p, "gui_conferma", Material.LIME_CONCRETE).name("<green><b>Conferma").lore(info).build(), e -> yes.run());
        if (!info.isEmpty()) m.set(13, Gui.button(p, "gui_info", Material.PAPER).name("<white><b>Dettagli").lore(info).build());
        m.set(15, Gui.button(p, "gui_annulla", Material.RED_CONCRETE).name("<red><b>Annulla").build(), e -> no.run());
        m.fill();
        m.open(p);
    }

    // =========================== SKIN ===========================

    public void skins(Player p) {
        PlayerData d = plugin.data().get(p);
        plugin.tutorial().onEvent(p, Tutorial.Ev.SKIN, 1);
        Menu m = new Menu(6, "Skin del piccone").emblem(Gui.Emblem.SKIN);
        m.set(4, new ItemBuilder(Material.AMETHYST_SHARD).model("gemma").name(Txt.S_GEMME + " <" + Txt.GEMME + "><b>" + Fmt.num(d.gemme) + " Gemme</b>")
                .lore("<gray>Le skin sono <white>oggetti</white>: si scambiano", "<gray>e si montano trascinandole sul piccone.",
                        "<gray>Bonus = base della rarità × tier del piccone", "<gray>(tuo tier: " + plugin.picks().tier(d).label() + " <gray>x"
                                + Fmt.num(plugin.picks().tier(d).mult) + ")").build());
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        int i = 0;
        for (PickaxeSkin s : PickaxeSkin.values()) {
            if (s == PickaxeSkin.CLASSICO || i >= slots.length) continue;
            int inInv = 0;
            for (ItemStack item : p.getInventory().getContents()) if (PickaxeManager_skinOf(item) == s) inInv += item.getAmount();
            boolean mounted = d.pickSkin.equals(s.id);
            ItemStack icon = plugin.picks().skinItem(s, 1);
            List<String> lore = new ArrayList<>();
            lore.add(s.rarity + (s.is3d ? " <dark_gray>• <gray>3D" : ""));
            lore.add("<gray>" + s.desc);
            double t = plugin.picks().tier(d).mult;
            lore.add("<gray>Bonus col tuo piccone: <" + Txt.SOLDI + ">+" + Fmt.pct(s.rar.soldi * t) + " <gray>/ <" + Txt.PEPITE + ">+" + Fmt.pct(s.rar.pepite * t)
                    + (s.rar.quantum > 0 ? " <gray>/ <" + Txt.QUANTUM + ">+" + Fmt.pct(s.rar.quantum * t) : ""));
            lore.add("");
            if (mounted) lore.add("<green>✔ Montata sul piccone");
            if (inInv > 0) lore.add("<gray>Nel tuo inventario: <white>" + inInv);
            lore.add(s.price > 0 ? "<gray>Prezzo: " + Txt.gemme(s.price) : "<gray>Solo da casse, battle pass e traguardi");
            if (s.price > 0) lore.add(click("Click per comprarla (diventa un oggetto)"));
            icon.editMeta(meta -> {
                meta.lore(Txt.lore(lore));
                meta.setEnchantmentGlintOverride(mounted);
            });
            m.set(slots[i++], icon, e -> {
                if (s.price <= 0) return;
                purchase(p, "Skin " + s.display, s.price, () -> plugin.picks().giveSkin(p, d, s), () -> skins(p));
            });
        }
        PickaxeSkin cur = PickaxeSkin.byId(d.pickSkin);
        m.set(38, Gui.button(p, "gui_skin", Material.SHEARS).name("<white><b>Skin montata: " + (cur == PickaxeSkin.CLASSICO ? "<gray>nessuna" : cur.rar.color + cur.display))
                .lore(cur == PickaxeSkin.CLASSICO ? List.of("<gray>Trascina un oggetto-skin sul piccone.") : List.of("", click("Click per smontarla"))).build(), e -> {
            plugin.picks().unmount(p, d);
            skins(p);
        });
        m.set(40, Gui.button(p, "gui_casse", Material.ENDER_CHEST).name("<gold><b>Deposito skin</b>")
                .lore("<gray>Skin in attesa: <white>" + d.skinDeposito.size(), "<gray>(arrivano qui se l'inventario è pieno)", "",
                        d.skinDeposito.isEmpty() ? "<dark_gray>Vuoto" : click("Click per ritirarle")).glow(!d.skinDeposito.isEmpty()).build(), e -> {
            withdraw(p, d);
            skins(p);
        });
        m.set(42, Gui.button(p, "gui_armatura", Material.LEATHER_CHESTPLATE).dye(it.pepita.core.armor.ArmorService.skinOn(d, 1).color)
                .name("<#FF7BD1><b>Skin della corazza</b>")
                .lore("<gray>Add-on per elmo, corpetto,", "<gray>gambali e stivali: cambiano", "<gray>l'aspetto e potenziano il pezzo.", "",
                        click("Click per aprire")).build(), e -> armorSkins(p));
        m.set(45, Gui.back(p), e -> main(p));
        m.set(53, Gui.close(p), e -> p.closeInventory());
        m.frame();
        m.open(p);
    }

    private static PickaxeSkin PickaxeManager_skinOf(ItemStack item) {
        return item == null ? null : it.pepita.core.pickaxe.PickaxeManager.skinOf(item);
    }

    private void withdraw(Player p, PlayerData d) {
        int n = 0;
        while (!d.skinDeposito.isEmpty() && p.getInventory().firstEmpty() >= 0) {
            ItemStack item = plugin.picks().depositItem(d.skinDeposito.removeFirst(), d);
            if (item != null) {
                p.getInventory().addItem(item);
                n++;
            }
        }
        d.dirty = true;
        if (n > 0) Txt.send(p, "Hai ritirato <white>" + n + "</white> skin dal deposito.");
        else if (!d.skinDeposito.isEmpty()) Txt.send(p, "Libera spazio nell'inventario per ritirare le skin.");
    }

    public void armorSkins(Player p) {
        PlayerData d = plugin.data().get(p);
        it.pepita.core.armor.ArmorService as = plugin.armor();
        Menu m = new Menu(6, "Skin della corazza").emblem(Gui.Emblem.ARMATURA);
        m.set(4, Gui.button(p, "gui_info", Material.BOOK).name("<#FF7BD1><b>Skin add-on</b>")
                .lore("<gray>Ogni skin è un <white>oggetto</white> per un solo pezzo:", "<gray>trascinala sul pezzo della corazza (o tasto destro)",
                        "<gray>per montarla. Cambia l'aspetto del pezzo e ne", "<gray>potenzia la statistica principale:",
                        "  <#FFB86B>Elmo</#FFB86B> <dark_gray>→</dark_gray> " + it.pepita.core.armor.ArmorService.mainStat(0),
                        "  <" + Txt.SOLDI + ">Corpetto</" + Txt.SOLDI + "> <dark_gray>→</dark_gray> " + it.pepita.core.armor.ArmorService.mainStat(1),
                        "  <" + Txt.PEPITE + ">Gambali</" + Txt.PEPITE + "> <dark_gray>→</dark_gray> " + it.pepita.core.armor.ArmorService.mainStat(2),
                        "  <" + Txt.QUANTUM + ">Stivali</" + Txt.QUANTUM + "> <dark_gray>→</dark_gray> " + it.pepita.core.armor.ArmorService.mainStat(3),
                        "<gray>Bonus = base della rarità × livello del pezzo.", "<gray>Si possono mischiare set diversi!").build());
        int[] worn = {10, 12, 14, 16};
        for (int i = 0; i < 4; i++) {
            final int piece = i;
            ArmorSkin cur = it.pepita.core.armor.ArmorService.skinOn(d, i);
            ItemStack icon = plugin.picks().armorPiece(PickaxeManager.SLOTS[i], cur, d.hasPack, d);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Livello del pezzo: <" + Txt.QUANTUM + ">" + d.armorLevels[i] + "</" + Txt.QUANTUM + "> <dark_gray>(skin x" + Fmt.num(as.skinLevelMult(d.armorLevels[i])) + ")");
            if (cur == ArmorSkin.GALEOTTO) {
                lore.add("<gray>Skin: <dark_gray>nessuna");
                lore.add("");
                lore.add("<gray>Trascina una skin da " + PickaxeManager.PIECE_NAMES[i].toLowerCase() + " sul pezzo.");
            } else {
                lore.add("<gray>Skin: " + cur.rar.color + cur.display);
                lore.add("  " + it.pepita.core.armor.ArmorService.mainStat(i) + " +" + Fmt.pct(as.skinBonus(cur, i, d.armorLevels[i])));
                lore.add("");
                lore.add(click("Click per smontarla (torna un oggetto)"));
            }
            icon.editMeta(meta -> {
                meta.itemName(Txt.item("<white><b>" + PickaxeManager.PIECE_NAMES[piece] + "</b>"));
                meta.lore(Txt.lore(lore));
            });
            m.set(worn[i], icon, e -> {
                plugin.picks().unmountArmorAddon(p, d, piece);
                armorSkins(p);
            });
        }
        int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        int i = 0;
        for (ArmorSkin s : ArmorSkin.values()) {
            if (s == ArmorSkin.GALEOTTO || i >= slots.length) continue;
            int inInv = 0;
            for (ItemStack item : p.getInventory().getContents()) {
                PickaxeManager.ArmorAddon a = PickaxeManager.addonOf(item);
                if (a != null && a.skin() == s) inInv++;
            }
            int mounted = 0;
            for (int k = 0; k < 4; k++) if (it.pepita.core.armor.ArmorService.skinOn(d, k) == s) mounted++;
            ItemBuilder b = new ItemBuilder(Material.LEATHER_CHESTPLATE).dye(s.color).name(s.rar.color + "<b>" + s.display);
            if (d.hasPack && s.model) b.model(s.id + "_chestplate");
            List<String> lore = new ArrayList<>();
            lore.add(s.rarity + (s.model ? " <dark_gray>• <gray>3D" : ""));
            lore.add("<gray>" + s.desc);
            lore.add("");
            lore.add("<gray>Sui tuoi pezzi:");
            for (int k = 0; k < 4; k++)
                lore.add("  <gray>" + PickaxeManager.PIECE_NAMES[k] + ": " + it.pepita.core.armor.ArmorService.mainStat(k) + " +" + Fmt.pct(as.skinBonus(s, k, d.armorLevels[k])));
            lore.add("");
            if (mounted > 0) lore.add("<green>✔ Montata su " + mounted + (mounted == 1 ? " pezzo" : " pezzi"));
            if (inInv > 0) lore.add("<gray>Nel tuo inventario: <white>" + inInv);
            lore.add(s.price > 0 ? "<gray>Kit di 4 add-on: " + Txt.gemme(s.price) : "<gray>Solo da casse, battle pass e traguardi");
            if (s.price > 0) lore.add(click("Click per comprare il kit (4 oggetti)"));
            m.set(slots[i++], b.lore(lore).glow(mounted > 0).build(), e -> {
                if (s.price <= 0) return;
                purchase(p, "Kit skin " + s.display, s.price, () -> plugin.picks().giveArmorSet(p, d, s), () -> armorSkins(p));
            });
        }
        m.set(45, Gui.back(p), e -> skins(p));
        m.set(48, Gui.button(p, "gui_casse", Material.ENDER_CHEST).name("<gold><b>Deposito skin</b>")
                .lore("<gray>Skin in attesa: <white>" + d.skinDeposito.size(), "", d.skinDeposito.isEmpty() ? "<dark_gray>Vuoto" : click("Click per ritirarle"))
                .glow(!d.skinDeposito.isEmpty()).build(), e -> {
            withdraw(p, d);
            armorSkins(p);
        });
        m.set(50, Gui.button(p, "gui_quantum", Material.AMETHYST_CLUSTER).name("<gradient:#3FE0F0:#D15BFF><b>Potenzia la corazza</b></gradient>")
                .lore("<gray>Più livello ha il pezzo,", "<gray>più rende la skin montata.", "", click("Click per aprire")).build(), e -> plugin.armor().open(p));
        m.set(53, Gui.close(p), e -> p.closeInventory());
        m.frame();
        m.open(p);
    }

    // =========================== CLASSIFICHE ===========================

    public void top(Player p) {
        Menu m = new Menu(3, "Classifiche").emblem(Gui.Emblem.CLASSIFICHE);
        DataManager dm = plugin.data();
        m.set(10, topItem(Material.EMERALD, "<" + Txt.SOLDI + "><b>Top Soldi", dm.topSoldi(), true));
        m.set(12, topItem(Material.DIAMOND_PICKAXE, "<#55E6FF><b>Top Blocchi", dm.topBlocchi(), false));
        List<String> pl = new ArrayList<>();
        List<DataManager.TopEntry> tp = dm.topPrestigio();
        for (int i = 0; i < tp.size(); i++) pl.add("<gold>" + (i + 1) + ". <white>" + Txt.esc(tp.get(i).name()) + " <dark_gray>- <light_purple>" + tp.get(i).extra());
        m.set(14, new ItemBuilder(Material.NETHER_STAR).name("<#D17BFF><b>Top Prestigio").lore(pl.isEmpty() ? List.of("<gray>Nessuno") : pl).build());
        List<String> gl = new ArrayList<>();
        List<GangManager.Gang> gt = plugin.gangs().top();
        for (int i = 0; i < Math.min(10, gt.size()); i++) gl.add("<gold>" + (i + 1) + ". <#FF7B7B>" + gt.get(i).name + " <dark_gray>- <white>" + Fmt.num(gt.get(i).blocchi));
        m.set(16, new ItemBuilder(Material.RED_BANNER).name("<#FF7B7B><b>Top Gang").lore(gl.isEmpty() ? List.of("<gray>Nessuna gang") : gl).build());
        m.set(22, Gui.back(p), e -> main(p));
        m.fill();
        m.open(p);
    }

    private ItemStack topItem(Material mat, String title, List<DataManager.TopEntry> list, boolean money) {
        List<String> lore = new ArrayList<>();
        for (int i = 0; i < list.size(); i++)
            lore.add("<gold>" + (i + 1) + ". <white>" + Txt.esc(list.get(i).name()) + " <dark_gray>- "
                    + (money ? Txt.soldi(list.get(i).value()) : "<white>" + Fmt.num(list.get(i).value())));
        if (lore.isEmpty()) lore.add("<gray>Nessuno");
        return new ItemBuilder(mat).name(title).lore(lore).build();
    }

    // =========================== IMPOSTAZIONI ===========================

    public void settings(Player p) {
        PlayerData d = plugin.data().get(p);
        Menu m = new Menu(3, "Impostazioni").emblem(Gui.Emblem.IMPOSTAZIONI);
        m.set(11, new ItemBuilder(d.procMessages ? Material.LIME_DYE : Material.GRAY_DYE)
                .name("<white>Messaggi incantesimi: " + (d.procMessages ? "<green>ON" : "<red>OFF")).build(), e -> {
            d.procMessages = !d.procMessages;
            d.dirty = true;
            settings(p);
        });
        boolean canFly = d.ench("volo") > 0 || d.vip >= 2 || p.hasPermission("pepita.fly");
        m.set(15, new ItemBuilder(Material.ELYTRA).name("<white>Volo: " + (d.fly ? "<green>ON" : "<red>OFF"))
                .lore(canFly ? List.of("<gray>Vola nell'hub, nella prigione e nelle miniere.") : List.of("<gray>Serve l'incantesimo <white>Volo</white>", "<gray>o il grado <#55E6FF>VIP+</#55E6FF>."))
                .build(), e -> {
            if (!canFly) return;
            d.fly = !d.fly;
            d.dirty = true;
            settings(p);
        });
        m.set(22, Gui.back(p), e -> main(p));
        m.fill();
        m.open(p);
    }

    // =========================== GIORNALIERO ===========================

    public void daily(Player p) {
        PlayerData d = plugin.data().get(p);
        long cd = plugin.getConfig().getLong("giornaliero.ore", 24) * 3600_000L;
        long left = cd - (System.currentTimeMillis() - d.lastDaily);
        if (left > 0) {
            Txt.send(p, "Hai già ritirato il premio. Torna tra <white>" + Fmt.time(left / 1000));
            return;
        }
        d.lastDaily = System.currentTimeMillis();
        int mult = d.vip >= 4 ? 2 : 1;
        Economy eco = plugin.eco();
        double money = eco.minutes(d, plugin.getConfig().getDouble("giornaliero.minuti-soldi", 20)) * mult;
        double pep = 150.0 * (1 + d.prestige) * mult;
        long gems = plugin.getConfig().getLong("gemme-guadagnabili.giornaliero", 5) * mult;
        eco.give(d, Economy.Cur.SOLDI, money, "giornaliero");
        eco.give(d, Economy.Cur.PEPITE, pep, "giornaliero");
        eco.give(d, Economy.Cur.GEMME, gems, "giornaliero");
        d.addKeys("comune", 2 * mult);
        if (d.vip >= 1) d.addKeys("rara", mult);
        if (d.vip >= 3) d.addKeys("leggendaria", mult);
        d.dirty = true;
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
        Txt.send(p, "<#6BE36B><b>Premio giornaliero!</b></#6BE36B> " + Txt.soldi(money) + "<gray>, " + Txt.pepite(pep) + "<gray>, "
                + Txt.gemme(gems) + "<gray>, " + (2 * mult) + " chiavi Comuni"
                + (d.vip >= 1 ? ", " + mult + " Rara" : "") + (d.vip >= 3 ? ", " + mult + " Leggendaria" : "") + ".");
    }
}

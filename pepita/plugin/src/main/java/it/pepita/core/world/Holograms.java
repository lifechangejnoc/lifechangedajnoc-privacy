package it.pepita.core.world;

import it.pepita.core.PepitaCore;
import it.pepita.core.crate.Crate;
import it.pepita.core.data.DataManager;
import it.pepita.core.mine.Mine;
import it.pepita.core.util.Fmt;
import it.pepita.core.util.Keys;
import it.pepita.core.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static it.pepita.core.world.Layout.*;

/** Scritte fluttuanti (TextDisplay) in tutti i mondi: portali, casse, classifiche, banchi, miniere. */
public final class Holograms {
    private final PepitaCore plugin;
    private final Map<String, TextDisplay> displays = new HashMap<>();

    public Holograms(PepitaCore plugin) {
        this.plugin = plugin;
    }

    /** Toglie tutte le entità del plugin (ologrammi, logo, NPC) da tutti i mondi gestiti. */
    public void removeAll() {
        for (String n : WorldService.WORLDS) {
            World w = Bukkit.getWorld(n);
            if (w == null) continue;
            for (Entity e : w.getEntities())
                if (e.getPersistentDataContainer().has(Keys.HOLO, PersistentDataType.STRING)
                        && !"fumetto".equals(e.getPersistentDataContainer().get(Keys.HOLO, PersistentDataType.STRING))) e.remove();
        }
        displays.clear();
    }

    private TextDisplay spawn(String id, World w, double x, double y, double z, String text, float scale) {
        if (w == null) return null;
        TextDisplay old = displays.remove(id);
        if (old != null) old.remove();
        Location l = new Location(w, x, y, z);
        TextDisplay td = w.spawn(l, TextDisplay.class, t -> {
            t.text(Txt.mm(text));
            t.setBillboard(Display.Billboard.CENTER);
            t.setShadowed(true);
            t.setSeeThrough(false);
            t.setDefaultBackground(false);
            t.setBackgroundColor(Color.fromARGB(90, 0, 0, 0));
            t.setLineWidth(260);
            t.setPersistent(false);
            t.setViewRange(1.2f);
            if (scale != 1f) {
                var tr = t.getTransformation();
                tr.getScale().set(scale, scale, scale);
                t.setTransformation(tr);
            }
            t.getPersistentDataContainer().set(Keys.HOLO, PersistentDataType.STRING, id);
        });
        displays.put(id, td);
        return td;
    }

    public void update(String id, String text) {
        TextDisplay td = displays.get(id);
        if (td != null && td.isValid()) td.text(Txt.mm(text));
    }

    public void spawnAll() {
        removeAll();
        spawnHub();
        spawnPrison();
        spawnMines();
        spawnPvp();
        spawnCells();
        updateBoards();
        plugin.billboard().spawnAll();
        plugin.tutorial().spawnNpc();
        plugin.lobby().spawnNpc();
    }

    // ---------------- Hub ----------------

    private void spawnHub() {
        World w = plugin.world().hub();
        if (w == null) return; // in rete la lobby è un altro server
        if (plugin.world().lobbyMap().active()) {
            // mappa esterna: niente portali del plugin, solo il benvenuto sopra il secondino del Prison
            Location n = plugin.world().hubNpc();
            spawn("hub_benvenuto", w, n.getX(), n.getY() + 3.4, n.getZ(),
                    "<gradient:#FFE259:#FFA751><b>BENVENUTO SU PEPITA</b></gradient>\n<gray>Usa la <yellow>bussola</yellow> per scegliere la modalità", 1.1f);
            return;
        }
        // la lobby è separata dalle modalità: per ora solo il portale del Prison è attivo
        String soon = "<dark_gray><b>✦ PROSSIMAMENTE ✦</b></dark_gray>\n<gray>Nuova modalità in arrivo";
        String[] text = {
                "<gradient:#FFE259:#FFA751><b>⛓ PRISON ⛓</b></gradient>\n<gray>Entra nel portale per giocare",
                soon, soon, soon};
        for (int i = 0; i < HUB_PORTAL_LABELS.length; i++) {
            double[] l = HUB_PORTAL_LABELS[i];
            spawn("hub_portale_" + i, w, l[0], l[1], l[2], text[i], 1.4f);
        }
        spawn("hub_benvenuto", w, HUB_SPAWN[0], HUB_FLOOR + 4.6, HUB_SPAWN[2] - 3,
                "<gradient:#FFE259:#FFA751><b>BENVENUTO SU PEPITA</b></gradient>\n<gray>Usa la <yellow>bussola</yellow> per scegliere la modalità\n"
                        + "<gray>o parla con il secondino del <gold>Prison</gold>", 1.1f);
    }

    // ---------------- Prigione ----------------

    private void spawnPrison() {
        World w = plugin.world().prison();
        spawn("benvenuto", w, PRISON_SPAWN[0], 102.6, PRISON_SPAWN[2] - 4,
                "<gradient:#FFE259:#FFA751><b>LA PRIGIONE DI PEPITA</b></gradient>\n"
                        + "<gray>Il cortile della <gold>Corsa all'Oro</gold>\n\n"
                        + "<white>Entra nel <light_purple>portale</light_purple> a nord per la miniera\n"
                        + "<gray>Apri il menu con la <yellow>stella</yellow> o <yellow>/menu", 1.05f);
        spawn("portale", w, 0.5, 106.4, PRISON_PORTAL_Z + 0.5,
                "<light_purple><b>⛏ PORTALE MINIERE ⛏</b></light_purple>\n<gray>Ti porta nella tua miniera migliore", 1.2f);
        Crate[] cs = Crate.values();
        for (int i = 0; i < cs.length; i++) {
            int[] p = PRISON_CRATES[i];
            spawn("cassa_" + cs[i].id, w, p[0] + 0.5, p[1] + 1.25, p[2] + 0.5, cs[i].title() + "\n<gray>Click destro per aprire", 0.9f);
        }
        spawn("titolo_casse", w, 23.5, 105.6, 0.5, "<gradient:#FFE259:#FFA751><b>CASSE</b></gradient>", 2f);
        spawn("titolo_top", w, -23.5, 105.6, 0.5, "<gradient:#FFE259:#FFA751><b>CLASSIFICHE</b></gradient>", 2f);
        label(w, "incantesimi", PRISON_ENCHANT, "<#FFD54A><b>✦ Incantesimi</b>\n<gray>Potenzia il piccone");
        label(w, "negozio", PRISON_SHOP, "<#D17BFF><b>◆ Negozio Gemme</b>\n<gray>VIP, chiavi, skin");
        label(w, "giornaliero", PRISON_DAILY, "<#6BE36B><b>🎁 Giornaliero</b>\n<gray>Ritira il premio ogni 24h");
        label(w, "armatura", PRISON_ARMOR, "<gradient:#3FE0F0:#D15BFF><b>⚛ Corazza Quantica</b></gradient>\n<gray>Potenziala con i Quantum");
        label(w, "battlepass", PRISON_PASS, "<gradient:#FFE259:#FFA751><b>✪ Battle Pass</b></gradient>\n<gray>Missioni e premi");
        for (int i = 0; i < PRISON_BOARDS.length; i++) {
            int[] b = PRISON_BOARDS[i];
            spawn("top_" + i, w, b[0] + 0.5, b[1] + 2.0, b[2] + 0.5, "<gray>Caricamento...", 0.85f);
        }
    }

    private void label(World w, String id, int[] pos, String text) {
        spawn(id, w, pos[0] + 0.5, pos[1] + 1.2, pos[2] + 0.5, text, 0.9f);
    }

    // ---------------- Miniere, PvP, Celle ----------------

    private void spawnMines() {
        for (Mine m : plugin.mines().all())
            spawn("miniera_" + m.id, Bukkit.getWorld(m.world), m.labelX, m.labelY, m.labelZ, plugin.mines().labelText(m), m.pvp ? 1.5f : 1.2f);
    }

    private void spawnPvp() {
        World w = plugin.world().pvp();
        spawn("pvp_sicuro", w, PVP_SPAWN[0], PVP_SPAWN[1] + 3.2, PVP_SPAWN[2] - 2,
                "<green><b>✔ ZONA SICURA</b></green>\n<gray>Qui i <" + Txt.QUANTUM + ">Quantum</" + Txt.QUANTUM + "> in tasca\n<gray>vengono messi al sicuro.\n"
                        + "<red>Fuori da qui il PvP è attivo!", 1.0f);
    }

    private void spawnCells() {
        World w = plugin.world().cells();
        spawn("celle_benvenuto", w, CELLS_SPAWN[0], CELLS_SPAWN[1] + 3.4, CELLS_SPAWN[2] - 4,
                "<gradient:#FFE259:#FFA751><b>🏛 COLOSSEO DELLE CELLE 🏛</b></gradient>\n<gray>Prendi un <gold>ascensore</gold> ai quattro angoli\n"
                        + "<gray>e scegli il piano. <yellow>/cella menu</yellow> per comprare.", 1.2f);
    }

    public void updateMines() {
        for (Mine m : plugin.mines().all()) update("miniera_" + m.id, plugin.mines().labelText(m));
    }

    public void updateBoards() {
        DataManager dm = plugin.data();
        update("top_0", board("<" + Txt.SOLDI + "><b>$ TOP SOLDI</b>", dm.topSoldi(), true));
        update("top_1", board("<#55E6FF><b>⛏ TOP BLOCCHI</b>", dm.topBlocchi(), false));
        update("top_2", boardPrestige("<#D17BFF><b>★ TOP PRESTIGIO</b>", dm.topPrestigio()));
    }

    private static String board(String title, List<DataManager.TopEntry> list, boolean money) {
        StringBuilder sb = new StringBuilder(title).append("\n");
        for (int i = 0; i < 10; i++) {
            if (i < list.size()) {
                DataManager.TopEntry t = list.get(i);
                sb.append(pos(i)).append(" <white>").append(Txt.esc(t.name())).append(" <dark_gray>- ")
                        .append(money ? Txt.soldi(t.value()) : "<white>" + Fmt.num(t.value()));
            } else sb.append(pos(i)).append(" <dark_gray>---");
            if (i < 9) sb.append("\n");
        }
        return sb.toString();
    }

    private static String boardPrestige(String title, List<DataManager.TopEntry> list) {
        StringBuilder sb = new StringBuilder(title).append("\n");
        for (int i = 0; i < 10; i++) {
            if (i < list.size()) {
                DataManager.TopEntry t = list.get(i);
                sb.append(pos(i)).append(" <white>").append(Txt.esc(t.name())).append(" <dark_gray>- <light_purple>").append(t.extra());
            } else sb.append(pos(i)).append(" <dark_gray>---");
            if (i < 9) sb.append("\n");
        }
        return sb.toString();
    }

    private static String pos(int i) {
        return switch (i) {
            case 0 -> "<#FFD54A><b>1.</b>";
            case 1 -> "<#E0E0E0><b>2.</b>";
            case 2 -> "<#CD7F32><b>3.</b>";
            default -> "<gray>" + (i + 1) + ".";
        };
    }
}

package it.pepita.core.economy;

import it.pepita.core.PepitaCore;
import it.pepita.core.data.PlayerData;
import it.pepita.core.mine.Mine;
import it.pepita.core.util.Fmt;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Architettura economica di Pepita (vedi ECONOMIA.md).
 * <ul>
 *     <li>Unità di reddito: i premi sono espressi in "minuti di scavo" del giocatore, non in cifre fisse.</li>
 *     <li>Soft cap del moltiplicatore soldi: eff = 1 + cap·(1 − e^(−(m−1)/cap)).</li>
 *     <li>Contatori di entrate/uscite per valuta e fonte, ora per ora, con log in economia.log.</li>
 * </ul>
 */
public final class Economy {
    public enum Cur {
        SOLDI("soldi"), PEPITE("pepite"), GEMME("gemme"), QUANTUM("quantum");
        public final String id;

        Cur(String id) {
            this.id = id;
        }
    }

    /** Totali di un'ora. */
    public static final class Hour {
        public final long start;
        public final Map<Cur, Map<String, Double>> in = new EnumMap<>(Cur.class);
        public final Map<Cur, Map<String, Double>> out = new EnumMap<>(Cur.class);
        public int maxOnline;

        Hour(long start) {
            this.start = start;
            for (Cur c : Cur.values()) {
                in.put(c, new HashMap<>());
                out.put(c, new HashMap<>());
            }
        }

        public double total(Map<String, Double> m) {
            double s = 0;
            for (double v : m.values()) s += v;
            return s;
        }
    }

    private final PepitaCore plugin;
    private final File log;
    private Hour hour;
    private final Deque<Hour> history = new ArrayDeque<>();

    private double refBpm, cap, payTax;

    public Economy(PepitaCore plugin) {
        this.plugin = plugin;
        this.log = new File(plugin.getDataFolder(), "economia.log");
        this.hour = new Hour(hourStart(System.currentTimeMillis()));
        reload();
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        refBpm = c.getDouble("economia.blocchi-minuto-riferimento", 90);
        cap = c.getDouble("economia.soft-cap", 25);
        payTax = c.getDouble("economia.tassa-paga", 0.05);
    }

    public double payTax() {
        return payTax;
    }

    private static long hourStart(long t) {
        return t - t % 3_600_000L;
    }

    // =====================================================================
    //  Unità di reddito e soft cap
    // =====================================================================

    /** Soft cap applicato alla parte del moltiplicatore sopra x1 (così x1 resta x1). */
    public double softCap(double m) {
        if (cap <= 0 || m <= 1) return m;
        return 1 + cap * (1 - Math.exp(-(m - 1) / cap));
    }

    /**
     * Valore di un minuto di scavo per il giocatore: valore medio di un blocco della miniera migliore
     * accessibile × moltiplicatore soldi permanente (soft cap, senza booster temporanei) × blocchi/minuto di riferimento.
     */
    public double minute(PlayerData d) {
        Mine best = plugin.mines().best(d);
        double avg = best == null ? 12 : best.avgValue();
        return avg * plugin.ranks().soldiMultBase(d) * refBpm;
    }

    public double minutes(PlayerData d, double mins) {
        return minute(d) * mins;
    }

    // =====================================================================
    //  Movimenti
    // =====================================================================

    private void roll() {
        long now = System.currentTimeMillis();
        long hs = hourStart(now);
        if (hs == hour.start) return;
        writeLog(hour);
        history.addFirst(hour);
        while (history.size() > 24) history.removeLast();
        hour = new Hour(hs);
    }

    public void record(Cur c, double amount, String key, boolean income) {
        if (amount <= 0 || Double.isNaN(amount) || Double.isInfinite(amount)) return;
        roll();
        (income ? hour.in : hour.out).get(c).merge(key, amount, Double::sum);
    }

    /** Accredita al giocatore e registra la fonte. */
    public void give(PlayerData d, Cur c, double amount, String source) {
        if (amount <= 0 || Double.isNaN(amount) || Double.isInfinite(amount)) return;
        switch (c) {
            case SOLDI -> d.soldi += amount;
            case PEPITE -> d.pepite += amount;
            case GEMME -> d.gemme += (long) Math.round(amount);
            case QUANTUM -> d.quantum += amount;
        }
        d.dirty = true;
        record(c, amount, source, true);
    }

    public double balance(PlayerData d, Cur c) {
        return switch (c) {
            case SOLDI -> d.soldi;
            case PEPITE -> d.pepite;
            case GEMME -> d.gemme;
            case QUANTUM -> d.quantum;
        };
    }

    public boolean has(PlayerData d, Cur c, double amount) {
        return balance(d, c) >= amount - 1e-9;
    }

    /** Toglie al giocatore se può permetterselo e registra l'uscita. */
    public boolean take(PlayerData d, Cur c, double amount, String sink) {
        if (amount < 0 || !has(d, c, amount)) return false;
        switch (c) {
            case SOLDI -> d.soldi = Math.max(0, d.soldi - amount);
            case PEPITE -> d.pepite = Math.max(0, d.pepite - amount);
            case GEMME -> d.gemme = Math.max(0, d.gemme - (long) Math.ceil(amount));
            case QUANTUM -> d.quantum = Math.max(0, d.quantum - amount);
        }
        d.dirty = true;
        record(c, amount, sink, false);
        return true;
    }

    /** Toglie tutto (prestigio/evasione azzerano i soldi). */
    public void wipe(PlayerData d, Cur c, String sink) {
        double v = balance(d, c);
        if (v > 0) take(d, c, v, sink);
    }

    // =====================================================================
    //  Monitoraggio
    // =====================================================================

    /** Ogni minuto: aggiorna il picco di giocatori e chiude l'ora se è passata. */
    public void tick() {
        roll();
        hour.maxOnline = Math.max(hour.maxOnline, Bukkit.getOnlinePlayers().size());
    }

    public void flush() {
        writeLog(hour);
    }

    private void writeLog(Hour h) {
        StringBuilder sb = new StringBuilder();
        LocalDateTime t = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(h.start), java.time.ZoneId.systemDefault());
        sb.append(t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00"))).append(" | online max ").append(h.maxOnline);
        boolean any = false;
        for (Cur c : Cur.values()) {
            double in = h.total(h.in.get(c)), out = h.total(h.out.get(c));
            if (in == 0 && out == 0) continue;
            any = true;
            sb.append(" | ").append(c.id).append(" +").append(Fmt.num(in)).append(top(h.in.get(c)))
                    .append(" -").append(Fmt.num(out)).append(top(h.out.get(c)));
        }
        if (!any) return;
        sb.append(System.lineSeparator());
        final String line = sb.toString();
        Runnable w = () -> {
            try {
                Files.writeString(log.toPath(), line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                plugin.getLogger().warning("economia.log: " + e.getMessage());
            }
        };
        if (plugin.isEnabled()) Bukkit.getScheduler().runTaskAsynchronously(plugin, w);
        else w.run();
    }

    private static String top(Map<String, Double> m) {
        List<Map.Entry<String, Double>> l = new ArrayList<>(m.entrySet());
        l.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        StringBuilder sb = new StringBuilder(" (");
        for (int i = 0; i < Math.min(4, l.size()); i++) {
            if (i > 0) sb.append(", ");
            sb.append(l.get(i).getKey()).append(' ').append(Fmt.num(l.get(i).getValue()));
        }
        return l.isEmpty() ? "" : sb.append(')').toString();
    }

    /** Righe per /pa economia: ora corrente e ultime 24 ore. */
    public List<String> report() {
        roll();
        List<String> out = new ArrayList<>();
        out.add("<gradient:#FFE259:#FFA751><b>━━ Economia di Pepita ━━</b></gradient>");
        out.add("<gray>Soft cap moltiplicatore: <white>" + Fmt.num(cap) + "</white> • blocchi/min di riferimento: <white>" + Fmt.num(refBpm)
                + "</white> • tassa /paga: <white>" + Fmt.pct(payTax));
        out.add("<yellow>Ora corrente:");
        out.addAll(lines(hour));
        Hour day = new Hour(0);
        for (Hour h : history) {
            for (Cur c : Cur.values()) {
                h.in.get(c).forEach((k, v) -> day.in.get(c).merge(k, v, Double::sum));
                h.out.get(c).forEach((k, v) -> day.out.get(c).merge(k, v, Double::sum));
            }
            day.maxOnline = Math.max(day.maxOnline, h.maxOnline);
        }
        out.add("<yellow>Ultime " + history.size() + " ore:");
        out.addAll(lines(day));
        double soldi = 0, pep = 0, q = 0;
        long gem = 0;
        for (PlayerData d : plugin.data().online()) {
            soldi += d.soldi;
            pep += d.pepite;
            gem += d.gemme;
            q += d.quantum;
        }
        out.add("<gray>In tasca ai giocatori online: <white>$" + Fmt.num(soldi) + " • " + Fmt.num(pep) + " ✦ • " + Fmt.num(gem)
                + " ◆ • " + Fmt.num(q) + " ⚛");
        return out;
    }

    private static List<String> lines(Hour h) {
        List<String> out = new ArrayList<>();
        for (Cur c : Cur.values()) {
            double in = h.total(h.in.get(c)), o = h.total(h.out.get(c));
            String bal = in >= o ? "<green>+" + Fmt.num(in - o) : "<red>-" + Fmt.num(o - in);
            out.add("<gray> " + c.id + ": <green>+" + Fmt.num(in) + "<dark_gray>" + top(h.in.get(c)) + " <red>-" + Fmt.num(o)
                    + "<dark_gray>" + top(h.out.get(c)) + " <gray>saldo " + bal);
        }
        return out;
    }
}

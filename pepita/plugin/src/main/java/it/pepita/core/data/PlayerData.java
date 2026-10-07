package it.pepita.core.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Tutti i dati di un giocatore. */
public final class PlayerData {
    public final UUID uuid;
    public String name = "?";

    // Valute
    public double soldi;
    public double pepite;
    public long gemme;
    /** Valuta della miniera PvP (depositata). */
    public double quantum;
    /** Quantum scavati nella miniera PvP ma non ancora messi al sicuro (si perdono morendo). */
    public double quantumTasca;
    /** Quantum guadagnati nell'ora corrente (rendimenti decrescenti) e inizio dell'ora. */
    public double quantumOra;
    public long quantumOraInizio;

    // Progressione
    public int rank;        // 0 = A ... 25 = Z
    public int prestige;
    public int evasioni;
    public long blocchi;
    public int vip;         // 0 nessuno, 1 VIP, 2 VIP+, 3 ELITE, 4 LEGGENDA

    // Piccone
    public final Map<String, Integer> enchants = new HashMap<>();
    public int pickLevel = 1;
    public double pickXp;
    public final Set<String> pickSkins = new HashSet<>();
    public String pickSkin = "classico";
    public final Set<String> armorSkins = new HashSet<>();
    public String armorSkin = "galeotto";
    public final Set<String> disabledEnchants = new HashSet<>();
    /** Tier del piccone (indice di PickTier). -1 = da migrare. */
    public int pickTier = -1;
    /** Le skin possedute sono già state trasformate in oggetti. */
    public boolean skinMigrate;
    /** Skin (oggetti) in attesa di essere ritirate perché l'inventario era pieno. */
    public final List<String> skinDeposito = new ArrayList<>();
    /** Livelli dei 4 pezzi della corazza: elmo, corpetto, gambali, stivali (0-25). */
    public final int[] armorLevels = new int[4];
    /** Skin add-on montata su ciascun pezzo della corazza (id di ArmorSkin; "galeotto" = nessuna). */
    public final String[] armorSkinPieces = {"galeotto", "galeotto", "galeotto", "galeotto"};
    /** Le skin della corazza possedute (v1, set interi) sono già state trasformate in add-on. */
    public boolean armorSkinMigrate;

    // Casse (chiavi virtuali)
    public final Map<String, Integer> keys = new HashMap<>();

    // Booster personale
    public double boostMult = 1;
    public long boostUntil;

    // Varie
    public long lastDaily;
    public long firstJoin;
    public long playtime;      // secondi
    public boolean starterBought;
    public boolean fly;
    public boolean procMessages = true;
    public String gang;
    public long pepiteTrovate; // pepite d'oro trovate
    public long gemmeSpese;
    public long luckyTrovati;

    // Tutorial: -1 = mai iniziato, 0..N-1 passo attuale, N = completato
    public int tutorialStep = -1;
    public int tutorialCount;
    public boolean tutorialDone;

    // Battle pass
    public int bpSeason;
    public double bpXp;
    public boolean bpPremium;
    public final Set<Integer> bpFree = new HashSet<>();
    public final Set<Integer> bpPrem = new HashSet<>();
    public long bpDay = -1, bpWeek = -1;
    public final Map<String, Double> bpDaily = new HashMap<>();
    public final Set<String> bpDailyDone = new HashSet<>();
    public final Map<String, Double> bpWeekly = new HashMap<>();
    public final Set<String> bpWeeklyDone = new HashSet<>();

    // Traguardi dei blocchi già riscossi
    public final Set<Long> traguardi = new HashSet<>();

    // Runtime (non salvato)
    public transient boolean hasPack;
    public transient double secSoldi, secPepite;
    public transient int combo;
    public transient long lastBreak;
    public transient long lastDeniedMsg;
    public transient boolean dirty;
    public transient boolean pickDirty;
    public transient long lastPickRefresh;
    public transient long lastPvpHit;
    public transient java.util.UUID lastPvpAttacker;
    public transient boolean newData;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        pickSkins.add("classico");
        armorSkins.add("galeotto");
    }

    public int ench(String id) {
        return enchants.getOrDefault(id, 0);
    }

    /** Livello effettivo: 0 se l'incantesimo è stato disattivato dal giocatore. */
    public int active(String id) {
        return disabledEnchants.contains(id) ? 0 : enchants.getOrDefault(id, 0);
    }

    public int keys(String crate) {
        return keys.getOrDefault(crate, 0);
    }

    public void addKeys(String crate, int n) {
        keys.merge(crate, n, Integer::sum);
        if (keys.get(crate) <= 0) keys.remove(crate);
        dirty = true;
    }

    public void read(YamlConfiguration y) {
        name = y.getString("nome", name);
        soldi = y.getDouble("soldi");
        pepite = y.getDouble("pepite");
        gemme = y.getLong("gemme");
        rank = y.getInt("rank");
        prestige = y.getInt("prestigio");
        evasioni = y.getInt("evasioni");
        blocchi = y.getLong("blocchi");
        vip = y.getInt("vip");
        ConfigurationSection e = y.getConfigurationSection("incantesimi");
        if (e != null) for (String k : e.getKeys(false)) enchants.put(k, e.getInt(k));
        pickLevel = Math.max(1, y.getInt("piccone.livello", 1));
        pickXp = y.getDouble("piccone.xp");
        pickSkins.addAll(y.getStringList("piccone.skin-possedute"));
        pickSkin = y.getString("piccone.skin", "classico");
        armorSkins.addAll(y.getStringList("armatura.skin-possedute"));
        armorSkin = y.getString("armatura.skin", "galeotto");
        disabledEnchants.addAll(y.getStringList("piccone.disattivati"));
        ConfigurationSection k = y.getConfigurationSection("chiavi");
        if (k != null) for (String c : k.getKeys(false)) keys.put(c, k.getInt(c));
        boostMult = y.getDouble("booster.moltiplicatore", 1);
        boostUntil = y.getLong("booster.fine");
        lastDaily = y.getLong("giornaliero");
        firstJoin = y.getLong("primo-accesso");
        playtime = y.getLong("tempo-gioco");
        starterBought = y.getBoolean("starter-pack");
        fly = y.getBoolean("volo");
        procMessages = y.getBoolean("messaggi-incantesimi", true);
        gang = y.getString("gang");
        pepiteTrovate = y.getLong("pepite-oro-trovate");
        gemmeSpese = y.getLong("gemme-spese");
        quantum = y.getDouble("quantum");
        quantumTasca = y.getDouble("quantum-tasca");
        quantumOra = y.getDouble("quantum-ora.guadagnati");
        quantumOraInizio = y.getLong("quantum-ora.inizio");
        pickTier = y.getInt("piccone.tier", -1);
        skinMigrate = y.getBoolean("piccone.skin-migrate");
        skinDeposito.addAll(y.getStringList("piccone.skin-deposito"));
        armorLevels[0] = y.getInt("armatura.livelli.elmo");
        armorLevels[1] = y.getInt("armatura.livelli.corpetto");
        armorLevels[2] = y.getInt("armatura.livelli.gambali");
        armorLevels[3] = y.getInt("armatura.livelli.stivali");
        List<String> pieces = y.getStringList("armatura.skin-pezzi");
        for (int i = 0; i < 4 && i < pieces.size(); i++) if (pieces.get(i) != null) armorSkinPieces[i] = pieces.get(i);
        armorSkinMigrate = y.getBoolean("armatura.skin-migrate");
        luckyTrovati = y.getLong("lucky-trovati");
        tutorialStep = y.getInt("tutorial.passo", -1);
        tutorialCount = y.getInt("tutorial.contatore");
        tutorialDone = y.getBoolean("tutorial.completato");
        bpSeason = y.getInt("battlepass.stagione");
        bpXp = y.getDouble("battlepass.xp");
        bpPremium = y.getBoolean("battlepass.premium");
        bpFree.addAll(y.getIntegerList("battlepass.riscossi-gratis"));
        bpPrem.addAll(y.getIntegerList("battlepass.riscossi-premium"));
        bpDay = y.getLong("battlepass.giorno", -1);
        bpWeek = y.getLong("battlepass.settimana", -1);
        ConfigurationSection bd = y.getConfigurationSection("battlepass.giornaliere");
        if (bd != null) for (String k2 : bd.getKeys(false)) bpDaily.put(k2, bd.getDouble(k2));
        bpDailyDone.addAll(y.getStringList("battlepass.giornaliere-fatte"));
        ConfigurationSection bw = y.getConfigurationSection("battlepass.settimanali");
        if (bw != null) for (String k2 : bw.getKeys(false)) bpWeekly.put(k2, bw.getDouble(k2));
        bpWeeklyDone.addAll(y.getStringList("battlepass.settimanali-fatte"));
        for (Object o : y.getList("traguardi", List.of())) if (o instanceof Number n) traguardi.add(n.longValue());
    }

    public YamlConfiguration write() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("nome", name);
        y.set("soldi", soldi);
        y.set("pepite", pepite);
        y.set("gemme", gemme);
        y.set("rank", rank);
        y.set("prestigio", prestige);
        y.set("evasioni", evasioni);
        y.set("blocchi", blocchi);
        y.set("vip", vip);
        for (Map.Entry<String, Integer> en : enchants.entrySet()) y.set("incantesimi." + en.getKey(), en.getValue());
        y.set("piccone.livello", pickLevel);
        y.set("piccone.xp", pickXp);
        y.set("piccone.skin-possedute", List.copyOf(pickSkins));
        y.set("piccone.skin", pickSkin);
        y.set("piccone.disattivati", List.copyOf(disabledEnchants));
        y.set("armatura.skin-possedute", List.copyOf(armorSkins));
        y.set("armatura.skin", armorSkin);
        for (Map.Entry<String, Integer> en : keys.entrySet()) y.set("chiavi." + en.getKey(), en.getValue());
        y.set("booster.moltiplicatore", boostMult);
        y.set("booster.fine", boostUntil);
        y.set("giornaliero", lastDaily);
        y.set("primo-accesso", firstJoin);
        y.set("tempo-gioco", playtime);
        y.set("starter-pack", starterBought);
        y.set("volo", fly);
        y.set("messaggi-incantesimi", procMessages);
        y.set("gang", gang);
        y.set("pepite-oro-trovate", pepiteTrovate);
        y.set("gemme-spese", gemmeSpese);
        y.set("quantum", quantum);
        y.set("quantum-tasca", quantumTasca);
        y.set("quantum-ora.guadagnati", quantumOra);
        y.set("quantum-ora.inizio", quantumOraInizio);
        y.set("piccone.tier", pickTier);
        y.set("piccone.skin-migrate", skinMigrate);
        y.set("piccone.skin-deposito", List.copyOf(skinDeposito));
        y.set("armatura.livelli.elmo", armorLevels[0]);
        y.set("armatura.livelli.corpetto", armorLevels[1]);
        y.set("armatura.livelli.gambali", armorLevels[2]);
        y.set("armatura.livelli.stivali", armorLevels[3]);
        y.set("armatura.skin-pezzi", List.of(armorSkinPieces));
        y.set("armatura.skin-migrate", armorSkinMigrate);
        y.set("lucky-trovati", luckyTrovati);
        y.set("tutorial.passo", tutorialStep);
        y.set("tutorial.contatore", tutorialCount);
        y.set("tutorial.completato", tutorialDone);
        y.set("battlepass.stagione", bpSeason);
        y.set("battlepass.xp", bpXp);
        y.set("battlepass.premium", bpPremium);
        y.set("battlepass.riscossi-gratis", new ArrayList<>(bpFree));
        y.set("battlepass.riscossi-premium", new ArrayList<>(bpPrem));
        y.set("battlepass.giorno", bpDay);
        y.set("battlepass.settimana", bpWeek);
        for (Map.Entry<String, Double> en : bpDaily.entrySet()) y.set("battlepass.giornaliere." + en.getKey(), en.getValue());
        y.set("battlepass.giornaliere-fatte", List.copyOf(bpDailyDone));
        for (Map.Entry<String, Double> en : bpWeekly.entrySet()) y.set("battlepass.settimanali." + en.getKey(), en.getValue());
        y.set("battlepass.settimanali-fatte", List.copyOf(bpWeeklyDone));
        y.set("traguardi", new ArrayList<>(traguardi));
        return y;
    }
}

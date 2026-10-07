package it.pepita.core.pickaxe;

import org.bukkit.Material;

/** Incantesimi personalizzati del piccone. I costi sono in Pepite (✦), quelli della categoria Quantum in Quantum (⚛). */
public enum Enchant {
    // ---- Utilità ----
    EFFICIENZA("efficienza", "Efficienza", Material.GOLDEN_PICKAXE, Cat.UTILITA, 30, 40, 10, 1.10, 0,
            "Scavi più veloce."),
    RAPIDITA("rapidita", "Rapidità", Material.SUGAR, Cat.UTILITA, 3, 500, 0, 4.0, 0,
            "Effetto Rapidità mentre tieni il piccone."),
    VELOCITA("velocita", "Velocità", Material.FEATHER, Cat.UTILITA, 3, 400, 0, 4.0, 0,
            "Corri più veloce con il piccone in mano."),
    SALTO("salto", "Salto", Material.RABBIT_FOOT, Cat.UTILITA, 3, 300, 0, 4.0, 0,
            "Salti più in alto."),
    VISIONE("visione", "Visione Notturna", Material.ENDER_EYE, Cat.UTILITA, 1, 800, 0, 1.0, 0,
            "Vedi al buio."),
    VOLO("volo", "Volo", Material.ELYTRA, Cat.UTILITA, 1, 150000, 0, 1.0, 1,
            "Puoi volare nelle miniere e allo spawn (/fly)."),

    // ---- Economia ----
    FORTUNA("fortuna", "Fortuna", Material.EMERALD, Cat.ECONOMIA, 5000, 15, 2, 1.0005, 0,
            "+2% soldi per livello su ogni blocco."),
    CERCAPEPITE("cercapepite", "Cercapepite", Material.GOLD_NUGGET, Cat.ECONOMIA, 1000, 25, 3, 1.002, 0,
            "Trovi più Pepite mentre scavi."),
    MERCANTE("mercante", "Mercante", Material.GOLD_INGOT, Cat.ECONOMIA, 100, 300, 50, 1.03, 0,
            "Possibilità di vendere 200 blocchi in un colpo."),
    CERCACHIAVI("cercachiavi", "Cercachiavi", Material.TRIPWIRE_HOOK, Cat.ECONOMIA, 100, 500, 100, 1.04, 0,
            "Possibilità di trovare chiavi delle casse."),
    FORTUNATO("fortunato", "Fortunato", Material.TOTEM_OF_UNDYING, Cat.ECONOMIA, 100, 600, 120, 1.04, 0,
            "Premi a sorpresa: soldi, pepite, gemme, chiavi."),
    BENEFICENZA("beneficenza", "Beneficenza", Material.CAKE, Cat.ECONOMIA, 50, 800, 150, 1.05, 0,
            "Regala soldi a tutti i giocatori online."),
    BENEDIZIONE("benedizione", "Benedizione", Material.AMETHYST_SHARD, Cat.ECONOMIA, 50, 1000, 200, 1.05, 0,
            "Regala pepite a tutti i giocatori online."),
    SAGGEZZA("saggezza", "Saggezza", Material.EXPERIENCE_BOTTLE, Cat.ECONOMIA, 100, 200, 40, 1.03, 0,
            "Il piccone sale di livello più in fretta."),
    MOLTIPLICATORE("moltiplicatore", "Moltiplicatore", Material.BLAZE_POWDER, Cat.ECONOMIA, 50, 2500, 400, 1.06, 0,
            "Attiva un booster x2 soldi per 60 secondi."),
    CERCABOMBE("cercabombe", "Cercabombe", Material.GUNPOWDER, Cat.ECONOMIA, 50, 1500, 250, 1.06, 0,
            "Trovi bombe da lanciare nelle miniere."),
    FIUTO("fiuto", "Fiuto d'Oro", Material.RAW_GOLD, Cat.ECONOMIA, 25, 5000, 1000, 1.10, 0,
            "Più probabilità di trovare la leggendaria Pepita d'Oro."),

    // ---- Distruzione ----
    ESPLOSIVO("esplosivo", "Esplosivo", Material.TNT, Cat.AREA, 50, 250, 60, 1.06, 0,
            "Fa esplodere i blocchi intorno."),
    MARTELLO("martello", "Martello Pneumatico", Material.ANVIL, Cat.AREA, 50, 1500, 200, 1.07, 0,
            "Distrugge un intero strato della miniera."),
    LASER("laser", "Laser", Material.END_ROD, Cat.AREA, 50, 1200, 150, 1.07, 0,
            "Spara un raggio che taglia la miniera."),
    TRIVELLA("trivella", "Trivella", Material.POINTED_DRIPSTONE, Cat.AREA, 50, 1000, 150, 1.07, 0,
            "Scava un pozzo 3x3 fino al fondo."),
    FULMINE("fulmine", "Fulmine", Material.LIGHTNING_ROD, Cat.AREA, 50, 2000, 300, 1.07, 0,
            "Un fulmine colpisce la miniera."),
    METEORA("meteora", "Meteora", Material.FIRE_CHARGE, Cat.AREA, 25, 8000, 1500, 1.09, 3,
            "Una pioggia di meteore cade sulla miniera."),
    NUKE("nuke", "Nuke", Material.NETHER_STAR, Cat.AREA, 5, 250000, 0, 3.0, 10,
            "Distrugge l'INTERA miniera."),

    // ---- Speciali ----
    FURIA("furia", "Furia", Material.BLAZE_ROD, Cat.SPECIALE, 10, 6000, 3000, 1.25, 1,
            "Più blocchi di fila = più soldi (fino a +10%/lv)."),
    SINERGIA("sinergia", "Sinergia", Material.LEAD, Cat.SPECIALE, 10, 20000, 5000, 1.30, 2,
            "Un incantesimo può attivarne un altro."),
    ECO("eco", "Eco", Material.ECHO_SHARD, Cat.SPECIALE, 10, 25000, 6000, 1.30, 2,
            "Ripete l'ultimo incantesimo attivato."),
    CAOS("caos", "Caos", Material.END_CRYSTAL, Cat.SPECIALE, 10, 50000, 10000, 1.35, 5,
            "Attiva incantesimi che ancora non hai."),

    // ---- Quantum (si pagano in Quantum, che si trovano solo nella miniera PvP) ----
    FRANTUMAZIONE("frantumazione", "Frantumazione", Material.AMETHYST_CLUSTER, Cat.QUANTUM, 25, 30, 6, 1.12, 0,
            "Rompi più in fretta i blocchi duri della miniera PvP."),
    SUPERPOSIZIONE("superposizione", "Superposizione", Material.ENDER_PEARL, Cat.QUANTUM, 50, 40, 8, 1.10, 0,
            "Possibilità di raddoppiare tutte le ricompense di un blocco."),
    OSSERVATORE("osservatore", "Osservatore", Material.SPYGLASS, Cat.QUANTUM, 50, 35, 6, 1.10, 0,
            "Piccola possibilità di trovare Quantum nelle miniere normali."),
    RISONANZA("risonanza", "Risonanza", Material.BELL, Cat.QUANTUM, 20, 60, 15, 1.16, 0,
            "+2,5% a tutte le probabilità degli incantesimi per livello."),
    TUNNEL("tunnel", "Tunnel", Material.TNT_MINECART, Cat.QUANTUM, 30, 50, 10, 1.12, 0,
            "Scava di colpo un cubo 5x5x5."),
    COLLASSO("collasso", "Collasso", Material.RESPAWN_ANCHOR, Cat.QUANTUM, 10, 400, 100, 1.35, 0,
            "Fa crollare circa il 30% della miniera.");

    public enum Cat {
        UTILITA("<#55E6FF>Utilità"), ECONOMIA("<#6BE36B>Economia"), AREA("<#FF7B7B>Distruzione"), SPECIALE("<#D17BFF>Speciali"),
        QUANTUM("<#3FE0F0>Quantum");
        public final String label;

        Cat(String label) {
            this.label = label;
        }
    }

    public final String id;
    public final String display;
    public final Material icon;
    public final Cat cat;
    public final int max;
    public final double base, inc, growth;
    public final int minPrestige;
    public final String desc;

    Enchant(String id, String display, Material icon, Cat cat, int max, double base, double inc, double growth,
            int minPrestige, String desc) {
        this.id = id;
        this.display = display;
        this.icon = icon;
        this.cat = cat;
        this.max = max;
        this.base = base;
        this.inc = inc;
        this.growth = growth;
        this.minPrestige = minPrestige;
        this.desc = desc;
    }

    /** Costo per passare da "level" a level+1. */
    public double cost(int level) {
        return (base + inc * level) * Math.pow(growth, level);
    }

    public static Enchant byId(String id) {
        for (Enchant e : values()) if (e.id.equalsIgnoreCase(id)) return e;
        return null;
    }

    public boolean isArea() {
        return cat == Cat.AREA;
    }

    /** Si paga in Quantum invece che in Pepite. */
    public boolean quantum() {
        return cat == Cat.QUANTUM;
    }
}

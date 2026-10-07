package it.pepita.core.cosmetics;

/**
 * Skin del piccone. Dalla v2 sono OGGETTI separati (carta con custom_model_data "piccone_&lt;id&gt;"), scambiabili:
 * si montano trascinandole sul piccone e danno un bonus base secondo la rarità, moltiplicato dal tier del piccone.
 */
public enum PickaxeSkin {
    CLASSICO("classico", "Classico", null, Rarity.NESSUNA, 0, "Il piccone senza skin.", false),
    // ---- vecchie skin 2D ----
    PEPITA("pepita", "Pepita d'Oro", "piccone_pepita", Rarity.LEGGENDARIA, 1500, "Oro puro con pepite che brillano.", false),
    PIZZA("pizza", "Pizza Margherita", "piccone_pizza", Rarity.RARA, 300, "Pomodoro, mozzarella e basilico.", false),
    FORCHETTONE("forchettone", "Forchettone", "piccone_forchettone", Rarity.RARA, 300, "Una forchetta gigante piena di spaghetti.", false),
    ARCOBALENO("arcobaleno", "Arcobaleno", "piccone_arcobaleno", Rarity.EPICA, 450, "Cambia colore di continuo.", false),
    LAVA("lava", "Lava Viva", "piccone_lava", Rarity.EPICA, 450, "Lava che scorre sulla lama.", false),
    GHIACCIO("ghiaccio", "Ghiaccio Eterno", "piccone_ghiaccio", Rarity.RARA, 300, "Non si scioglie mai.", false),
    NEON("neon", "Neon Cyber", "piccone_neon", Rarity.EPICA, 450, "Luci al neon pulsanti.", false),
    GALASSIA("galassia", "Galassia", "piccone_galassia", Rarity.EPICA, 600, "Stelle che brillano nello spazio.", false),
    DRAGO("drago", "Osso di Drago", "piccone_drago", Rarity.RARA, 300, "Ricavato dalle ossa di un drago.", false),
    CARAMELLA("caramella", "Lecca-Lecca", "piccone_caramella", Rarity.COMUNE, 250, "Dolce, a strisce, appiccicoso.", false),
    BAGUETTE("baguette", "Filone di Pane", "piccone_pane", Rarity.COMUNE, 250, "Appena sfornato.", false),
    // ---- nuove skin 3D ----
    AUREO("aureo", "Piccone Aureo", "piccone_aureo", Rarity.LEGGENDARIA, 1400, "Forgiato nell'oro del tesoro della prigione.", true),
    SMERALDO("smeraldo", "Piccone di Smeraldo", "piccone_smeraldo", Rarity.EPICA, 800, "Gemme verdi incastonate nell'acciaio.", true),
    GLACIALE("glaciale", "Piccone Glaciale", "piccone_glaciale", Rarity.EPICA, 800, "Ghiaccio perenne dal cuore delle montagne.", true),
    INFERNO("inferno", "Piccone Infernale", "piccone_inferno", Rarity.LEGGENDARIA, 1400, "Brucia ancora delle fiamme del Nether.", true),
    AMETISTA("ametista", "Piccone d'Ametista", "piccone_ametista", Rarity.EPICA, 800, "Cristalli viola che risuonano a ogni colpo.", true),
    ABISSO("abisso", "Piccone dell'Abisso", "piccone_abisso", Rarity.MITICA, 2200, "Viene dal fondo dove nessuno scava.", true),
    FARAONE("faraone", "Piccone del Faraone", "piccone_faraone", Rarity.LEGGENDARIA, 1400, "Sabbia, oro e lapislazzuli dell'antico Egitto.", true),
    SAKURA("sakura", "Piccone Sakura", "piccone_sakura", Rarity.RARA, 500, "Petali di ciliegio che non cadono mai.", true),
    TEMPESTA("tempesta", "Piccone della Tempesta", "piccone_tempesta", Rarity.MITICA, 2200, "Scaglie di fulmine imprigionate nel metallo.", true),
    ECLISSE("eclisse", "Piccone dell'Eclisse", "piccone_eclisse", Rarity.MITICA, 2200, "Sole e luna sulla stessa lama.", true);

    /** Rarità e bonus base (frazioni: 0.05 = +5%). */
    public enum Rarity {
        NESSUNA("<gray>Comune", "<gray>", 0, 0, 0),
        COMUNE("<white>Comune", "<white>", 0.02, 0.01, 0),
        RARA("<#55E6FF>Rara", "<#55E6FF>", 0.04, 0.02, 0),
        EPICA("<#D17BFF>Epica", "<#D17BFF>", 0.06, 0.04, 0.01),
        LEGGENDARIA("<gold>Leggendaria", "<gold>", 0.09, 0.06, 0.02),
        MITICA("<gradient:#FF7BD1:#7BE3FF>Mitica</gradient>", "<#FF7BD1>", 0.12, 0.08, 0.04);

        public final String label, color;
        public final double soldi, pepite, quantum;

        Rarity(String label, String color, double soldi, double pepite, double quantum) {
            this.label = label;
            this.color = color;
            this.soldi = soldi;
            this.pepite = pepite;
            this.quantum = quantum;
        }
    }

    public final String id, display, model, rarity, desc;
    public final Rarity rar;
    public final int price;
    public final boolean is3d;

    PickaxeSkin(String id, String display, String model, Rarity rar, int price, String desc, boolean is3d) {
        this.id = id;
        this.display = display;
        this.model = model;
        this.rar = rar;
        this.rarity = rar.label;
        this.price = price;
        this.desc = desc;
        this.is3d = is3d;
    }

    public static PickaxeSkin byId(String id) {
        for (PickaxeSkin s : values()) if (s.id.equalsIgnoreCase(id)) return s;
        return CLASSICO;
    }

    public static PickaxeSkin byIdOrNull(String id) {
        if (id == null) return null;
        for (PickaxeSkin s : values()) if (s.id.equalsIgnoreCase(id)) return s;
        return null;
    }
}

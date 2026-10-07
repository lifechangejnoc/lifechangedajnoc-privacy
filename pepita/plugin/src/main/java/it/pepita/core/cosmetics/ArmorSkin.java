package it.pepita.core.cosmetics;

import org.bukkit.Color;

/**
 * Aspetto della corazza (asset "equipment" del resource pack "pepita"). I 10 set nuovi hanno anche i modelli 3D
 * degli oggetti (&lt;set&gt;_helmet, _chestplate, _leggings, _boots su leather_*).
 */
public enum ArmorSkin {
    GALEOTTO("galeotto", "Tuta da Galeotto", Color.fromRGB(0xFF7A1A), "<gray>Base", 0, "La divisa arancione di ogni nuovo detenuto.", false),
    RIGHE("righe", "Divisa a Righe", Color.fromRGB(0xEEEEEE), "<aqua>Rara", 400, "Il classico pigiama a strisce da carcerato.", false),
    PEPITA("pepita", "Armatura di Pepita", Color.fromRGB(0xFFC83D), "<gold>Leggendaria", 1800, "Placcata in oro e pepite.", false),
    NEON("neon", "Armatura Neon", Color.fromRGB(0xFF2BD6), "<#55E6FF>Epica", 600, "Strisce luminose fucsia e ciano.", false),
    LAVA("lava", "Armatura di Lava", Color.fromRGB(0xFF5A00), "<#55E6FF>Epica", 600, "Roccia vulcanica incandescente.", false),
    GHIACCIO("ghiaccio", "Armatura di Ghiaccio", Color.fromRGB(0x9FE8FF), "<aqua>Rara", 400, "Cristalli di ghiaccio eterno.", false),
    GALASSIA("galassia", "Armatura Galattica", Color.fromRGB(0x3A1D6E), "<#55E6FF>Epica", 700, "Un pezzo di cielo stellato.", false),
    GUARDIA("guardia", "Uniforme da Guardia", Color.fromRGB(0x23324F), "<#D17BFF>Speciale", 900, "Hai cambiato lato delle sbarre.", false),
    // ---- set 3D ----
    AUREO("aureo", "Corazza Aurea", Color.fromRGB(0xF2C230), "<gold>Leggendaria", 1600, "Piastre d'oro cesellate a mano.", true),
    SMERALDO("smeraldo", "Corazza di Smeraldo", Color.fromRGB(0x2FCB6A), "<#D17BFF>Epica", 1000, "Scaglie verdi che brillano al sole.", true),
    GLACIALE("glaciale", "Corazza Glaciale", Color.fromRGB(0x8FDFFF), "<#D17BFF>Epica", 1000, "Ghiaccio che non si scioglie mai.", true),
    INFERNO("inferno", "Corazza Infernale", Color.fromRGB(0xD6361A), "<gold>Leggendaria", 1600, "Ferro rovente del Nether.", true),
    AMETISTA("ametista", "Corazza d'Ametista", Color.fromRGB(0x9B5FD9), "<#D17BFF>Epica", 1000, "Cristalli viola che cantano.", true),
    ABISSO("abisso", "Corazza dell'Abisso", Color.fromRGB(0x152A4A), "<gradient:#FF7BD1:#7BE3FF>Mitica</gradient>", 2400, "Forgiata nelle profondità.", true),
    FARAONE("faraone", "Corazza del Faraone", Color.fromRGB(0xE0B04A), "<gold>Leggendaria", 1600, "Oro e lapislazzuli dell'antico Egitto.", true),
    SAKURA("sakura", "Corazza Sakura", Color.fromRGB(0xF7A8C8), "<#55E6FF>Rara", 700, "Petali di ciliegio e seta.", true),
    TEMPESTA("tempesta", "Corazza della Tempesta", Color.fromRGB(0x4A6CD9), "<gradient:#FF7BD1:#7BE3FF>Mitica</gradient>", 2400, "Fulmini imprigionati nell'acciaio.", true),
    ECLISSE("eclisse", "Corazza dell'Eclisse", Color.fromRGB(0x2A1A3A), "<gradient:#FF7BD1:#7BE3FF>Mitica</gradient>", 2400, "Metà sole, metà luna.", true);

    public final String id, display, rarity, desc;
    public final Color color;
    public final int price;
    /** Ha i modelli 3D degli oggetti nel resource pack. */
    public final boolean model;

    ArmorSkin(String id, String display, Color color, String rarity, int price, String desc, boolean model) {
        this.id = id;
        this.display = display;
        this.color = color;
        this.rarity = rarity;
        this.price = price;
        this.desc = desc;
        this.model = model;
    }

    public static ArmorSkin byId(String id) {
        for (ArmorSkin s : values()) if (s.id.equalsIgnoreCase(id)) return s;
        return GALEOTTO;
    }

    public static ArmorSkin byIdOrNull(String id) {
        if (id == null) return null;
        for (ArmorSkin s : values()) if (s.id.equalsIgnoreCase(id)) return s;
        return null;
    }
}

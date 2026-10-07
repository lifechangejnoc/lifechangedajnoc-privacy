package it.pepita.core.rank;

/** Rank acquistabili con le gemme. */
public enum VipTier {
    NESSUNO(0, "", "Nessuno", 0, 0, 0),
    VIP(1, "<#55FF7F><b>VIP</b></#55FF7F>", "VIP", 500, 0.10, 0.10),
    VIP_PLUS(2, "<#55E6FF><b>VIP+</b></#55E6FF>", "VIP+", 1100, 0.20, 0.20),
    ELITE(3, "<#D17BFF><b>ELITE</b></#D17BFF>", "ELITE", 2400, 0.35, 0.30),
    LEGGENDA(4, "<gradient:#FFE259:#FFA751><b>LEGGENDA</b></gradient>", "LEGGENDA", 6500, 0.50, 0.50);

    public final int level;
    public final String tag;
    public final String plain;
    public final int price;
    public final double soldiBonus;
    public final double pepiteBonus;

    VipTier(int level, String tag, String plain, int price, double soldiBonus, double pepiteBonus) {
        this.level = level;
        this.tag = tag;
        this.plain = plain;
        this.price = price;
        this.soldiBonus = soldiBonus;
        this.pepiteBonus = pepiteBonus;
    }

    public static VipTier of(int level) {
        for (VipTier t : values()) if (t.level == level) return t;
        return NESSUNO;
    }

    public String perks() {
        return switch (this) {
            case NESSUNO -> "";
            case VIP -> "+10% soldi, +10% pepite, chiave Rara nel giornaliero";
            case VIP_PLUS -> "+20% soldi, +20% pepite, /fly in spawn e miniere";
            case ELITE -> "+35% soldi, +30% pepite, Miniera VIP, chiave Leggendaria nel giornaliero";
            case LEGGENDA -> "+50% soldi, +50% pepite, giornaliero doppio, tag animato";
        };
    }
}

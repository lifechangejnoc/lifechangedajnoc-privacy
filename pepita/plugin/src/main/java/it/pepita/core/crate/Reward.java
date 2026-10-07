package it.pepita.core.crate;

/** Un premio di una cassa. */
public record Reward(Type type, double amount, String arg, int weight) {
    public enum Type { SOLDI, PEPITE, GEMME, CHIAVE, BOMBA, SKIN_PICCONE, SKIN_ARMATURA, BOOSTER, INCANTESIMO, PEPITA_ORO, QUANTUM }

    /** amount = minuti di scavo del giocatore */
    public static Reward soldi(double minutes, int w) { return new Reward(Type.SOLDI, minutes, null, w); }
    public static Reward quantum(double n, int w) { return new Reward(Type.QUANTUM, n, null, w); }
    public static Reward pepite(double n, int w) { return new Reward(Type.PEPITE, n, null, w); }
    public static Reward gemme(int n, int w) { return new Reward(Type.GEMME, n, null, w); }
    public static Reward chiave(String crate, int n, int w) { return new Reward(Type.CHIAVE, n, crate, w); }
    public static Reward bomba(String tipo, int n, int w) { return new Reward(Type.BOMBA, n, tipo, w); }
    public static Reward skinPiccone(String id, int w) { return new Reward(Type.SKIN_PICCONE, 1, id, w); }
    public static Reward skinArmatura(String id, int w) { return new Reward(Type.SKIN_ARMATURA, 1, id, w); }
    /** amount = minuti, arg = moltiplicatore */
    public static Reward booster(double mult, int minutes, int w) { return new Reward(Type.BOOSTER, minutes, String.valueOf(mult), w); }
    public static Reward incantesimo(String id, int levels, int w) { return new Reward(Type.INCANTESIMO, levels, id, w); }
    public static Reward pepitaOro(int w) { return new Reward(Type.PEPITA_ORO, 1, null, w); }
}

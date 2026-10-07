package it.pepita.core.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Chiavi per i PersistentDataContainer. */
public final class Keys {
    private Keys() {}

    /** Tipo di oggetto speciale: piccone, menu, bomba, pepita_oro, divisa... */
    public static NamespacedKey ITEM;
    /** Proprietario (UUID) di un oggetto legato all'anima. */
    public static NamespacedKey OWNER;
    /** Entità ologramma creata dal plugin. */
    public static NamespacedKey HOLO;
    /** Bomba lanciata. */
    public static NamespacedKey BOMB;
    /** Modificatore di velocità di scavo nella miniera PvP. */
    public static NamespacedKey PVP_SPEED;
    /** NPC del tutorial. */
    public static NamespacedKey NPC;
    /** Oggetti generati dal lucky block (polli della trappola ecc.). */
    public static NamespacedKey LUCKY;

    public static void init(Plugin p) {
        ITEM = new NamespacedKey(p, "oggetto");
        OWNER = new NamespacedKey(p, "proprietario");
        HOLO = new NamespacedKey(p, "ologramma");
        BOMB = new NamespacedKey(p, "bomba");
        PVP_SPEED = new NamespacedKey(p, "scavo_pvp");
        NPC = new NamespacedKey(p, "npc");
        LUCKY = new NamespacedKey(p, "lucky");
    }
}

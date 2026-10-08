package it.pepita.lobby.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Chiavi per i PersistentDataContainer. */
public final class Keys {
    private Keys() {}

    /** Tipo di oggetto speciale (bussola delle modalità). */
    public static NamespacedKey ITEM;
    /** Proprietario (non usato nella lobby, serve a ItemBuilder). */
    public static NamespacedKey OWNER;
    /** Entità creata dal plugin (NPC, scritte). */
    public static NamespacedKey HOLO;
    /** NPC di una modalità: valore = id della modalità. */
    public static NamespacedKey NPC;

    public static void init(Plugin p) {
        ITEM = new NamespacedKey(p, "oggetto");
        OWNER = new NamespacedKey(p, "proprietario");
        HOLO = new NamespacedKey(p, "ologramma");
        NPC = new NamespacedKey(p, "npc");
    }
}

package it.pepita.lobby;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parla con il proxy sul canale "BungeeCord" (Velocity lo supporta con bungee-plugin-message-channel = true):
 * Connect per mandare un giocatore a un altro server, PlayerCount per sapere quanti giocano in ogni modalità.
 */
public final class Network implements PluginMessageListener {
    public static final String CHANNEL = "BungeeCord";
    /** Totale della rete (tutti i server). */
    public static final String ALL = "ALL";

    private final PepitaLobby plugin;
    private final Map<String, Integer> counts = new ConcurrentHashMap<>();

    public Network(PepitaLobby plugin) {
        this.plugin = plugin;
    }

    public void connect(Player p, String server) {
        send(p, "Connect", server);
    }

    /** Giocatori su un server (o ALL); -1 se il proxy non ha ancora risposto. */
    public int count(String server) {
        return counts.getOrDefault(server, -1);
    }

    /** Totale della rete, o almeno i giocatori di questo server se il proxy non ha risposto. */
    public int total() {
        int n = count(ALL);
        return n < 0 ? Bukkit.getOnlinePlayers().size() : n;
    }

    /** Chiede i conteggi al proxy (serve un giocatore online per mandare il messaggio). */
    public void refresh() {
        Player any = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (any == null) return;
        send(any, "PlayerCount", ALL);
        for (Modes.Mode m : plugin.modes().all()) if (m.ready()) send(any, "PlayerCount", m.server());
    }

    private void send(Player via, String sub, String arg) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF(sub);
            out.writeUTF(arg);
            via.sendPluginMessage(plugin, CHANNEL, b.toByteArray());
        } catch (IOException ex) {
            plugin.getLogger().warning("Messaggio al proxy non riuscito (" + sub + "): " + ex.getMessage());
        }
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (!CHANNEL.equals(channel)) return;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String sub = in.readUTF();
            if (sub.equals("PlayerCount")) {
                String server = in.readUTF();
                counts.put(server, in.readInt());
            }
        } catch (IOException ignored) {
            // messaggi di altri sottocanali: non ci interessano
        }
    }
}

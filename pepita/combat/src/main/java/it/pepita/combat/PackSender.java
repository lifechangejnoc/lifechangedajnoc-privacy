package it.pepita.combat;

import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Invia il pack dei suoni 1.8 IN AGGIUNTA al pack di Pepita (replace false) e, se manca, calcola lo SHA-1 scaricando lo zip.
 */
public final class PackSender {
    private final PepitaCombat plugin;
    private volatile String hash;

    public PackSender(PepitaCombat plugin) {
        this.plugin = plugin;
        String url = url();
        String sha1 = plugin.getConfig().getString("resource-pack.sha1", "");
        if (sha1 != null && sha1.trim().length() == 40) hash = sha1.trim().toLowerCase();
        else if (!url.isBlank()) Bukkit.getScheduler().runTaskAsynchronously(plugin, this::computeHash);
    }

    private String url() {
        String u = plugin.getConfig().getString("resource-pack.url", "");
        return u == null ? "" : u.trim();
    }

    private void computeHash() {
        try {
            HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(15)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url())).timeout(Duration.ofSeconds(60)).GET().build();
            HttpResponse<InputStream> res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (res.statusCode() != 200) {
                plugin.getLogger().warning("Resource pack: il link risponde " + res.statusCode() + ". Controlla che sia un link DIRETTO allo zip.");
                return;
            }
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            long total = 0;
            try (InputStream in = res.body()) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) {
                    md.update(buf, 0, n);
                    total += n;
                    if (total > 250L * 1024 * 1024) throw new IllegalStateException("file troppo grande");
                }
            }
            hash = HexFormat.of().formatHex(md.digest());
            plugin.getLogger().info("Resource pack verificato (" + total / 1024 + " KB), SHA-1 " + hash);
        } catch (Exception e) {
            plugin.getLogger().warning("Resource pack: impossibile scaricarlo per calcolare lo SHA-1 (" + e.getMessage() + "). Verrà inviato comunque.");
        }
    }

    public void send(Player p) {
        String url = url();
        if (url.isBlank()) return;
        boolean req = plugin.getConfig().getBoolean("resource-pack.obbligatorio", false);
        try {
            ResourcePackInfo.Builder info = ResourcePackInfo.resourcePackInfo()
                    .id(UUID.nameUUIDFromBytes(("pepita-combat:" + url).getBytes())).uri(URI.create(url));
            if (hash != null) info.hash(hash);
            p.sendResourcePacks(ResourcePackRequest.resourcePackRequest().packs(info.build()).required(req).replace(false)
                    .prompt(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(plugin.getConfig().getString("resource-pack.messaggio",
                            "<gold>Pepita</gold> <gray>usa i suoni dei colpi della 1.8!"))));
        } catch (Exception ex) {
            plugin.getLogger().warning("Resource pack non valido: " + ex.getMessage());
        }
    }
}

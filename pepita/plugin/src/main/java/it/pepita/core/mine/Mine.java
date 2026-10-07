package it.pepita.core.mine;

import it.pepita.core.data.PlayerData;
import it.pepita.core.rank.RankManager;
import it.pepita.core.rank.VipTier;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Una miniera: area cubica che si riempie da sola. */
public final class Mine {
    public final String id;
    public String name;
    public Material icon;
    public String theme;
    /** Nome del mondo della miniera (pepita_miniere o pepita_pvp). */
    public String world = it.pepita.core.world.Layout.W_MINES;

    // Interno della miniera (incluso)
    public int minX, minY, minZ, maxX, maxY, maxZ;
    public double spawnX, spawnY, spawnZ;
    public float spawnYaw;
    public double labelX, labelY, labelZ;

    public final List<Material> mats = new ArrayList<>();
    public final List<Integer> weights = new ArrayList<>();

    public int reqRank, reqPrestige, reqEvasioni, reqVip;
    public boolean pvp;

    // Stato
    public int total, remaining;
    public long lastReset;

    private BlockData[] cache;
    private int[] cumulative;

    public Mine(String id) {
        this.id = id;
    }

    public int volume() {
        return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public int centerX() {
        return (minX + maxX) / 2;
    }

    public int centerZ() {
        return (minZ + maxZ) / 2;
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean in(World w) {
        return w != null && w.getName().equals(world);
    }

    public boolean contains(Location l) {
        return in(l.getWorld()) && contains(l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    /** Giocatore dentro la buca della miniera (anche sopra i blocchi). */
    public boolean inPit(Location l) {
        return in(l.getWorld()) && l.getX() >= minX && l.getX() < maxX + 1 && l.getZ() >= minZ && l.getZ() < maxZ + 1
                && l.getY() >= minY - 1 && l.getY() <= maxY + 3;
    }

    /** Zona della miniera compreso il bordo (per il volo e per la PvP). */
    public boolean inArea(Location l) {
        int m = pvp ? 40 : 0;
        return in(l.getWorld()) && l.getX() >= minX - 7 - m && l.getX() < maxX + 8 + m && l.getZ() >= minZ - 7 - m && l.getZ() < maxZ + 8 + m
                && l.getY() >= minY - 2 && l.getY() <= maxY + 25 + m;
    }


    public Location spawn(World w) {
        return new Location(w, spawnX, spawnY, spawnZ, spawnYaw, 0);
    }

    public boolean canAccess(PlayerData d) {
        if (d.evasioni < reqEvasioni) return false;
        // chi è evaso conserva l'accesso alle miniere del prestigio
        if (d.prestige < reqPrestige && d.evasioni <= reqEvasioni) return false;
        if (d.rank < reqRank) return false;
        return d.vip >= reqVip;
    }

    public String requirement() {
        List<String> parts = new ArrayList<>();
        if (reqEvasioni > 0) parts.add("Evasione " + reqEvasioni);
        if (reqPrestige > 0) parts.add("Prestigio " + reqPrestige);
        if (reqRank > 0) parts.add("Rank " + RankManager.letter(reqRank));
        if (reqVip > 0) parts.add(VipTier.of(reqVip).plain);
        return parts.isEmpty() ? "Nessuno" : String.join(" + ", parts);
    }

    /** Valore medio di un blocco di questa miniera. */
    public double avgValue() {
        if (avg >= 0) return avg;
        double tot = 0, w = 0;
        for (int i = 0; i < mats.size(); i++) {
            tot += Prices.of(mats.get(i)) * weights.get(i);
            w += weights.get(i);
        }
        avg = w == 0 ? 0 : tot / w;
        return avg;
    }

    private double avg = -1;

    public void invalidate() {
        cache = null;
        avg = -1;
    }

    private static BlockData goldData;

    private static BlockData gold() {
        if (goldData == null) goldData = Prices.PEPITA_GIGANTE.createBlockData();
        return goldData;
    }

    public BlockData pick(boolean goldRush) {
        if (cache == null) {
            cache = new BlockData[mats.size()];
            cumulative = new int[mats.size()];
            int sum = 0;
            for (int i = 0; i < mats.size(); i++) {
                cache[i] = mats.get(i).createBlockData();
                sum += weights.get(i);
                cumulative[i] = sum;
            }
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (goldRush && r.nextInt(100) < 3) return gold();
        int roll = r.nextInt(Math.max(1, cumulative[cumulative.length - 1]));
        for (int i = 0; i < cumulative.length; i++) if (roll < cumulative[i]) return cache[i];
        return cache[cache.length - 1];
    }
}

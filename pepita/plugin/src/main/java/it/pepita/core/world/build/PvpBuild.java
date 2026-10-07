package it.pepita.core.world.build;

import it.pepita.core.mine.Mine;
import it.pepita.core.world.BuildPlan;

import static it.pepita.core.world.Layout.*;

/** Miniera PvP dei Quantum (versione base, arricchita nella fase delle costruzioni). */
public final class PvpBuild {
    private PvpBuild() {}

    public static void build(BuildPlan p, Mine m) {
        int x1 = m.minX - 1, x2 = m.maxX + 1, z1 = m.minZ - 1, z2 = m.maxZ + 1;
        for (int x = x1 - 8; x <= x2 + 8; x++)
            for (int z = z1 - 8; z <= Math.max(z2 + 8, (int) PVP_SPAWN[2] + 8); z++) {
                boolean pit = x > x1 && x < x2 && z > z1 && z < z2;
                if (!pit) p.set(x, m.maxY + 1, z, "minecraft:polished_blackstone_bricks");
            }
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                p.set(x, m.minY - 1, z, "minecraft:blackstone");
                if (x == x1 || x == x2 || z == z1 || z == z2)
                    for (int y = m.minY; y <= m.maxY; y++) p.set(x, y, z, "minecraft:basalt[axis=y]");
            }
    }
}

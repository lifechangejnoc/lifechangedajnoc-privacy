package it.pepita.core.world.build;

import it.pepita.core.world.BuildPlan;
import it.pepita.core.world.CellGeometry;

import static it.pepita.core.world.Layout.*;
import static it.pepita.core.world.build.BuildUtil.*;

/** Struttura del colosseo (versione base, arricchita nella fase delle costruzioni). */
final class Colosseum {
    private Colosseum() {}

    static void build(BuildPlan p) {
        int R = 49;
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++) {
                double r = CellGeometry.radius(x, z);
                if (r >= 48.5) continue;
                p.set(x, COL_ARENA_Y, z, r < 33 ? "minecraft:smooth_sandstone" : "minecraft:polished_andesite");
                for (int f = 0; f < CELL_FLOORS; f++) {
                    int y0 = CELL_Y0 + f * CELL_FH;
                    if (r >= BALCONY_IN && r < CELL_RIN) p.set(x, y0, z, "minecraft:polished_andesite");
                    if (r >= BALCONY_IN && r < BALCONY_IN + 1) p.set(x, y0 + 1, z, "minecraft:stone_brick_wall");
                }
                if (r >= CELL_RIN && r < CELL_ROUT) p.set(x, ROOF_Y(), z, "minecraft:smooth_stone");
            }
        for (int f = 0; f < CELL_FLOORS; f++)
            for (int s = 0; s < CELL_SECTORS; s++) ColosseumBuild.buildCell(p, f, s, true);
        for (int[] pad : ColosseumBuild.elevatorPads()) {
            p.set(pad[0], pad[1] - 1, pad[2], "minecraft:gold_block");
            p.set(pad[0], pad[1], pad[2], "minecraft:light_weighted_pressure_plate[power=0]");
        }
        finish(p);
    }

    static int ROOF_Y() {
        return CELL_Y0 + CELL_FLOORS * CELL_FH;
    }
}

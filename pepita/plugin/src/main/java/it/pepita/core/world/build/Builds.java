package it.pepita.core.world.build;

import it.pepita.core.mine.Mine;
import it.pepita.core.mine.Prices;
import it.pepita.core.world.BuildPlan;
import it.pepita.core.world.Layout;
import org.bukkit.Material;

/** Punto d'ingresso unico per le anteprime offline (tools/PlanRender) e per i test. */
public final class Builds {
    private Builds() {}

    public static BuildPlan plan(String name) {
        BuildPlan p = new BuildPlan();
        switch (name.split(":")[0]) {
            case "hub" -> HubBuild.build(p);
            case "spawn", "prigione" -> PrisonBuild.build(p);
            case "pvp" -> PvpBuild.build(p, samplePvp());
            case "colosseo", "celle" -> ColosseumBuild.build(p);
            case "mine", "miniera" -> MineBuild.build(p, sampleMine(name.contains(":") ? name.split(":")[1] : "pietra"));
            default -> throw new IllegalArgumentException("Build sconosciuta: " + name);
        }
        return p;
    }

    /** La miniera PvP di default. */
    public static Mine samplePvp() {
        Mine m = new Mine("pvp");
        m.name = "Miniera PvP dei Quantum";
        m.theme = "pvp";
        m.pvp = true;
        m.world = Layout.W_PVP;
        int h = Layout.PVP_HALF;
        m.minX = -h; m.maxX = h; m.minZ = -h; m.maxZ = h;
        m.maxY = Layout.PVP_TOP; m.minY = Layout.PVP_TOP - Layout.PVP_DEPTH + 1;
        m.spawnX = Layout.PVP_SPAWN[0]; m.spawnY = Layout.PVP_SPAWN[1]; m.spawnZ = Layout.PVP_SPAWN[2]; m.spawnYaw = 180;
        m.labelX = 0.5; m.labelY = Layout.PVP_TOP + 9.4; m.labelZ = -h - 4 + 0.5;
        m.mats.add(Material.NETHERRACK); m.weights.add(1);
        m.icon = Material.BUDDING_AMETHYST;
        return m;
    }

    /** Miniera di esempio centrata in 0,0 con il tema indicato. */
    public static Mine sampleMine(String theme) {
        Mine m = new Mine("esempio");
        m.name = "Miniera di prova";
        m.theme = theme;
        int h = Layout.MINE_HALF;
        m.minX = -h; m.maxX = h; m.minZ = -h; m.maxZ = h;
        m.maxY = Layout.MINE_TOP; m.minY = Layout.MINE_TOP - Layout.MINE_DEPTH + 1;
        m.spawnX = 0.5; m.spawnY = Layout.MINE_TOP + 2; m.spawnZ = h + 7.5; m.spawnYaw = 180;
        m.labelX = 0.5; m.labelY = Layout.MINE_TOP + 7.4; m.labelZ = -h - 4 + 0.5;
        for (int k = 0; k < 4; k++) { m.mats.add(Prices.LADDER.get(k)); m.weights.add(1); }
        m.icon = Material.STONE;
        return m;
    }
}

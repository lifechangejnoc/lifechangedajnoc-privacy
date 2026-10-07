package it.pepita.core.world;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Random;

/** Mondo completamente vuoto: tutto ciò che esiste lo costruisce il plugin. */
public final class VoidGenerator extends ChunkGenerator {
    private final double sx, sy, sz;
    private final Biome biome;

    public VoidGenerator() {
        this(0.5, 100, 27.5, Biome.MEADOW);
    }

    public VoidGenerator(double sx, double sy, double sz, Biome biome) {
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.biome = biome;
    }

    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }

    @Override
    public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        return new Location(world, sx, sy, sz);
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
        return new BiomeProvider() {
            @Override
            public @NotNull Biome getBiome(@NotNull WorldInfo info, int x, int y, int z) {
                return biome;
            }

            @Override
            public @NotNull List<Biome> getBiomes(@NotNull WorldInfo info) {
                return List.of(biome);
            }
        };
    }
}

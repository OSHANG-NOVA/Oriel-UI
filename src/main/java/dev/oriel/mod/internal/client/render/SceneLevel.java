package dev.oriel.mod.internal.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * The blocks of one scene group as a level the block and fluid renderers can read, so faces between neighbours are
 * culled and fluids meet their neighbours as in the world. Everything else is air.
 *
 * <p>A scene is lit like the inventory — by the GUI's directional light, full bright — so this reports no face shading
 * and full brightness, and there is no light engine behind it: nothing of the player's dimension or time of day shows.
 * Tints are a plains biome's (the world's, when one is loaded).
 */
public final class SceneLevel implements BlockAndTintGetter {
    /** Plains, for when no world is loaded. */
    private static final int GRASS = 0xFF91BD59, FOLIAGE = 0xFF77AB2F, WATER = 0xFF3F76E4;

    private final Map<BlockPos, BlockState> blocks;
    private final Map<BlockPos, BlockEntity> blockEntities;
    private final @Nullable Biome plains;
    private final int minY, height;
    private final LevelLightEngine lightEngine;

    public SceneLevel(Map<BlockPos, BlockState> blocks, Map<BlockPos, BlockEntity> blockEntities) {
        this.blocks = blocks;
        this.blockEntities = blockEntities;
        var level = Minecraft.getInstance().level;
        this.plains = level == null ? null
                : level.registryAccess().registryOrThrow(Registries.BIOME).getHolder(Biomes.PLAINS).map(h -> h.value()).orElse(null);
        int lo = 0, hi = 1;
        for (BlockPos p : blocks.keySet()) {
            lo = Math.min(lo, p.getY());
            hi = Math.max(hi, p.getY() + 1);
        }
        this.minY = lo - 1;
        this.height = hi - lo + 2;
        // Nothing asks it anything — every brightness is answered below — but the interface wants one.
        this.lightEngine = new LevelLightEngine(new LightChunkGetter() {
            @Override
            public @Nullable LightChunk getChunkForLighting(int x, int z) {
                return null;
            }

            @Override
            public net.minecraft.world.level.BlockGetter getLevel() {
                return SceneLevel.this;
            }
        }, false, false);
    }

    /** No face shading: the GUI's directional light does that. */
    @Override
    public float getShade(Direction direction, boolean shaded) {
        return 1F;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return lightEngine;
    }

    /** Full bright, in both layers, as an inventory render is. */
    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        return 15;
    }

    @Override
    public int getRawBrightness(BlockPos pos, int amount) {
        return 15;
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver color) {
        if (plains != null) return color.getColor(plains, pos.getX(), pos.getZ());
        if (color == BiomeColors.GRASS_COLOR_RESOLVER) return GRASS;
        if (color == BiomeColors.FOLIAGE_COLOR_RESOLVER) return FOLIAGE;
        if (color == BiomeColors.WATER_COLOR_RESOLVER) return WATER;
        return -1;
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return blockEntities.get(pos);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        BlockState state = blocks.get(pos);
        return state == null ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getMinBuildHeight() {
        return minY;
    }
}

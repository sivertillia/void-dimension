package com.finnk42.void_dimension.world;

import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Creates and tracks a separate void dimension per player at runtime. Minecraft has no public API
 * for dynamic dimensions, so we build the {@link ServerLevel} ourselves and insert it into the
 * server's level map (the private fields are opened via {@code META-INF/accesstransformer.cfg}).
 * Every player's void reuses the dimension type and generator of the datapack-defined
 * {@code void_dimension:the_void}, which acts purely as a template.
 */
public final class VoidDimensions {
    public static final String NAMESPACE = "void_dimension";
    private static final ResourceLocation TEMPLATE = ResourceLocation.fromNamespaceAndPath(NAMESPACE, "the_void");

    private VoidDimensions() {
    }

    /** The dimension key for a given player's private void: {@code void_dimension:<uuid>}. */
    public static ResourceKey<Level> keyForPlayer(UUID playerId) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(NAMESPACE, playerId.toString()));
    }

    /** True if the given dimension belongs to this mod (any per-player void, or the template). */
    public static boolean isVoid(ResourceKey<Level> dimension) {
        return dimension.location().getNamespace().equals(NAMESPACE);
    }

    /**
     * True if this void already exists — either currently loaded, or previously created and saved to
     * disk. Lets callers avoid fabricating a brand-new world for a player who never entered one.
     */
    public static boolean exists(MinecraftServer server, ResourceKey<Level> levelKey) {
        if (server.getLevel(levelKey) != null) {
            return true;
        }
        return Files.isDirectory(server.storageSource.getDimensionPath(levelKey));
    }

    /** Returns the player's void level, creating and registering it if it does not exist yet. */
    public static ServerLevel getOrCreate(MinecraftServer server, ResourceKey<Level> levelKey) {
        ServerLevel existing = server.getLevel(levelKey);
        if (existing != null) {
            return existing;
        }
        return createAndRegister(server, levelKey);
    }

    /**
     * Teleports a player into the given void level: places a landing platform, records where the
     * player came from (so the teleporter can return them) and moves them to the spawn point.
     */
    public static void sendToVoid(ServerPlayer player, ServerLevel voidLevel) {
        ServerLevel from = player.serverLevel();
        if (!isVoid(from.dimension())) {
            CompoundTag data = player.getPersistentData();
            data.putInt("VoidReturnX", player.getBlockX());
            data.putInt("VoidReturnY", player.getBlockY());
            data.putInt("VoidReturnZ", player.getBlockZ());
            data.putString("VoidReturnDim", from.dimension().location().toString());
        }
        BlockPos targetPos = new BlockPos(0, 100, 0);
        BlockPos belowPos = targetPos.below();
        if (voidLevel.isEmptyBlock(belowPos) || !voidLevel.getFluidState(belowPos).isEmpty()) {
            voidLevel.setBlockAndUpdate(belowPos, Blocks.OBSIDIAN.defaultBlockState());
        }
        player.fallDistance = 0.0f;
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        DimensionTransition transition = new DimensionTransition(voidLevel, new Vec3(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5), Vec3.ZERO, player.getYRot(), player.getXRot(), DimensionTransition.DO_NOTHING);
        player.changeDimension(transition);
    }

    private static ServerLevel createAndRegister(MinecraftServer server, ResourceKey<Level> levelKey) {
        Registry<LevelStem> stemRegistry = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM);
        LevelStem template = stemRegistry.get(TEMPLATE);
        if (template == null) {
            throw new IllegalStateException("Missing template void dimension: " + TEMPLATE);
        }
        LevelStem stem = new LevelStem(template.type(), template.generator());

        ServerLevelData overworldData = server.getWorldData().overworldData();
        DerivedLevelData levelData = new DerivedLevelData(server.getWorldData(), overworldData);
        long biomeZoomSeed = BiomeManager.obfuscateSeed(server.getWorldData().worldGenOptions().seed());
        ChunkProgressListener progressListener = server.progressListenerFactory.create(11);

        ServerLevel newLevel = new ServerLevel(
                server,
                server.executor,
                server.storageSource,
                levelData,
                levelKey,
                stem,
                progressListener,
                false,
                biomeZoomSeed,
                ImmutableList.of(),
                false,
                null
        );

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld != null) {
            overworld.getWorldBorder().addListener(new BorderChangeListener.DelegateBorderChangeListener(newLevel.getWorldBorder()));
        }

        server.levels.put(levelKey, newLevel);
        // NeoForge ticks a cached snapshot of the level list (server.getWorldArray()); without this the
        // new level never ticks, so block-change packets are never flushed to clients (blocks appear
        // unbreakable until relog). markWorldsDirty() invalidates that snapshot so our level ticks.
        server.markWorldsDirty();
        NeoForge.EVENT_BUS.post(new LevelEvent.Load(newLevel));
        return newLevel;
    }
}

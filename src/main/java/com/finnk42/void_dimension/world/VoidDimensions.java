package com.finnk42.void_dimension.world;

import com.google.common.collect.ImmutableList;
import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Creates and tracks a separate void dimension per player at runtime. Minecraft has no public API
 * for dynamic dimensions, so we build the {@link ServerLevel} ourselves and insert it into the
 * server's level map (the private fields are opened via {@code META-INF/accesstransformer.cfg}).
 * Every player's void reuses the dimension type and generator of one of the datapack-defined
 * template dimensions (see {@link VoidType}), chosen by the owner the first time they enter.
 */
public final class VoidDimensions {
    public static final String NAMESPACE = "void_dimension";
    private static final Set<UUID> RELOCATING = new HashSet<>();
    private VoidDimensions() {
    }

    /**
     * The dimension key for a player's private void: {@code void_dimension:<nick in lower case>}. A
     * void created under an older nick (or, before voids were named after players, under the UUID) is
     * moved to the current nick when possible — i.e. while it isn't loaded and the nick is free —
     * otherwise it keeps its old path. Nothing is recorded for players who have no void yet.
     */
    public static ResourceKey<Level> keyForPlayer(MinecraftServer server, GameProfile owner) {
        VoidNames names = VoidNames.get(server);
        UUID id = owner.getId();
        String wanted = names.wantedPath(id, owner.getName());
        String current = names.pathOf(id);
        if (current == null) {
            current = id.toString();
            if (!Files.isDirectory(dimensionPath(server, current))) {
                return key(wanted);
            }
        }
        if (!current.equals(wanted) && server.getLevel(key(current)) == null && moveFolder(server, current, wanted)) {
            current = wanted;
        }
        names.assign(id, current);
        return key(current);
    }

    /** True if the given dimension belongs to this mod (any per-player void, or the template). */
    public static boolean isVoid(ResourceKey<Level> dimension) {
        return dimension.location().getNamespace().equals(NAMESPACE);
    }

    /** The owner of a per-player void, or null if the key is not one (e.g. a template dimension). */
    public static UUID ownerOf(MinecraftServer server, ResourceKey<Level> dimension) {
        if (!isVoid(dimension) || VoidNames.isReserved(dimension.location().getPath())) {
            return null;
        }
        return VoidNames.get(server).ownerOf(dimension.location().getPath());
    }

    /**
     * True if this void already exists — either currently loaded, previously created and saved to
     * disk, or with a type already chosen. Lets callers avoid fabricating a brand-new world for a
     * player who never entered one.
     */
    public static boolean exists(MinecraftServer server, ResourceKey<Level> levelKey) {
        if (server.getLevel(levelKey) != null) {
            return true;
        }
        UUID owner = ownerOf(server, levelKey);
        if (owner != null && VoidTypes.get(server).has(owner)) {
            return true;
        }
        return Files.isDirectory(server.storageSource.getDimensionPath(levelKey));
    }

    /**
     * All per-player voids that currently exist — both loaded levels and dimensions saved to disk from
     * a previous session. The template dimensions are excluded.
     */
    public static Set<ResourceKey<Level>> listExisting(MinecraftServer server) {
        Set<ResourceKey<Level>> result = new LinkedHashSet<>();
        for (ServerLevel level : server.getAllLevels()) {
            ResourceKey<Level> key = level.dimension();
            if (isVoid(key) && !VoidNames.isReserved(key.location().getPath())) {
                result.add(key);
            }
        }
        Path namespaceDir = dimensionPath(server, VoidType.END.template().getPath()).getParent();
        if (namespaceDir != null && Files.isDirectory(namespaceDir)) {
            try (Stream<Path> children = Files.list(namespaceDir)) {
                children.filter(Files::isDirectory)
                        .map(dir -> dir.getFileName().toString())
                        .filter(path -> !VoidNames.isReserved(path))
                        .forEach(path -> result.add(key(path)));
            } catch (IOException ignored) {
                // best effort: fall back to whatever is already loaded
            }
        }
        return result;
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
     * Creates the player's own void with the chosen type and sends them into it. Called once, from the
     * {@link com.finnk42.void_dimension.menu.VoidTypeMenu} shown on their first trip; does nothing if
     * the void already exists.
     */
    public static void createAndEnter(ServerPlayer player, VoidType type) {
        MinecraftServer server = player.getServer();
        ResourceKey<Level> key = keyForPlayer(server, player.getGameProfile());
        if (exists(server, key)) {
            return;
        }
        VoidNames.get(server).assign(player.getUUID(), key.location().getPath());
        VoidTypes.get(server).set(player.getUUID(), type);
        sendToVoid(player, getOrCreate(server, key));
    }

    /**
     * Teleports a player into the given void level: records where the player came from (so the
     * teleporter can return them) and moves them to the owner's custom spawn point, or else to the
     * default one at 0 100 0, placing a landing block there if needed.
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
        UUID owner = ownerOf(player.getServer(), voidLevel.dimension());
        VoidSettings.Spawn spawn = owner == null ? null : VoidSettings.get(player.getServer()).spawn(owner);
        Vec3 target;
        float yaw = player.getYRot();
        if (spawn != null) {
            target = spawn.pos();
            yaw = spawn.yaw();
        } else {
            BlockPos targetPos = new BlockPos(0, 100, 0);
            BlockPos belowPos = targetPos.below();
            if (voidLevel.isEmptyBlock(belowPos) || !voidLevel.getFluidState(belowPos).isEmpty()) {
                voidLevel.setBlockAndUpdate(belowPos, Blocks.OBSIDIAN.defaultBlockState());
            }
            target = Vec3.atBottomCenterOf(targetPos);
        }
        player.fallDistance = 0.0f;
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.changeDimension(new DimensionTransition(voidLevel, target, Vec3.ZERO, yaw, player.getXRot(), DimensionTransition.DO_NOTHING));
    }

    /**
     * Sends a player out of a void, back to where they came from before entering it (recorded by
     * {@link #sendToVoid}), or to the overworld spawn if that is unknown or no longer loaded.
     */
    public static void sendBack(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        CompoundTag data = player.getPersistentData();
        ServerLevel returnLevel = server.getLevel(Level.OVERWORLD);
        if (data.contains("VoidReturnDim")) {
            ResourceKey<Level> savedDimKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(data.getString("VoidReturnDim")));
            ServerLevel savedLevel = server.getLevel(savedDimKey);
            if (savedLevel != null) {
                returnLevel = savedLevel;
            }
        }
        if (returnLevel == null) {
            return;
        }
        BlockPos targetPos = data.contains("VoidReturnX") ? new BlockPos(data.getInt("VoidReturnX"), data.getInt("VoidReturnY"), data.getInt("VoidReturnZ")) : returnLevel.getSharedSpawnPos();
        player.fallDistance = 0.0f;
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        DimensionTransition transition = new DimensionTransition(returnLevel, new Vec3(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5), Vec3.ZERO, player.getYRot(), player.getXRot(), DimensionTransition.DO_NOTHING);
        player.changeDimension(transition);
    }

    /**
     * Permanently deletes a player's void: everyone inside is sent back out, the level is unloaded and
     * its folder removed, and the owner's recorded name, type and settings are forgotten, so their next
     * trip starts over with the type menu. Access lists are kept. Must not run while the server is iterating
     * its levels (defer it with {@code server.execute}). Returns false if the player had no void.
     */
    public static boolean delete(MinecraftServer server, GameProfile owner) throws IOException {
        ResourceKey<Level> key = keyForPlayer(server, owner);
        if (!exists(server, key)) {
            return false;
        }
        ServerLevel level = server.getLevel(key);
        if (level != null) {
            for (ServerPlayer player : new ArrayList<>(level.players())) {
                sendBack(player);
                player.displayClientMessage(Component.literal("This void has been deleted."), true);
            }
            unload(server, level);
        }
        Path folder = server.storageSource.getDimensionPath(key);
        if (Files.isDirectory(folder)) {
            try (Stream<Path> files = Files.walk(folder)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(file);
                }
            }
        }
        VoidNames.get(server).remove(owner.getId());
        VoidTypes.get(server).remove(owner.getId());
        VoidSettings.get(server).remove(owner.getId());
        return true;
    }

    /**
     * Switches the owner's void to another type. A level's dimension type can't change in place, so if
     * the void is loaded everyone inside is moved out, the level is saved and reloaded from the new
     * template, and they are brought back to where they stood. Old chunks get the new biome as they
     * load (see {@link VoidRules}). Must not run while the server is iterating its levels.
     */
    public static void changeType(MinecraftServer server, UUID owner, VoidType type) throws IOException {
        VoidTypes types = VoidTypes.get(server);
        if (types.typeOf(owner) == type) {
            return;
        }
        types.set(owner, type);
        String path = VoidNames.get(server).pathOf(owner);
        ServerLevel level = path == null ? null : server.getLevel(key(path));
        if (level == null) {
            return; // picked up the next time the void loads
        }
        record Stay(ServerPlayer player, Vec3 pos, float yRot, float xRot) {
        }
        List<Stay> inside = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            inside.add(new Stay(player, player.position(), player.getYRot(), player.getXRot()));
        }
        try {
            for (Stay stay : inside) {
                RELOCATING.add(stay.player().getUUID());
                sendBack(stay.player());
            }
            unload(server, level);
            ServerLevel reloaded = createAndRegister(server, level.dimension());
            for (Stay stay : inside) {
                stay.player().changeDimension(new DimensionTransition(reloaded, stay.pos(), Vec3.ZERO, stay.yRot(), stay.xRot(), DimensionTransition.DO_NOTHING));
            }
        } finally {
            inside.forEach(stay -> RELOCATING.remove(stay.player().getUUID()));
        }
    }

    /** True while a player is being moved out and back in by {@link #changeType} (no visitor notices). */
    public static boolean isRelocating(ServerPlayer player) {
        return RELOCATING.contains(player.getUUID());
    }

    /** Saves a loaded void, removes it from the server and closes it. */
    private static void unload(MinecraftServer server, ServerLevel level) throws IOException {
        level.save(null, true, false);
        server.levels.remove(level.dimension());
        server.markWorldsDirty();
        NeoForge.EVENT_BUS.post(new LevelEvent.Unload(level));
        level.close();
    }

    private static ServerLevel createAndRegister(MinecraftServer server, ResourceKey<Level> levelKey) {
        Registry<LevelStem> stemRegistry = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM);
        UUID owner = ownerOf(server, levelKey);
        ResourceLocation templateId = (owner == null ? VoidType.END : VoidTypes.get(server).typeOf(owner)).template();
        LevelStem template = stemRegistry.get(templateId);
        if (template == null) {
            throw new IllegalStateException("Missing template void dimension: " + templateId);
        }
        LevelStem stem = new LevelStem(template.type(), template.generator());

        ServerLevelData overworldData = server.getWorldData().overworldData();
        VoidLevelData levelData = new VoidLevelData(server.getWorldData(), overworldData, server, owner);
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

    private static ResourceKey<Level> key(String path) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(NAMESPACE, path));
    }

    private static Path dimensionPath(MinecraftServer server, String path) {
        return server.storageSource.getDimensionPath(key(path));
    }

    /** Renames a void's folder on disk. True if it now lives at the new path (or had no folder yet). */
    private static boolean moveFolder(MinecraftServer server, String from, String to) {
        Path source = dimensionPath(server, from);
        if (!Files.isDirectory(source)) {
            return true;
        }
        try {
            Files.move(source, dimensionPath(server, to));
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}

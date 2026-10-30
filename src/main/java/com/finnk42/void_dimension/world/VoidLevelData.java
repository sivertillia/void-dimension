package com.finnk42.void_dimension.world;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import org.jetbrains.annotations.Nullable;

/**
 * Level data of a player's void: shares everything with the overworld like vanilla's secondary
 * dimensions do, except the time of day, which comes from the owner's {@link VoidTime} setting. The
 * server sends each level's time to its players, so this is all it takes for every void to have its
 * own sky. A fixed time also reports a time speed of zero so modded clients don't drift between syncs.
 */
public class VoidLevelData extends DerivedLevelData {
    private final MinecraftServer server;
    @Nullable
    private final UUID owner;

    public VoidLevelData(WorldData worldData, ServerLevelData overworldData, MinecraftServer server, @Nullable UUID owner) {
        super(worldData, overworldData);
        this.server = server;
        this.owner = owner;
    }

    private VoidTime time() {
        return this.owner == null ? VoidTime.CYCLE : VoidSettings.get(this.server).time(this.owner);
    }

    @Override
    public long getDayTime() {
        VoidTime time = this.time();
        return time.isFixed() ? time.dayTime() : super.getDayTime();
    }

    @Override
    public float getDayTimePerTick() {
        return this.time().isFixed() ? 0.0f : super.getDayTimePerTick();
    }
}

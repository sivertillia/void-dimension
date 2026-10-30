package com.finnk42.void_dimension.world;

import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Implements {@link VoidSetting#FALL_LOOP}: in a void with the setting on, anything that falls out of
 * the bottom of the world — players, items, mobs — is moved to the top of the same column, keeping its
 * speed. Fall distance is reset on each wrap, so only the last stretch counts when you land.
 */
public final class VoidFallLoop {
    /** How far below the world's floor an entity falls before it wraps (vanilla void damage starts at 64). */
    private static final int DEPTH = 32;

    private VoidFallLoop() {
    }

    public static void onEntityTick(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        // Cheap checks first: this runs for every entity every tick.
        if (!(entity.level() instanceof ServerLevel level) || entity.isPassenger()
                || entity.getY() >= level.getMinBuildHeight() - DEPTH || !VoidDimensions.isVoid(level.dimension())) {
            return;
        }
        UUID owner = VoidDimensions.ownerOf(level.getServer(), level.dimension());
        if (owner == null || !VoidSettings.get(level.getServer()).isEnabled(owner, VoidSetting.FALL_LOOP)) {
            return;
        }
        double top = level.getMaxBuildHeight();
        entity.resetFallDistance();
        if (entity instanceof ServerPlayer player) {
            // Relative on every axis so the client keeps its velocity and look direction.
            player.connection.teleport(player.getX(), top, player.getZ(), player.getYRot(), player.getXRot(), EnumSet.allOf(RelativeMovement.class));
        } else {
            entity.teleportTo(entity.getX(), top, entity.getZ());
        }
    }
}

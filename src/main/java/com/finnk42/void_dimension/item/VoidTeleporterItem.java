package com.finnk42.void_dimension.item;

import com.finnk42.void_dimension.Config;
import com.finnk42.void_dimension.menu.VoidTypeMenu;
import com.finnk42.void_dimension.world.VoidAccess;
import com.finnk42.void_dimension.world.VoidDimensions;
import com.mojang.authlib.GameProfile;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

public class VoidTeleporterItem
extends Item {
    public VoidTeleporterItem(Item.Properties properties) {
        super(properties);
    }

    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 15;
    }

    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    public boolean isFoil(ItemStack stack) {
        return Config.ENABLE_TELEPORTER_GLOW.get();
    }

    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (level.isClientSide()) {
            int ticksUsed = this.getUseDuration(stack, entity) - remainingUseDuration;
            double radius = 1.2;
            DustParticleOptions darkVoid = new DustParticleOptions(new Vector3f(0.15f, 0.15f, 0.15f), 1.0f);
            DustParticleOptions lightVoid = new DustParticleOptions(new Vector3f(0.95f, 0.95f, 0.95f), 1.0f);
            for (int i = 0; i < 3; ++i) {
                double angle = (double)ticksUsed * 0.25 + (double)i * 2.0943951023931953;
                double x = entity.getX() + radius * Math.cos(angle);
                double y = entity.getY() + 0.1;
                double z = entity.getZ() + radius * Math.sin(angle);
                DustParticleOptions currentColor = i % 2 == 0 ? darkVoid : lightVoid;
                level.addParticle(currentColor, x, y, z, 0.0, 0.0, 0.0);
            }
            if (remainingUseDuration < 20) {
                level.addParticle(ParticleTypes.END_ROD, entity.getX() + (level.random.nextDouble() - 0.5) * 2.0, entity.getY() + level.random.nextDouble(), entity.getZ() + (level.random.nextDouble() - 0.5) * 2.0, 0.0, 0.05, 0.0);
            }
        }
    }

    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entityLiving) {
        if (!level.isClientSide() && entityLiving instanceof ServerPlayer player) {
            ServerLevel currentLevel = player.serverLevel();
            MinecraftServer server = player.getServer();
            if (!VoidDimensions.isVoid(currentLevel.dimension())) {
                // A teleporter renamed to a player's nick targets that player's void (access-checked);
                // an unnamed one targets your own. Returns null if the trip is not allowed (a message
                // was already sent to the player).
                ResourceKey<Level> destination = resolveDestination(player, server, stack);
                if (destination != null && destination.equals(VoidDimensions.keyForPlayer(server, player.getGameProfile())) && !VoidDimensions.exists(server, destination)) {
                    // First trip: let the player pick what their void looks like; the menu creates it.
                    VoidTypeMenu.open(player);
                } else if (destination != null) {
                    currentLevel.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
                    // Creating a dimension mutates the server's level map, which is being iterated during
                    // the tick that runs this method — defer it one tick to avoid a ConcurrentModificationException.
                    server.execute(() -> VoidDimensions.sendToVoid(player, VoidDimensions.getOrCreate(server, destination)));
                }
            } else {
                currentLevel.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
                VoidDimensions.sendBack(player);
            }
        }
        return stack;
    }

    /**
     * Works out which void the teleporter should send the player to. An unnamed teleporter sends the
     * player to their own void; a teleporter renamed (e.g. in an anvil) to a player's nick sends them
     * to that player's void, but only if it exists and the player is allowed in (owner, granted via
     * {@code /void access}, or an operator). Returns null and notifies the player when the trip is
     * refused.
     */
    private static ResourceKey<Level> resolveDestination(ServerPlayer player, MinecraftServer server, ItemStack stack) {
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        if (customName == null) {
            return VoidDimensions.keyForPlayer(server, player.getGameProfile());
        }
        // Nicks are matched case-insensitively (the profile cache lower-cases its lookups).
        String targetName = customName.getString().trim();
        GameProfileCache cache = server.getProfileCache();
        Optional<GameProfile> profile = cache == null ? Optional.empty() : cache.get(targetName);
        if (profile.isEmpty()) {
            player.displayClientMessage(Component.literal("No player named \"" + targetName + "\"."), true);
            return null;
        }
        UUID ownerId = profile.get().getId();
        ResourceKey<Level> key = VoidDimensions.keyForPlayer(server, profile.get());
        if (!VoidDimensions.exists(server, key)) {
            player.displayClientMessage(Component.literal(targetName + " has no void dimension yet."), true);
            return null;
        }
        if (!player.hasPermissions(2) && !VoidAccess.get(server).hasAccess(ownerId, player.getUUID())) {
            player.displayClientMessage(Component.literal(targetName + " has not allowed you into their void."), true);
            return null;
        }
        return key;
    }
}

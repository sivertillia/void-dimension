package com.finnk42.void_dimension.item;

import com.finnk42.void_dimension.Config;
import com.finnk42.void_dimension.init.ModConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
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
            currentLevel.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
            CompoundTag playerData = player.getPersistentData();
            if (currentLevel.dimension() != ModConstants.VOID_DIMENSION_KEY) {
                playerData.putInt("VoidReturnX", player.getBlockX());
                playerData.putInt("VoidReturnY", player.getBlockY());
                playerData.putInt("VoidReturnZ", player.getBlockZ());
                playerData.putString("VoidReturnDim", currentLevel.dimension().location().toString());
                ServerLevel voidLevel = player.getServer().getLevel(ModConstants.VOID_DIMENSION_KEY);
                if (voidLevel != null) {
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
            } else {
                ServerLevel returnLevel = player.getServer().getLevel(Level.OVERWORLD);
                if (playerData.contains("VoidReturnDim")) {
                    ResourceLocation dimLoc = ResourceLocation.parse(playerData.getString("VoidReturnDim"));
                    ResourceKey<Level> savedDimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
                    ServerLevel savedLevel = player.getServer().getLevel(savedDimKey);
                    if (savedLevel != null) {
                        returnLevel = savedLevel;
                    }
                }
                if (returnLevel != null) {
                    BlockPos targetPos = playerData.contains("VoidReturnX") ? new BlockPos(playerData.getInt("VoidReturnX"), playerData.getInt("VoidReturnY"), playerData.getInt("VoidReturnZ")) : returnLevel.getSharedSpawnPos();
                    player.fallDistance = 0.0f;
                    player.setDeltaMovement(Vec3.ZERO);
                    player.hurtMarked = true;
                    DimensionTransition transition = new DimensionTransition(returnLevel, new Vec3(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5), Vec3.ZERO, player.getYRot(), player.getXRot(), DimensionTransition.DO_NOTHING);
                    player.changeDimension(transition);
                }
            }
        }
        return stack;
    }
}

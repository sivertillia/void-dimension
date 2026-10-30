package com.finnk42.void_dimension.world;

import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jetbrains.annotations.Nullable;

/** Enforces the per-void {@link VoidSetting}s, plus the biome fix-up needed after a type change. */
public final class VoidRules {
    /** How far below the world's floor an entity falls before it wraps (vanilla void damage starts at 64). */
    private static final int FALL_LOOP_DEPTH = 32;
    /** Persistent-data key holding a dead player's inventory until they respawn. */
    private static final String KEPT_INVENTORY = "VoidKeptInventory";

    private VoidRules() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(VoidRules::onEntityTick);
        bus.addListener(VoidRules::onBreak);
        bus.addListener(VoidRules::onPlace);
        bus.addListener(VoidRules::onRightClickBlock);
        bus.addListener(VoidRules::onRightClickItem);
        bus.addListener(VoidRules::onAttack);
        bus.addListener(VoidRules::onEntityInteract);
        bus.addListener(VoidRules::onEntityInteractSpecific);
        bus.addListener(VoidRules::onIncomingDamage);
        // Last, so a death another mod cancels never gets its inventory stashed.
        bus.addListener(EventPriority.LOWEST, VoidRules::onDeath);
        bus.addListener(VoidRules::onClone);
        bus.addListener(VoidRules::onChangedDimension);
        bus.addListener(VoidRules::onChunkLoad);
    }

    /** The owner of the void this level is, or null if it isn't a player's void. */
    @Nullable
    private static UUID ownerOf(Level level) {
        return level instanceof ServerLevel serverLevel && VoidDimensions.isVoid(level.dimension())
                ? VoidDimensions.ownerOf(serverLevel.getServer(), level.dimension())
                : null;
    }

    // --- Fall loop ---------------------------------------------------------------------------------

    /**
     * Anything that falls out of the bottom of a void with {@link VoidSetting#FALL_LOOP} on — players,
     * items, mobs — moves to the top of the same column, keeping its speed. Fall distance is reset on
     * each wrap, so only the last stretch counts when you land.
     */
    private static void onEntityTick(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        // Cheap checks first: this runs for every entity every tick.
        if (!(entity.level() instanceof ServerLevel level) || entity.isPassenger()
                || entity.getY() >= level.getMinBuildHeight() - FALL_LOOP_DEPTH) {
            return;
        }
        UUID owner = ownerOf(level);
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

    // --- Guests can build --------------------------------------------------------------------------

    /**
     * True if the player is a guest in a void whose owner has turned {@link VoidSetting#GUESTS_CAN_BUILD}
     * off. Owners and operators are never restricted. Tells the player why when restricted.
     */
    private static boolean isRestrictedGuest(Player player) {
        UUID owner = ownerOf(player.level());
        if (owner == null || owner.equals(player.getUUID()) || player.hasPermissions(2)
                || VoidSettings.get(player.getServer()).isEnabled(owner, VoidSetting.GUESTS_CAN_BUILD)) {
            return false;
        }
        player.displayClientMessage(Component.literal("Only the owner can build in this void.").withStyle(ChatFormatting.RED), true);
        return true;
    }

    private static void cancelForGuest(Player player, ICancellableEvent event) {
        if (isRestrictedGuest(player)) {
            event.setCanceled(true);
        }
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        cancelForGuest(event.getPlayer(), event);
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player) {
            cancelForGuest(player, event);
        }
    }

    /** Guests may still open doors and chests, but not use items on blocks (flint and steel, bone meal...). */
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getItemStack().isEmpty() && isRestrictedGuest(event.getEntity())) {
            event.setUseItem(TriState.FALSE);
        }
    }

    /** Buckets place fluids through the item's own use, which no block event covers. */
    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getItemStack().getItem() instanceof BucketItem) {
            cancelForGuest(event.getEntity(), event);
        }
    }

    /** Guests can't break item frames, armor stands, boats... PvP is a separate setting. */
    private static void onAttack(AttackEntityEvent event) {
        if (!(event.getTarget() instanceof Player)) {
            cancelForGuest(event.getEntity(), event);
        }
    }

    /** Guests can ride boats and minecarts, but not edit item frames, armor stands and the like. */
    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Player) && !(event.getTarget() instanceof VehicleEntity)) {
            cancelForGuest(event.getEntity(), event);
        }
    }

    private static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getTarget() instanceof Player) && !(event.getTarget() instanceof VehicleEntity)) {
            cancelForGuest(event.getEntity(), event);
        }
    }

    // --- PvP ---------------------------------------------------------------------------------------

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getSource().getEntity() instanceof Player attacker) || attacker == victim) {
            return;
        }
        UUID owner = ownerOf(victim.level());
        if (owner != null && !VoidSettings.get(victim.getServer()).isEnabled(owner, VoidSetting.PVP)) {
            event.setCanceled(true);
        }
    }

    // --- Keep inventory ----------------------------------------------------------------------------

    /**
     * Stashes a dying player's inventory and experience in their persistent data (so it survives a
     * disconnect or restart on the death screen) and empties them, so nothing drops.
     */
    private static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            return;
        }
        UUID owner = ownerOf(player.level());
        if (owner == null || !VoidSettings.get(player.getServer()).isEnabled(owner, VoidSetting.KEEP_INVENTORY)) {
            return;
        }
        CompoundTag kept = new CompoundTag();
        kept.put("Items", player.getInventory().save(new ListTag()));
        kept.putInt("Level", player.experienceLevel);
        kept.putFloat("Progress", player.experienceProgress);
        kept.putInt("Total", player.totalExperience);
        player.getPersistentData().put(KEPT_INVENTORY, kept);
        player.getInventory().clearContent();
        player.experienceLevel = 0;
        player.experienceProgress = 0.0f;
        player.totalExperience = 0;
    }

    /** Hands a stashed inventory to the respawned player. */
    private static void onClone(PlayerEvent.Clone event) {
        CompoundTag data = event.getOriginal().getPersistentData();
        if (!event.isWasDeath() || !data.contains(KEPT_INVENTORY, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag kept = data.getCompound(KEPT_INVENTORY);
        Player player = event.getEntity();
        player.getInventory().load(kept.getList("Items", Tag.TAG_COMPOUND));
        player.experienceLevel = kept.getInt("Level");
        player.experienceProgress = kept.getFloat("Progress");
        player.totalExperience = kept.getInt("Total");
        player.getPersistentData().remove(KEPT_INVENTORY);
    }

    // --- Visitor notices ---------------------------------------------------------------------------

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer visitor) || VoidDimensions.isRelocating(visitor)) {
            return;
        }
        notifyOwner(visitor, event.getFrom(), " left your void.");
        notifyOwner(visitor, event.getTo(), " entered your void.");
    }

    private static void notifyOwner(ServerPlayer visitor, ResourceKey<Level> dimension, String what) {
        MinecraftServer server = visitor.getServer();
        UUID owner = VoidDimensions.isVoid(dimension) ? VoidDimensions.ownerOf(server, dimension) : null;
        if (owner == null || owner.equals(visitor.getUUID()) || !VoidSettings.get(server).isEnabled(owner, VoidSetting.VISITOR_NOTICES)) {
            return;
        }
        ServerPlayer ownerPlayer = server.getPlayerList().getPlayer(owner);
        if (ownerPlayer != null) {
            ownerPlayer.sendSystemMessage(Component.literal(visitor.getGameProfile().getName() + what).withStyle(ChatFormatting.GRAY));
        }
    }

    // --- Type change -------------------------------------------------------------------------------

    /**
     * Chunks store their biomes, and the biome decides sky colour, fog and ambience. After a void's
     * type changes, its old chunks still carry the old type's biome, so rewrite them as they load.
     */
    private static void onChunkLoad(ChunkEvent.Load event) {
        if (event.isNewChunk() || !(event.getLevel() instanceof ServerLevel level) || !VoidDimensions.isVoid(level.dimension())) {
            return;
        }
        ChunkAccess chunk = event.getChunk();
        BiomeSource biomes = level.getChunkSource().getGenerator().getBiomeSource();
        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
        ChunkPos pos = chunk.getPos();
        int x = QuartPos.fromBlock(pos.getMinBlockX());
        int y = QuartPos.fromBlock(level.getMinBuildHeight());
        int z = QuartPos.fromBlock(pos.getMinBlockZ());
        Holder<Biome> expected = biomes.getNoiseBiome(x, y, z, sampler);
        if (chunk.getNoiseBiome(x, y, z).value() != expected.value()) {
            chunk.fillBiomesFromNoise(biomes, sampler);
            chunk.setUnsaved(true);
        }
    }
}

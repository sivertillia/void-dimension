package com.finnk42.void_dimension.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

public class VoidMonolithBlock
extends Block {
    public static final BooleanProperty ACTIVE = BooleanProperty.create((String)"active");
    private static final ResourceKey<Level> VOID_DIMENSION_KEY = ResourceKey.create((ResourceKey)Registries.DIMENSION, (ResourceLocation)ResourceLocation.fromNamespaceAndPath((String)"void_dimension", (String)"the_void"));

    public VoidMonolithBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue((Property)ACTIVE, (Comparable)Boolean.valueOf(false)));
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(new Property[]{ACTIVE});
    }

    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        boolean isVoid = level.dimension() == VOID_DIMENSION_KEY;
        return (BlockState)this.defaultBlockState().setValue((Property)ACTIVE, (Comparable)Boolean.valueOf(isVoid));
    }

    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide() && level instanceof ServerLevel) {
            ServerLevel serverLevel = (ServerLevel)level;
            if (((Boolean)state.getValue((Property)ACTIVE)).booleanValue()) {
                ChunkPos chunkPos = new ChunkPos(pos);
                serverLevel.setChunkForced(chunkPos.x, chunkPos.z, true);
            }
        }
    }

    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel) {
            ServerLevel serverLevel = (ServerLevel)level;
            ChunkPos chunkPos = new ChunkPos(pos);
            serverLevel.setChunkForced(chunkPos.x, chunkPos.z, false);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}


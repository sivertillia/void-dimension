package com.finnk42.void_dimension.block;

import com.finnk42.void_dimension.world.VoidDimensions;
import net.minecraft.core.BlockPos;
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
import org.jetbrains.annotations.Nullable;

public class VoidMonolithBlock
extends Block {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public VoidMonolithBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, false));
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        boolean isVoid = VoidDimensions.isVoid(level.dimension());
        return this.defaultBlockState().setValue(ACTIVE, isVoid);
    }

    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            if (state.getValue(ACTIVE)) {
                ChunkPos chunkPos = new ChunkPos(pos);
                serverLevel.setChunkForced(chunkPos.x, chunkPos.z, true);
            }
        }
    }

    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            ChunkPos chunkPos = new ChunkPos(pos);
            serverLevel.setChunkForced(chunkPos.x, chunkPos.z, false);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}

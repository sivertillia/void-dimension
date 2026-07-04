package com.finnk42.void_dimension;

import com.finnk42.void_dimension.block.VoidMonolithBlock;
import com.finnk42.void_dimension.command.VoidCommand;
import com.finnk42.void_dimension.item.VoidTeleporterItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(value = "void_dimension")
public class VoidDimensionMod {
    public static final String MODID = "void_dimension";
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredItem<Item> VOID_TELEPORTER = ITEMS.register("void_teleporter", () -> new VoidTeleporterItem(new Item.Properties().stacksTo(1)));
    public static final DeferredBlock<Block> VOID_MONOLITH = BLOCKS.register("void_monolith", () -> new VoidMonolithBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(5.0f, 1200.0f).lightLevel(state -> state.getValue(VoidMonolithBlock.ACTIVE) ? 15 : 0).sound(SoundType.AMETHYST)));
    public static final DeferredItem<Item> VOID_MONOLITH_ITEM = ITEMS.register("void_monolith", () -> new BlockItem(VOID_MONOLITH.get(), new Item.Properties()));

    public VoidDimensionMod(IEventBus modEventBus, ModContainer modContainer) {
        ITEMS.register(modEventBus);
        BLOCKS.register(modEventBus);
        modEventBus.addListener(this::addCreative);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        VoidCommand.register(event.getDispatcher());
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(VOID_MONOLITH_ITEM.get());
            event.accept(VOID_TELEPORTER.get());
        }
    }
}

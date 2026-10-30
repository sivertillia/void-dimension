package com.finnk42.void_dimension.menu;

import com.finnk42.void_dimension.world.VoidDimensions;
import com.finnk42.void_dimension.world.VoidType;
import java.util.function.BiConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * A single row with one icon per {@link VoidType}. Shown the first time a player uses their teleporter
 * (choosing creates the void and sends them in) and from the settings menu (choosing changes the
 * type). Closing it without choosing does nothing.
 */
public class VoidTypeMenu extends VoidMenu {
    private static final VoidType[] SLOTS = {null, null, VoidType.OVERWORLD, null, VoidType.NETHER, null, VoidType.END, null, null};

    @Nullable
    private final VoidType current;
    private final BiConsumer<ServerPlayer, VoidType> onChoose;
    private boolean chosen;

    private VoidTypeMenu(int containerId, Inventory playerInventory, @Nullable VoidType current, BiConsumer<ServerPlayer, VoidType> onChoose) {
        super(containerId, playerInventory, 1);
        this.current = current;
        this.onChoose = onChoose;
        this.setIcon(2, this.typeIcon(VoidType.OVERWORLD, Items.GRASS_BLOCK, "Overworld", ChatFormatting.GREEN, "Blue sky; pick the time of day."));
        this.setIcon(4, this.typeIcon(VoidType.NETHER, Items.NETHERRACK, "Nether", ChatFormatting.RED, "Red fog; water evaporates."));
        this.setIcon(6, this.typeIcon(VoidType.END, Items.END_STONE, "End", ChatFormatting.LIGHT_PURPLE, "Dark, starless void."));
    }

    /** The first-trip menu: choosing creates the player's void with that type and sends them in. */
    public static void open(ServerPlayer player) {
        open(player, "Choose your void", null, (chooser, type) -> {
            chooser.serverLevel().playSound(null, chooser.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
            VoidDimensions.createAndEnter(chooser, type);
        });
    }

    /**
     * Opens the menu with the given current type marked. The choice is handed to {@code onChoose} on the
     * next server task, after the menu has closed, so it may create or reload levels.
     */
    public static void open(ServerPlayer player, String title, @Nullable VoidType current, BiConsumer<ServerPlayer, VoidType> onChoose) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new VoidTypeMenu(id, inventory, current, onChoose), Component.literal(title)));
    }

    @Override
    protected void onClick(int slot, boolean rightClick, ServerPlayer player) {
        VoidType type = slot < SLOTS.length ? SLOTS[slot] : null;
        if (this.chosen || type == null || type == this.current) {
            return;
        }
        this.chosen = true;
        player.getServer().execute(() -> {
            player.closeContainer();
            this.onChoose.accept(player, type);
        });
    }

    private ItemStack typeIcon(VoidType type, Item item, String name, ChatFormatting color, String description) {
        ItemStack stack = icon(new ItemStack(item), plain(name).withStyle(color),
                plain(description).withStyle(ChatFormatting.GRAY),
                type == this.current
                        ? plain("Current type").withStyle(ChatFormatting.YELLOW)
                        : plain("No mobs spawn here.").withStyle(ChatFormatting.DARK_GRAY));
        if (type == this.current) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return stack;
    }
}

package com.finnk42.void_dimension.menu;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * Base of the mod's menus: a plain vanilla chest menu used as a row of buttons, so it needs no
 * client-side code. Every click is swallowed — nothing in the menu (or the player's inventory while it
 * is open) can move — and plain left/right/shift clicks on the menu's own slots go to {@link #onClick}.
 */
public abstract class VoidMenu extends ChestMenu {
    protected VoidMenu(int containerId, Inventory playerInventory, int rows) {
        super(menuType(rows), containerId, playerInventory, new SimpleContainer(rows * 9), rows);
    }

    private static MenuType<?> menuType(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            case 6 -> MenuType.GENERIC_9x6;
            default -> throw new IllegalArgumentException("Chest menus have 1-6 rows, not " + rows);
        };
    }

    /**
     * Handles a click on one of the menu's slots. Opening another menu or changing levels from here
     * must be deferred with {@code server.execute}: the click packet is still being processed against
     * this menu when this returns.
     */
    protected abstract void onClick(int slot, boolean rightClick, ServerPlayer player);

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // Never call super: see the class comment.
        if (slotId >= 0 && slotId < this.getRowCount() * 9 && (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE)
                && player instanceof ServerPlayer serverPlayer) {
            this.onClick(slotId, clickType == ClickType.PICKUP && button == 1, serverPlayer);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    protected void setIcon(int slot, ItemStack icon) {
        this.getContainer().setItem(slot, icon);
    }

    /** Names an icon and gives it lore lines. */
    protected static ItemStack icon(ItemStack stack, Component name, Component... lore) {
        stack.set(DataComponents.CUSTOM_NAME, name);
        stack.set(DataComponents.LORE, new ItemLore(List.of(lore)));
        return stack;
    }

    /** Text without the italics item names and lore get by default. */
    protected static MutableComponent plain(String text) {
        return Component.literal(text).withStyle(style -> style.withItalic(false));
    }

    protected static void clickSound(ServerPlayer player) {
        player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.MASTER, 0.5f, 1.0f);
    }
}

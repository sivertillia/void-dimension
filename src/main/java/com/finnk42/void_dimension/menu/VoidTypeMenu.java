package com.finnk42.void_dimension.menu;

import com.finnk42.void_dimension.world.VoidDimensions;
import com.finnk42.void_dimension.world.VoidType;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * The menu shown the first time a player uses their teleporter, before their void exists: a single
 * chest row with one item per {@link VoidType}. Clicking one creates the void with that type and sends
 * the player in. It is a plain vanilla chest menu, so it needs no client-side code; every click is
 * swallowed so the icons can't be taken. Closing it without choosing does nothing — the player is
 * simply asked again next time.
 */
public class VoidTypeMenu extends ChestMenu {
    private static final Component TITLE = Component.literal("Choose your void");
    private static final VoidType[] SLOTS = {null, null, VoidType.OVERWORLD, null, VoidType.NETHER, null, VoidType.END, null, null};

    private boolean chosen;

    private VoidTypeMenu(int containerId, Inventory playerInventory) {
        super(MenuType.GENERIC_9x1, containerId, playerInventory, createIcons(), 1);
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new VoidTypeMenu(id, inventory), TITLE));
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // Never call super: nothing in this menu (or the player's inventory while it is open) may move.
        if (this.chosen || slotId < 0 || slotId >= SLOTS.length || SLOTS[slotId] == null || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        this.chosen = true;
        VoidType type = SLOTS[slotId];
        MinecraftServer server = serverPlayer.getServer();
        // Creating a dimension mutates the server's level map; defer it out of packet handling, the same
        // way the teleporter does.
        server.execute(() -> {
            serverPlayer.closeContainer();
            serverPlayer.serverLevel().playSound(null, serverPlayer.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
            VoidDimensions.createAndEnter(serverPlayer, type);
        });
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    private static SimpleContainer createIcons() {
        SimpleContainer icons = new SimpleContainer(SLOTS.length);
        icons.setItem(2, icon(new ItemStack(Items.GRASS_BLOCK), "Overworld", ChatFormatting.GREEN, "Eternal day, always 12:00."));
        icons.setItem(4, icon(new ItemStack(Items.NETHERRACK), "Nether", ChatFormatting.RED, "Red fog; water evaporates."));
        icons.setItem(6, icon(new ItemStack(Items.END_STONE), "End", ChatFormatting.LIGHT_PURPLE, "Dark, starless void."));
        return icons;
    }

    private static ItemStack icon(ItemStack stack, String name, ChatFormatting color, String description) {
        stack.set(DataComponents.CUSTOM_NAME, plain(name).withStyle(color));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                plain(description).withStyle(ChatFormatting.GRAY),
                plain("No mobs spawn here.").withStyle(ChatFormatting.DARK_GRAY))));
        return stack;
    }

    private static MutableComponent plain(String text) {
        return Component.literal(text).withStyle(style -> style.withItalic(false));
    }
}

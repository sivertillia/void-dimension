package com.finnk42.void_dimension.menu;

import com.finnk42.void_dimension.world.VoidSetting;
import com.finnk42.void_dimension.world.VoidSettings;
import java.util.List;
import java.util.UUID;
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
import net.minecraft.world.item.component.ItemLore;

/**
 * The {@code /void setting local} menu: a single chest row with one icon per {@link VoidSetting} of the
 * owner's void. Clicking an icon toggles the setting and redraws it. Like {@link VoidTypeMenu} it is a
 * plain vanilla chest menu with every click swallowed, so it needs no client-side code.
 */
public class VoidSettingsMenu extends ChestMenu {
    private static final Component TITLE = Component.literal("Void settings");
    private static final VoidSetting[] SETTINGS = VoidSetting.values();
    /** Slot of the first icon; the icons are centred in the row. */
    private static final int FIRST_SLOT = (9 - SETTINGS.length) / 2;

    private final MinecraftServer server;
    private final UUID owner;

    private VoidSettingsMenu(int containerId, Inventory playerInventory, MinecraftServer server, UUID owner) {
        super(MenuType.GENERIC_9x1, containerId, playerInventory, new SimpleContainer(9), 1);
        this.server = server;
        this.owner = owner;
        for (VoidSetting setting : SETTINGS) {
            this.redraw(setting);
        }
    }

    /** Opens the settings of the given owner's void for the player. */
    public static void open(ServerPlayer player, UUID owner) {
        MinecraftServer server = player.getServer();
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new VoidSettingsMenu(id, inventory, server, owner), TITLE));
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // Never call super: nothing in this menu (or the player's inventory while it is open) may move.
        int index = slotId - FIRST_SLOT;
        if (index < 0 || index >= SETTINGS.length) {
            return;
        }
        VoidSettings.get(this.server).toggle(this.owner, SETTINGS[index]);
        this.redraw(SETTINGS[index]);
        player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.MASTER, 0.5f, 1.0f);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    private void redraw(VoidSetting setting) {
        boolean on = VoidSettings.get(this.server).isEnabled(this.owner, setting);
        ItemStack icon = new ItemStack(setting.icon());
        icon.set(DataComponents.CUSTOM_NAME, plain(setting.title() + ": ").withStyle(ChatFormatting.WHITE)
                .append(plain(on ? "ON" : "OFF").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED)));
        icon.set(DataComponents.LORE, new ItemLore(List.of(
                plain(setting.description()).withStyle(ChatFormatting.GRAY),
                plain("Click to toggle.").withStyle(ChatFormatting.DARK_GRAY))));
        icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, on);
        this.getContainer().setItem(FIRST_SLOT + setting.ordinal(), icon);
    }

    private static MutableComponent plain(String text) {
        return Component.literal(text).withStyle(style -> style.withItalic(false));
    }
}

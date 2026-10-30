package com.finnk42.void_dimension.menu;

import com.finnk42.void_dimension.world.VoidAccess;
import com.finnk42.void_dimension.world.VoidDimensions;
import com.finnk42.void_dimension.world.VoidSetting;
import com.finnk42.void_dimension.world.VoidSettings;
import com.finnk42.void_dimension.world.VoidTime;
import com.finnk42.void_dimension.world.VoidType;
import com.finnk42.void_dimension.world.VoidTypes;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.Vec3;

/**
 * The {@code /void setting local} menu for an owner's void. The top row holds the type, time of day,
 * spawn point and the users menu; the bottom row the {@link VoidSetting} toggles. Toggles and cycles
 * change in place; the type and users buttons open their own menus.
 */
public class VoidSettingsMenu extends VoidMenu {
    private static final Component TITLE = Component.literal("Void settings");
    private static final int TYPE_SLOT = 1;
    private static final int TIME_SLOT = 3;
    private static final int SPAWN_SLOT = 5;
    private static final int USERS_SLOT = 7;
    private static final VoidSetting[] TOGGLES = Arrays.stream(VoidSetting.values()).filter(setting -> !setting.hidden()).toArray(VoidSetting[]::new);
    /** Slot of the first toggle; the toggles are centred in the second row. */
    private static final int FIRST_TOGGLE_SLOT = 9 + (9 - TOGGLES.length) / 2;

    private final MinecraftServer server;
    private final UUID owner;

    private VoidSettingsMenu(int containerId, Inventory playerInventory, MinecraftServer server, UUID owner) {
        super(containerId, playerInventory, 2);
        this.server = server;
        this.owner = owner;
        this.redrawType();
        this.redrawTime();
        this.redrawSpawn();
        this.redrawUsers();
        for (int i = 0; i < TOGGLES.length; ++i) {
            this.redrawToggle(i);
        }
    }

    /** Opens the settings of the given owner's void for the player. */
    public static void open(ServerPlayer player, UUID owner) {
        MinecraftServer server = player.getServer();
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new VoidSettingsMenu(id, inventory, server, owner), TITLE));
    }

    @Override
    protected void onClick(int slot, boolean rightClick, ServerPlayer player) {
        VoidSettings settings = VoidSettings.get(this.server);
        switch (slot) {
            case TYPE_SLOT -> this.server.execute(() -> VoidTypeMenu.open(player, "Change void type", this.type(), (chooser, type) -> {
                try {
                    VoidDimensions.changeType(this.server, this.owner, type);
                    chooser.sendSystemMessage(Component.literal("Your void is now " + title(type) + "."));
                } catch (IOException e) {
                    chooser.sendSystemMessage(Component.literal("Failed to change your void's type: " + e.getMessage()).withStyle(ChatFormatting.RED));
                }
            }));
            case TIME_SLOT -> {
                VoidTime time = settings.time(this.owner);
                settings.setTime(this.owner, rightClick ? time.previous() : time.next());
                this.server.forceTimeSynchronization();
                this.redrawTime();
                clickSound(player);
            }
            case SPAWN_SLOT -> {
                if (rightClick) {
                    settings.setSpawn(this.owner, null);
                    player.sendSystemMessage(Component.literal("Spawn point reset to 0 100 0."));
                } else if (!this.owner.equals(VoidDimensions.ownerOf(this.server, player.level().dimension()))) {
                    player.sendSystemMessage(Component.literal("Stand in your void to set its spawn point.").withStyle(ChatFormatting.RED));
                    return;
                } else if (!player.onGround()) {
                    player.sendSystemMessage(Component.literal("Stand on a block to set the spawn point.").withStyle(ChatFormatting.RED));
                    return;
                } else {
                    settings.setSpawn(this.owner, new VoidSettings.Spawn(player.position(), player.getYRot()));
                    player.sendSystemMessage(Component.literal("Spawn point set to " + describe(player.position()) + "."));
                }
                this.redrawSpawn();
                clickSound(player);
            }
            case USERS_SLOT -> this.server.execute(() -> VoidUsersMenu.open(player, this.owner, 0));
            default -> {
                int index = slot - FIRST_TOGGLE_SLOT;
                if (index >= 0 && index < TOGGLES.length) {
                    settings.toggle(this.owner, TOGGLES[index]);
                    this.redrawToggle(index);
                    clickSound(player);
                }
            }
        }
    }

    private VoidType type() {
        return VoidTypes.get(this.server).typeOf(this.owner);
    }

    private void redrawType() {
        VoidType type = this.type();
        ItemStack stack = new ItemStack(switch (type) {
            case OVERWORLD -> Items.GRASS_BLOCK;
            case NETHER -> Items.NETHERRACK;
            case END -> Items.END_STONE;
        });
        this.setIcon(TYPE_SLOT, icon(stack, plain("Type: ").withStyle(ChatFormatting.WHITE).append(plain(title(type)).withStyle(ChatFormatting.AQUA)),
                plain("Click to change.").withStyle(ChatFormatting.DARK_GRAY),
                plain("Everyone inside is moved out and back.").withStyle(ChatFormatting.DARK_GRAY)));
    }

    private void redrawTime() {
        VoidTime time = VoidSettings.get(this.server).time(this.owner);
        this.setIcon(TIME_SLOT, icon(new ItemStack(Items.CLOCK), plain("Time of day: ").withStyle(ChatFormatting.WHITE).append(plain(time.title()).withStyle(ChatFormatting.AQUA)),
                plain("Only Overworld voids have a sky.").withStyle(ChatFormatting.GRAY),
                plain("Left-click: next, right-click: previous.").withStyle(ChatFormatting.DARK_GRAY)));
    }

    private void redrawSpawn() {
        VoidSettings.Spawn spawn = VoidSettings.get(this.server).spawn(this.owner);
        String where = spawn == null ? "0 100 0 (default)" : describe(spawn.pos());
        this.setIcon(SPAWN_SLOT, icon(new ItemStack(Items.RECOVERY_COMPASS), plain("Spawn point: ").withStyle(ChatFormatting.WHITE).append(plain(where).withStyle(ChatFormatting.AQUA)),
                plain("Where visitors arrive.").withStyle(ChatFormatting.GRAY),
                plain("Left-click: set to where you stand.").withStyle(ChatFormatting.DARK_GRAY),
                plain("Right-click: reset to default.").withStyle(ChatFormatting.DARK_GRAY)));
    }

    private void redrawUsers() {
        int members = VoidAccess.get(this.server).members(this.owner).size();
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        ServerPlayer ownerPlayer = this.server.getPlayerList().getPlayer(this.owner);
        if (ownerPlayer != null) {
            stack.set(DataComponents.PROFILE, new ResolvableProfile(ownerPlayer.getGameProfile()));
        }
        this.setIcon(USERS_SLOT, icon(stack, plain("Users").withStyle(ChatFormatting.WHITE),
                plain(members + (members == 1 ? " player has" : " players have") + " access.").withStyle(ChatFormatting.GRAY),
                plain("Click to grant or revoke access.").withStyle(ChatFormatting.DARK_GRAY)));
    }

    private void redrawToggle(int index) {
        VoidSetting setting = TOGGLES[index];
        boolean on = VoidSettings.get(this.server).isEnabled(this.owner, setting);
        ItemStack stack = icon(new ItemStack(setting.icon()),
                plain(setting.title() + ": ").withStyle(ChatFormatting.WHITE).append(plain(on ? "ON" : "OFF").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED)),
                plain(setting.description()).withStyle(ChatFormatting.GRAY),
                plain("Click to toggle.").withStyle(ChatFormatting.DARK_GRAY));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, on);
        this.setIcon(FIRST_TOGGLE_SLOT + index, stack);
    }

    private static String title(VoidType type) {
        return switch (type) {
            case OVERWORLD -> "Overworld";
            case NETHER -> "Nether";
            case END -> "End";
        };
    }

    private static String describe(Vec3 pos) {
        return String.format("%d %d %d", (int) Math.floor(pos.x), (int) Math.floor(pos.y), (int) Math.floor(pos.z));
    }
}

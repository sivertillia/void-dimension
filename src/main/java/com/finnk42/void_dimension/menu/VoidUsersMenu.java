package com.finnk42.void_dimension.menu;

import com.finnk42.void_dimension.world.VoidAccess;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * Lets an owner grant and revoke access to their void with a click, like {@code /void access} and
 * {@code /void revoke}. Lists every online player plus offline players who currently have access (so
 * they can still be revoked), sorted by name, 45 to a page; the bottom row has back and page buttons.
 */
public class VoidUsersMenu extends VoidMenu {
    private static final int PER_PAGE = 45;
    private static final int BACK_SLOT = 45;
    private static final int PREVIOUS_SLOT = 48;
    private static final int NEXT_SLOT = 50;

    private final MinecraftServer server;
    private final UUID owner;
    private final List<GameProfile> users;
    private final int page;

    private VoidUsersMenu(int containerId, Inventory playerInventory, MinecraftServer server, UUID owner, List<GameProfile> users, int page) {
        super(containerId, playerInventory, 6);
        this.server = server;
        this.owner = owner;
        this.users = users;
        this.page = page;
        for (int slot = 0; slot < PER_PAGE && this.index(slot) < users.size(); ++slot) {
            this.redrawUser(slot);
        }
        this.setIcon(BACK_SLOT, icon(new ItemStack(Items.ARROW), plain("Back").withStyle(ChatFormatting.WHITE)));
        if (page > 0) {
            this.setIcon(PREVIOUS_SLOT, icon(new ItemStack(Items.PAPER), plain("Previous page").withStyle(ChatFormatting.WHITE)));
        }
        if (this.index(PER_PAGE) < users.size()) {
            this.setIcon(NEXT_SLOT, icon(new ItemStack(Items.PAPER), plain("Next page").withStyle(ChatFormatting.WHITE)));
        }
    }

    public static void open(ServerPlayer player, UUID owner, int page) {
        MinecraftServer server = player.getServer();
        List<GameProfile> users = listUsers(server, owner);
        int lastPage = Math.max(0, (users.size() - 1) / PER_PAGE);
        int shown = Math.max(0, Math.min(page, lastPage));
        Component title = Component.literal("Users (" + (shown + 1) + "/" + (lastPage + 1) + ")");
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new VoidUsersMenu(id, inventory, server, owner, users, shown), title));
    }

    /** Online players and offline members, without the owner, sorted by name. */
    private static List<GameProfile> listUsers(MinecraftServer server, UUID owner) {
        Map<UUID, GameProfile> users = new LinkedHashMap<>();
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            users.put(online.getUUID(), online.getGameProfile());
        }
        GameProfileCache cache = server.getProfileCache();
        Set<UUID> members = VoidAccess.get(server).members(owner);
        for (UUID member : members) {
            users.computeIfAbsent(member, id -> (cache == null ? Optional.<GameProfile>empty() : cache.get(id))
                    .orElseGet(() -> new GameProfile(id, id.toString())));
        }
        users.remove(owner);
        List<GameProfile> sorted = new ArrayList<>(users.values());
        sorted.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName()));
        return sorted;
    }

    @Override
    protected void onClick(int slot, boolean rightClick, ServerPlayer player) {
        if (slot < PER_PAGE) {
            if (this.index(slot) < this.users.size()) {
                GameProfile user = this.users.get(this.index(slot));
                VoidAccess access = VoidAccess.get(this.server);
                if (access.hasAccess(this.owner, user.getId())) {
                    access.revoke(this.owner, user.getId());
                } else {
                    access.grant(this.owner, user.getId());
                }
                this.redrawUser(slot);
                clickSound(player);
            }
        } else if (slot == BACK_SLOT) {
            this.server.execute(() -> VoidSettingsMenu.open(player, this.owner));
        } else if (slot == PREVIOUS_SLOT && this.page > 0) {
            this.server.execute(() -> open(player, this.owner, this.page - 1));
        } else if (slot == NEXT_SLOT && this.index(PER_PAGE) < this.users.size()) {
            this.server.execute(() -> open(player, this.owner, this.page + 1));
        }
    }

    private int index(int slot) {
        return this.page * PER_PAGE + slot;
    }

    private void redrawUser(int slot) {
        GameProfile user = this.users.get(this.index(slot));
        boolean allowed = VoidAccess.get(this.server).hasAccess(this.owner, user.getId());
        boolean online = this.server.getPlayerList().getPlayer(user.getId()) != null;
        ItemStack stack = icon(new ItemStack(Items.PLAYER_HEAD), plain(user.getName()).withStyle(ChatFormatting.WHITE),
                allowed ? plain("Has access").withStyle(ChatFormatting.GREEN) : plain("No access").withStyle(ChatFormatting.RED),
                plain(online ? "Online" : "Offline").withStyle(ChatFormatting.GRAY),
                plain(allowed ? "Click to revoke." : "Click to grant.").withStyle(ChatFormatting.DARK_GRAY));
        stack.set(DataComponents.PROFILE, new ResolvableProfile(user));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, allowed);
        this.setIcon(slot, stack);
    }
}

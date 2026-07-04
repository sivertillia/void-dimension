package com.finnk42.void_dimension.world;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Server-wide, persistent access lists for player voids: which players an owner has allowed into
 * their private void. Stored on the overworld's data storage. Owners always have access to their
 * own void, and operators bypass these lists entirely.
 */
public class VoidAccess extends SavedData {
    private static final String DATA_NAME = "void_dimension_access";

    private final Map<UUID, Set<UUID>> allowed = new HashMap<>();

    public static VoidAccess get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(VoidAccess::new, VoidAccess::load, null), DATA_NAME);
    }

    /** Grants a member access to the owner's void. Returns false if they already had it. */
    public boolean grant(UUID owner, UUID member) {
        boolean added = this.allowed.computeIfAbsent(owner, k -> new HashSet<>()).add(member);
        if (added) {
            this.setDirty();
        }
        return added;
    }

    /** Revokes a member's access. Returns false if they did not have it. */
    public boolean revoke(UUID owner, UUID member) {
        Set<UUID> members = this.allowed.get(owner);
        boolean removed = members != null && members.remove(member);
        if (removed) {
            this.setDirty();
        }
        return removed;
    }

    /** The players an owner has granted access to (a copy; never null). */
    public Set<UUID> members(UUID owner) {
        Set<UUID> members = this.allowed.get(owner);
        return members == null ? Set.of() : new HashSet<>(members);
    }

    /** True if member is the owner or has been granted access to the owner's void. */
    public boolean hasAccess(UUID owner, UUID member) {
        if (owner.equals(member)) {
            return true;
        }
        Set<UUID> members = this.allowed.get(owner);
        return members != null && members.contains(member);
    }

    private static VoidAccess load(CompoundTag tag, HolderLookup.Provider registries) {
        VoidAccess data = new VoidAccess();
        ListTag owners = tag.getList("Access", Tag.TAG_COMPOUND);
        for (int i = 0; i < owners.size(); ++i) {
            CompoundTag ownerTag = owners.getCompound(i);
            UUID owner = ownerTag.getUUID("Owner");
            Set<UUID> members = new HashSet<>();
            ListTag memberList = ownerTag.getList("Members", Tag.TAG_COMPOUND);
            for (int j = 0; j < memberList.size(); ++j) {
                members.add(memberList.getCompound(j).getUUID("Member"));
            }
            data.allowed.put(owner, members);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag owners = new ListTag();
        for (Map.Entry<UUID, Set<UUID>> entry : this.allowed.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            CompoundTag ownerTag = new CompoundTag();
            ownerTag.putUUID("Owner", entry.getKey());
            ListTag memberList = new ListTag();
            for (UUID member : entry.getValue()) {
                CompoundTag memberTag = new CompoundTag();
                memberTag.putUUID("Member", member);
                memberList.add(memberTag);
            }
            ownerTag.put("Members", memberList);
            owners.add(ownerTag);
        }
        tag.put("Access", owners);
        return tag;
    }
}

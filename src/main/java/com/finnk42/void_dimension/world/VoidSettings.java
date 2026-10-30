package com.finnk42.void_dimension.world;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Server-wide, persistent per-owner settings of player voids: the {@link VoidSetting} toggles, the
 * {@link VoidTime} and a custom spawn point. Only values the owner changed are stored; everything else
 * falls back to its default. Stored on the overworld's data storage.
 */
public class VoidSettings extends SavedData {
    private static final String DATA_NAME = "void_dimension_settings";

    /** Where visitors land in a void, and which way they face. */
    public record Spawn(Vec3 pos, float yaw) {
    }

    private final Map<UUID, CompoundTag> owners = new HashMap<>();

    public static VoidSettings get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(VoidSettings::new, VoidSettings::load, null), DATA_NAME);
    }

    public boolean isEnabled(UUID owner, VoidSetting setting) {
        CompoundTag tag = this.owners.get(owner);
        return tag != null && tag.contains(setting.name(), Tag.TAG_BYTE) ? tag.getBoolean(setting.name()) : setting.defaultValue();
    }

    /** Flips a setting for the owner's void and returns its new state. */
    public boolean toggle(UUID owner, VoidSetting setting) {
        boolean on = !this.isEnabled(owner, setting);
        this.edit(owner).putBoolean(setting.name(), on);
        return on;
    }

    public VoidTime time(UUID owner) {
        CompoundTag tag = this.owners.get(owner);
        return tag == null ? VoidTime.NOON : VoidTime.byName(tag.getString("Time"));
    }

    public void setTime(UUID owner, VoidTime time) {
        this.edit(owner).putString("Time", time.name());
    }

    /** The owner's custom spawn point, or null to use the default landing platform. */
    @Nullable
    public Spawn spawn(UUID owner) {
        CompoundTag tag = this.owners.get(owner);
        if (tag == null || !tag.contains("Spawn", Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag spawn = tag.getCompound("Spawn");
        return new Spawn(new Vec3(spawn.getDouble("X"), spawn.getDouble("Y"), spawn.getDouble("Z")), spawn.getFloat("Yaw"));
    }

    /** Sets the owner's spawn point; null resets it to the default landing platform. */
    public void setSpawn(UUID owner, @Nullable Spawn spawn) {
        if (spawn == null) {
            this.edit(owner).remove("Spawn");
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putDouble("X", spawn.pos().x);
        tag.putDouble("Y", spawn.pos().y);
        tag.putDouble("Z", spawn.pos().z);
        tag.putFloat("Yaw", spawn.yaw());
        this.edit(owner).put("Spawn", tag);
    }

    public void remove(UUID owner) {
        if (this.owners.remove(owner) != null) {
            this.setDirty();
        }
    }

    /** The owner's stored settings, for writing; marks the data dirty. */
    private CompoundTag edit(UUID owner) {
        this.setDirty();
        return this.owners.computeIfAbsent(owner, k -> new CompoundTag());
    }

    private static VoidSettings load(CompoundTag tag, HolderLookup.Provider registries) {
        VoidSettings data = new VoidSettings();
        ListTag owners = tag.getList("Owners", Tag.TAG_COMPOUND);
        for (int i = 0; i < owners.size(); ++i) {
            CompoundTag ownerTag = owners.getCompound(i);
            data.owners.put(ownerTag.getUUID("Owner"), ownerTag.getCompound("Settings"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag owners = new ListTag();
        for (Map.Entry<UUID, CompoundTag> entry : this.owners.entrySet()) {
            CompoundTag ownerTag = new CompoundTag();
            ownerTag.putUUID("Owner", entry.getKey());
            ownerTag.put("Settings", entry.getValue());
            owners.add(ownerTag);
        }
        tag.put("Owners", owners);
        return tag;
    }
}

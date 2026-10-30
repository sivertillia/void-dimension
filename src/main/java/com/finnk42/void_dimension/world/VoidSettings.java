package com.finnk42.void_dimension.world;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Server-wide, persistent record of which {@link VoidSetting}s each owner has turned on for their
 * void. Stored on the overworld's data storage.
 */
public class VoidSettings extends SavedData {
    private static final String DATA_NAME = "void_dimension_settings";

    private final Map<UUID, Set<VoidSetting>> enabled = new HashMap<>();

    public static VoidSettings get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(VoidSettings::new, VoidSettings::load, null), DATA_NAME);
    }

    public boolean isEnabled(UUID owner, VoidSetting setting) {
        Set<VoidSetting> settings = this.enabled.get(owner);
        return settings != null && settings.contains(setting);
    }

    /** Flips a setting for the owner's void and returns its new state. */
    public boolean toggle(UUID owner, VoidSetting setting) {
        Set<VoidSetting> settings = this.enabled.computeIfAbsent(owner, k -> EnumSet.noneOf(VoidSetting.class));
        boolean on = settings.add(setting) || !settings.remove(setting);
        this.setDirty();
        return on;
    }

    public void remove(UUID owner) {
        if (this.enabled.remove(owner) != null) {
            this.setDirty();
        }
    }

    private static VoidSettings load(CompoundTag tag, HolderLookup.Provider registries) {
        VoidSettings data = new VoidSettings();
        ListTag owners = tag.getList("Settings", Tag.TAG_COMPOUND);
        for (int i = 0; i < owners.size(); ++i) {
            CompoundTag ownerTag = owners.getCompound(i);
            Set<VoidSetting> settings = EnumSet.noneOf(VoidSetting.class);
            ListTag names = ownerTag.getList("Enabled", Tag.TAG_STRING);
            for (int j = 0; j < names.size(); ++j) {
                VoidSetting setting = VoidSetting.byName(names.getString(j));
                if (setting != null) {
                    settings.add(setting);
                }
            }
            data.enabled.put(ownerTag.getUUID("Owner"), settings);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag owners = new ListTag();
        for (Map.Entry<UUID, Set<VoidSetting>> entry : this.enabled.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            CompoundTag ownerTag = new CompoundTag();
            ownerTag.putUUID("Owner", entry.getKey());
            ListTag names = new ListTag();
            for (VoidSetting setting : entry.getValue()) {
                names.add(StringTag.valueOf(setting.name()));
            }
            ownerTag.put("Enabled", names);
            owners.add(ownerTag);
        }
        tag.put("Settings", owners);
        return tag;
    }
}

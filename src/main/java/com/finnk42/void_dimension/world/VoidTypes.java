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

/**
 * Server-wide, persistent record of which {@link VoidType} each owner chose for their void, so the
 * void is rebuilt with the same type after a restart. Stored on the overworld's data storage. Voids
 * created before types existed have no entry and are treated as {@link VoidType#END}.
 */
public class VoidTypes extends SavedData {
    private static final String DATA_NAME = "void_dimension_types";

    private final Map<UUID, VoidType> types = new HashMap<>();

    public static VoidTypes get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(VoidTypes::new, VoidTypes::load, null), DATA_NAME);
    }

    /** True if the owner has already chosen a type for their void. */
    public boolean has(UUID owner) {
        return this.types.containsKey(owner);
    }

    /** The owner's chosen type, or {@link VoidType#END} if none was recorded. */
    public VoidType typeOf(UUID owner) {
        return this.types.getOrDefault(owner, VoidType.END);
    }

    public void set(UUID owner, VoidType type) {
        if (this.types.put(owner, type) != type) {
            this.setDirty();
        }
    }

    public void remove(UUID owner) {
        if (this.types.remove(owner) != null) {
            this.setDirty();
        }
    }

    private static VoidTypes load(CompoundTag tag, HolderLookup.Provider registries) {
        VoidTypes data = new VoidTypes();
        ListTag entries = tag.getList("Types", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); ++i) {
            CompoundTag entry = entries.getCompound(i);
            data.types.put(entry.getUUID("Owner"), VoidType.byName(entry.getString("Type")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (Map.Entry<UUID, VoidType> type : this.types.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Owner", type.getKey());
            entry.putString("Type", type.getValue().name());
            entries.add(entry);
        }
        tag.put("Types", entries);
        return tag;
    }
}

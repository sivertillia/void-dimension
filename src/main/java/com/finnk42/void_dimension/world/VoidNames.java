package com.finnk42.void_dimension.world;

import java.util.HashMap;
import java.util.Locale;
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
 * Server-wide, persistent record of which dimension path ({@code void_dimension:<path>}) belongs to
 * which owner. Paths are the owner's nick in lower case; keeping the mapping lets a void follow its
 * owner across nick changes and keeps two owners from ever sharing a path. Stored on the overworld's
 * data storage.
 */
public class VoidNames extends SavedData {
    private static final String DATA_NAME = "void_dimension_names";
    /** Paths of the datapack template dimensions, which a player's void must never take. */
    private static final Set<String> RESERVED = Set.of("the_void", "the_void_overworld", "the_void_nether");

    private final Map<UUID, String> paths = new HashMap<>();

    public static VoidNames get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(VoidNames::new, VoidNames::load, null), DATA_NAME);
    }

    public static boolean isReserved(String path) {
        return RESERVED.contains(path);
    }

    /** The path recorded for this owner's void, or null if they have none yet. */
    public String pathOf(UUID owner) {
        return this.paths.get(owner);
    }

    /** The owner of the void at this path, or null if no owner is recorded for it. */
    public UUID ownerOf(String path) {
        for (Map.Entry<UUID, String> entry : this.paths.entrySet()) {
            if (entry.getValue().equals(path)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * The path this owner's void should have: their nick in lower case. If that is a template's path or
     * already belongs to someone else (e.g. a nick freed by a rename), a short piece of the UUID is
     * appended to keep it unique.
     */
    public String wantedPath(UUID owner, String playerName) {
        String base = playerName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        UUID holder = this.ownerOf(base);
        if (base.isEmpty() || isReserved(base) || (holder != null && !holder.equals(owner))) {
            return (base.isEmpty() ? "" : base + "_") + owner.toString().substring(0, 8);
        }
        return base;
    }

    public void assign(UUID owner, String path) {
        if (!path.equals(this.paths.put(owner, path))) {
            this.setDirty();
        }
    }

    public void remove(UUID owner) {
        if (this.paths.remove(owner) != null) {
            this.setDirty();
        }
    }

    private static VoidNames load(CompoundTag tag, HolderLookup.Provider registries) {
        VoidNames data = new VoidNames();
        ListTag entries = tag.getList("Names", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); ++i) {
            CompoundTag entry = entries.getCompound(i);
            data.paths.put(entry.getUUID("Owner"), entry.getString("Path"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (Map.Entry<UUID, String> path : this.paths.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Owner", path.getKey());
            entry.putString("Path", path.getValue());
            entries.add(entry);
        }
        tag.put("Names", entries);
        return tag;
    }
}

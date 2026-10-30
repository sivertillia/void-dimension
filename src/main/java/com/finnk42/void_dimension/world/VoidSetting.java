package com.finnk42.void_dimension.world;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * An on/off option an owner can toggle for their own void with {@code /void setting local}. Every
 * setting is off until the owner turns it on. Adding a constant here is enough to show it in the menu;
 * the behaviour itself lives wherever the setting is checked.
 */
public enum VoidSetting {
    /** Falling out of the bottom of the void drops you back in from the top instead of killing you. */
    FALL_LOOP(Items.FEATHER, "Fall loop", "Fall out of the bottom, reappear at the top.");

    private final Item icon;
    private final String title;
    private final String description;

    VoidSetting(Item icon, String title, String description) {
        this.icon = icon;
        this.title = title;
        this.description = description;
    }

    public Item icon() {
        return this.icon;
    }

    public String title() {
        return this.title;
    }

    public String description() {
        return this.description;
    }

    /** Parses a stored name, or null for settings that no longer exist. */
    public static VoidSetting byName(String name) {
        for (VoidSetting setting : values()) {
            if (setting.name().equals(name)) {
                return setting;
            }
        }
        return null;
    }
}

package com.finnk42.void_dimension.world;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * An on/off option an owner can toggle for their own void with {@code /void setting local}. Adding a
 * constant here is enough to show it in the menu; the behaviour itself lives in {@link VoidRules}.
 */
public enum VoidSetting {
    /** Falling out of the bottom of the void drops you back in from the top instead of killing you. */
    FALL_LOOP(Items.FEATHER, "Fall loop", "Fall out of the bottom, reappear at the top.", false),
    /** Players given access may break and place blocks; otherwise only the owner (and operators) can. */
    GUESTS_CAN_BUILD(Items.BRICKS, "Guests can build", "Players you gave access can change blocks.", true),
    /** Players may hurt each other. */
    PVP(Items.IRON_SWORD, "PvP", "Players can hurt each other here.", true),
    /** Anyone who dies here keeps their items and experience. */
    KEEP_INVENTORY(Items.TOTEM_OF_UNDYING, "Keep inventory", "Keep items and XP when dying here.", false),
    /** The owner is told when someone enters or leaves their void. */
    VISITOR_NOTICES(Items.BELL, "Visitor notices", "Tell you when someone enters or leaves.", true);

    private final Item icon;
    private final String title;
    private final String description;
    private final boolean defaultValue;

    VoidSetting(Item icon, String title, String description, boolean defaultValue) {
        this.icon = icon;
        this.title = title;
        this.description = description;
        this.defaultValue = defaultValue;
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

    /** The value used until the owner changes it; defaults match how voids behaved before settings existed. */
    public boolean defaultValue() {
        return this.defaultValue;
    }
}

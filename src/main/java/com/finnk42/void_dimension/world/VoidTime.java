package com.finnk42.void_dimension.world;

/**
 * The time of day an owner picks for their void. Only Overworld voids show it — Nether and End voids
 * have a fixed sky of their own — but it is stored for every void so it survives a type change.
 */
public enum VoidTime {
    NOON("Noon (12:00)", 6000),
    SUNSET("Sunset", 12000),
    MIDNIGHT("Midnight", 18000),
    SUNRISE("Sunrise", 23000),
    /** Follows the overworld's normal day/night cycle. */
    CYCLE("Day/night cycle", -1);

    private final String title;
    private final long dayTime;

    VoidTime(String title, long dayTime) {
        this.title = title;
        this.dayTime = dayTime;
    }

    public String title() {
        return this.title;
    }

    public boolean isFixed() {
        return this.dayTime >= 0;
    }

    public long dayTime() {
        return this.dayTime;
    }

    public VoidTime next() {
        return values()[(this.ordinal() + 1) % values().length];
    }

    public VoidTime previous() {
        return values()[(this.ordinal() + values().length - 1) % values().length];
    }

    /** Parses a stored name, falling back to {@link #NOON} for unknown values. */
    public static VoidTime byName(String name) {
        for (VoidTime time : values()) {
            if (time.name().equals(name)) {
                return time;
            }
        }
        return NOON;
    }
}

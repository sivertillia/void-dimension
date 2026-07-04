package com.finnk42.void_dimension;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.IntValue MONOLITH_LIGHT_LEVEL;
    public static final ModConfigSpec.DoubleValue MONOLITH_STRENGTH;
    public static final ModConfigSpec.BooleanValue ENABLE_TELEPORTER_GLOW;
    static final ModConfigSpec SPEC;

    static {
        BUILDER.push("Monolith Settings");
        MONOLITH_LIGHT_LEVEL = BUILDER.comment("How bright the monolith is when active (0-15)").defineInRange("lightLevel", 15, 0, 15);
        MONOLITH_STRENGTH = BUILDER.comment("The mining hardness of the monolith").defineInRange("hardness", 5.0, 0.1, 100.0);
        BUILDER.pop();
        BUILDER.push("Teleporter Settings");
        ENABLE_TELEPORTER_GLOW = BUILDER.comment("Whether the teleporter should glow when active").define("enableGlow", true);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}


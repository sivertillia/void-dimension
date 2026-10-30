package com.finnk42.void_dimension.world;

import net.minecraft.resources.ResourceLocation;

/**
 * The look and feel a player picks for their void the first time they enter it. Each type points at a
 * datapack-defined template dimension whose dimension type and generator the void is cloned from. All
 * templates are empty and their biomes have no spawners, so mobs never spawn naturally in any of them.
 */
public enum VoidType {
    /** Eternal noon under a blue sky. */
    OVERWORLD("the_void_overworld"),
    /** Red fog, ultrawarm (water evaporates), no sky. */
    NETHER("the_void_nether"),
    /** The original dark void; also the type of every void created before types existed. */
    END("the_void");

    private final ResourceLocation template;

    VoidType(String templatePath) {
        this.template = ResourceLocation.fromNamespaceAndPath(VoidDimensions.NAMESPACE, templatePath);
    }

    public ResourceLocation template() {
        return this.template;
    }

    /** Parses a stored name, falling back to {@link #END} for unknown values. */
    public static VoidType byName(String name) {
        for (VoidType type : values()) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        return END;
    }
}

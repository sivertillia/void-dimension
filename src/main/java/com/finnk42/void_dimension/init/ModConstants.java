package com.finnk42.void_dimension.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public class ModConstants {
    public static final ResourceKey<Level> VOID_DIMENSION_KEY = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("void_dimension", "the_void"));
}

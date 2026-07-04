package com.finnk42.void_dimension.command;

import com.finnk42.void_dimension.world.VoidAccess;
import com.finnk42.void_dimension.world.VoidDimensions;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.level.Level;

/**
 * Void dimension commands, all under {@code /void}:
 * <ul>
 *   <li>{@code /void access <player>} — anyone; allow a player into your own void.</li>
 *   <li>{@code /void revoke <player>} — anyone; remove a player's access to your void.</li>
 *   <li>{@code /void my list} — anyone; list who you have granted access to your own void.</li>
 *   <li>{@code /void tp <player>} — operators only; teleport into any existing player's void.</li>
 *   <li>{@code /void list} — operators only; list every void that currently exists.</li>
 * </ul>
 */
public final class VoidCommand {
    private static final SimpleCommandExceptionType ERROR_SINGLE_PLAYER =
            new SimpleCommandExceptionType(Component.literal("Specify exactly one target player."));
    private static final SimpleCommandExceptionType ERROR_SELF =
            new SimpleCommandExceptionType(Component.literal("You always have access to your own void."));
    private static final DynamicCommandExceptionType ERROR_NO_VOID =
            new DynamicCommandExceptionType(name -> Component.literal(name + " has no void dimension yet."));
    private static final DynamicCommandExceptionType ERROR_NO_ACCESS =
            new DynamicCommandExceptionType(name -> Component.literal(name + " has not allowed you into their void."));

    private VoidCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("void")
                // Everyone: manage access to your own void and visit voids you are allowed into.
                .then(Commands.literal("access")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(ctx -> setAccess(ctx.getSource(), single(ctx), true))))
                .then(Commands.literal("revoke")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(ctx -> setAccess(ctx.getSource(), single(ctx), false))))
                .then(Commands.literal("my")
                        .then(Commands.literal("list")
                                .executes(ctx -> myAccessList(ctx.getSource()))))
                // Operators only: teleport into any existing player's void.
                .then(Commands.literal("tp")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(ctx -> visit(ctx.getSource(), single(ctx)))))
                // Operators only: list every void that exists.
                .then(Commands.literal("list")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> listVoids(ctx.getSource()))));
    }

    private static GameProfile single(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "player");
        if (profiles.size() != 1) {
            throw ERROR_SINGLE_PLAYER.create();
        }
        return profiles.iterator().next();
    }

    private static int setAccess(CommandSourceStack source, GameProfile target, boolean grant) throws CommandSyntaxException {
        ServerPlayer runner = source.getPlayerOrException();
        if (target.getId().equals(runner.getUUID())) {
            throw ERROR_SELF.create();
        }
        VoidAccess access = VoidAccess.get(source.getServer());
        String name = target.getName();
        boolean changed = grant
                ? access.grant(runner.getUUID(), target.getId())
                : access.revoke(runner.getUUID(), target.getId());
        String message = grant
                ? (changed ? name + " can now enter your void." : name + " already has access to your void.")
                : (changed ? name + " can no longer enter your void." : name + " did not have access to your void.");
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int visit(CommandSourceStack source, GameProfile target) throws CommandSyntaxException {
        ServerPlayer runner = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        ResourceKey<Level> key = VoidDimensions.keyForPlayer(target.getId());
        if (!VoidDimensions.exists(server, key)) {
            throw ERROR_NO_VOID.create(target.getName());
        }
        boolean allowed = source.hasPermission(Commands.LEVEL_GAMEMASTERS) // operators bypass the access rule
                || VoidAccess.get(server).hasAccess(target.getId(), runner.getUUID());
        if (!allowed) {
            throw ERROR_NO_ACCESS.create(target.getName());
        }
        // Defer: loading a dimension mutates the server's level list, which must not happen while it
        // is being iterated (e.g. if this command runs from a command block during a level tick).
        server.execute(() -> {
            ServerLevel voidLevel = VoidDimensions.getOrCreate(server, key);
            VoidDimensions.sendToVoid(runner, voidLevel);
        });
        source.sendSuccess(() -> Component.literal("Teleporting to " + target.getName() + "'s void..."), false);
        return 1;
    }

    private static int listVoids(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        GameProfileCache cache = server.getProfileCache();
        List<String> names = new ArrayList<>();
        for (ResourceKey<Level> key : VoidDimensions.listExisting(server)) {
            names.add(resolveName(cache, key.location().getPath()));
        }
        if (names.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No void dimensions exist yet."), false);
            return 0;
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        source.sendSuccess(() -> Component.literal("Existing voids (" + names.size() + "): " + String.join(", ", names)), false);
        return names.size();
    }

    private static int myAccessList(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer runner = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        Set<UUID> members = VoidAccess.get(server).members(runner.getUUID());
        if (members.isEmpty()) {
            source.sendSuccess(() -> Component.literal("You haven't given anyone access to your void."), false);
            return 0;
        }
        GameProfileCache cache = server.getProfileCache();
        List<String> names = new ArrayList<>();
        for (UUID id : members) {
            names.add(resolveName(cache, id.toString()));
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        source.sendSuccess(() -> Component.literal("Players with access to your void (" + names.size() + "): " + String.join(", ", names)), false);
        return names.size();
    }

    /** Resolves a UUID string to a player name via the profile cache, falling back to the raw id. */
    private static String resolveName(GameProfileCache cache, String uuid) {
        if (cache != null) {
            try {
                return cache.get(UUID.fromString(uuid)).map(GameProfile::getName).orElse(uuid);
            } catch (IllegalArgumentException notAUuid) {
                // fall through
            }
        }
        return uuid;
    }
}

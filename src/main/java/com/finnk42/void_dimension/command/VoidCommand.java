package com.finnk42.void_dimension.command;

import com.finnk42.void_dimension.world.VoidDimensions;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * {@code /voidtp <player>} — teleports the command's runner into the target player's private void
 * dimension. Restricted to operators (permission level 2). Works for offline players too, since the
 * void is keyed by the target's UUID.
 */
public final class VoidCommand {
    private static final SimpleCommandExceptionType ERROR_SINGLE_PLAYER =
            new SimpleCommandExceptionType(Component.literal("Specify exactly one target player."));
    private static final DynamicCommandExceptionType ERROR_NO_VOID =
            new DynamicCommandExceptionType(name -> Component.literal(name + " has no void dimension yet."));

    private VoidCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("voidtp")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .executes(ctx -> teleport(ctx.getSource(), GameProfileArgument.getGameProfiles(ctx, "player")))));
    }

    private static int teleport(CommandSourceStack source, Collection<GameProfile> profiles) throws CommandSyntaxException {
        ServerPlayer runner = source.getPlayerOrException();
        if (profiles.size() != 1) {
            throw ERROR_SINGLE_PLAYER.create();
        }
        GameProfile target = profiles.iterator().next();
        MinecraftServer server = source.getServer();
        ResourceKey<Level> key = VoidDimensions.keyForPlayer(target.getId());
        // Only visit an existing void — never fabricate a new one for a player who never entered theirs.
        if (!VoidDimensions.exists(server, key)) {
            throw ERROR_NO_VOID.create(target.getName());
        }
        // Defer: loading a dimension mutates the server's level list, which must not happen while it
        // is being iterated (e.g. if this command runs from a command block during a level tick).
        server.execute(() -> {
            ServerLevel voidLevel = VoidDimensions.getOrCreate(server, key);
            VoidDimensions.sendToVoid(runner, voidLevel);
        });
        source.sendSuccess(() -> Component.literal("Teleporting to " + target.getName() + "'s void..."), true);
        return 1;
    }
}

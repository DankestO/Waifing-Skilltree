package com.waifingskilltree.command;

import com.waifingskilltree.logic.SkillManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import com.mojang.brigadier.arguments.IntegerArgumentType;

/**
 * /skilltree respec            resets your own skills (returns the paid skill points)
 * /skilltree respec <players>  resets the skills of the given players
 * /skilltree points <players> add|remove <n>   gives or takes bonus skill points
 * /skilltree points <player>                   shows available / earned points
 * (trees are reloaded with vanilla /reload)
 */
public final class WaifingSkilltreeCommands {
    private WaifingSkilltreeCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("skilltree").requires(s -> s.hasPermission(2))
                .then(Commands.literal("respec")
                        .executes(c -> {
                            SkillManager.respec(c.getSource().getPlayerOrException());
                            return 1;
                        })
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(c -> {
                                    var list = EntityArgument.getPlayers(c, "players");
                                    list.forEach(SkillManager::respec);
                                    return list.size();
                                })))
                .then(Commands.literal("points")
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(c -> {
                                    for (var p : EntityArgument.getPlayers(c, "players")) {
                                        var s = SkillManager.get(p);
                                        if (s == null) continue;
                                        c.getSource().sendSuccess(() -> Component.translatable("waifingskilltree.msg.points_info",
                                                p.getDisplayName(), SkillManager.available(s), s.spent(), s.highestLevel), false);
                                    }
                                    return 1;
                                })
                                .then(Commands.literal("add").then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(c -> points(c, IntegerArgumentType.getInteger(c, "amount")))))
                                .then(Commands.literal("remove").then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(c -> points(c, -IntegerArgumentType.getInteger(c, "amount"))))))));
    }

    private static int points(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, int amount) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var list = EntityArgument.getPlayers(c, "players");
        list.forEach(p -> SkillManager.addPoints(p, amount));
        c.getSource().sendSuccess(() -> Component.translatable("waifingskilltree.msg.points_changed", amount, list.size()), true);
        return list.size();
    }
}

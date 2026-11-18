package com.waifingskilltree.logic;

import com.waifingskilltree.data.ClickRecipes;
import com.waifingskilltree.data.TreeStorage;
import com.waifingskilltree.net.S2CClickRecipes;
import com.waifingskilltree.net.Network;
import com.waifingskilltree.net.S2CTrees;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Broadcasts trees to players after datapacks load/reload. */
public final class TreeAdmin {
    private TreeAdmin() {}

    /** Sends the trees to everyone and revalidates each player's skills. */
    public static void broadcast(MinecraftServer server) {
        Network.sendToAll(new S2CTrees(TreeStorage.encodeAll()));
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) SkillManager.validate(sp);
    }

    public static void sendTo(ServerPlayer p) {
        Network.sendTo(p, new S2CTrees(TreeStorage.encodeAll()));
        Network.sendTo(p, new S2CClickRecipes(ClickRecipes.encode()));
    }

    public static void broadcastClickRecipes() {
        Network.sendToAll(new S2CClickRecipes(ClickRecipes.encode()));
    }
}

package com.waifingskilltree.net;

import com.waifingskilltree.logic.SkillManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> servidor: tomar un nodo. */
public class C2SUnlock {
    private final String tree, node;

    public C2SUnlock(String tree, String node) {
        this.tree = tree;
        this.node = node;
    }

    public static void encode(C2SUnlock m, FriendlyByteBuf buf) {
        buf.writeUtf(m.tree);
        buf.writeUtf(m.node);
    }

    public static C2SUnlock decode(FriendlyByteBuf buf) {
        return new C2SUnlock(buf.readUtf(), buf.readUtf());
    }

    public static void handle(C2SUnlock m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) SkillManager.unlock(p, m.tree, m.node);
        });
        ctx.get().setPacketHandled(true);
    }
}

package com.waifingskilltree.net;

import com.waifingskilltree.WaifingSkilltreeConfig;
import com.waifingskilltree.logic.SkillManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> servidor: devolver un nodo (si la config lo permite). */
public class C2SRefund {
    private final String tree, node;

    public C2SRefund(String tree, String node) {
        this.tree = tree;
        this.node = node;
    }

    public static void encode(C2SRefund m, FriendlyByteBuf buf) {
        buf.writeUtf(m.tree);
        buf.writeUtf(m.node);
    }

    public static C2SRefund decode(FriendlyByteBuf buf) {
        return new C2SRefund(buf.readUtf(), buf.readUtf());
    }

    public static void handle(C2SRefund m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null && (WaifingSkilltreeConfig.ALLOW_SELF_REFUND.get() || p.hasPermissions(2))) {
                SkillManager.refund(p, m.tree, m.node);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

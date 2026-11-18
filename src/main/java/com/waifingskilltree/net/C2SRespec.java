package com.waifingskilltree.net;

import com.waifingskilltree.WaifingSkilltreeConfig;
import com.waifingskilltree.logic.SkillManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: reset all skills (only if allowSelfRefund is on, or the sender is an op). */
public class C2SRespec {
    public static void encode(C2SRespec m, FriendlyByteBuf buf) {}

    public static C2SRespec decode(FriendlyByteBuf buf) {
        return new C2SRespec();
    }

    public static void handle(C2SRespec m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null && (WaifingSkilltreeConfig.ALLOW_SELF_REFUND.get() || p.hasPermissions(2))) {
                SkillManager.respec(p);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

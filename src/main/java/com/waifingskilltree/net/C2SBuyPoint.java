package com.waifingskilltree.net;

import com.waifingskilltree.logic.SkillManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: buy one skill point with experience levels (price grows with every purchase). */
public class C2SBuyPoint {
    public static void encode(C2SBuyPoint m, FriendlyByteBuf buf) {}

    public static C2SBuyPoint decode(FriendlyByteBuf buf) {
        return new C2SBuyPoint();
    }

    public static void handle(C2SBuyPoint m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) SkillManager.buyPoint(p);
        });
        ctx.get().setPacketHandled(true);
    }
}

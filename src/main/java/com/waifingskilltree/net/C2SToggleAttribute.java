package com.waifingskilltree.net;

import com.waifingskilltree.logic.SkillManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: switch the tree bonuses of one attribute off (or on again). */
public class C2SToggleAttribute {
    private final String attribute;
    private final boolean off;

    public C2SToggleAttribute(String attribute, boolean off) {
        this.attribute = attribute;
        this.off = off;
    }

    public static void encode(C2SToggleAttribute m, FriendlyByteBuf buf) {
        buf.writeUtf(m.attribute);
        buf.writeBoolean(m.off);
    }

    public static C2SToggleAttribute decode(FriendlyByteBuf buf) {
        return new C2SToggleAttribute(buf.readUtf(), buf.readBoolean());
    }

    public static void handle(C2SToggleAttribute m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) SkillManager.setAttributeDisabled(p, m.attribute, m.off);
        });
        ctx.get().setPacketHandled(true);
    }
}

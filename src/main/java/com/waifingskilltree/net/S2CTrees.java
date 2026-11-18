package com.waifingskilltree.net;

import com.waifingskilltree.client.ClientTrees;
import com.waifingskilltree.data.TreeStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: all trees (gzip-compressed JSON). */
public class S2CTrees {
    private final byte[] data;

    public S2CTrees(byte[] data) {
        this.data = data;
    }

    public static void encode(S2CTrees m, FriendlyByteBuf buf) {
        buf.writeByteArray(m.data);
    }

    public static S2CTrees decode(FriendlyByteBuf buf) {
        return new S2CTrees(buf.readByteArray());
    }

    public static void handle(S2CTrees m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientTrees.setTrees(TreeStorage.decodeAll(m.data))));
        ctx.get().setPacketHandled(true);
    }
}

package com.waifingskilltree.net;

import com.waifingskilltree.data.ClickRecipes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: every right-click recipe (so the client knows which clicks to cancel). */
public class S2CClickRecipes {
    private final String json;

    public S2CClickRecipes(String json) {
        this.json = json;
    }

    public static void encode(S2CClickRecipes m, FriendlyByteBuf buf) {
        buf.writeUtf(m.json, 1 << 20);
    }

    public static S2CClickRecipes decode(FriendlyByteBuf buf) {
        return new S2CClickRecipes(buf.readUtf(1 << 20));
    }

    public static void handle(S2CClickRecipes m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // in single player the integrated server already filled the same map
            if (!ctx.get().getNetworkManager().isMemoryConnection()) ClickRecipes.set(ClickRecipes.decode(m.json));
        });
        ctx.get().setPacketHandled(true);
    }
}

package com.waifingskilltree.net;

import com.waifingskilltree.client.ClientTrees;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.function.Supplier;

/** Server -> client: taken nodes (levels), locked nodes, self refunds allowed, available skill points and chosen exclusive trees. */
public class S2CPlayerData {
    private final Map<String, Map<String, Integer>> taken;
    private final Set<String> gated;
    private final boolean selfRefund;
    private final int points;
    private final Map<String, String> primary;
    private final int buyCost;

    public S2CPlayerData(Map<String, Map<String, Integer>> taken, Set<String> gated, boolean selfRefund, int points, Map<String, String> primary, int buyCost) {
        this.buyCost = buyCost;
        this.taken = taken;
        this.gated = gated;
        this.selfRefund = selfRefund;
        this.points = points;
        this.primary = primary;
    }

    public static void encode(S2CPlayerData m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.taken.size());
        m.taken.forEach((tree, nodes) -> {
            buf.writeUtf(tree);
            buf.writeVarInt(nodes.size());
            nodes.forEach((n, c) -> {
                buf.writeUtf(n);
                buf.writeVarInt(c);
            });
        });
        buf.writeVarInt(m.gated.size());
        m.gated.forEach(buf::writeUtf);
        buf.writeBoolean(m.selfRefund);
        buf.writeVarInt(m.points);
        buf.writeVarInt(m.primary.size());
        m.primary.forEach((g, t) -> { buf.writeUtf(g); buf.writeUtf(t); });
        buf.writeVarInt(m.buyCost + 2);
    }

    public static S2CPlayerData decode(FriendlyByteBuf buf) {
        Map<String, Map<String, Integer>> taken = new HashMap<>();
        int trees = buf.readVarInt();
        for (int i = 0; i < trees; i++) {
            String tree = buf.readUtf();
            Map<String, Integer> nodes = new HashMap<>();
            int n = buf.readVarInt();
            for (int j = 0; j < n; j++) nodes.put(buf.readUtf(), buf.readVarInt());
            taken.put(tree, nodes);
        }
        Set<String> gated = new HashSet<>();
        int g = buf.readVarInt();
        for (int i = 0; i < g; i++) gated.add(buf.readUtf());
        boolean self = buf.readBoolean();
        int points = buf.readVarInt();
        Map<String, String> primary = new HashMap<>();
        int pc = buf.readVarInt();
        for (int i = 0; i < pc; i++) primary.put(buf.readUtf(), buf.readUtf());
        int buyCost = buf.readVarInt() - 2;
        return new S2CPlayerData(taken, gated, self, points, primary, buyCost);
    }

    public static void handle(S2CPlayerData m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientTrees.setPlayerData(m.taken, m.gated, m.selfRefund, m.points, m.primary, m.buyCost)));
        ctx.get().setPacketHandled(true);
    }
}

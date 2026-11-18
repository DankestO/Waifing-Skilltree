package com.waifingskilltree.net;

import com.waifingskilltree.WaifingSkilltreeMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class Network {
    private static final String PROTOCOL = "5";   // 1.0.0: choice limits, choice groups, free choices
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(WaifingSkilltreeMod.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private Network() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, S2CTrees.class, S2CTrees::encode, S2CTrees::decode, S2CTrees::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, S2CPlayerData.class, S2CPlayerData::encode, S2CPlayerData::decode, S2CPlayerData::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, C2SUnlock.class, C2SUnlock::encode, C2SUnlock::decode, C2SUnlock::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, C2SRefund.class, C2SRefund::encode, C2SRefund::decode, C2SRefund::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, S2CClickRecipes.class, S2CClickRecipes::encode, S2CClickRecipes::decode, S2CClickRecipes::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, C2SBuyPoint.class, C2SBuyPoint::encode, C2SBuyPoint::decode, C2SBuyPoint::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, C2SRespec.class, C2SRespec::encode, C2SRespec::decode, C2SRespec::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void sendTo(ServerPlayer p, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }

    public static void sendToAll(Object msg) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), msg);
    }

    public static void sendToServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }
}

package com.waifingskilltree.client;

import com.waifingskilltree.WaifingSkilltreeMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public final class WaifingSkilltreeClient {
    public static final KeyMapping OPEN_KEY = new KeyMapping("key.waifingskilltree.open",
            InputConstants.KEY_U, "key.categories.waifingskilltree");

    private WaifingSkilltreeClient() {}

    @Mod.EventBusSubscriber(modid = WaifingSkilltreeMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) {
            e.register(OPEN_KEY);
        }

        /** Optional: Apothic Attributes shows the skill tree's star as the source of our modifiers. */
        @SubscribeEvent
        public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e) {
            if (net.minecraftforge.fml.ModList.get().isLoaded("attributeslib")) {
                e.enqueueWork(() -> {
                    try {
                        com.waifingskilltree.compat.ApothicSourceCompat.register();
                    } catch (Throwable t) {   // another Apothic version without this API: just skip the icons
                        org.slf4j.LoggerFactory.getLogger("waifing_skilltree").warn("Apothic Attributes source icons disabled: {}", t.toString());
                    }
                });
            }
        }
    }

    @Mod.EventBusSubscriber(modid = WaifingSkilltreeMod.MODID, value = Dist.CLIENT)
    public static final class ForgeBus {
        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            while (OPEN_KEY.consumeClick()) {
                if (mc.player != null && mc.screen == null) mc.setScreen(new SkillTreeScreen());
            }
        }
    }
}

package com.waifingskilltree;

import com.waifingskilltree.capability.PlayerSkills;
import com.waifingskilltree.command.WaifingSkilltreeCommands;
import com.waifingskilltree.data.TreeReloadListener;
import com.waifingskilltree.logic.SkillManager;
import com.waifingskilltree.logic.TreeAdmin;
import com.waifingskilltree.net.Network;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(WaifingSkilltreeMod.MODID)
public class WaifingSkilltreeMod {
    public static final String MODID = "waifing_skilltree";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation CAP_ID = new ResourceLocation(MODID, "skills");

    public WaifingSkilltreeMod() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::registerCaps);
        com.waifingskilltree.recipe.ModRecipes.SERIALIZERS.register(modBus);
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, WaifingSkilltreeConfig.SPEC);
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, com.waifingskilltree.client.WaifingSkilltreeClientConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(FMLCommonSetupEvent e) {
        e.enqueueWork(Network::register);
    }

    private void registerCaps(RegisterCapabilitiesEvent e) {
        e.register(PlayerSkills.class);
    }

    // ------------------------------------------------ eventos de Forge

    @SubscribeEvent
    public void attach(AttachCapabilitiesEvent<Entity> e) {
        if (e.getObject() instanceof Player) e.addCapability(CAP_ID, new PlayerSkills.Provider());
    }

    @SubscribeEvent
    public void onClone(PlayerEvent.Clone e) {
        e.getOriginal().reviveCaps();
        e.getOriginal().getCapability(PlayerSkills.CAP).ifPresent(old ->
                e.getEntity().getCapability(PlayerSkills.CAP).ifPresent(n -> {
                    n.copyFrom(old);
                    if (e.isWasDeath() && WaifingSkilltreeConfig.RESET_ON_DEATH.get()) n.wipeOnRespawn = true;
                }));
        e.getOriginal().invalidateCaps();
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent e) {
        WaifingSkilltreeCommands.register(e.getDispatcher());
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        TreeAdmin.sendTo(p);
        SkillManager.validate(p);
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            PlayerSkills s = SkillManager.get(p);
            if (s != null && s.wipeOnRespawn) {
                s.wipeOnRespawn = false;
                SkillManager.wipeOnDeath(p);
                return;
            }
            SkillManager.applyAttributes(p);
            SkillManager.sync(p);
        }
    }

    @SubscribeEvent
    public void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            SkillManager.applyAttributes(p);
            SkillManager.sync(p);
        }
    }

    /** Nodes may require advancements: when one is earned the state is resent. */
    @SubscribeEvent
    public void onAdvancement(AdvancementEvent.AdvancementEarnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) SkillManager.sync(p);
    }

    /** Using the configured respec item (right click) resets the player's skill tree. */
    @SubscribeEvent
    public void onRespecItem(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p)) return;
        String id = WaifingSkilltreeConfig.RESPEC_ITEM.get().trim();
        if (id.isEmpty()) return;
        var key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(e.getItemStack().getItem());
        if (key == null || !key.toString().equals(id)) return;
        e.setCanceled(true);
        e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        if (!com.waifingskilltree.logic.SkillManager.hasAnything(p)) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("waifingskilltree.msg.nothing_to_reset"), true);
            return;
        }
        if (WaifingSkilltreeConfig.RESPEC_ITEM_CONSUME.get() && !p.getAbilities().instabuild) e.getItemStack().shrink(1);
        com.waifingskilltree.logic.SkillManager.respec(p);
    }

    /** Re-evaluates conditional attribute bonuses a few times per second. */
    @SubscribeEvent
    public void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent e) {
        if (e.phase != net.minecraftforge.event.TickEvent.Phase.END || e.side.isClient()) return;
        if (!(e.player instanceof net.minecraft.server.level.ServerPlayer p)) return;
        com.waifingskilltree.logic.SkillManager.checkLevelPoints(p);
        if (p.tickCount % WaifingSkilltreeConfig.CONDITION_INTERVAL.get() == 0) com.waifingskilltree.logic.SkillManager.refreshConditional(p);
    }

    /** Right-click recipes unlocked by nodes (cursor item right-clicked on an inventory item). */
    @SubscribeEvent
    public void onItemStacked(net.minecraftforge.event.ItemStackedOnOtherEvent e) {
        com.waifingskilltree.logic.ClickRecipeHandler.onStacked(e);
    }

    @SubscribeEvent
    public void onCommandUsed(net.minecraftforge.event.CommandEvent e) {
        com.waifingskilltree.logic.CommandGate.onCommand(e);
    }

    @SubscribeEvent
    public void onReloadListeners(AddReloadListenerEvent e) {
        e.addListener(new TreeReloadListener());
        e.addListener(new com.waifingskilltree.data.ClickRecipeReloadListener());
    }
}

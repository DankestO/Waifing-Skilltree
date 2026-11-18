package com.waifingskilltree.client;

import com.waifingskilltree.WaifingSkilltreeMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A small button in the survival inventory, right of the recipe book button (below the 2x2 crafting grid).
 * It follows the inventory when the recipe book opens or closes. Can be moved or hidden in the client config.
 */
@Mod.EventBusSubscriber(modid = WaifingSkilltreeMod.MODID, value = Dist.CLIENT)
public final class InventoryButton {
    /** Place relative to the inventory's left/top corner: the recipe book button is at (104, 61), 20x18. */
    private static final int X = 126, Y = 61, W = 20, H = 18;
    private static final ItemStack ICON = new ItemStack(Items.NETHER_STAR);

    private InventoryButton() {}

    @SubscribeEvent
    public static void init(ScreenEvent.Init.Post e) {
        if (!(e.getScreen() instanceof InventoryScreen inv) || !WaifingSkilltreeClientConfig.INVENTORY_BUTTON.get()) return;
        Button b = new Button(pos(inv.getGuiLeft(), true), pos(inv.getGuiTop(), false), W, H, Component.empty(),
                btn -> Minecraft.getInstance().setScreen(new SkillTreeScreen()), s -> Component.translatable("waifingskilltree.button.open")) {
            @Override
            public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                setX(pos(inv.getGuiLeft(), true));          // follow the inventory when the recipe book moves it
                setY(pos(inv.getGuiTop(), false));
                super.renderWidget(g, mx, my, pt);
                g.pose().pushPose();
                g.pose().translate(getX() + 2.5f, getY() + 1.5f, 0);
                g.pose().scale(0.94f, 0.94f, 1f);
                g.renderFakeItem(ICON, 0, 0);
                g.pose().popPose();
            }
        };
        b.setTooltip(Tooltip.create(Component.translatable("waifingskilltree.button.open.tooltip",
                WaifingSkilltreeClient.OPEN_KEY.getTranslatedKeyMessage())));
        e.addListener(b);
    }

    private static int pos(int corner, boolean x) {
        return x ? corner + X + WaifingSkilltreeClientConfig.BUTTON_OFFSET_X.get() : corner + Y + WaifingSkilltreeClientConfig.BUTTON_OFFSET_Y.get();
    }
}

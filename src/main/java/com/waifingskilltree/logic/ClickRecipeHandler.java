package com.waifingskilltree.logic;

import com.waifingskilltree.client.ClientTrees;
import com.waifingskilltree.data.ClickRecipe;
import com.waifingskilltree.data.ClickRecipes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.ItemStackedOnOtherEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;

/**
 * Right-click recipes: cursor item (catalyst) right-clicked on an inventory item (target).
 * Same pattern as Waifing Attributes: NORMAL priority and {@code isCanceled()} check, so that mod's own actions
 * (HIGH priority) win; nothing happens (vanilla swap) without a matching recipe unlocked by a node.
 * Both sides cancel the click (so the client does not predict a swap); only the server crafts.
 */
public final class ClickRecipeHandler {
    private ClickRecipeHandler() {}

    public static void onStacked(ItemStackedOnOtherEvent e) {
        if (e.isCanceled() || e.getClickAction() != ClickAction.SECONDARY) return;
        Player p = e.getPlayer();
        if (p == null || p.getAbilities().instabuild) return;          // like Waifing Attributes: not in creative
        if (ClickRecipes.all().isEmpty()) return;
        Slot slot = e.getSlot();
        ItemStack target = slot.getItem(), carried = p.containerMenu.getCarried();
        if (target.isEmpty() || carried.isEmpty() || !slot.mayPickup(p) || !slot.mayPlace(target)) return;

        Set<String> mine = p.level().isClientSide()
                ? DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> ClientTrees::clickRecipes)
                : p instanceof ServerPlayer sp ? SkillManager.clickRecipesOf(sp) : Set.of();
        if (mine == null || mine.isEmpty()) return;
        ClickRecipe recipe = null;
        for (String id : mine) {
            ClickRecipe r = ClickRecipes.all().get(id);
            if (r != null && r.matches(target, carried)) { recipe = r; break; }
        }
        if (recipe == null) return;
        e.setCanceled(true);
        if (p instanceof ServerPlayer sp) craft(sp, slot, recipe);
    }

    private static void craft(ServerPlayer p, Slot slot, ClickRecipe r) {
        ItemStack target = slot.getItem();
        ItemStack out = r.result(target);
        if (out.isEmpty()) return;
        ItemStack carried = p.containerMenu.getCarried();
        if (r.consumeCatalyst()) {
            carried.shrink(r.catalystCount());
            p.containerMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
        }
        target.shrink(r.targetCount());
        if (target.isEmpty() && slot.mayPlace(out)) {
            slot.set(out);
        } else {
            if (target.isEmpty()) slot.set(ItemStack.EMPTY);
            if (p.containerMenu.getCarried().isEmpty()) p.containerMenu.setCarried(out);
            else if (!p.getInventory().add(out)) p.drop(out, false);
        }
        slot.setChanged();
        SoundEvent snd = null;
        if (!r.sound().isEmpty()) {
            ResourceLocation rl = ResourceLocation.tryParse(r.sound());
            snd = rl == null ? null : ForgeRegistries.SOUND_EVENTS.getValue(rl);
        }
        p.level().playSound(null, p.blockPosition(), snd != null ? snd : SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 0.8f, 1f);
        p.containerMenu.sendAllDataToRemote();
    }
}

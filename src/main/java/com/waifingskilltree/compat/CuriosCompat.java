package com.waifingskilltree.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Optional Curios support through reflection: {@code CuriosApi.getCuriosInventory(entity)} ->
 * {@code ICuriosItemHandler.findCurios(String... slotIds)} -> {@code SlotResult.stack()}. Verified against curios-forge 5.14.1+1.20.1.
 */
public final class CuriosCompat {
    private static Boolean loaded;
    private static Method getInv, findCurios, findAll, stack;

    private CuriosCompat() {}

    public static boolean loaded() {
        if (loaded == null) {
            loaded = false;
            if (ModList.get() != null && ModList.get().isLoaded("curios")) {
                try {
                    Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
                    Class<?> handler = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
                    Class<?> result = Class.forName("top.theillusivec4.curios.api.SlotResult");
                    getInv = api.getMethod("getCuriosInventory", LivingEntity.class);
                    findCurios = handler.getMethod("findCurios", String[].class);
                    findAll = handler.getMethod("findCurios", java.util.function.Predicate.class);
                    stack = result.getMethod("stack");
                    loaded = true;
                } catch (ReflectiveOperationException | LinkageError e) {
                    loaded = false;
                }
            }
        }
        return loaded;
    }

    /** Stacks equipped in the given curio slot type ("spellbook", "ring"...); empty slot id = every curio slot. */
    public static List<ItemStack> equipped(LivingEntity e, String slot) {
        List<ItemStack> out = new ArrayList<>();
        if (!loaded()) return out;
        try {
            LazyOptional<?> opt = (LazyOptional<?>) getInv.invoke(null, e);
            Object h = opt.resolve().orElse(null);
            if (h == null) return out;
            List<?> res = slot.isEmpty()
                    ? (List<?>) findAll.invoke(h, (java.util.function.Predicate<ItemStack>) s -> !s.isEmpty())
                    : (List<?>) findCurios.invoke(h, (Object) new String[]{slot});
            for (Object r : res) {
                ItemStack st = (ItemStack) stack.invoke(r);
                if (st != null && !st.isEmpty()) out.add(st);
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return out;
        }
        return out;
    }
}

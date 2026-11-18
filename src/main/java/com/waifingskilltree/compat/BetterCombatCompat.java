package com.waifingskilltree.compat;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * Optional Better Combat support through reflection (no compile-time dependency; does nothing if the mod is absent).
 * Uses {@code net.bettercombat.logic.WeaponRegistry.getAttributes(ItemStack)} -> {@code WeaponAttributes}
 * ({@code isTwoHanded()}, {@code category()}), verified against bettercombat-forge 1.9.0+1.20.1.
 */
public final class BetterCombatCompat {
    private static Boolean loaded;
    private static Method getAttributes, isTwoHanded, category;

    private BetterCombatCompat() {}

    public static boolean loaded() {
        if (loaded == null) {
            loaded = false;
            if (ModList.get() != null && ModList.get().isLoaded("bettercombat")) {
                try {
                    Class<?> reg = Class.forName("net.bettercombat.logic.WeaponRegistry");
                    Class<?> attrs = Class.forName("net.bettercombat.api.WeaponAttributes");
                    getAttributes = reg.getMethod("getAttributes", ItemStack.class);
                    isTwoHanded = attrs.getMethod("isTwoHanded");
                    category = attrs.getMethod("category");
                    loaded = true;
                } catch (ReflectiveOperationException | LinkageError e) {
                    loaded = false;
                }
            }
        }
        return loaded;
    }

    @Nullable
    private static Object attrs(ItemStack st) {
        if (st.isEmpty() || !loaded()) return null;
        try {
            return getAttributes.invoke(null, st);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Is this a Better Combat weapon (has weapon attributes)? */
    public static boolean isWeapon(ItemStack st) {
        return attrs(st) != null;
    }

    public static boolean isTwoHanded(ItemStack st) {
        Object a = attrs(st);
        try {
            return a != null && (boolean) isTwoHanded.invoke(a);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** Weapon category ("claymore", "dagger", "sword"...) or "" when unknown. */
    public static String category(ItemStack st) {
        Object a = attrs(st);
        try {
            Object c = a == null ? null : category.invoke(a);
            return c == null ? "" : c.toString();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "";
        }
    }
}

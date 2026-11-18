package com.waifingskilltree.recipe;

import com.waifingskilltree.client.ClientTrees;
import com.waifingskilltree.logic.SkillManager;
import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A crafting recipe only the players who unlocked it in a skill tree can craft, for everybody else it does not exist.
 * No need for the doLimitedCrafting gamerule (which would limit every recipe for every player).
 * <pre>
 * { "type": "waifing_skilltree:skill_locked",
 *   "recipe": { "type": "minecraft:crafting_shaped", "pattern": [...], "key": {...}, "result": {...} } }
 * </pre>
 * The node lists THIS file's id in "recipes". Automated crafters (no player) can never craft it.
 */
public class SkillLockedRecipe implements CraftingRecipe {
    private final ResourceLocation id;
    final CraftingRecipe inner;

    public SkillLockedRecipe(ResourceLocation id, CraftingRecipe inner) {
        this.id = id;
        this.inner = inner;
    }

    @Override
    public boolean matches(CraftingContainer inv, Level level) {
        if (!inner.matches(inv, level)) return false;
        Player p = playerOf(inv);
        if (p == null) return false;
        String key = id.toString();
        if (p instanceof ServerPlayer sp) return SkillManager.recipesOf(sp).contains(key);
        if (level.isClientSide()) {
            Boolean ok = DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> ClientTrees.recipes().contains(key));
            return ok != null && ok;
        }
        return false;
    }

    @Override public ItemStack assemble(CraftingContainer inv, RegistryAccess ra) { return inner.assemble(inv, ra); }
    @Override public boolean canCraftInDimensions(int w, int h) { return inner.canCraftInDimensions(w, h); }
    @Override public ItemStack getResultItem(RegistryAccess ra) { return inner.getResultItem(ra); }
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingContainer inv) { return inner.getRemainingItems(inv); }
    @Override public NonNullList<Ingredient> getIngredients() { return inner.getIngredients(); }
    @Override public String getGroup() { return inner.getGroup(); }
    @Override public CraftingBookCategory category() { return inner.category(); }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return ModRecipes.SKILL_LOCKED.get(); }

    // ------------------------------------------------------------ who is crafting?

    private static final Map<Class<?>, Field> FIELDS = new ConcurrentHashMap<>();
    private static final Field NONE;
    static {
        try { NONE = SkillLockedRecipe.class.getDeclaredField("id"); } catch (NoSuchFieldException e) { throw new RuntimeException(e); }
    }

    /** Finds the player through the container's menu (field types, not names, so it works in any mapping). */
    @Nullable
    static Player playerOf(CraftingContainer inv) {
        Object menu = read(inv, AbstractContainerMenu.class);
        if (!(menu instanceof AbstractContainerMenu)) return null;
        Object p = read(menu, Player.class);
        return p instanceof Player pl ? pl : null;
    }

    @Nullable
    private static Object read(Object owner, Class<?> type) {
        Field f = FIELDS.computeIfAbsent(owner.getClass(), c -> find(c, type));
        if (f == NONE) return null;
        try {
            return f.get(owner);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private static Field find(Class<?> c, Class<?> type) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (type.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        return f;
                    } catch (RuntimeException ignored) {
                    }
                }
            }
        }
        return NONE;
    }

    // ------------------------------------------------------------ serializer

    public static class Serializer implements RecipeSerializer<SkillLockedRecipe> {
        @Override
        public SkillLockedRecipe fromJson(ResourceLocation id, JsonObject json) {
            JsonObject in = GsonHelper.getAsJsonObject(json, "recipe");
            ResourceLocation type = new ResourceLocation(GsonHelper.getAsString(in, "type"));
            RecipeSerializer<?> ser = ForgeRegistries.RECIPE_SERIALIZERS.getValue(type);
            if (ser == null) throw new IllegalArgumentException("Unknown recipe type " + type);
            Recipe<?> r = ser.fromJson(id, in);
            if (!(r instanceof CraftingRecipe cr)) throw new IllegalArgumentException(type + " is not a crafting recipe");
            return new SkillLockedRecipe(id, cr);
        }

        @Override
        public SkillLockedRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            ResourceLocation type = buf.readResourceLocation();
            RecipeSerializer<?> ser = ForgeRegistries.RECIPE_SERIALIZERS.getValue(type);
            Recipe<?> r = ser == null ? null : ser.fromNetwork(id, buf);
            if (!(r instanceof CraftingRecipe cr)) throw new IllegalStateException("Bad skill_locked recipe " + id);
            return new SkillLockedRecipe(id, cr);
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void toNetwork(FriendlyByteBuf buf, SkillLockedRecipe r) {
            RecipeSerializer ser = r.inner.getSerializer();
            buf.writeResourceLocation(ForgeRegistries.RECIPE_SERIALIZERS.getKey(ser));
            ser.toNetwork(buf, r.inner);
        }
    }
}

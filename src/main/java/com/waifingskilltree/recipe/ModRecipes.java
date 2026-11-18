package com.waifingskilltree.recipe;

import com.waifingskilltree.WaifingSkilltreeMod;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, WaifingSkilltreeMod.MODID);
    public static final RegistryObject<RecipeSerializer<SkillLockedRecipe>> SKILL_LOCKED = SERIALIZERS.register("skill_locked", SkillLockedRecipe.Serializer::new);

    private ModRecipes() {}
}

package com.waifingskilltree.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.LinkedHashMap;
import java.util.Map;

/** All right-click recipes. Filled by {@link ClickRecipeReloadListener} on the server and by the S2CClickRecipes packet on clients. */
public final class ClickRecipes {
    private static Map<String, ClickRecipe> RECIPES = new LinkedHashMap<>();

    private ClickRecipes() {}

    public static Map<String, ClickRecipe> all() {
        return RECIPES;
    }

    public static void set(Map<String, ClickRecipe> r) {
        RECIPES = new LinkedHashMap<>(r);
    }

    public static String encode() {
        JsonObject root = new JsonObject();
        RECIPES.forEach((id, r) -> root.add(id, r.toJson()));
        return root.toString();
    }

    public static Map<String, ClickRecipe> decode(String json) {
        Map<String, ClickRecipe> out = new LinkedHashMap<>();
        JsonParser.parseString(json).getAsJsonObject().entrySet()
                .forEach(e -> out.put(e.getKey(), ClickRecipe.fromJson(e.getKey(), e.getValue().getAsJsonObject())));
        return out;
    }
}

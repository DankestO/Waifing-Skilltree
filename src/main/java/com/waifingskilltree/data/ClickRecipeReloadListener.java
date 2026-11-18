package com.waifingskilltree.data;

import com.waifingskilltree.WaifingSkilltreeMod;
import com.waifingskilltree.logic.TreeAdmin;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.LinkedHashMap;
import java.util.Map;

/** Loads {@code data/<ns>/waifingskilltree/click_recipes/<name>.json} (id "ns:name"). */
public class ClickRecipeReloadListener extends SimpleJsonResourceReloadListener {
    public static final String FOLDER = "waifingskilltree/click_recipes";

    public ClickRecipeReloadListener() {
        super(new Gson(), FOLDER);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
        Map<String, ClickRecipe> out = new LinkedHashMap<>();
        files.forEach((rl, json) -> {
            try {
                out.put(rl.toString(), ClickRecipe.fromJson(rl.toString(), json.getAsJsonObject()));
            } catch (Exception e) {
                WaifingSkilltreeMod.LOGGER.error("Invalid right-click recipe " + rl + ": " + e.getMessage());
            }
        });
        ClickRecipes.set(out);
        WaifingSkilltreeMod.LOGGER.info("Loaded {} right-click recipe(s)", out.size());
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.isRunning()) TreeAdmin.broadcastClickRecipes();
    }
}

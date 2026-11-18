package com.waifingskilltree.data;

import com.waifingskilltree.WaifingSkilltreeMod;
import com.waifingskilltree.logic.TreeAdmin;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads trees from datapacks: {@code data/<namespace>/waifingskilltree/trees/<name>.json}.
 * The tree id is {@code <namespace>_<name>}. Updated on /reload and sent to players.
 */
public class TreeReloadListener extends SimpleJsonResourceReloadListener {
    public static final String FOLDER = "waifingskilltree/trees";

    public TreeReloadListener() {
        super(new Gson(), FOLDER);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
        Map<String, SkillTree> out = new LinkedHashMap<>();
        files.forEach((rl, json) -> {
            String id = TreeStorage.sanitizeId(rl.getNamespace() + "_" + rl.getPath().replace('/', '_'));
            try {
                out.put(id, SkillTree.fromJson(id, json.getAsJsonObject()));
            } catch (Exception e) {
                WaifingSkilltreeMod.LOGGER.error("Invalid datapack tree: " + rl, e);
            }
        });
        TreeStorage.setDatapackTrees(out);
        WaifingSkilltreeMod.LOGGER.info("Loaded {} tree(s) from datapacks", out.size());
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.isRunning()) TreeAdmin.broadcast(server);
    }
}

package com.waifingskilltree.client;

import com.waifingskilltree.data.SkillNode;
import com.waifingskilltree.data.SkillTree;
import com.waifingskilltree.data.TreeLocks;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Client-side copy of the trees and the player's progress. */
public final class ClientTrees {
    public static Map<String, SkillTree> trees = new LinkedHashMap<>();
    public static Map<String, Map<String, Integer>> taken = new HashMap<>();
    /** Nodos bloqueados por logros pendientes, como "arbol:nodo". */
    public static Set<String> gated = new HashSet<>();

    private ClientTrees() {}

    public static void setTrees(Map<String, SkillTree> t) {
        trees = t;
        version++;
        refresh();
    }

    /** Whether the server lets players refund nodes / reset by themselves (config allowSelfRefund). */
    public static boolean selfRefund = false;

    /** Skill points the player can spend now. */
    public static int points = 0;
    /** Exclusive group -> tree the player chose first. */
    public static Map<String, String> primary = new HashMap<>();
    /** Levels the next skill point costs; -1 = buying disabled, -2 = maxBoughtPoints reached. */
    public static int buyCost = -1;
    /** Bumped on every data change so the screen can cache node states. */
    public static int version = 0;

    public static void setPlayerData(Map<String, Map<String, Integer>> tk, Set<String> g, boolean self, int pts, Map<String, String> prim, int buy) {
        buyCost = buy;
        selfRefund = self;
        points = pts;
        primary = prim;
        taken = tk;
        gated = g;
        version++;
        refresh();
    }

    public static boolean isGated(String tree, String node) {
        return gated.contains(tree + ":" + node);
    }

    /** Current level of a node (0 = not taken). */
    public static int level(String tree, String node) {
        Map<String, Integer> m = taken.get(tree);
        Integer l = m == null ? null : m.get(node);
        return l == null ? 0 : l;
    }

    public static boolean has(String tree, String node) {
        Map<String, Integer> m = taken.get(tree);
        return level(tree, node) > 0;
    }

    /** Can the player use this tree now? (exclusive groups) */
    public static boolean treeAllowed(SkillTree t) {
        return TreeLocks.allowed(t, primary, TreeLocks.unlocked(trees, taken));
    }

    /** Recipe ids unlocked by the player's nodes (for skill_locked crafting recipes). */
    public static Set<String> recipes() {
        return collect(n -> n.recipes);
    }

    /** Right-click recipe ids unlocked by the player's nodes. */
    public static Set<String> clickRecipes() {
        return collect(n -> n.clickRecipes);
    }

    private static Set<String> collect(java.util.function.Function<SkillNode, java.util.List<String>> f) {
        Set<String> out = new HashSet<>();
        taken.forEach((treeId, nodes) -> {
            SkillTree t = trees.get(treeId);
            if (t == null) return;
            nodes.forEach((id, lv) -> {
                SkillNode n = t.nodes.get(id);
                if (n != null && lv > 0) out.addAll(f.apply(n));
            });
        });
        return out;
    }

    private static void refresh() {
        if (Minecraft.getInstance().screen instanceof SkillTreeScreen s) s.onDataChanged();
    }
}

package com.waifingskilltree.data;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Exclusive tree groups (shared by client and server).
 * A tree with {@code exclusive_group} is usable when: the player has not chosen a tree of that group yet,
 * it is the tree the player chose first ("primary"), or a node the player owns lists it in {@code unlock_trees}
 * (by tree id, or "#group" for the whole group).
 * A tree with {@code requires_unlock} is closed for everyone until an owned node lists it in {@code unlock_trees}.
 */
public final class TreeLocks {
    private TreeLocks() {}

    /** Tree ids and "#group" tokens opened by every node the player has (levels: tree -> node -> level). */
    public static Set<String> unlocked(Map<String, SkillTree> trees, Map<String, Map<String, Integer>> levels) {
        Set<String> out = new HashSet<>();
        levels.forEach((treeId, nodes) -> {
            SkillTree t = trees.get(treeId);
            if (t == null) return;
            nodes.forEach((nodeId, lv) -> {
                SkillNode n = t.nodes.get(nodeId);
                if (n != null && lv > 0) out.addAll(n.unlockTrees);
            });
        });
        return out;
    }

    public static boolean allowed(SkillTree t, Map<String, String> primary, Set<String> unlocked) {
        if (t.requiresUnlock && !opened(t, unlocked)) return false;   // closed until a node opens it
        if (t.exclusiveGroup.isEmpty()) return true;
        String p = primary.get(t.exclusiveGroup);
        return p == null || p.equals(t.id) || unlocked.contains(t.id) || unlocked.contains("#" + t.exclusiveGroup);
    }

    /** Does an owned node open this tree (by id, or by "#group" when the tree has an exclusive group)? */
    public static boolean opened(SkillTree t, Set<String> unlocked) {
        return unlocked.contains(t.id) || (!t.exclusiveGroup.isEmpty() && unlocked.contains("#" + t.exclusiveGroup));
    }

    /** Is the state consistent? Every exclusive tree with nodes must be allowed, and a group with nodes must keep its primary tree used. */
    public static boolean consistent(Map<String, SkillTree> trees, Map<String, String> primary, Map<String, Map<String, Integer>> levels) {
        Set<String> unlocked = unlocked(trees, levels);
        for (var e : levels.entrySet()) {
            SkillTree t = trees.get(e.getKey());
            if (t == null || !hasAny(e.getValue())) continue;
            if (t.requiresUnlock && !opened(t, unlocked)) return false;   // the node that opened it must stay
            if (t.exclusiveGroup.isEmpty()) continue;
            String p = primary.get(t.exclusiveGroup);
            if (p == null || !hasAny(levels.get(p))) return false;   // the primary tree must not be emptied while others are used
            if (!allowed(t, primary, unlocked)) return false;
        }
        return true;
    }

    public static boolean hasAny(Map<String, Integer> nodes) {
        if (nodes == null) return false;
        for (int v : nodes.values()) if (v > 0) return true;
        return false;
    }
}

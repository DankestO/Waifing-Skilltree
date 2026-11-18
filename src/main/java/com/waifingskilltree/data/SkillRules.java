package com.waifingskilltree.data;

import java.util.*;
import java.util.function.ToIntFunction;

/** Rules shared by client and server: availability, cost and validity of a set of taken nodes. */
public final class SkillRules {

    private SkillRules() {}

    /** Can the first level be taken now? (cost, items and advancements are not checked here). {@code level} gives a node's current level (0 = not taken). */
    public static boolean canTake(SkillTree t, SkillNode n, ToIntFunction<String> level) {
        return canTake(t, n, level, null);
    }

    /** {@code freeStart}: with first_start_only, the start node treated as the free one (null = any start while none is owned). */
    static boolean canTake(SkillTree t, SkillNode n, ToIntFunction<String> level, String freeStart) {
        if (!requiredNodesMet(n, level)) return false;
        if (n.isStart()) {
            if (!t.firstStartOnly) return true;
            if (freeStart != null) { if (freeStart.equals(n.id)) return true; }
            else {
                boolean ownsStart = false;
                for (SkillNode o : t.nodes.values()) if (o.isStart() && !o.id.equals(n.id) && level.applyAsInt(o.id) > 0) { ownsStart = true; break; }
                if (!ownsStart) return true;      // the first class is free; later ones are reached like any node
            }
        }
        if (chosenSibling(t, n, level) != null) return false;   // another option of the same choice was picked
        int count = 0;
        for (SkillTree.Link l : t.incomingLinks(n.id)) if (level.applyAsInt(l.other(n.id)) >= minLevelOf(n, l)) count++;
        int need = SkillNode.AND.equals(n.kind) ? n.require : 1;
        return count >= need;
    }

    /** Level the other end of {@code l} needs before {@code n} can be reached through it (the node's prev_level or the link's min_level). */
    public static int minLevelOf(SkillNode n, SkillTree.Link l) {
        return Math.max(1, Math.max(n.prevLevel, l.minLevel));
    }

    /**
     * Choice links: if {@code n} is one option of a choice (a "choice" link A -> n) and the player already took another
     * option of that same A, returns that option's id (n is closed); "" when the choice is locked (limit 0, nothing taken);
     * otherwise null. A node already taken is never closed.
     */
    public static String chosenSibling(SkillTree t, SkillNode n, ToIntFunction<String> level) {
        if (level.applyAsInt(n.id) > 0 || freeChoice(t, n.id, level)) return null;   // free options are never closed
        for (SkillTree.Link l : t.links.values()) {
            if (!SkillTree.CHOICE.equals(l.type) || !l.to.equals(n.id)) continue;
            if (choiceTaken(t, l.from, level) < choiceLimit(t, l.from, level)) continue;
            String g = t.choiceGroups.get(l.from);
            if (g != null && choicePoolUsed(t, g, level) < choicePoolLimit(t, g, level)) continue;   // shared extra pick left
            for (SkillTree.Link o : t.links.values())
                if (SkillTree.CHOICE.equals(o.type) && o.from.equals(l.from) && level.applyAsInt(o.to) > 0 && !freeChoice(t, o.to, level)) return o.to;
            return "";   // nothing taken yet: a locked choice (limit 0) that no taken node opened
        }
        return null;
    }

    /** How many picks of the choice leaving {@code from} the player used: options taken, except free ones (free_choice_if). */
    public static int choiceTaken(SkillTree t, String from, ToIntFunction<String> level) {
        int taken = 0;
        for (SkillTree.Link o : t.links.values())
            if (SkillTree.CHOICE.equals(o.type) && o.from.equals(from) && level.applyAsInt(o.to) > 0 && !freeChoice(t, o.to, level)) taken++;
        return taken;
    }

    /** Nodes whose group_bonus can open or widen the choice leaving {@code from} (its own key or its choice group's). */
    public static java.util.List<SkillNode> choiceOpeners(SkillTree t, String from) {
        String g = t.choiceGroups.get(from);
        java.util.List<SkillNode> out = new java.util.ArrayList<>();
        for (SkillNode o : t.nodes.values())
            if (o.groupBonus.getOrDefault(choiceGroup(from), 0) > 0 || (g != null && o.groupBonus.getOrDefault(choicePoolKey(g), 0) > 0)) out.add(o);
        return out;
    }

    /** Bonus key of a choice group: group_bonus {"choices:<group>": N} gives N extra picks shared by its choices. */
    public static String choicePoolKey(String group) {
        return "choices:" + group;
    }

    /** Extra picks the player has for a choice group: group_bonus "choices:<group>" of taken nodes. */
    public static int choicePoolLimit(SkillTree t, String group, ToIntFunction<String> level) {
        int limit = 0;
        String k = choicePoolKey(group);
        for (SkillNode o : t.nodes.values()) {
            Integer b = o.groupBonus.get(k);
            if (b != null && level.applyAsInt(o.id) > 0) limit += b;
        }
        return limit;
    }

    /** Extra picks of a choice group already spent: options taken past each choice's own limit. */
    public static int choicePoolUsed(SkillTree t, String group, ToIntFunction<String> level) {
        int used = 0;
        for (var e : t.choiceGroups.entrySet())
            if (e.getValue().equals(group)) used += Math.max(0, choiceTaken(t, e.getKey(), level) - choiceLimit(t, e.getKey(), level));
        return used;
    }

    /** Choice group whose shared extra pick taking option {@code n} would spend (its own choice is full), or null. */
    public static String poolNeeded(SkillTree t, SkillNode n, ToIntFunction<String> level) {
        if (level.applyAsInt(n.id) > 0 || freeChoice(t, n.id, level)) return null;
        for (SkillTree.Link l : t.links.values()) {
            if (!SkillTree.CHOICE.equals(l.type) || !l.to.equals(n.id)) continue;
            String g = t.choiceGroups.get(l.from);
            if (g != null && choiceTaken(t, l.from, level) >= choiceLimit(t, l.from, level)) return g;
        }
        return null;
    }

    /** Group id of the choice that leaves {@code from}: a node's group_bonus on it lets the player pick one more option. */
    public static String choiceGroup(String from) {
        return "choice:" + from;
    }

    /** How many options of the choice leaving {@code from} the player may take: choice_limits (default 1) + group_bonus "choice:<from>" of taken nodes. */
    public static int choiceLimit(SkillTree t, String from, ToIntFunction<String> level) {
        int limit = t.choiceLimits.getOrDefault(from, 1);
        String g = choiceGroup(from);
        for (SkillNode o : t.nodes.values()) {
            Integer b = o.groupBonus.get(g);
            if (b != null && level.applyAsInt(o.id) > 0) limit += b;
        }
        return limit;
    }

    /**
     * Group limit: a node of a limited group can be taken only if the player already has it
     * or has fewer nodes of that group than group_limits[group] (+ group_bonus of taken nodes).
     */
    public static boolean groupAllowed(SkillTree t, SkillNode n, ToIntFunction<String> level) {
        if (n.group.isEmpty() || !t.groupLimits.containsKey(n.group)) return true;
        if (level.applyAsInt(n.id) > 0) return true;
        return groupUsed(t, n.group, level) < groupLimit(t, n.group, level);
    }

    /** May the player raise {@code n} to {@code nextLevel}? False when that level is above the node's level cap and the
     *  player does not own enough of its cap nodes (level_cap). */
    public static boolean levelAllowed(SkillNode n, int nextLevel, ToIntFunction<String> level) {
        if (n.capLevel <= 0 || n.capNodes.isEmpty() || nextLevel <= n.capLevel) return true;
        int have = 0;
        for (String id : n.capNodes) if (level.applyAsInt(id) > 0) have++;
        return have >= n.capNodeCount();
    }

    /** First node of {@code free_choice_if} the player owns (the option costs no pick of its choice), or null. */
    public static String freeBy(SkillNode n, ToIntFunction<String> level) {
        for (String id : n.freeChoiceIf) if (level.applyAsInt(id) > 0) return id;
        return null;
    }

    private static boolean freeChoice(SkillTree t, String id, ToIntFunction<String> level) {
        SkillNode n = t.nodes.get(id);
        return n != null && !n.freeChoiceIf.isEmpty() && freeBy(n, level) != null;
    }

    /** Does the player own enough of the node's {@code requires_nodes}? */
    public static boolean requiredNodesMet(SkillNode n, ToIntFunction<String> level) {
        if (n.requiresNodes.isEmpty()) return true;
        int have = 0;
        for (String id : n.requiresNodes) if (level.applyAsInt(id) > 0) have++;
        return have >= n.requiredNodeCount();
    }

    /** How many nodes of a group the player has (a node with several levels counts once). */
    public static int groupUsed(SkillTree t, String group, ToIntFunction<String> level) {
        int used = 0;
        for (SkillNode o : t.nodes.values()) if (group.equals(o.group) && level.applyAsInt(o.id) > 0) used++;
        return used;
    }

    /** Limit of a group for this player: the tree's group_limits + group_bonus of every taken node. */
    public static int groupLimit(SkillTree t, String group, ToIntFunction<String> level) {
        int limit = t.groupLimits.getOrDefault(group, 0);
        for (SkillNode o : t.nodes.values()) {
            Integer b = o.groupBonus.get(group);
            if (b != null && level.applyAsInt(o.id) > 0) limit += b;
        }
        return limit;
    }

    /** Cost in points of reaching {@code next} (1-based). */
    public static int cost(SkillTree t, SkillNode n, ToIntFunction<String> level, int next) {
        return n.levelCost(next);
    }

    /** Subset of {@code levels} (node -> level) that is still reachable from a start node while meeting every rule. */
    public static Set<String> valid(SkillTree t, Map<String, Integer> levels) {
        if (!t.firstStartOnly) return validWith(t, levels, null);
        // the free start is unknown: keep the reading that keeps the most nodes
        Set<String> best = null;
        for (var e : levels.entrySet()) {
            SkillNode n = t.nodes.get(e.getKey());
            if (n == null || !n.isStart() || e.getValue() <= 0) continue;
            Set<String> r = validWith(t, levels, n.id);
            if (best == null || r.size() > best.size()) best = r;
        }
        return best != null ? best : validWith(t, levels, null);
    }

    private static Set<String> validWith(SkillTree t, Map<String, Integer> levels, String freeStart) {
        Map<String, Integer> ok = new HashMap<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (var e : levels.entrySet()) {
                if (ok.containsKey(e.getKey()) || e.getValue() <= 0) continue;
                SkillNode n = t.nodes.get(e.getKey());
                if (n != null && canTake(t, n, id -> ok.getOrDefault(id, 0), freeStart)) {
                    ok.put(e.getKey(), e.getValue());
                    changed = true;
                }
            }
        }
        return ok.keySet();
    }
}

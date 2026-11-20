package com.waifingskilltree.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

/** A tree node. All fields are editable. */
public class SkillNode {
    // Size/design: small = normal attribute, medium = special attribute, large = start node.
    public static final String SMALL = "small", MEDIUM = "medium", LARGE = "large";
    // Outline of the node: round (default) or a diamond (rhombus).
    public static final String CIRCLE = "circle", SQUARE = "square", DIAMOND = "diamond", STAR = "star";
    // Behaviour: normal; and = needs several taken neighbours.
    public static final String NORMAL = "normal", AND = "and";

    public String id;
    public float x, y;
    public String title = "Node";
    public List<String> description = new ArrayList<>();
    public String icon = "minecraft:stone";
    public String shape = SMALL;
    /** circle (default), square, diamond or star: only changes how the node is drawn. */
    public String form = CIRCLE;
    public String kind = NORMAL;
    /** For kind=and: how many incoming neighbours must be taken. */
    public int require = 2;   // AND nodes: connected nodes needed; 0 = all of them
    /** Optional color (#RRGGBB) to tell parts of the tree apart; empty = no tint. */
    public String color = "";
    /** Explicit start flag (independent of the visual size). The player may begin at any start node. */
    public boolean start = false;
    /** How many times the node can be levelled up (1 = ordinary node). */
    public int maxLevel = 1;
    /** Extra cost added per additional level: level k costs {@code cost + (k-1) * costStep}. */
    public int costStep = 0;
    /** Minimum level the connected previous node(s) must have before this node can be taken. */
    public int prevLevel = 1;
    /** Items handed over to the tree when the first level is taken. */
    public List<ItemReq> items = new ArrayList<>();
    /** Minimum attribute values the player must have to take the first level (never re-checked afterwards). */
    public List<AttrReq> attrReqs = new ArrayList<>();
    /** Recipe ids unlocked (in the recipe book) while the node is taken. */
    public List<String> recipes = new ArrayList<>();
    /** Right-click recipe ids ({@code data/<ns>/waifingskilltree/click_recipes/<name>.json} -> "ns:name") usable while the node is taken. */
    public List<String> clickRecipes = new ArrayList<>();
    /** Limited group this node belongs to (e.g. "keystone_mage"); the tree's group_limits says how many of its nodes a player may take. */
    public String group = "";
    /** Raises the limit of other groups while this node is taken: group -> +N (e.g. "keystone_mage" +1). */
    public Map<String, Integer> groupBonus = new LinkedHashMap<>();
    /** Exclusive trees this node opens: tree ids, or "#group" for every tree of that group. */
    public List<String> unlockTrees = new ArrayList<>();
    /** Command prefixes (e.g. "say", "gamemode creative") the player may use while the node is taken. */
    public List<String> unlockCommands = new ArrayList<>();
    /** Advancements (ids) the player must have to take this node. */
    public List<String> advancements = new ArrayList<>();
    /** Nodes of the same tree the player must already own (no link needed), e.g. two class masteries. */
    public List<String> requiresNodes = new ArrayList<>();
    /** How many of {@link #requiresNodes} are needed; 0 = all of them. */
    public int requiresCount = 0;
    /** For a choice option: while the player owns any of these nodes, taking this option does not use a pick of its
     *  choice and the option is never closed (e.g. a class's Rebirth is free once its mastery is owned). */
    public List<String> freeChoiceIf = new ArrayList<>();
    /** Levels above {@code capLevel} need {@code capCount} of {@code capNodes} (0 = no cap). E.g. a keystone open up to
     *  level 5, and its levels 6-10 only after two class masteries. */
    public int capLevel = 0;
    public List<String> capNodes = new ArrayList<>();
    public int capCount = 0;
    /** How many of {@link #capNodes} are needed for the levels above {@link #capLevel}; 0 = all of them. */
    public int capNodeCount() {
        return capCount <= 0 ? capNodes.size() : Math.min(capCount, capNodes.size());
    }
    /** Required nodes still needed: how many of {@link #requiresNodes} must be owned. */
    public int requiredNodeCount() {
        return requiresCount <= 0 ? requiresNodes.size() : Math.min(requiresCount, requiresNodes.size());
    }

    /** Cost in skill points (per level of the node). */
    public int cost = 1;
    public List<AttrMod> attributes = new ArrayList<>();
    /** Commands run when a level is gained (no slash). %player% = player name, %level% = new level.
     *  A leading "@N " restricts the command to reaching level N exactly. */
    public List<String> commands = new ArrayList<>();
    public List<String> refundCommands = new ArrayList<>();

    public SkillNode(String id, float x, float y) {
        this.id = id;
        this.x = x;
        this.y = y;
    }

    /** Start nodes are always available: the player chooses where to start. */
    public boolean isStart() {
        return start;
    }

    public int maxLevel() {
        return Math.max(1, maxLevel);
    }

    /** Cost of reaching {@code level} (1-based). */
    public int levelCost(int level) {
        return Math.max(0, cost + Math.max(0, level - 1) * costStep);
    }


    /** An item requirement: {@code count} of {@code item}. */
    public record ItemReq(String item, int count) {}

    /** The player's current value of {@code attribute} must be at least {@code amount}. */
    public record AttrReq(String attribute, double amount) {}

    public SkillNode copy() {
        SkillNode n = new SkillNode(id, x, y);
        n.copyPropsFrom(this);
        return n;
    }

    /** Copies everything except id and position (used by mirror editing). */
    public void copyPropsFrom(SkillNode o) {
        title = o.title;
        description = new ArrayList<>(o.description);
        icon = o.icon;
        shape = o.shape;
        form = o.form;
        kind = o.kind;
        require = o.require;
        cost = o.cost;
        color = o.color;
        advancements = new ArrayList<>(o.advancements);
        requiresNodes = new ArrayList<>(o.requiresNodes);
        requiresCount = o.requiresCount;
        freeChoiceIf = new ArrayList<>(o.freeChoiceIf);
        capLevel = o.capLevel;
        capNodes = new ArrayList<>(o.capNodes);
        capCount = o.capCount;
        attributes = new ArrayList<>(o.attributes);
        commands = new ArrayList<>(o.commands);
        refundCommands = new ArrayList<>(o.refundCommands);
        start = o.start;
        maxLevel = o.maxLevel;
        costStep = o.costStep;
        prevLevel = o.prevLevel;
        items = new ArrayList<>(o.items);
        attrReqs = new ArrayList<>(o.attrReqs);
        recipes = new ArrayList<>(o.recipes);
        unlockCommands = new ArrayList<>(o.unlockCommands);
        clickRecipes = new ArrayList<>(o.clickRecipes);
        unlockTrees = new ArrayList<>(o.unlockTrees);
        group = o.group;
        groupBonus = new LinkedHashMap<>(o.groupBonus);
    }

    /** Hit/draw radius in tree units. */
    public int radius() {
        return switch (shape) {
            case LARGE -> 27;
            case MEDIUM -> 19;
            default -> 13;
        };
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("x", x);
        o.addProperty("y", y);
        o.addProperty("title", title);
        o.add("description", strings(description));
        o.addProperty("icon", icon);
        o.addProperty("shape", shape);
        if (!CIRCLE.equals(form)) o.addProperty("form", form);
        o.addProperty("start", start);
        if (!NORMAL.equals(kind)) o.addProperty("kind", kind);
        if (AND.equals(kind)) o.addProperty("require", require);
        o.addProperty("cost", cost);
        if (maxLevel > 1) o.addProperty("max_level", maxLevel);
        if (costStep != 0) o.addProperty("cost_step", costStep);
        if (prevLevel > 1) o.addProperty("requires_prev_level", prevLevel);
        if (!items.isEmpty()) {
            JsonArray ia = new JsonArray();
            for (ItemReq r : items) {
                JsonObject io = new JsonObject();
                io.addProperty("item", r.item());
                io.addProperty("count", r.count());
                ia.add(io);
            }
            o.add("requires_items", ia);
        }
        if (!attrReqs.isEmpty()) {
            JsonArray aa = new JsonArray();
            for (AttrReq r : attrReqs) {
                JsonObject ao = new JsonObject();
                ao.addProperty("attribute", r.attribute());
                ao.addProperty("amount", r.amount());
                aa.add(ao);
            }
            o.add("requires_attributes", aa);
        }
        if (!recipes.isEmpty()) o.add("recipes", strings(recipes));
        if (!unlockCommands.isEmpty()) o.add("unlock_commands", strings(unlockCommands));
        if (!clickRecipes.isEmpty()) o.add("click_recipes", strings(clickRecipes));
        if (!unlockTrees.isEmpty()) o.add("unlock_trees", strings(unlockTrees));
        if (!group.isEmpty()) o.addProperty("group", group);
        if (!groupBonus.isEmpty()) {
            JsonObject bb = new JsonObject();
            groupBonus.forEach(bb::addProperty);
            o.add("group_bonus", bb);
        }
        if (!color.isEmpty()) o.addProperty("color", color);
        if (!advancements.isEmpty()) o.add("requires_advancements", strings(advancements));
        if (!requiresNodes.isEmpty()) {
            o.add("requires_nodes", strings(requiresNodes));
            if (requiresCount > 0) o.addProperty("requires_count", requiresCount);
        }
        if (!freeChoiceIf.isEmpty()) o.add("free_choice_if", strings(freeChoiceIf));
        if (capLevel > 0 && !capNodes.isEmpty()) {
            JsonObject cap = new JsonObject();
            cap.addProperty("level", capLevel);
            cap.add("requires_nodes", strings(capNodes));
            if (capCount > 0) cap.addProperty("requires_count", capCount);
            o.add("level_cap", cap);
        }
        JsonArray at = new JsonArray();
        attributes.forEach(a -> at.add(a.toJson()));
        o.add("attributes", at);
        o.add("commands", strings(commands));
        o.add("refund_commands", strings(refundCommands));
        return o;
    }

    public static SkillNode fromJson(JsonObject o) {
        SkillNode n = new SkillNode(o.get("id").getAsString(), o.get("x").getAsFloat(), o.get("y").getAsFloat());
        if (o.has("title")) n.title = o.get("title").getAsString();
        n.description = readStrings(o, "description");
        if (o.has("icon")) n.icon = o.get("icon").getAsString();
        if (o.has("shape")) {
            n.shape = switch (o.get("shape").getAsString()) {
                case "notable", MEDIUM -> MEDIUM;
                case "keystone", LARGE -> LARGE;
                default -> SMALL;
            };
        }
        if (o.has("form")) { String f = o.get("form").getAsString(); n.form = SQUARE.equals(f) || DIAMOND.equals(f) || STAR.equals(f) ? f : CIRCLE; }
        // Legacy trees have no "start" field: large nodes were the starts.
        n.start = o.has("start") ? o.get("start").getAsBoolean() : LARGE.equals(n.shape);
        if (o.has("max_level")) n.maxLevel = Math.max(1, o.get("max_level").getAsInt());
        if (o.has("cost_step")) n.costStep = o.get("cost_step").getAsInt();
        if (o.has("requires_prev_level")) n.prevLevel = Math.max(1, o.get("requires_prev_level").getAsInt());
        if (o.has("requires_items")) {
            for (JsonElement e : o.getAsJsonArray("requires_items")) {
                if (e.isJsonPrimitive()) { n.items.add(new ItemReq(e.getAsString(), 1)); continue; }
                JsonObject io = e.getAsJsonObject();
                n.items.add(new ItemReq(io.get("item").getAsString(), io.has("count") ? Math.max(1, io.get("count").getAsInt()) : 1));
            }
        }
        if (o.has("requires_attributes")) {
            for (JsonElement e : o.getAsJsonArray("requires_attributes")) {
                JsonObject ao = e.getAsJsonObject();
                n.attrReqs.add(new AttrReq(ao.get("attribute").getAsString(), ao.get("amount").getAsDouble()));
            }
        }
        n.recipes = readStrings(o, "recipes");
        n.unlockCommands = readStrings(o, "unlock_commands");
        n.clickRecipes = readStrings(o, "click_recipes");
        n.unlockTrees = readStrings(o, "unlock_trees");
        if (o.has("group")) n.group = o.get("group").getAsString().trim();
        if (o.has("group_bonus")) o.getAsJsonObject("group_bonus").entrySet().forEach(e -> n.groupBonus.put(e.getKey(), e.getValue().getAsInt()));
        if (o.has("kind")) n.kind = AND.equals(o.get("kind").getAsString()) ? AND : NORMAL;   // old "wormhole" nodes are normal now
        if (o.has("require")) n.require = Math.max(0, o.get("require").getAsInt());   // 0 = every connected path
        if (o.has("cost")) n.cost = o.get("cost").getAsInt();
        if (o.has("color")) n.color = o.get("color").getAsString();
        n.advancements = readStrings(o, "requires_advancements");
        n.requiresNodes = readStrings(o, "requires_nodes");
        n.freeChoiceIf = readStrings(o, "free_choice_if");
        if (o.has("level_cap") && o.get("level_cap").isJsonObject()) {
            JsonObject cap = o.getAsJsonObject("level_cap");
            n.capLevel = cap.has("level") ? Math.max(0, cap.get("level").getAsInt()) : 0;
            n.capNodes = readStrings(cap, "requires_nodes");
            n.capCount = cap.has("requires_count") ? Math.max(0, cap.get("requires_count").getAsInt()) : 0;
        }
        if (o.has("requires_count")) n.requiresCount = Math.max(0, o.get("requires_count").getAsInt());
        if (o.has("attributes")) {
            for (JsonElement e : o.getAsJsonArray("attributes")) n.attributes.add(AttrMod.fromJson(e.getAsJsonObject()));
        }
        n.commands = readStrings(o, "commands");
        n.refundCommands = readStrings(o, "refund_commands");
        return n;
    }

    private static JsonArray strings(List<String> l) {
        JsonArray a = new JsonArray();
        l.forEach(a::add);
        return a;
    }

    private static List<String> readStrings(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        if (!o.has(key)) return out;
        JsonElement v = o.get(key);
        if (v.isJsonArray()) for (JsonElement e : v.getAsJsonArray()) out.add(e.getAsString());
        else if (v.isJsonPrimitive())   // one text instead of a list: one entry per line
            for (String s : v.getAsString().split("\n")) if (!s.isBlank()) out.add(s.trim());
        return out;
    }
}

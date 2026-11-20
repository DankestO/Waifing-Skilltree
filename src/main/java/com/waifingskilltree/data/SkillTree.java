package com.waifingskilltree.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.util.*;

/** A whole tree: nodes + links. Each tree is a tab. */
public class SkillTree {
    public static final String DEFAULT_BG = "minecraft:textures/gui/advancements/backgrounds/stone.png";
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final String NORMAL = "normal", ONEWAY = "oneway";
    /** One way (A -> B) that is also a choice: of all the choice links leaving A, the player may follow only one. */
    public static final String CHOICE = "choice";

    /** Link between two nodes. With "oneway" you can only go from {@code from} to {@code to}. */
    public static final class Link {
        public final String from, to;
        public final String type;
        /** Bend of the link: the curve's control point sits at the middle of the line, moved sideways by curve * length
         *  (left of from -> to). 0 = straight. Only visual. */
        public final float curve;
        /** Level the origin must have before this link can be followed (0 = just taken). E.g. 5: "needs A at level 5". */
        public final int minLevel;

        public Link(String from, String to, String type) {
            this(from, to, type, 0f, 0);
        }

        public Link(String from, String to, String type, float curve) {
            this(from, to, type, curve, 0);
        }

        public Link(String from, String to, String type, float curve, int minLevel) {
            this.from = from;
            this.to = to;
            this.type = type;
            this.curve = curve;
            this.minLevel = Math.max(0, minLevel);
        }

        /** One way and choice links can only be followed from {@code from} to {@code to}. */
        public boolean directed() {
            return ONEWAY.equals(type) || CHOICE.equals(type);
        }

        public boolean touches(String id) {
            return from.equals(id) || to.equals(id);
        }

        public String other(String id) {
            return from.equals(id) ? to : from;
        }
    }

    public String id;
    public String title;
    public String icon = "minecraft:nether_star";
    public String background = DEFAULT_BG;
    /** Trees sharing a non-empty group are mutually exclusive: the first one a player uses locks the others
     *  until a node with {@code unlock_trees} opens them. Empty = a normal tree, never locked. */
    public String exclusiveGroup = "";
    /** When true the tree is closed until a node the player owns lists it in {@code unlock_trees} (by id or "#group"). */
    public boolean requiresUnlock = false;
    /** When true only the first start node is free; further start nodes need a taken neighbour like any node. */
    public boolean firstStartOnly = false;
    /** Limited group -> how many of its nodes a player may take (e.g. "keystone_mage" -> 2). Nodes join a group with "group". */
    public final Map<String, Integer> groupLimits = new LinkedHashMap<>();
    /** Choice origin node -> choice group (e.g. "mage_class" -> "class_paths"). A node's group_bonus "choices:<group>"
     *  gives one extra pick shared by every choice of that group; it is spent by the first choice that goes past its own limit. */
    public final Map<String, String> choiceGroups = new LinkedHashMap<>();
    /** Choice origin node -> how many options it allows before any bonus (default 1). 0 = a locked choice that only
     *  opens with a group_bonus "choice:<origin>" (or "choices:<group>") from a node the player took. */
    public final Map<String, Integer> choiceLimits = new LinkedHashMap<>();
    public final Map<String, SkillNode> nodes = new LinkedHashMap<>();
    /** Clave no dirigida "a|b" (a < b) -> enlace. */
    public final Map<String, Link> links = new LinkedHashMap<>();

    public SkillTree(String id) {
        this.id = id;
        this.title = id;
    }

    public static String linkKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    @Nullable
    public Link getLink(String a, String b) {
        return links.get(linkKey(a, b));
    }

    public boolean isLinked(String a, String b) {
        return links.containsKey(linkKey(a, b));
    }

    /** Crea o reemplaza el enlace entre a y b. */
    public void link(String a, String b, String type) {
        link(a, b, type, 0f);
    }

    public void link(String a, String b, String type, float curve) {
        link(a, b, type, curve, 0);
    }

    public void link(String a, String b, String type, float curve, int minLevel) {
        if (!a.equals(b) && nodes.containsKey(a) && nodes.containsKey(b)) {
            links.put(linkKey(a, b), new Link(a, b, type, curve, minLevel));
        }
    }

    public void link(String a, String b) {
        link(a, b, NORMAL);
    }

    public void unlink(String a, String b) {
        links.remove(linkKey(a, b));
    }

    /** Todos los nodos conectados, sin importar el sentido. */
    public Set<String> neighbors(String id) {
        Set<String> out = new HashSet<>();
        for (Link l : links.values()) if (l.touches(id)) out.add(l.other(id));
        return out;
    }

    /** Nodos desde los que se puede llegar a {@code id}. */
    public Set<String> incoming(String id) {
        Set<String> out = new HashSet<>();
        for (Link l : links.values()) {
            if (!l.touches(id)) continue;
            if (l.directed() && !l.to.equals(id)) continue;
            out.add(l.other(id));
        }
        return out;
    }

    /** Links through which {@code id} can be reached (the other end is where the player comes from). */
    public List<Link> incomingLinks(String id) {
        List<Link> out = new ArrayList<>();
        for (Link l : links.values()) {
            if (!l.touches(id)) continue;
            if (l.directed() && !l.to.equals(id)) continue;
            out.add(l);
        }
        return out;
    }

    /** Nodos a los que se puede avanzar desde {@code id}. */
    public Set<String> outgoing(String id) {
        Set<String> out = new HashSet<>();
        for (Link l : links.values()) {
            if (!l.touches(id)) continue;
            if (l.directed() && !l.from.equals(id)) continue;
            out.add(l.other(id));
        }
        return out;
    }


    public void removeNode(String id) {
        nodes.remove(id);
        links.values().removeIf(l -> l.touches(id));
    }

    public String freshId() {
        int i = nodes.size();
        while (nodes.containsKey("n" + i)) i++;
        return "n" + i;
    }

    public SkillTree copy() {
        return fromJson(id, toJson());
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("title", title);
        o.addProperty("icon", icon);
        o.addProperty("background", background);
        if (!exclusiveGroup.isEmpty()) o.addProperty("exclusive_group", exclusiveGroup);
        if (requiresUnlock) o.addProperty("requires_unlock", true);
        if (firstStartOnly) o.addProperty("first_start_only", true);
        if (!groupLimits.isEmpty()) {
            JsonObject bl = new JsonObject();
            groupLimits.forEach(bl::addProperty);
            o.add("group_limits", bl);
        }
        if (!choiceGroups.isEmpty()) {
            JsonObject cg = new JsonObject();
            choiceGroups.forEach(cg::addProperty);
            o.add("choice_groups", cg);
        }
        if (!choiceLimits.isEmpty()) {
            JsonObject cl = new JsonObject();
            choiceLimits.forEach(cl::addProperty);
            o.add("choice_limits", cl);
        }
        JsonArray ns = new JsonArray();
        nodes.values().forEach(n -> ns.add(n.toJson()));
        o.add("nodes", ns);
        JsonArray ls = new JsonArray();
        for (Link l : links.values()) {
            JsonArray pair = new JsonArray();
            pair.add(l.from);
            pair.add(l.to);
            if (!NORMAL.equals(l.type) || l.curve != 0f || l.minLevel > 0) pair.add(l.type);
            if (l.curve != 0f || l.minLevel > 0) pair.add(l.curve);
            if (l.minLevel > 0) pair.add(l.minLevel);
            ls.add(pair);
        }
        o.add("links", ls);
        return o;
    }

    public static SkillTree fromJson(String id, JsonObject o) {
        SkillTree t = new SkillTree(id);
        if (o.has("title")) t.title = o.get("title").getAsString();
        if (o.has("icon")) t.icon = o.get("icon").getAsString();
        if (o.has("background")) t.background = o.get("background").getAsString();
        if (o.has("exclusive_group")) t.exclusiveGroup = o.get("exclusive_group").getAsString().trim();
        if (o.has("requires_unlock")) t.requiresUnlock = o.get("requires_unlock").getAsBoolean();
        if (o.has("first_start_only")) t.firstStartOnly = o.get("first_start_only").getAsBoolean();
        if (o.has("group_limits")) o.getAsJsonObject("group_limits").entrySet().forEach(e -> t.groupLimits.put(e.getKey(), Math.max(0, e.getValue().getAsInt())));
        if (o.has("choice_groups")) o.getAsJsonObject("choice_groups").entrySet().forEach(e -> {
            String g = e.getValue().getAsString().trim();
            if (!g.isEmpty()) t.choiceGroups.put(e.getKey(), g);
        });
        if (o.has("choice_limits")) o.getAsJsonObject("choice_limits").entrySet().forEach(e -> t.choiceLimits.put(e.getKey(), Math.max(0, e.getValue().getAsInt())));
        if (o.has("nodes")) {
            for (JsonElement e : o.getAsJsonArray("nodes")) {
                SkillNode n = SkillNode.fromJson(e.getAsJsonObject());
                t.nodes.put(n.id, n);
            }
        }
        if (o.has("links")) {
            for (JsonElement e : o.getAsJsonArray("links")) {
                JsonArray p = e.getAsJsonArray();
                String type = p.size() > 2 ? p.get(2).getAsString() : NORMAL;
                float curve = p.size() > 3 ? p.get(3).getAsFloat() : 0f;
                int minLevel = p.size() > 4 ? p.get(4).getAsInt() : 0;
                if (!ONEWAY.equals(type) && !CHOICE.equals(type)) type = NORMAL;   // old "wormhole" links: a long link is drawn as a portal anyway
                t.link(p.get(0).getAsString(), p.get(1).getAsString(), type, curve, minLevel);
            }
        }
        return t;
    }

    public String toJsonString() {
        return GSON.toJson(toJson());
    }

    public static SkillTree fromJsonString(String id, String json) {
        return fromJson(id, JsonParser.parseString(json).getAsJsonObject());
    }
}

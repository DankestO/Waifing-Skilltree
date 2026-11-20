package com.waifingskilltree.logic;

import com.waifingskilltree.WaifingSkilltreeMod;
import com.waifingskilltree.capability.PlayerSkills;
import com.waifingskilltree.data.AttrMod;
import com.waifingskilltree.data.SkillNode;
import com.waifingskilltree.data.SkillRules;
import com.waifingskilltree.data.SkillTree;
import com.waifingskilltree.data.TreeLocks;
import com.waifingskilltree.data.TreeStorage;
import com.waifingskilltree.net.Network;
import com.waifingskilltree.net.S2CPlayerData;
import net.minecraft.advancements.Advancement;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import com.waifingskilltree.WaifingSkilltreeConfig;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Server logic: take/refund nodes (paid in skill points), attributes, recipes, commands and exclusive trees. */
public final class SkillManager {
    private static final String MOD_PREFIX = "waifingskilltree:";
    /** Prefix used by test builds before 1.0.0: still removed so old worlds don't keep stale bonuses. */
    private static final String LEGACY_PREFIX = "forge" + "tree:";

    private SkillManager() {}

    @Nullable
    public static PlayerSkills get(ServerPlayer p) {
        return p.getCapability(PlayerSkills.CAP).orElse(null);
    }

    // ------------------------------------------------------------ actions

    /** Takes the first level of a node or raises it by one level. */
    public static void unlock(ServerPlayer p, String treeId, String nodeId) {
        PlayerSkills s = get(p);
        SkillTree t = TreeStorage.get(treeId);
        if (s == null || t == null) return;
        SkillNode n = t.nodes.get(nodeId);
        if (n == null) return;

        int cur = s.level(treeId, nodeId);
        if (cur >= n.maxLevel()) { deny(p); return; }
        Map<String, Integer> lv = s.levels(treeId);
        if (!SkillRules.levelAllowed(n, cur + 1, id -> lv.getOrDefault(id, 0))) { deny(p); return; }   // level_cap
        boolean creative = p.getAbilities().instabuild;

        if (cur == 0) {
            if (!treeAllowed(s, t)) {
                deny(p);
                return;
            }
            if (!SkillRules.canTake(t, n, id -> lv.getOrDefault(id, 0))) {
                deny(p);
                return;
            }
            if (!SkillRules.groupAllowed(t, n, id -> lv.getOrDefault(id, 0))) {
                deny(p);
                return;
            }
            if (!advancementsMet(p, n)) {
                deny(p);
                return;
            }
            if (!creative && !attributesMet(p, n)) {
                deny(p);
                return;
            }
            if (!creative && !hasItems(p, n)) {
                deny(p);
                return;
            }
        }
        int cost = SkillRules.cost(t, n, id -> lv.getOrDefault(id, 0), cur + 1);
        if (available(s) < cost && !creative) {
            deny(p);
            return;
        }

        Set<String> recipesBefore = recipesOf(s);
        if (!t.exclusiveGroup.isEmpty()) s.primary.putIfAbsent(t.exclusiveGroup, treeId);
        if (cur == 0 && !creative && !n.items.isEmpty()) {
            takeItems(p, n);
            s.paidItems(treeId).add(nodeId);
        }
        s.of(treeId).computeIfAbsent(nodeId, k -> new ArrayList<>()).add(creative ? 0 : cost);
        applyAttributes(p);
        syncRecipes(p, s, recipesBefore);
        runCommands(p, n.commands, cur + 1, false);
        sync(p);
        // feedback: a chime that rises with each level; notables and keystones sound grander
        boolean big = !SkillNode.SMALL.equals(n.shape);
        sound(p, SoundEvents.ENCHANTMENT_TABLE_USE, 1f, Math.min(2f, 1.1f + 0.08f * cur));
        if (big) sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 0.9f);
    }

    /** Removes one level of a node (the last one bought). */
    public static void refund(ServerPlayer p, String treeId, String nodeId) {
        PlayerSkills s = get(p);
        SkillTree t = TreeStorage.get(treeId);
        if (s == null || t == null) return;
        int cur = s.level(treeId, nodeId);
        if (cur == 0) return;

        Map<String, Integer> remaining = s.levels(treeId);
        if (cur == 1) remaining.remove(nodeId);
        else remaining.put(nodeId, cur - 1);
        // Whatever remains taken must still be valid after removing this level.
        if (!SkillRules.valid(t, remaining).containsAll(remaining.keySet())) { deny(p); return; }
        // ...and must not leave an exclusive tree open without the node that opened it.
        Map<String, Map<String, Integer>> all = s.allLevels();
        all.put(treeId, remaining);
        Map<String, String> prim = new HashMap<>(s.primary);
        if (!t.exclusiveGroup.isEmpty() && !TreeLocks.hasAny(remaining) && treeId.equals(prim.get(t.exclusiveGroup))
                && !groupUsed(all, t.exclusiveGroup)) prim.remove(t.exclusiveGroup);
        if (!TreeLocks.consistent(TreeStorage.all(), prim, all)) {
            deny(p);
            return;
        }

        Set<String> recipesBefore = recipesOf(s);
        removeLevel(p, s, t, treeId, nodeId, true);
        cleanPrimary(s);
        applyAttributes(p);
        syncRecipes(p, s, recipesBefore);
        sync(p);
        sound(p, SoundEvents.GRINDSTONE_USE, 0.6f, 1.3f);
    }

    /** Pops the last level of a node (its points become available again; items come back when the node empties). */
    private static void removeLevel(ServerPlayer p, PlayerSkills s, SkillTree t, String treeId, String nodeId, boolean runCmds) {
        removeLevel(p, s, t, treeId, nodeId, runCmds, true);
    }

    private static void removeLevel(ServerPlayer p, PlayerSkills s, SkillTree t, String treeId, String nodeId, boolean runCmds, boolean items) {
        List<Integer> paid = s.of(treeId).get(nodeId);
        if (paid == null || paid.isEmpty()) return;
        int level = paid.size();
        paid.remove(paid.size() - 1);
        SkillNode n = t == null ? null : t.nodes.get(nodeId);
        if (paid.isEmpty()) {
            s.of(treeId).remove(nodeId);
            if (s.paidItems(treeId).remove(nodeId) && items && n != null && WaifingSkilltreeConfig.REFUND_ITEMS.get()) giveItems(p, n);
        }
        if (runCmds && n != null) runCommands(p, n.refundCommands, level, true);
    }

    /** Does the player hold any node level? */
    public static boolean hasAnything(ServerPlayer p) {
        PlayerSkills s = get(p);
        if (s == null) return false;
        for (String tree : s.taken.keySet()) if (!s.levels(tree).isEmpty()) return true;
        return false;
    }

    // ------------------------------------------------------------ feedback (sounds only: chat text is hidden behind the tree screen)

    private static void sound(ServerPlayer p, net.minecraft.sounds.SoundEvent e, float vol, float pitch) {
        p.playNotifySound(e, net.minecraft.sounds.SoundSource.PLAYERS, vol, pitch);
    }

    /** Low "nope" when a purchase or refund is refused. */
    private static void deny(ServerPlayer p) {
        sound(p, SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f, 0.55f);
    }

    /** Hardcore option (resetOnDeath): the whole tree is lost on death. Items paid for nodes do not come back;
     *  the earned and bought points are lost too (buying starts again at 1 level). */
    public static void wipeOnDeath(ServerPlayer p) {
        PlayerSkills s = get(p);
        if (s == null) return;
        boolean had = hasAnything(p);
        Set<String> recipesBefore = recipesOf(s);
        for (String treeId : new ArrayList<>(s.taken.keySet())) {
            SkillTree t = TreeStorage.get(treeId);
            for (String nodeId : new ArrayList<>(s.of(treeId).keySet())) {
                while (s.level(treeId, nodeId) > 0) removeLevel(p, s, t, treeId, nodeId, true, false);
            }
        }
        s.taken.clear();
        s.itemsPaid.clear();
        s.primary.clear();
        s.bonus = 0;
        s.bought = 0;
        s.highestLevel = 0;
        applyAttributes(p);
        syncRecipes(p, s, recipesBefore);
        sync(p);
        if (had) p.sendSystemMessage(Component.translatable("waifingskilltree.msg.death_reset").withStyle(net.minecraft.ChatFormatting.DARK_RED));
    }

    public static void respec(ServerPlayer p) {
        PlayerSkills s = get(p);
        if (s == null) return;
        Set<String> recipesBefore = recipesOf(s);
        for (String treeId : new ArrayList<>(s.taken.keySet())) {
            SkillTree t = TreeStorage.get(treeId);
            for (String nodeId : new ArrayList<>(s.of(treeId).keySet())) {
                while (s.level(treeId, nodeId) > 0) removeLevel(p, s, t, treeId, nodeId, true);
            }
        }
        s.taken.clear();
        s.itemsPaid.clear();
        s.primary.clear();
        applyAttributes(p);
        syncRecipes(p, s, recipesBefore);
        sync(p);
        p.displayClientMessage(Component.translatable("waifingskilltree.msg.respec"), true);
    }

    // ------------------------------------------------------------ attribute requirements

    /** Does the player currently have every attribute value the node asks for? Checked only when taking the first level. */
    public static boolean attributesMet(ServerPlayer p, SkillNode n) {
        for (SkillNode.AttrReq r : n.attrReqs) {
            ResourceLocation rl = ResourceLocation.tryParse(r.attribute());
            Attribute a = rl == null ? null : ForgeRegistries.ATTRIBUTES.getValue(rl);
            if (a == null || p.getAttribute(a) == null || p.getAttributeValue(a) + 1.0E-6 < r.amount()) return false;
        }
        return true;
    }

    // ------------------------------------------------------------ items

    public static boolean hasItems(ServerPlayer p, SkillNode n) {
        for (SkillNode.ItemReq r : n.items) {
            Item it = item(r.item());
            if (it == null || countItem(p, it) < r.count()) return false;
        }
        return true;
    }

    private static int countItem(ServerPlayer p, Item it) {
        int c = 0;
        for (ItemStack st : p.getInventory().items) if (st.is(it)) c += st.getCount();
        for (ItemStack st : p.getInventory().offhand) if (st.is(it)) c += st.getCount();
        return c;
    }

    private static void takeItems(ServerPlayer p, SkillNode n) {
        for (SkillNode.ItemReq r : n.items) {
            Item it = item(r.item());
            if (it == null) continue;
            int left = r.count();
            for (var list : List.of(p.getInventory().items, p.getInventory().offhand)) {
                for (ItemStack st : list) {
                    if (left <= 0) break;
                    if (!st.is(it)) continue;
                    int take = Math.min(left, st.getCount());
                    st.shrink(take);
                    left -= take;
                }
            }
        }
        p.getInventory().setChanged();
        p.containerMenu.broadcastChanges();
    }

    private static void giveItems(ServerPlayer p, SkillNode n) {
        for (SkillNode.ItemReq r : n.items) {
            Item it = item(r.item());
            if (it == null) continue;
            int left = r.count();
            while (left > 0) {
                ItemStack st = new ItemStack(it, Math.min(left, it.getMaxStackSize()));
                left -= st.getCount();
                if (!p.getInventory().add(st)) p.drop(st, false);
            }
        }
    }

    @Nullable
    private static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
    }

    // ------------------------------------------------------------ recipes and commands unlocked by nodes

    /** Ids of every recipe granted by the nodes the player currently has. */
    public static Set<String> recipesOf(PlayerSkills s) {
        Set<String> out = new HashSet<>();
        for (var te : s.taken.entrySet()) {
            SkillTree t = TreeStorage.get(te.getKey());
            if (t == null) continue;
            for (var ne : te.getValue().entrySet()) {
                SkillNode n = t.nodes.get(ne.getKey());
                if (n != null && !ne.getValue().isEmpty()) out.addAll(n.recipes);
            }
        }
        return out;
    }

    public static Set<String> recipesOf(ServerPlayer p) {
        PlayerSkills s = get(p);
        return s == null ? Set.of() : recipesOf(s);
    }

    /** Ids of the right-click recipes the player's nodes unlock. */
    public static Set<String> clickRecipesOf(ServerPlayer p) {
        PlayerSkills s = get(p);
        Set<String> out = new HashSet<>();
        if (s == null) return out;
        for (var te : s.taken.entrySet()) {
            SkillTree t = TreeStorage.get(te.getKey());
            if (t == null) continue;
            for (var ne : te.getValue().entrySet()) {
                SkillNode n = t.nodes.get(ne.getKey());
                if (n != null && !ne.getValue().isEmpty()) out.addAll(n.clickRecipes);
            }
        }
        return out;
    }

    /** Awards/revokes recipes so the recipe book matches the nodes taken now. */
    private static void syncRecipes(ServerPlayer p, PlayerSkills s, Set<String> before) {
        Set<String> after = recipesOf(s);
        List<Recipe<?>> add = new ArrayList<>(), remove = new ArrayList<>();
        for (String id : after) if (!before.contains(id)) recipe(p, id, add);
        for (String id : before) if (!after.contains(id)) recipe(p, id, remove);
        if (!add.isEmpty()) p.awardRecipes(add);
        if (!remove.isEmpty()) p.resetRecipes(remove);
    }

    private static void recipe(ServerPlayer p, String id, List<Recipe<?>> out) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return;
        p.server.getRecipeManager().byKey(rl).ifPresentOrElse(out::add,
                () -> WaifingSkilltreeMod.LOGGER.warn("Unknown recipe in skill tree: {}", id));
    }

    /** Command prefixes the player has unlocked through taken nodes (lower case, no slash). */
    public static Set<String> unlockedCommands(PlayerSkills s) {
        Set<String> out = new HashSet<>();
        for (var te : s.taken.entrySet()) {
            SkillTree t = TreeStorage.get(te.getKey());
            if (t == null) continue;
            for (var ne : te.getValue().entrySet()) {
                SkillNode n = t.nodes.get(ne.getKey());
                if (n != null && !ne.getValue().isEmpty()) for (String c : n.unlockCommands) out.add(CommandGate.normalize(c));
            }
        }
        return out;
    }

    // ------------------------------------------------------------ atributos

    /** Quita todos los modificadores de este mod y vuelve a aplicar los de los nodos tomados. */
    public static void applyAttributes(ServerPlayer p) {
        PlayerSkills s = get(p);
        if (s == null) return;

        for (Attribute a : ForgeRegistries.ATTRIBUTES.getValues()) {
            AttributeInstance inst = p.getAttribute(a);
            if (inst == null) continue;
            List<AttributeModifier> old = new ArrayList<>();
            for (AttributeModifier m : inst.getModifiers()) {
                if (m.getName().startsWith(MOD_PREFIX) || m.getName().startsWith(LEGACY_PREFIX)) old.add(m);
            }
            old.forEach(inst::removeModifier);
        }

        for (var te : s.taken.entrySet()) {
            SkillTree t = TreeStorage.get(te.getKey());
            if (t == null) continue;
            for (var ne : te.getValue().entrySet()) {
                String nodeId = ne.getKey();
                int level = ne.getValue().size();
                SkillNode n = t.nodes.get(nodeId);
                if (n == null || level <= 0) continue;
                for (int i = 0; i < n.attributes.size(); i++) {
                    AttrMod am = n.attributes.get(i);
                    Attribute attr = am.resolve();
                    if (attr == null) continue;
                    if (s.disabled.contains(am.attribute())) continue;                     // switched off by the player
                    if (am.condition() != null && !am.condition().test(p)) continue;   // conditional bonus currently off
                    AttributeInstance inst = p.getAttribute(attr);
                    if (inst == null) continue;
                    String key = te.getKey() + ":" + nodeId + ":" + i;
                    UUID id = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
                    if (inst.getModifier(id) == null) {
                        inst.addPermanentModifier(new AttributeModifier(id, MOD_PREFIX + key, am.amount() * level, am.op()));
                    }
                }
            }
        }
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
        CONDITION_SIGS.put(p, conditionSignature(p, s));
    }

    /** Switches the tree bonuses of one attribute off or on again (summary panel of the screen). */
    public static void setAttributeDisabled(ServerPlayer p, String attribute, boolean off) {
        PlayerSkills s = get(p);
        if (s == null || attribute == null || attribute.isEmpty()) return;
        if (off) s.disabled.add(attribute); else s.disabled.remove(attribute);
        applyAttributes(p);
        sync(p);
    }

    // ------------------------------------------------------------ conditional bonuses

    /** Last evaluated state of every conditional attribute, per player (weak: entries vanish with the player). */
    private static final Map<ServerPlayer, Integer> CONDITION_SIGS = new WeakHashMap<>();

    /** Hash of the true/false results of all conditions of the player's taken nodes. */
    private static int conditionSignature(ServerPlayer p, PlayerSkills s) {
        int h = 1;
        for (var te : s.taken.entrySet()) {
            SkillTree t = TreeStorage.get(te.getKey());
            if (t == null) continue;
            for (var ne : te.getValue().entrySet()) {
                SkillNode n = t.nodes.get(ne.getKey());
                if (n == null || ne.getValue().isEmpty()) continue;
                for (AttrMod am : n.attributes) {
                    if (am.condition() != null) h = h * 31 + (am.condition().test(p) ? 1 : 2);
                }
            }
        }
        return h;
    }

    /**
     * Called every few ticks (config conditionCheckInterval). Cheap: it only evaluates the conditions of the
     * player's taken nodes and re-applies attributes when at least one result changed.
     */
    public static void refreshConditional(ServerPlayer p) {
        PlayerSkills s = get(p);
        if (s == null || s.taken.isEmpty()) return;
        Integer last = CONDITION_SIGS.get(p);
        if (last != null && last == conditionSignature(p, s)) return;
        applyAttributes(p);
    }

    // ------------------------------------------------------------ comandos

    /** Runs node commands. {@code level} is the level reached (or lost); "@N " prefixes filter by level. */
    public static void runCommands(ServerPlayer p, List<String> commands, int level, boolean refund) {
        if (commands.isEmpty()) return;
        CommandSourceStack src = p.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        String name = p.getGameProfile().getName();
        for (String raw : commands) {
            String cmd = raw.trim();
            if (cmd.startsWith("@")) {
                int sp = cmd.indexOf(' ');
                if (sp < 0) continue;
                try {
                    if (Integer.parseInt(cmd.substring(1, sp)) != level) continue;
                } catch (NumberFormatException e) {
                    continue;
                }
                cmd = cmd.substring(sp + 1).trim();
            }
            if (cmd.isEmpty()) continue;
            try {
                p.server.getCommands().performPrefixedCommand(src,
                        cmd.replace("%player%", name).replace("%level%", String.valueOf(level)));
            } catch (Exception e) {
                WaifingSkilltreeMod.LOGGER.error("Failed to run node command '" + cmd + "'", e);
            }
        }
    }

    // ------------------------------------------------------------ validation and sync

    /** After editing/reloading trees: refunds levels of nodes that no longer exist, are unreachable or exceed max_level. */
    public static void validate(ServerPlayer p) {
        PlayerSkills s = get(p);
        if (s == null) return;
        Set<String> recipesBefore = recipesOf(s);
        for (String treeId : new ArrayList<>(s.taken.keySet())) {
            SkillTree t = TreeStorage.get(treeId);
            Map<String, List<Integer>> mine = s.of(treeId);
            if (t == null) {
                for (String id : new ArrayList<>(mine.keySet())) {
                    while (s.level(treeId, id) > 0) removeLevel(p, s, null, treeId, id, false);
                }
                s.taken.remove(treeId);
                s.itemsPaid.remove(treeId);
                continue;
            }
            Set<String> ok = treeAllowed(s, t) ? SkillRules.valid(t, s.levels(treeId)) : Set.of();
            for (String id : new ArrayList<>(mine.keySet())) {
                SkillNode n = t.nodes.get(id);
                if (!ok.contains(id)) {
                    while (s.level(treeId, id) > 0) removeLevel(p, s, t, treeId, id, false);
                } else {
                    while (s.level(treeId, id) > n.maxLevel()) removeLevel(p, s, t, treeId, id, false);
                }
            }
        }
        cleanPrimary(s);
        applyAttributes(p);
        syncRecipes(p, s, recipesBefore);
        sync(p);
    }

    // ------------------------------------------------------------ skill points and exclusive trees

    /** Points given by experience levels (config levelsPerFreePoint / pointsPerLevel, applied retroactively). */
    public static int levelPoints(int highestLevel) {
        int per = WaifingSkilltreeConfig.LEVELS_PER_POINT.get();
        return per <= 0 ? 0 : highestLevel / per * WaifingSkilltreeConfig.POINTS_PER_LEVEL.get();
    }

    public static int available(PlayerSkills s) {
        return levelPoints(s.highestLevel) + s.bonus + WaifingSkilltreeConfig.STARTING_POINTS.get() - s.spent();
    }

    /** Remembers a new highest level (and tells the player when it gave points). Cheap: called each player tick, it only compares two ints. */
    public static void checkLevelPoints(ServerPlayer p) {
        PlayerSkills s = get(p);
        if (s == null || p.experienceLevel <= s.highestLevel) return;
        int gained = levelPoints(p.experienceLevel) - levelPoints(s.highestLevel);
        s.highestLevel = p.experienceLevel;
        if (gained > 0) p.displayClientMessage(Component.translatable("waifingskilltree.msg.points_gained", gained, available(s)), true);
        sync(p);
    }

    /** Experience levels the next bought point costs (1, 2, 3... times buyCostStep); -1 when buying is disabled, -2 when maxBoughtPoints is reached. */
    public static int nextPointCost(PlayerSkills s) {
        if (!WaifingSkilltreeConfig.BUY_POINTS.get()) return -1;
        int max = WaifingSkilltreeConfig.MAX_BOUGHT_POINTS.get();
        if (max > 0 && s.bought >= max) return -2;
        return (s.bought + 1) * WaifingSkilltreeConfig.BUY_COST_STEP.get();
    }

    /** Pays experience levels for one skill point. Each purchase costs more than the last. */
    public static void buyPoint(ServerPlayer p) {
        PlayerSkills s = get(p);
        if (s == null) return;
        int cost = nextPointCost(s);
        if (cost < 0) { deny(p); return; }
        boolean creative = p.getAbilities().instabuild;
        if (!creative && p.experienceLevel < cost) {
            deny(p);
            return;
        }
        if (!creative) p.giveExperienceLevels(-cost);
        s.bought++;
        s.bonus++;
        sync(p);
        sound(p, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.5f);
    }

    /** Adds (or removes, with a negative amount) bonus points. */
    public static void addPoints(ServerPlayer p, int amount) {
        PlayerSkills s = get(p);
        if (s == null) return;
        s.bonus += amount;
        sync(p);
    }

    public static boolean treeAllowed(PlayerSkills s, SkillTree t) {
        return TreeLocks.allowed(t, s.primary, TreeLocks.unlocked(TreeStorage.all(), s.allLevels()));
    }

    private static boolean groupUsed(Map<String, Map<String, Integer>> all, String group) {
        for (var e : all.entrySet()) {
            SkillTree t = TreeStorage.get(e.getKey());
            if (t != null && group.equals(t.exclusiveGroup) && TreeLocks.hasAny(e.getValue())) return true;
        }
        return false;
    }

    /** Forgets the chosen tree of groups the player no longer uses at all. */
    private static void cleanPrimary(PlayerSkills s) {
        Map<String, Map<String, Integer>> all = s.allLevels();
        s.primary.keySet().removeIf(g -> !groupUsed(all, g));
    }

    /** Does the player have all advancements the node requires? */
    public static boolean advancementsMet(ServerPlayer p, SkillNode n) {
        for (String id : n.advancements) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null) return false;
            Advancement adv = p.server.getAdvancements().getAdvancement(rl);
            if (adv == null || !p.getAdvancements().getOrStartProgress(adv).isDone()) return false;
        }
        return true;
    }

    public static void sync(ServerPlayer p) {
        PlayerSkills s = get(p);
        if (s == null) return;
        // nodes locked by pending advancements ("tree:node") so the client shows them locked
        Set<String> gated = new HashSet<>();
        for (SkillTree t : TreeStorage.all().values()) {
            for (SkillNode n : t.nodes.values()) {
                if (!n.advancements.isEmpty() && !advancementsMet(p, n)) gated.add(t.id + ":" + n.id);
            }
        }
        Network.sendTo(p, new S2CPlayerData(s.allLevels(), gated, WaifingSkilltreeConfig.ALLOW_SELF_REFUND.get(), available(s), new HashMap<>(s.primary), nextPointCost(s), new HashSet<>(s.disabled)));
    }
}

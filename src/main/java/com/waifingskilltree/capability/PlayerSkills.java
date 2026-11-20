package com.waifingskilltree.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Player data: taken nodes (skill points paid per node level), points earned and the chosen exclusive trees. */
public class PlayerSkills {
    public static final Capability<PlayerSkills> CAP = CapabilityManager.get(new CapabilityToken<>() {});

    /** Bonus skill points given by commands (level points are computed from highestLevel with the current config). */
    public int bonus = 0;
    /** Points bought with experience levels so far (each purchase costs more than the previous one). */
    public int bought = 0;
    /** Set on a death clone when resetOnDeath is on: the tree is wiped when the player respawns (not saved). */
    public transient boolean wipeOnRespawn = false;
    /** Highest experience level ever reached: points are only granted above it (dying and levelling again gives nothing). */
    public int highestLevel = 0;
    /** Exclusive group -> tree the player chose first in that group. */
    public final Map<String, String> primary = new HashMap<>();
    /** tree -> (node -> skill points paid for each node level; list size = current level) */
    public final Map<String, Map<String, List<Integer>>> taken = new HashMap<>();
    /** tree -> nodes whose item requirement was actually paid (creative mode pays nothing). */
    public final Map<String, Set<String>> itemsPaid = new HashMap<>();
    /** Attributes the player switched off in the summary panel (their bonuses from the trees are not applied). */
    public final Set<String> disabled = new HashSet<>();

    public Map<String, List<Integer>> of(String tree) {
        return taken.computeIfAbsent(tree, k -> new HashMap<>());
    }

    public Set<String> paidItems(String tree) {
        return itemsPaid.computeIfAbsent(tree, k -> new HashSet<>());
    }

    public int level(String tree, String node) {
        Map<String, List<Integer>> m = taken.get(tree);
        List<Integer> l = m == null ? null : m.get(node);
        return l == null ? 0 : l.size();
    }

    public boolean has(String tree, String node) {
        return level(tree, node) > 0;
    }

    /** node -> level for one tree. */
    public Map<String, Integer> levels(String tree) {
        Map<String, Integer> out = new HashMap<>();
        Map<String, List<Integer>> m = taken.get(tree);
        if (m != null) m.forEach((k, v) -> { if (!v.isEmpty()) out.put(k, v.size()); });
        return out;
    }

    /** Points currently spent in all trees. */
    public int spent() {
        int s = 0;
        for (var m : taken.values()) for (var l : m.values()) for (int v : l) s += v;
        return s;
    }

    /** Available points = level points + bonus + starting - spent (level points: see SkillManager.levelPoints). */

    /** tree -> node -> level, for every tree. */
    public Map<String, Map<String, Integer>> allLevels() {
        Map<String, Map<String, Integer>> out = new HashMap<>();
        for (String t : taken.keySet()) out.put(t, levels(t));
        return out;
    }

    public void copyFrom(PlayerSkills o) {
        taken.clear();
        itemsPaid.clear();
        primary.clear();
        primary.putAll(o.primary);
        bonus = o.bonus;
        bought = o.bought;
        highestLevel = o.highestLevel;
        o.taken.forEach((k, v) -> {
            Map<String, List<Integer>> c = new HashMap<>();
            v.forEach((n, l) -> c.put(n, new ArrayList<>(l)));
            taken.put(k, c);
        });
        o.itemsPaid.forEach((k, v) -> itemsPaid.put(k, new HashSet<>(v)));
        disabled.clear();
        disabled.addAll(o.disabled);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        CompoundTag trees = new CompoundTag();
        taken.forEach((tree, nodes) -> {
            CompoundTag t = new CompoundTag();
            nodes.forEach((n, l) -> t.putIntArray(n, l));
            trees.put(tree, t);
        });
        tag.put("taken", trees);
        CompoundTag items = new CompoundTag();
        itemsPaid.forEach((tree, set) -> {
            ListTag l = new ListTag();
            set.forEach(n -> l.add(StringTag.valueOf(n)));
            items.put(tree, l);
        });
        tag.put("items_paid", items);
        tag.putInt("bonus_points", bonus);
        tag.putInt("highest_level", highestLevel);
        tag.putInt("bought_points", bought);
        CompoundTag pr = new CompoundTag();
        primary.forEach(pr::putString);
        tag.put("primary", pr);
        ListTag dis = new ListTag();
        disabled.forEach(a -> dis.add(StringTag.valueOf(a)));
        tag.put("disabled_attributes", dis);
        return tag;
    }

    public void load(CompoundTag tag) {
        taken.clear();
        itemsPaid.clear();
        primary.clear();
        bonus = tag.getInt("bonus_points");
        highestLevel = tag.getInt("highest_level");
        bought = tag.getInt("bought_points");
        CompoundTag pr = tag.getCompound("primary");
        for (String g : pr.getAllKeys()) primary.put(g, pr.getString(g));
        disabled.clear();
        ListTag dis = tag.getList("disabled_attributes", Tag.TAG_STRING);
        for (int i = 0; i < dis.size(); i++) disabled.add(dis.getString(i));
        CompoundTag trees = tag.getCompound("taken");
        for (String tree : trees.getAllKeys()) {
            CompoundTag t = trees.getCompound(tree);
            Map<String, List<Integer>> m = new HashMap<>();
            for (String n : t.getAllKeys()) {
                List<Integer> l = new ArrayList<>();
                if (t.get(n) instanceof IntArrayTag arr) for (int v : arr.getAsIntArray()) l.add(v);
                else l.add(t.getInt(n));   // old saves: a single int = one level
                m.put(n, l);
            }
            taken.put(tree, m);
        }
        CompoundTag items = tag.getCompound("items_paid");
        for (String tree : items.getAllKeys()) {
            Set<String> set = new HashSet<>();
            for (Tag e : items.getList(tree, Tag.TAG_STRING)) set.add(e.getAsString());
            itemsPaid.put(tree, set);
        }
    }

    public static class Provider implements ICapabilitySerializable<CompoundTag> {
        private final PlayerSkills inst = new PlayerSkills();
        private final LazyOptional<PlayerSkills> opt = LazyOptional.of(() -> inst);

        @Nonnull
        @Override
        public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
            return cap == CAP ? opt.cast() : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            return inst.save();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            inst.load(nbt);
        }
    }
}

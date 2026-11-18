package com.waifingskilltree.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A condition on the player's current state. Used by attribute modifiers: the bonus is only active while the
 * condition holds. Evaluated identically on server (to apply modifiers) and client (tooltips).
 *
 * JSON: {"type": "...", "value": "...", "not": false}; "all" / "any" take "conditions": [...].
 */
public final class Condition {
    public static final String ALL = "all", ANY = "any";
    /** Types that take no value. */
    public static final Set<String> NO_VALUE = Set.of("day", "night", "in_water", "sneaking", "sprinting", "on_fire", "raining", "underground", "two_handed");
    public static final Set<String> ITEM_TYPES = Set.of("main_hand", "off_hand", "helmet", "chestplate", "leggings", "boots");
    public static final Set<String> NUMERIC = Set.of("health_at_most", "health_at_least", "food_at_most", "food_at_least",
            "light_at_most", "light_at_least", "y_at_most", "y_at_least", "enchanted_main_hand", "enchanted_armor");
    public static final Set<String> KNOWN = Set.of("main_hand", "off_hand", "helmet", "chestplate", "leggings", "boots", "full_armor",
            "day", "night", "health_at_most", "health_at_least", "food_at_most", "food_at_least", "light_at_most", "light_at_least",
            "y_at_most", "y_at_least", "in_water", "sneaking", "sprinting", "on_fire", "raining", "underground",
            "dimension", "biome", "effect", "two_handed", "weapon_category", "off_hand_category", "dual_wield", "curio",
            "enchanted_main_hand", "enchanted_armor", ALL, ANY);

    public final String type;
    /** Item id / tag ("#ns:tag"), armor material, dimension, biome, effect id, or a number as text. */
    public final String value;
    public final boolean not;
    public final List<Condition> children;

    public Condition(String type, String value, boolean not, List<Condition> children) {
        this.type = type;
        this.value = value == null ? "" : value;
        this.not = not;
        this.children = children == null ? List.of() : children;
    }

    // ------------------------------------------------------------ json

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("type", type);
        if (!value.isEmpty()) {
            try {
                if (NUMERIC.contains(type)) o.addProperty("value", Double.parseDouble(value));
                else o.addProperty("value", value);
            } catch (NumberFormatException e) {
                o.addProperty("value", value);
            }
        }
        if (not) o.addProperty("not", true);
        if (!children.isEmpty()) {
            JsonArray a = new JsonArray();
            children.forEach(c -> a.add(c.toJson()));
            o.add("conditions", a);
        }
        return o;
    }

    public static Condition fromJson(JsonObject o) {
        String type = o.get("type").getAsString();
        String value = "";
        if (o.has("value")) {
            JsonElement e = o.get("value");
            if (e.getAsJsonPrimitive().isNumber()) {
                double d = e.getAsDouble();
                value = d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
            } else value = e.getAsString();
        }
        List<Condition> kids = new ArrayList<>();
        if (o.has("conditions")) for (JsonElement e : o.getAsJsonArray("conditions")) kids.add(fromJson(e.getAsJsonObject()));
        return new Condition(type, value, o.has("not") && o.get("not").getAsBoolean(), kids);
    }

    // ------------------------------------------------------------ evaluation

    public boolean test(Player p) {
        return evaluate(p) != not;
    }

    private double number() {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean evaluate(Player p) {
        Level level = p.level();
        switch (type) {
            case ALL: {
                for (Condition c : children) if (!c.test(p)) return false;
                return true;
            }
            case ANY: {
                for (Condition c : children) if (c.test(p)) return true;
                return children.isEmpty();
            }
            case "main_hand": return matchItem(p.getMainHandItem(), value);
            case "off_hand": return matchItem(p.getOffhandItem(), value);
            case "helmet": return matchItem(p.getItemBySlot(EquipmentSlot.HEAD), value);
            case "chestplate": return matchItem(p.getItemBySlot(EquipmentSlot.CHEST), value);
            case "leggings": return matchItem(p.getItemBySlot(EquipmentSlot.LEGS), value);
            case "boots": return matchItem(p.getItemBySlot(EquipmentSlot.FEET), value);
            case "full_armor": {
                for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    ItemStack st = p.getItemBySlot(s);
                    if (st.isEmpty()) return false;
                    if (!value.isEmpty()) {
                        if (!(st.getItem() instanceof ArmorItem ai)) return false;
                        String name = ai.getMaterial().getName();
                        if (!name.equals(value) && !name.endsWith(":" + value)) return false;
                    }
                }
                return true;
            }
            case "day": return hasCycle(level) && isDayTime(level);
            case "night": return hasCycle(level) && !isDayTime(level);
            case "health_at_most": return p.getHealth() / Math.max(1f, p.getMaxHealth()) * 100.0 <= number();
            case "health_at_least": return p.getHealth() / Math.max(1f, p.getMaxHealth()) * 100.0 >= number();
            case "food_at_most": return p.getFoodData().getFoodLevel() <= number();
            case "food_at_least": return p.getFoodData().getFoodLevel() >= number();
            case "light_at_most": return level.getMaxLocalRawBrightness(p.blockPosition()) <= number();
            case "light_at_least": return level.getMaxLocalRawBrightness(p.blockPosition()) >= number();
            case "y_at_most": return p.getY() <= number();
            case "y_at_least": return p.getY() >= number();
            case "in_water": return p.isInWater();
            case "sneaking": return p.isShiftKeyDown();
            case "sprinting": return p.isSprinting();
            case "on_fire": return p.isOnFire();
            case "raining": return level.isRainingAt(p.blockPosition());
            case "underground": return !level.canSeeSky(p.blockPosition());
            case "dimension": return level.dimension().location().toString().equals(value);
            case "biome": {
                var biome = level.getBiome(p.blockPosition());
                if (value.startsWith("#")) {
                    ResourceLocation rl = ResourceLocation.tryParse(value.substring(1));
                    return rl != null && biome.is(TagKey.create(Registries.BIOME, rl));
                }
                ResourceLocation rl = ResourceLocation.tryParse(value);
                return rl != null && biome.is(rl);
            }
            // any active effect of that category (value = how many at least; empty = 1). Neutral effects (glowing...) never count
            case "beneficial_effects": return countEffects(p, net.minecraft.world.effect.MobEffectCategory.BENEFICIAL) >= Math.max(1, number());
            case "harmful_effects": return countEffects(p, net.minecraft.world.effect.MobEffectCategory.HARMFUL) >= Math.max(1, number());
            case "effect": {
                ResourceLocation rl = ResourceLocation.tryParse(value);
                var eff = rl == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(rl);
                return eff != null && p.hasEffect(eff);
            }
            // ---- optional compat: Better Combat (weapon data) and Curios; false when the mod is missing
            case "two_handed": return com.waifingskilltree.compat.BetterCombatCompat.isTwoHanded(p.getMainHandItem());
            case "weapon_category": return inList(com.waifingskilltree.compat.BetterCombatCompat.category(p.getMainHandItem()), value);
            case "off_hand_category": return inList(com.waifingskilltree.compat.BetterCombatCompat.category(p.getOffhandItem()), value);
            case "dual_wield": {
                ItemStack m = p.getMainHandItem(), o = p.getOffhandItem();
                if (m.isEmpty() || o.isEmpty()) return false;
                if (com.waifingskilltree.compat.BetterCombatCompat.loaded()) {
                    if (!com.waifingskilltree.compat.BetterCombatCompat.isWeapon(m) || !com.waifingskilltree.compat.BetterCombatCompat.isWeapon(o)) return false;
                    if (com.waifingskilltree.compat.BetterCombatCompat.isTwoHanded(m)) return false;
                    return value.isEmpty() || (inList(com.waifingskilltree.compat.BetterCombatCompat.category(m), value)
                            && inList(com.waifingskilltree.compat.BetterCombatCompat.category(o), value));
                }
                return isBlade(m) && isBlade(o);
            }
            case "curio": {
                String slot = value, item = "";
                int eq = value.indexOf('=');
                if (eq >= 0) { slot = value.substring(0, eq).trim(); item = value.substring(eq + 1).trim(); }
                for (ItemStack st : com.waifingskilltree.compat.CuriosCompat.equipped(p, slot)) if (matchItem(st, item)) return true;
                return false;
            }
            case "enchanted_main_hand": return enchantCount(p.getMainHandItem()) >= Math.max(1, number());
            case "enchanted_armor": {
                int n = 0;
                for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    if (enchantCount(p.getItemBySlot(s)) > 0) n++;
                }
                return n >= Math.max(1, number());
            }
            default: return false;
        }
    }

    /** Enchantments of an item; Tinkers' Construct tools count the modifiers the player added (NBT "tic_upgrades")
     *  instead, unless the config turns that off. Material traits ("tic_modifiers") never count. */
    static int enchantCount(ItemStack st) {
        if (st.isEmpty()) return 0;
        int n = st.getAllEnchantments().size();
        if (tinkersCount() && st.getTag() != null && st.getTag().contains("tic_upgrades", net.minecraft.nbt.Tag.TAG_LIST))
            n += st.getTag().getList("tic_upgrades", net.minecraft.nbt.Tag.TAG_COMPOUND).size();
        return n;
    }

    private static boolean tinkersCount() {
        try { return com.waifingskilltree.WaifingSkilltreeConfig.TINKERS_MODIFIERS_AS_ENCHANTMENTS.get(); }
        catch (IllegalStateException e) { return true; }   // config not loaded yet: default
    }

    private static int countEffects(Player p, net.minecraft.world.effect.MobEffectCategory cat) {
        int n = 0;
        for (var e : p.getActiveEffects()) if (e.getEffect().getCategory() == cat) n++;
        return n;
    }

    /** value is a comma separated list; true when {@code s} is one of its entries. */
    private static boolean inList(String s, String list) {
        if (s.isEmpty()) return false;
        for (String e : list.split(",")) if (e.trim().equalsIgnoreCase(s)) return true;
        return false;
    }

    private static boolean isBlade(ItemStack st) {
        return st.is(TagKey.create(Registries.ITEM, new ResourceLocation("minecraft", "swords")))
                || st.is(TagKey.create(Registries.ITEM, new ResourceLocation("minecraft", "axes")));
    }

    /** Day / night only exist in the Overworld; in every other dimension both conditions are false (the bonus is off). */
    private static boolean hasCycle(Level l) {
        return l.dimension() == Level.OVERWORLD;
    }

    /** Vanilla day by the clock, not by light: ticks 0-12999 day, 13000-23999 night. (Torches and storms do not matter.) */
    private static boolean isDayTime(Level l) {
        return Math.floorMod(l.getDayTime(), 24000L) < 13000L;
    }

    private static boolean matchItem(ItemStack st, String v) {
        if (st.isEmpty()) return false;
        if (v.isEmpty()) return true;
        if (v.startsWith("#")) {
            ResourceLocation rl = ResourceLocation.tryParse(v.substring(1));
            return rl != null && st.is(TagKey.create(Registries.ITEM, rl));
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(st.getItem());
        return key != null && key.toString().equals(v);
    }

    // ------------------------------------------------------------ description (tooltips)

    public Component describe() {
        Component inner;
        if (ALL.equals(type) || ANY.equals(type)) {
            var out = Component.empty();
            for (int i = 0; i < children.size(); i++) {
                if (i > 0) out.append(Component.translatable(ALL.equals(type) ? "waifingskilltree.cond.and" : "waifingskilltree.cond.or"));
                out.append(children.get(i).describe());
            }
            inner = out;
        } else if (NO_VALUE.contains(type)) {
            inner = Component.translatable("waifingskilltree.cond." + type);
        } else if (value.isEmpty()) {
            inner = Component.translatable("waifingskilltree.cond." + type + ".any");
        } else {
            Component arg = Component.literal(value);
            if (ITEM_TYPES.contains(type)) {
                arg = itemOrTag(value);
            } else if ("effect".equals(type)) {
                ResourceLocation rl = ResourceLocation.tryParse(value);
                var eff = rl == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(rl);
                if (eff != null) arg = eff.getDisplayName();
            }
            inner = Component.translatable("waifingskilltree.cond." + type, arg);
        }
        return not ? Component.translatable("waifingskilltree.cond.not", inner) : inner;
    }

    /** Readable name for an item id or an item tag. A tag shows the name of one of its items, changing every 1.2 s
     *  (shield, then the next shield of the tag...), so "#forge:tools/shields" reads as "Shield". */
    public static Component itemOrTag(String v) {
        if (v.startsWith("#")) {
            ResourceLocation rl = ResourceLocation.tryParse(v.substring(1));
            if (rl == null) return Component.literal(v);
            var tags = ForgeRegistries.ITEMS.tags();
            List<net.minecraft.world.item.Item> items = new ArrayList<>();
            if (tags != null) tags.getTag(TagKey.create(Registries.ITEM, rl)).forEach(items::add);
            if (items.isEmpty()) {   // unknown or empty tag: its last word, without underscores ("tools/shields" -> "shields")
                String p = rl.getPath();
                return Component.literal(p.substring(p.lastIndexOf('/') + 1).replace('_', ' '));
            }
            int i = (int) (net.minecraft.Util.getMillis() / 1200L % items.size());
            return items.get(i).getDescription();
        }
        ResourceLocation rl = ResourceLocation.tryParse(v);
        var item = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        return item != null && item != net.minecraft.world.item.Items.AIR ? item.getDescription() : Component.literal(v);
    }
}

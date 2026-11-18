package com.waifingskilltree.data;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * A right-click recipe: hold {@code catalyst} on the cursor and right click {@code target} in an inventory slot.
 * Usable only by players owning a node that lists its id in {@code click_recipes}.
 * <pre>
 * data/&lt;ns&gt;/waifingskilltree/click_recipes/&lt;name&gt;.json   (id "ns:name")
 * { "target": "minecraft:iron_sword" | "#minecraft:swords",  "target_count": 1,
 *   "catalyst": "minecraft:diamond",                           "catalyst_count": 1, "consume_catalyst": true,
 *   "result": { "item": "minecraft:diamond_sword", "count": 1, "nbt": "{Damage:0}" },
 *   "keep_nbt": false,  "sound": "minecraft:block.smithing_table.use" }
 * </pre>
 * (Español: receta de clic derecho; el catalizador va en el cursor y el objetivo en el inventario.)
 */
public record ClickRecipe(String id, String target, int targetCount, String catalyst, int catalystCount, boolean consumeCatalyst,
                          String resultItem, int resultCount, String resultNbt, boolean keepNbt, String sound) {

    public static ClickRecipe fromJson(String id, JsonObject o) {
        JsonObject r = o.getAsJsonObject("result");
        if (r == null) throw new IllegalArgumentException("missing \"result\"");
        return new ClickRecipe(id,
                req(o, "target"), Math.max(1, o.has("target_count") ? o.get("target_count").getAsInt() : 1),
                req(o, "catalyst"), Math.max(1, o.has("catalyst_count") ? o.get("catalyst_count").getAsInt() : 1),
                !o.has("consume_catalyst") || o.get("consume_catalyst").getAsBoolean(),
                req(r, "item"), Math.max(1, r.has("count") ? r.get("count").getAsInt() : 1),
                r.has("nbt") ? (r.get("nbt").isJsonObject() ? r.get("nbt").toString() : r.get("nbt").getAsString()) : "",
                o.has("keep_nbt") && o.get("keep_nbt").getAsBoolean(),
                o.has("sound") ? o.get("sound").getAsString() : "");
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("target", target);
        o.addProperty("target_count", targetCount);
        o.addProperty("catalyst", catalyst);
        o.addProperty("catalyst_count", catalystCount);
        o.addProperty("consume_catalyst", consumeCatalyst);
        JsonObject r = new JsonObject();
        r.addProperty("item", resultItem);
        r.addProperty("count", resultCount);
        if (!resultNbt.isEmpty()) r.addProperty("nbt", resultNbt);
        o.add("result", r);
        if (keepNbt) o.addProperty("keep_nbt", true);
        if (!sound.isEmpty()) o.addProperty("sound", sound);
        return o;
    }

    private static String req(JsonObject o, String k) {
        if (!o.has(k)) throw new IllegalArgumentException("missing \"" + k + "\"");
        return o.get(k).getAsString().trim();
    }

    /** "mod:item" or "#mod:tag". */
    public static boolean matches(ItemStack st, String spec) {
        if (st.isEmpty() || spec.isEmpty()) return false;
        if (spec.startsWith("#")) {
            ResourceLocation rl = ResourceLocation.tryParse(spec.substring(1));
            return rl != null && st.is(TagKey.create(Registries.ITEM, rl));
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(st.getItem());
        return key != null && key.toString().equals(spec);
    }

    public boolean matches(ItemStack targetStack, ItemStack carried) {
        return matches(targetStack, target) && targetStack.getCount() >= targetCount
                && matches(carried, catalyst) && carried.getCount() >= catalystCount;
    }

    /** Result stack; with keep_nbt the target's tag (enchantments, damage, names...) is copied first. */
    public ItemStack result(ItemStack targetStack) {
        ResourceLocation rl = ResourceLocation.tryParse(resultItem);
        Item it = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        if (it == null || it == Items.AIR) return ItemStack.EMPTY;
        ItemStack out = new ItemStack(it, resultCount);
        if (keepNbt && targetStack.hasTag()) out.setTag(targetStack.getTag().copy());
        CompoundTag extra = parseNbt();
        if (extra != null) out.getOrCreateTag().merge(extra);
        return out;
    }

    @Nullable
    private CompoundTag parseNbt() {
        if (resultNbt.isEmpty()) return null;
        try {
            return TagParser.parseTag(resultNbt);
        } catch (Exception e) {
            return null;
        }
    }
}

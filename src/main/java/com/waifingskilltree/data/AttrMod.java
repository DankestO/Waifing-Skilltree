package com.waifingskilltree.data;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * An attribute modifier attached to a node. operation: 0 = add, 1 = multiply base, 2 = multiply total.
 * {@code condition} (optional): the bonus is only active while the player meets it.
 */
public record AttrMod(String attribute, double amount, int operation, @Nullable Condition condition) {

    public AttrMod(String attribute, double amount, int operation) {
        this(attribute, amount, operation, null);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("attribute", attribute);
        o.addProperty("amount", amount);
        o.addProperty("operation", operationName());
        if (condition != null) o.add("condition", condition.toJson());
        return o;
    }

    public static AttrMod fromJson(JsonObject o) {
        String attr = normalize(o.get("attribute").getAsString());
        double amount = o.get("amount").getAsDouble();
        int op = 0;
        if (o.has("operation")) {
            var e = o.get("operation");
            op = e.getAsJsonPrimitive().isNumber() ? e.getAsInt() : parseOp(e.getAsString());
        }
        Condition cond = o.has("condition") && o.get("condition").isJsonObject() ? Condition.fromJson(o.getAsJsonObject("condition")) : null;
        return new AttrMod(attr, amount, op, cond);
    }

    /** Line format for the editor: "attribute, amount, operation". */
    public static AttrMod parse(String line) {
        String[] p = line.split(",");
        if (p.length < 2) throw new IllegalArgumentException("Formato: atributo, cantidad[, add|mult_base|mult_total]");
        String attr = normalize(p[0].trim());
        double amount = Double.parseDouble(p[1].trim());
        int op = p.length > 2 ? parseOp(p[2].trim()) : 0;
        return new AttrMod(attr, amount, op);
    }

    public String format() {
        return attribute + ", " + amount + ", " + operationName();
    }

    public String operationName() {
        return switch (operation) {
            case 1 -> "mult_base";
            case 2 -> "mult_total";
            default -> "add";
        };
    }

    private static int parseOp(String s) {
        return switch (s.toLowerCase()) {
            case "1", "mult_base", "multiply_base" -> 1;
            case "2", "mult_total", "multiply_total" -> 2;
            default -> 0;
        };
    }

    private static String normalize(String s) {
        return s.contains(":") ? s : "minecraft:" + s;
    }

    public AttributeModifier.Operation op() {
        return switch (operation) {
            case 1 -> AttributeModifier.Operation.MULTIPLY_BASE;
            case 2 -> AttributeModifier.Operation.MULTIPLY_TOTAL;
            default -> AttributeModifier.Operation.ADDITION;
        };
    }

    @Nullable
    public Attribute resolve() {
        ResourceLocation rl = ResourceLocation.tryParse(attribute);
        return rl == null ? null : ForgeRegistries.ATTRIBUTES.getValue(rl);
    }
}

package com.waifingskilltree.compat;

import com.waifingskilltree.client.ClientTrees;
import com.waifingskilltree.data.SkillNode;
import com.waifingskilltree.data.SkillTree;
import dev.shadowsoffire.attributeslib.client.ModifierSource;
import dev.shadowsoffire.attributeslib.client.ModifierSourceType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Apothic Attributes (optional, client only): in its attribute screen every modifier shows the icon of where it comes from.
 * Skill tree modifiers get the tree's nether star instead of the "?" icon.
 * Only loaded when the mod "attributeslib" is present (see WaifingSkilltreeClient).
 */
public final class ApothicSourceCompat {
    private ApothicSourceCompat() {}

    public static void register() {
        ModifierSourceType.register(new ModifierSourceType<ItemStack>() {
            @Override
            public void extract(LivingEntity entity, BiConsumer<AttributeModifier, ModifierSource<?>> out) {
                if (entity != Minecraft.getInstance().player) return;   // the client only knows its own skill tree
                Set<UUID> ours = ourModifierIds();
                if (ours.isEmpty()) return;
                ModifierSource<?> star = new ModifierSource.ItemModifierSource(new ItemStack(Items.NETHER_STAR));
                for (Attribute a : ForgeRegistries.ATTRIBUTES.getValues()) {
                    AttributeInstance inst = entity.getAttribute(a);
                    if (inst == null) continue;
                    for (AttributeModifier m : inst.getModifiers()) if (ours.contains(m.getId())) out.accept(m, star);
                }
            }

            @Override
            public int getPriority() {
                return 50;   // after equipment (0), before potion effects (100)
            }
        });
    }

    /** Same ids the server gives the modifiers: UUID of "tree:node:index" (SkillManager.applyAttributes). */
    private static Set<UUID> ourModifierIds() {
        Set<UUID> ids = new HashSet<>();
        for (Map.Entry<String, Map<String, Integer>> te : ClientTrees.taken.entrySet()) {
            SkillTree t = ClientTrees.trees.get(te.getKey());
            if (t == null) continue;
            for (Map.Entry<String, Integer> ne : te.getValue().entrySet()) {
                SkillNode n = t.nodes.get(ne.getKey());
                if (n == null || ne.getValue() <= 0) continue;
                for (int i = 0; i < n.attributes.size(); i++) {
                    String key = te.getKey() + ":" + ne.getKey() + ":" + i;
                    ids.add(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)));
                }
            }
        }
        return ids;
    }
}

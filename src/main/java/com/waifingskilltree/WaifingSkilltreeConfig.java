package com.waifingskilltree;

import net.minecraftforge.common.ForgeConfigSpec;

public final class WaifingSkilltreeConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ALLOW_SELF_REFUND;
    public static final ForgeConfigSpec.BooleanValue REFUND_ITEMS;
    public static final ForgeConfigSpec.IntValue CONDITION_INTERVAL;
    public static final ForgeConfigSpec.ConfigValue<String> RESPEC_ITEM;
    public static final ForgeConfigSpec.BooleanValue RESPEC_ITEM_CONSUME;
    public static final ForgeConfigSpec.IntValue COMMAND_GATE_BYPASS_LEVEL;
    public static final ForgeConfigSpec.IntValue UNLOCKED_COMMAND_LEVEL;
    public static final ForgeConfigSpec.IntValue POINTS_PER_LEVEL;
    public static final ForgeConfigSpec.IntValue LEVELS_PER_POINT;
    public static final ForgeConfigSpec.IntValue STARTING_POINTS;
    public static final ForgeConfigSpec.BooleanValue BUY_POINTS;
    public static final ForgeConfigSpec.IntValue BUY_COST_STEP;
    public static final ForgeConfigSpec.IntValue MAX_BOUGHT_POINTS;
    public static final ForgeConfigSpec.BooleanValue RESET_ON_DEATH;
    public static final ForgeConfigSpec.BooleanValue TINKERS_MODIFIERS_AS_ENCHANTMENTS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        LEVELS_PER_POINT = b.comment("FREE skill points from experience: levels needed for each point (1 = one point per level, 2 = every 2 levels...).",
                        "0 (default) = no free points: players buy them with the Buy button (1, 2, 3... levels).",
                        "Points come from the HIGHEST level the player ever reached: spending points does not consume experience,",
                        "and dying then levelling again gives nothing. Changing this value applies to every player immediately.")
                .defineInRange("levelsPerFreePoint", 0, 0, 1000);
        POINTS_PER_LEVEL = b.comment("Skill points given for every 'levelsPerFreePoint' levels (usually 1).")
                .defineInRange("pointsPerLevel", 1, 0, 100);
        STARTING_POINTS = b.comment("Skill points every player has from the start (added to the earned ones).")
                .defineInRange("startingPoints", 0, 0, 10000);
        ALLOW_SELF_REFUND = b.comment("Lets players refund nodes (right click) and use the Reset button whenever they want. Off by default:",
                        "use a respec item (respecItem) or the /skilltree respec command instead. Refunds always return the paid skill points.")
                .define("allowSelfRefund", false);
        RESPEC_ITEM = b.comment("Item id that resets the player's whole skill tree when used (right click), e.g. \"minecraft:nether_star\" or an item of another mod.",
                        "Empty = disabled (default).")
                .define("respecItem", "");
        RESPEC_ITEM_CONSUME = b.comment("Consume one respec item per use (creative players never consume it).")
                .define("respecItemConsume", true);
        CONDITION_INTERVAL = b.comment("How often (in ticks) conditional attribute bonuses (holding a sword, night, low health...) are re-checked per player.",
                        "Attributes are only re-applied when a condition actually changes, so the default is cheap.")
                .defineInRange("conditionCheckInterval", 10, 1, 200);
        REFUND_ITEMS = b.comment("Give back the items a node required when its last level is refunded.")
                .define("refundItems", true);
        COMMAND_GATE_BYPASS_LEVEL = b.comment("Players with at least this permission level are never blocked by commands locked behind nodes (unlock_commands).")
                .defineInRange("commandGateBypassLevel", 2, 0, 4);
        UNLOCKED_COMMAND_LEVEL = b.comment("Permission level a player gets for an unlocked command (so e.g. /give works for a player who unlocked it). 0 = no elevation.")
                .defineInRange("unlockedCommandLevel", 2, 0, 4);
        BUY_POINTS = b.comment("Shows a 'Buy skill point' button in the tree screen. The player pays experience LEVELS:",
                        "the first point costs 1 level, the second 2 levels, the third 3 levels... (each purchase costs one more than the previous).")
                .define("buyPoints", true);
        BUY_COST_STEP = b.comment("Levels added to the price of each purchase (1 = 1,2,3,4... levels; 2 = 2,4,6,8...).")
                .defineInRange("buyCostStep", 1, 1, 100);
        MAX_BOUGHT_POINTS = b.comment("Most skill points a player can buy with the button (0 = no limit). The button turns grey and shows 'max' when reached.")
                .defineInRange("maxBoughtPoints", 0, 0, 100000);
        RESET_ON_DEATH = b.comment("Hardcore: dying wipes the player's whole skill tree (every node, in every tree). Off by default.",
                        "Skill points (earned and bought) are lost too and buying starts again at 1 level. Items paid for nodes are not given back.")
                .define("resetOnDeath", false);
        TINKERS_MODIFIERS_AS_ENCHANTMENTS = b.comment("Tinkers' Construct tools have no vanilla enchantments: their modifiers do that job.",
                        "true = the modifiers a player added to a Tinkers tool or armor (its upgrades and abilities) count as enchantments",
                        "for the 'enchanted weapon' and 'enchanted armor' conditions. The traits that come with the materials never count.")
                .define("tinkersModifiersAsEnchantments", true);
        SPEC = b.build();
    }

    private WaifingSkilltreeConfig() {}
}

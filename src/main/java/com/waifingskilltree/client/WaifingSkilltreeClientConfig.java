package com.waifingskilltree.client;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client-only options (config/waifing_skilltree-client.toml). Each player sets them on their own game. */
public final class WaifingSkilltreeClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue INVENTORY_BUTTON;
    public static final ForgeConfigSpec.IntValue BUTTON_OFFSET_X;
    public static final ForgeConfigSpec.IntValue BUTTON_OFFSET_Y;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("inventory_button");
        INVENTORY_BUTTON = b.comment("Show a skill tree button in the survival inventory, next to the recipe book.")
                .define("enabled", true);
        BUTTON_OFFSET_X = b.comment("Move the button sideways (pixels, negative = left) if another mod puts a button in the same place.")
                .defineInRange("offsetX", 0, -400, 400);
        BUTTON_OFFSET_Y = b.comment("Move the button up or down (pixels, negative = up).")
                .defineInRange("offsetY", 0, -400, 400);
        b.pop();
        SPEC = b.build();
    }

    private WaifingSkilltreeClientConfig() {}
}

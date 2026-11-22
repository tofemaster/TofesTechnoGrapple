package technograpple;

import net.minecraftforge.common.ForgeConfigSpec;

final class TechnoGrappleConfig {
    static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue HCTS;
    private static final ForgeConfigSpec.BooleanValue VANILLA_LIKE;

    static {
        var builder = new ForgeConfigSpec.Builder();
        HCTS = builder.comment("Enable/disable the 'Here Comes The Sun' event")
                .define("hcts", true);
        VANILLA_LIKE = builder.comment("Enable/disable vanilla appearance")
                .define("vanillaLike", false);
        SPEC = builder.build();
    }

    static boolean hctsEnabled() {
        return HCTS.get();
    }

    static void setHctsEnabled(boolean enabled) {
        HCTS.set(enabled);
        SPEC.save();
    }

    static boolean vanillaLikeEnabled() {
        return VANILLA_LIKE.get();
    }

    static void setVanillaLikeEnabled(boolean enabled) {
        VANILLA_LIKE.set(enabled);
        SPEC.save();
    }

    private TechnoGrappleConfig() {}
}

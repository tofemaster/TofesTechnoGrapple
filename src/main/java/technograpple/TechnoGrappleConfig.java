package technograpple;

import net.neoforged.neoforge.common.ModConfigSpec;

final class TechnoGrappleConfig {
    static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue HCTS;
    private static final ModConfigSpec.BooleanValue VANILLA_LIKE;

    static {
        var builder = new ModConfigSpec.Builder();
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

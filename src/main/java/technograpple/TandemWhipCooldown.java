package technograpple;

import net.minecraft.network.FriendlyByteBuf;

record TandemWhipCooldown() {
    static final long DURATION_NANOS = 800_000_000L;
    static final TandemWhipCooldown INSTANCE = new TandemWhipCooldown();
    void encode(FriendlyByteBuf buffer) {
    }

    static TandemWhipCooldown decode(FriendlyByteBuf buffer) {
        return INSTANCE;
    }
}

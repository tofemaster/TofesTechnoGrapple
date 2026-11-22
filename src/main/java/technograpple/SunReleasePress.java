package technograpple;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

record SunReleasePress() {
    static final SunReleasePress INSTANCE = new SunReleasePress();
    void encode(FriendlyByteBuf buffer) {
    }

    static SunReleasePress decode(FriendlyByteBuf buffer) {
        return INSTANCE;
    }

    static void receive(SunReleasePress press, ServerPlayer player) {
        if (player != null) Grapple.pressSunRelease(player);
    }
}

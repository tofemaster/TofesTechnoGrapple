package technograpple;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

record SunReleasePress() implements CustomPacketPayload {
    static final SunReleasePress INSTANCE = new SunReleasePress();
    static final Type<SunReleasePress> TYPE = new Type<>(Identifier.fromNamespaceAndPath(TechnoGrapple.ID, "sun_release_press"));
    static final StreamCodec<RegistryFriendlyByteBuf, SunReleasePress> CODEC = StreamCodec.unit(INSTANCE);

    static void receive(SunReleasePress press, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) Grapple.pressSunRelease(player);
    }

    @Override
    public Type<SunReleasePress> type() {
        return TYPE;
    }
}

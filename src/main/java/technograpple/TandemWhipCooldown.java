package technograpple;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

record TandemWhipCooldown() implements CustomPacketPayload {
    static final long DURATION_NANOS = 800_000_000L;
    static final TandemWhipCooldown INSTANCE = new TandemWhipCooldown();
    static final Type<TandemWhipCooldown> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(TechnoGrapple.ID, "tandem_whip_cooldown"));
    static final StreamCodec<RegistryFriendlyByteBuf, TandemWhipCooldown> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<TandemWhipCooldown> type() {
        return TYPE;
    }
}

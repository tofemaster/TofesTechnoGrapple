package technograpple;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

record SunNight(boolean active) implements CustomPacketPayload {
    static final Type<SunNight> TYPE = new Type<>(Identifier.fromNamespaceAndPath(TechnoGrapple.ID, "sun_night"));
    static final StreamCodec<RegistryFriendlyByteBuf, SunNight> CODEC =
            ByteBufCodecs.BOOL.map(SunNight::new, SunNight::active).cast();

    @Override
    public Type<SunNight> type() {
        return TYPE;
    }
}

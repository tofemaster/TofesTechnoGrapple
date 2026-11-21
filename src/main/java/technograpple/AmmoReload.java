package technograpple;

import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

record AmmoReload(UUID player, int outgoingTypes, int incomingTypes)
        implements CustomPacketPayload {
    private static final long DURATION = 1_120_000_000;
    static final float HATCH_OPEN_AT = 0.19F;
    static final float INCOMING_START = 0.43F;
    private static final float EJECT_WINDOW = INCOMING_START - HATCH_OPEN_AT;
    static final float EJECT_AT = 0.23F;
    static final float INSERT_SOUND_AT = 0.52F;
    static final float INSERT_AT = 0.60F;
    static final Type<AmmoReload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(TechnoGrapple.ID, "ammo_reload"));
    static final StreamCodec<RegistryFriendlyByteBuf, AmmoReload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, AmmoReload::player,
            ByteBufCodecs.VAR_INT, AmmoReload::outgoingTypes,
            ByteBufCodecs.VAR_INT, AmmoReload::incomingTypes,
            AmmoReload::new);

    long duration() {
        return hasOutgoing() ? DURATION : (long) (DURATION * (1 - EJECT_WINDOW));
    }

    boolean hasOutgoing() {
        return outgoingTypes != 0;
    }

    float progress(long elapsed) {
        float progress = elapsed / (float) DURATION;
        // Skip the ejection interval once the empty hatch has opened.
        return !hasOutgoing() && progress >= HATCH_OPEN_AT ? progress + EJECT_WINDOW : progress;
    }

    @Override
    public Type<AmmoReload> type() {
        return TYPE;
    }
}

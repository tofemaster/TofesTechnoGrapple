package technograpple;

import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

record SunRelease(UUID player, int elapsed) implements CustomPacketPayload {
    static final int SHAKE = 80;
    static final int PUMPS = 160;
    static final int CAMERA_SHAKE = 200;
    static final int CUTOFF = 280;
    static final int CAMERA_SHAKE_PEAK = 360;
    static final int PEAK = 440;
    static final int END = 500;
    static final int WHITEOUT_END = END + 100;
    static final int COOLDOWN = 100;
    static final Type<SunRelease> TYPE = new Type<>(Identifier.fromNamespaceAndPath(TechnoGrapple.ID, "sun_release"));
    static final StreamCodec<RegistryFriendlyByteBuf, SunRelease> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SunRelease::player, ByteBufCodecs.VAR_INT, SunRelease::elapsed, SunRelease::new);

    boolean reverses() {
        return elapsed < CUTOFF;
    }

    @Override
    public Type<SunRelease> type() {
        return TYPE;
    }
}

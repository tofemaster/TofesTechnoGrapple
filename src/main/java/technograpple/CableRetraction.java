package technograpple;

import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

record CableRetraction(UUID player, int rail, Vec3 outlet, Vec3 eye, boolean miss, boolean surfaceDetach, boolean rope)
        implements CustomPacketPayload {
    static final Type<CableRetraction> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(TechnoGrapple.ID, "cable_retraction"));
    static final StreamCodec<RegistryFriendlyByteBuf, CableRetraction> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, CableRetraction::player,
            ByteBufCodecs.VAR_INT, CableRetraction::rail,
            Vec3.STREAM_CODEC, CableRetraction::outlet,
            Vec3.STREAM_CODEC, CableRetraction::eye,
            ByteBufCodecs.BOOL, CableRetraction::miss,
            ByteBufCodecs.BOOL, CableRetraction::surfaceDetach,
            ByteBufCodecs.BOOL, CableRetraction::rope,
            CableRetraction::new);

    long durationNanos() {
        int durationMultiplier = miss ? 4 : surfaceDetach ? 2 : 1;
        return (long) (Math.clamp(outlet.distanceTo(eye) / 320, 0.09, 0.28)
                * durationMultiplier * 1_000_000_000);
    }

    @Override
    public Type<CableRetraction> type() {
        return TYPE;
    }
}

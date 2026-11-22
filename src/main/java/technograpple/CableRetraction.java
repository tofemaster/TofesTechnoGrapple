package technograpple;

import net.minecraft.network.FriendlyByteBuf;
import java.util.UUID;

import net.minecraft.world.phys.Vec3;

record CableRetraction(UUID player, int rail, Vec3 outlet, Vec3 eye, boolean miss, boolean surfaceDetach, boolean rope) {
    void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(player);
        buffer.writeVarInt(rail);
        GrappleNetwork.writeVec3(buffer, outlet);
        GrappleNetwork.writeVec3(buffer, eye);
        buffer.writeBoolean(miss);
        buffer.writeBoolean(surfaceDetach);
        buffer.writeBoolean(rope);
    }

    static CableRetraction decode(FriendlyByteBuf buffer) {
        return new CableRetraction(buffer.readUUID(), buffer.readVarInt(), GrappleNetwork.readVec3(buffer), GrappleNetwork.readVec3(buffer), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean());
    }

    long durationNanos() {
        int durationMultiplier = miss ? 4 : surfaceDetach ? 2 : 1;
        return (long) (net.minecraft.util.Mth.clamp(outlet.distanceTo(eye) / 320, 0.09, 0.28)
                * durationMultiplier * 1_000_000_000);
    }
}

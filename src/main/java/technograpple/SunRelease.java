package technograpple;

import net.minecraft.network.FriendlyByteBuf;
import java.util.UUID;

record SunRelease(UUID player, int elapsed) {
    static final int SHAKE = 80;
    static final int PUMPS = 160;
    static final int CAMERA_SHAKE = 200;
    static final int CUTOFF = 280;
    static final int CAMERA_SHAKE_PEAK = 360;
    static final int PEAK = 440;
    static final int END = 500;
    static final int WHITEOUT_END = END + 100;
    static final int COOLDOWN = 100;
    void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(player);
        buffer.writeVarInt(elapsed);
    }

    static SunRelease decode(FriendlyByteBuf buffer) {
        return new SunRelease(buffer.readUUID(), buffer.readVarInt());
    }

    boolean reverses() {
        return elapsed < CUTOFF;
    }
}

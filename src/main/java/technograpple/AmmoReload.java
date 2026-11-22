package technograpple;

import net.minecraft.network.FriendlyByteBuf;
import java.util.UUID;

record AmmoReload(UUID player, int outgoingTypes, int incomingTypes) {
    private static final long DURATION = 1_120_000_000;
    static final float HATCH_OPEN_AT = 0.19F;
    static final float INCOMING_START = 0.43F;
    private static final float EJECT_WINDOW = INCOMING_START - HATCH_OPEN_AT;
    static final float EJECT_AT = 0.23F;
    static final float INSERT_SOUND_AT = 0.52F;
    static final float INSERT_AT = 0.60F;
    void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(player);
        buffer.writeVarInt(outgoingTypes);
        buffer.writeVarInt(incomingTypes);
    }

    static AmmoReload decode(FriendlyByteBuf buffer) {
        return new AmmoReload(buffer.readUUID(), buffer.readVarInt(), buffer.readVarInt());
    }

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
}

package technograpple;

import net.minecraft.network.FriendlyByteBuf;
import java.util.UUID;

record SunScene(UUID player, int elapsed, boolean reversing) {
    void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(player);
        buffer.writeVarInt(elapsed);
        buffer.writeBoolean(reversing);
    }

    static SunScene decode(FriendlyByteBuf buffer) {
        return new SunScene(buffer.readUUID(), buffer.readVarInt(), buffer.readBoolean());
    }
}

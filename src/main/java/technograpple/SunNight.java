package technograpple;

import net.minecraft.network.FriendlyByteBuf;

record SunNight(boolean active) {
    void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(active);
    }

    static SunNight decode(FriendlyByteBuf buffer) {
        return new SunNight(buffer.readBoolean());
    }
}

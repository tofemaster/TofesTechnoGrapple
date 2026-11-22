package technograpple;

import net.minecraft.network.FriendlyByteBuf;

record MotionInput(int flags) {
    static final MotionInput EMPTY = new MotionInput(0);

    MotionInput(boolean forward, boolean backward, boolean left, boolean right, boolean jump, boolean shift) {
        this((forward ? 1 : 0) | (backward ? 2 : 0) | (left ? 4 : 0) | (right ? 8 : 0)
                | (jump ? 16 : 0) | (shift ? 32 : 0));
    }

    boolean forward() { return (flags & 1) != 0; }
    boolean backward() { return (flags & 2) != 0; }
    boolean left() { return (flags & 4) != 0; }
    boolean right() { return (flags & 8) != 0; }
    boolean jump() { return (flags & 16) != 0; }
    boolean shift() { return (flags & 32) != 0; }

    void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(flags);
    }

    static MotionInput decode(FriendlyByteBuf buffer) {
        return new MotionInput(buffer.readUnsignedByte() & 63);
    }
}

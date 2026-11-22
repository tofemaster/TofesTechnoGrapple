package technograpple;

import net.minecraft.network.FriendlyByteBuf;
import java.util.UUID;

record RailState(UUID player, int rail, int hookId, int emptyRail, int display, boolean firing, boolean taut,
                 boolean pulling, boolean whipPull, int displayGauge, boolean tandem, boolean gripSurfaceAttached) {
    static final int DOCKED = 0;
    static final int PORT = 1;
    static final int STARBOARD = 2;
    static final int IDLE = 0;
    static final int TRAVELLING = 1;
    static final int SURFACE_ATTACHED = 2;
    static final int CANNOT_FIRE = 3;
    static final int SUN_LOCKED = 6;
    static final int TANDEM_IDLE = 7;
    static final int TANDEM_WHIP_EMPTY = 8;
    static final int TANDEM_GRIP_EMPTY = 9;
    void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(player);
        buffer.writeVarInt(rail);
        buffer.writeVarInt(hookId);
        buffer.writeVarInt(emptyRail);
        buffer.writeVarInt(display);
        buffer.writeBoolean(firing);
        buffer.writeBoolean(taut);
        buffer.writeBoolean(pulling);
        buffer.writeBoolean(whipPull);
        buffer.writeVarInt(displayGauge);
        buffer.writeBoolean(tandem);
        buffer.writeBoolean(gripSurfaceAttached);
    }

    static RailState decode(FriendlyByteBuf buffer) {
        return new RailState(buffer.readUUID(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean());
    }

    static RailState idle(UUID player, int emptyRail, boolean whipPull) {
        return idle(player, emptyRail, whipPull, false);
    }

    static RailState idle(UUID player, int emptyRail, boolean whipPull, boolean tandem) {
        return new RailState(player, DOCKED, -1, emptyRail, IDLE, false, false, false, whipPull, 0, tandem, false);
    }

    static RailState cannotFire(UUID player) {
        return new RailState(player, DOCKED, -1, DOCKED, CANNOT_FIRE, false, false, false, false, 0, false, false);
    }

    boolean cableConnected() {
        return rail != DOCKED && (display == TRAVELLING || display == SURFACE_ATTACHED || display == SUN_LOCKED);
    }

    boolean rope() {
        // Negative gauge values identify rope shots, including the zero-cell travelling state.
        return displayGauge < 0;
    }

    int attachmentCells() {
        return rope() ? -displayGauge - 1 : displayGauge;
    }
}

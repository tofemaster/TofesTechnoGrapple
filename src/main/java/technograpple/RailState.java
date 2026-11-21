package technograpple;

import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

record RailState(UUID player, int rail, int hookId, int emptyRail, int display, boolean firing, boolean taut,
                 boolean pulling, boolean whipPull, int displayGauge, boolean tandem, boolean gripSurfaceAttached)
        implements CustomPacketPayload {
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
    static final Type<RailState> TYPE = new Type<>(Identifier.fromNamespaceAndPath(TechnoGrapple.ID, "rail_state"));
    private static final StreamCodec<RegistryFriendlyByteBuf, RailState> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RailState::player,
            ByteBufCodecs.VAR_INT, RailState::rail,
            ByteBufCodecs.VAR_INT, RailState::hookId,
            ByteBufCodecs.VAR_INT, RailState::emptyRail,
            ByteBufCodecs.VAR_INT, RailState::display,
            ByteBufCodecs.BOOL, RailState::firing,
            ByteBufCodecs.BOOL, RailState::taut,
            ByteBufCodecs.BOOL, RailState::pulling,
            ByteBufCodecs.BOOL, RailState::whipPull,
            ByteBufCodecs.VAR_INT, RailState::displayGauge,
            ByteBufCodecs.BOOL, RailState::tandem,
            ByteBufCodecs.BOOL, RailState::gripSurfaceAttached,
            RailState::new);

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

    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("0.4.58").playToClient(TYPE, CODEC).playToClient(CableRetraction.TYPE, CableRetraction.CODEC)
                .playToClient(SunNight.TYPE, SunNight.CODEC)
                .playToServer(SunView.TYPE, SunView.CODEC, SunView::receive)
                .playToClient(SunWave.TYPE, SunWave.CODEC)
                .playToClient(SunScene.TYPE, SunScene.CODEC)
                .playToServer(SunReleasePress.TYPE, SunReleasePress.CODEC, SunReleasePress::receive)
                .playToClient(SunShot.TYPE, SunShot.CODEC)
                .playToClient(SunRelease.TYPE, SunRelease.CODEC)
                .playToClient(AmmoReload.TYPE, AmmoReload.CODEC)
                .playToClient(TandemWhipCooldown.TYPE, TandemWhipCooldown.CODEC)
                .playBidirectional(GlideControl.TYPE, GlideControl.CODEC, GlideControl::receive)
                .playToClient(DartAmmo.TYPE, DartAmmo.CODEC);
    }

    @Override
    public Type<RailState> type() {
        return TYPE;
    }
}

package technograpple;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterItemModelsEvent;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;
import net.neoforged.neoforge.client.event.RegisterConditionalItemModelPropertyEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import technograpple.mixin.ItemInHandRendererInvoker;

@EventBusSubscriber(modid = TechnoGrapple.ID, value = Dist.CLIENT)
public final class GauntletClient {
    @SubscribeEvent
    static void registerConfigScreen(FMLClientSetupEvent event) {
        ModList.get().getModContainerById(TechnoGrapple.ID).orElseThrow()
                .registerExtensionPoint(IConfigScreenFactory.class,
                        (container, parent) -> new TechnoGrappleConfigScreen(parent));
    }

    private static final Set<UUID> GLIDE_RAISED = new HashSet<>();
    private static boolean wasJumpDown;
    private static boolean wasGliding;
    private static boolean reloadClickHeld;
    private static boolean sunClickHeld;
    private static long localGlideRaisedAt;

    public static float glideRaiseProgress() {
        return smoothstep((System.nanoTime() - localGlideRaisedAt) / 150_000_000F);
    }
    private static LaunchPose glideSink = new LaunchPose(false, 0, 0);

    private static void sendGlideControl(UUID player, boolean raised) {
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new GlideControl(player, raised));
    }

    public static boolean glideArmRaised(int entityId) {
        var level = Minecraft.getInstance().level;
        return level != null && level.getEntity(entityId) instanceof net.minecraft.world.entity.player.Player player
                && GLIDE_RAISED.contains(player.getUUID());
    }
    private static final Map<UUID, GauntletVisual> VISUALS = new HashMap<>();
    private static final Map<UUID, Long> FAILED_AT = new HashMap<>();
    private static final Map<UUID, Long> SUN_IMPACTS = new HashMap<>();

    static float sunJolt(UUID player) {
        Long start = SUN_IMPACTS.get(player);
        if (start == null) return 0;
        float t = (System.nanoTime() - start) / 300_000_000F;
        return t >= 1 ? 0 : Mth.sin(t * 17) * (1 - t) * (1 - t);
    }

    @SubscribeEvent
    static void shakeCamera(net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles event) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getCameraEntity() != minecraft.player) return;
        UUID player = minecraft.player.getUUID();
        float impact = sunJolt(player);
        event.setRoll(event.getRoll() + 3 * impact + 2.4F * SunEffects.cameraShake(0));
        event.setPitch(event.getPitch() + 2 * impact + 1.6F * SunEffects.cameraShake(1.8F));
        event.setYaw(event.getYaw() + 1.2F * SunEffects.cameraShake(3.2F));
    }
    private static final Map<UUID, ReloadMotion> RELOADS = new HashMap<>();

    static final class ReloadMotion {
        final AmmoReload ammo;
        private final long startedAt = System.nanoTime();
        private boolean ejected;
        private boolean inserted;

        ReloadMotion(AmmoReload ammo) {
            this.ammo = ammo;
        }

        float progress() {
            return ammo.progress(System.nanoTime() - startedAt);
        }
    }

    static ReloadMotion reloadMotion(@Nullable ItemOwner owner) {
        var holder = owner == null ? null : owner.asLivingEntity();
        return holder == null ? null : RELOADS.get(holder.getUUID());
    }
    private static final Map<UUID, Integer> AMMO_TYPES = new HashMap<>();
    private static final long FAILURE_DISPLAY = 360_000_000;
    private static final long FAILURE_BOUNCE = 160_000_000;
    private static final long MISS_POSE_HOLD = 150_000_000;
    private static final int PUMP_FRAMES = 6;
    private static final long RAIL_ROLL_DURATION = 120_000_000;
    private static final long DOOR_TRAVEL = 65_000_000;
    private static final long WHIP_PUMP_HOLD = 300_000_000;
    private static final HazardDoor CLOSED_DOOR = new HazardDoor(false, 0, 0);
    private static final RailIndex LOADED_RAILS = new RailIndex(false, false, 0, 0);
    private static @Nullable PoseStack gauntletArmPose;
    private static final LaunchPose IDLE_POSE = new LaunchPose(false, 0, 0);
    private static LaunchPose launchPose = IDLE_POSE;
    private static long predictedLaunchUntil;
    private static long tandemWhipRefireAt;
    private static boolean waitForMissPump;
    private static long missPumpIdleAt;
    private static @Nullable ReleaseWhip releaseWhip;
    private static @Nullable HandOutlet handOutlet;

    private record HandOutlet(RopeRenderer.RailOutlets outlets, long capturedAt) {
    }

    record ReleaseWhip(float launchAmount, long startedAt) {
        private static final long RISE = 50_000_000;
        private static final long RECOVERY = 210_000_000;

        boolean finished(long now) {
            return now - startedAt >= RISE + RECOVERY;
        }

        float launchAmount(long now) {
            return launchAmount * (1 - smoothstep((now - startedAt) / (float) (RISE + RECOVERY)));
        }

        float whipAmount(long now) {
            long elapsed = now - startedAt;
            if (elapsed < RISE) {
                float remaining = 1 - elapsed / (float) RISE;
                return 1 - remaining * remaining;
            }
            return 1 - smoothstep((elapsed - RISE) / (float) RECOVERY);
        }
    }

    private static float smoothstep(float time) {
        float t = Math.clamp(time, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private record LaunchPose(boolean active, float from, long startedAt) {
        float amount(long now) {
            long duration = active ? 85_000_000 : 140_000_000;
            float eased = smoothstep((now - startedAt) / (float) duration);
            return from + ((active ? 1 : 0) - from) * eased;
        }
    }

    private record PumpMotion(float from, long startedAt, long returnAt, boolean returnSoundPlayed, boolean whip) {
        PumpMotion requestReturn(long now) {
            return returnAt != 0 ? this
                    : new PumpMotion(from, startedAt, TechnoGrapple.pumpReturnAt(startedAt, now), false, false);
        }

        PumpMotion refire(long now) {
            if (returnAt == 0 || (whip && now < returnAt)) return this;
            if (now < returnAt) return new PumpMotion(from, startedAt, 0, false, false);
            return new PumpMotion(position(now), now, 0, false, false);
        }

        boolean shouldPlayReturnSound(long now) {
            return !whip && !returnSoundPlayed && returnAt != 0 && now >= returnAt && !finished(now);
        }

        PumpMotion markReturnSoundPlayed() {
            return new PumpMotion(from, startedAt, returnAt, true, whip);
        }

        boolean finished(long now) {
            return returnAt != 0 && now >= idleAt();
        }

        long idleAt() {
            return returnAt + TechnoGrapple.PUMP_RECOVERY;
        }

        float position(long now) {
            if (returnAt != 0 && now >= returnAt) {
                return 1 - smoothstep((now - returnAt) / (float) TechnoGrapple.PUMP_RECOVERY);
            }
            float remaining = 1 - Math.clamp((now - startedAt) / (float) TechnoGrapple.PUMP_STROKE, 0, 1);
            return 1 - (1 - from) * remaining * remaining;
        }
    }

    private record RailIndex(boolean portFlipped, boolean starboardFlipped, long portStartedAt, long starboardStartedAt) {
        RailIndex advance(int rail, long now) {
            return rail == RailState.PORT
                    ? new RailIndex(!portFlipped, starboardFlipped, now, starboardStartedAt)
                    : new RailIndex(portFlipped, !starboardFlipped, portStartedAt, now);
        }

        RailPose pose(int rail, boolean topLoaded, long now) {
            boolean port = rail == RailState.PORT;
            float halfTurn = port ? Mth.PI : -Mth.PI;
            float settled = (port ? portFlipped : starboardFlipped) ? halfTurn : 0;
            long startedAt = port ? portStartedAt : starboardStartedAt;
            boolean rolling = startedAt != 0 && now - startedAt < RAIL_ROLL_DURATION;
            float travel = rolling ? smoothstep((now - startedAt) / (float) RAIL_ROLL_DURATION) : 1;
            return new RailPose(settled - halfTurn * (1 - travel), halfTurn * travel, rolling, topLoaded);
        }
    }

    record RailPose(float housingAngle, float travelAngle, boolean rolling, boolean topLoaded) {
    }

    private record HazardDoor(boolean open, float from, long changedAt) {
        float position(long now) {
            if (changedAt == 0) return 0;
            float progress = smoothstep((now - changedAt) / (float) DOOR_TRAVEL);
            return Mth.lerp(progress, from, open ? 1 : 0);
        }

        HazardDoor update(boolean nextOpen, long now) {
            return nextOpen == open ? this : new HazardDoor(nextOpen, position(now), now);
        }
    }

    private record Pumps(@Nullable PumpMotion port, @Nullable PumpMotion starboard) {
        private static final Pumps IDLE = new Pumps(null, null);

        @Nullable PumpMotion forRail(int rail) {
            return rail == RailState.PORT ? port : starboard;
        }

        Pumps update(RailState state, boolean newShot, long now) {
            if (state.whipPull()) return new Pumps(whip(port, now), whip(starboard, now));
            return new Pumps(update(port, state, RailState.PORT, newShot, now),
                    update(starboard, state, RailState.STARBOARD, newShot, now));
        }

        private static PumpMotion whip(@Nullable PumpMotion pump, long now) {
            return new PumpMotion(pump == null ? 0 : pump.position(now), now, now + WHIP_PUMP_HOLD, false, true);
        }

        private static @Nullable PumpMotion update(@Nullable PumpMotion pump, RailState state,
                                                   int rail, boolean newShot, long now) {
            if (state.firing() && state.rail() == rail) {
                if (pump == null) return new PumpMotion(0, now, 0, false, false);
                return newShot ? pump.refire(now) : pump;
            }
            return pump == null ? null : pump.requestReturn(now);
        }

        boolean idle(long now) {
            return (port == null || port.finished(now)) && (starboard == null || starboard.finished(now));
        }
    }

    private record GauntletVisual(RailState state, Pumps pumps, RailIndex rails,
                                  HazardDoor portDoor, HazardDoor starboardDoor) {
        float pumpPosition(int rail, long now) {
            PumpMotion pump = pumps.forRail(rail);
            return pump == null ? 0 : pump.position(now);
        }

        GauntletVisual withPumps(Pumps motion) {
            return new GauntletVisual(state, motion, rails, portDoor, starboardDoor);
        }
    }

    @SubscribeEvent
    static void registerRailModel(RegisterItemModelsEvent event) {
        event.register(Identifier.fromNamespaceAndPath(TechnoGrapple.ID, "reload_hatch"), ReloadHatchModel.Unbaked.CODEC);
        event.register(Identifier.fromNamespaceAndPath(TechnoGrapple.ID, "rotating_rail"), RailFrontModel.MAP_CODEC);
    }

    @SubscribeEvent
    static void registerModelConditions(RegisterConditionalItemModelPropertyEvent event) {
        event.register(Identifier.fromNamespaceAndPath(TechnoGrapple.ID, "shader_display_glow"), ShaderDisplayGlow.CODEC);
        event.register(Identifier.fromNamespaceAndPath(TechnoGrapple.ID, "vanilla_like"), VanillaLikeModel.CODEC);
    }

    @SubscribeEvent
    static void registerModels(RegisterRangeSelectItemModelPropertyEvent event) {
        for (VisualProperty property : VisualProperty.values()) {
            event.register(Identifier.fromNamespaceAndPath(TechnoGrapple.ID, property.id), property.type());
        }
    }

    @SubscribeEvent
    static void registerPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(AmmoReload.TYPE, (ammo, context) -> RELOADS.put(ammo.player(), new ReloadMotion(ammo)));
        event.register(GlideControl.TYPE, (state, context) -> {
            if (state.raised()) {
                var local = Minecraft.getInstance().player;
                if (GLIDE_RAISED.add(state.player()) && local != null && state.player().equals(local.getUUID())) {
                    localGlideRaisedAt = System.nanoTime();
                }
            }
            else GLIDE_RAISED.remove(state.player());
        });
        event.register(SunRelease.TYPE, (release, context) -> SunEffects.release(release));
        event.register(SunScene.TYPE, (scene, context) -> SunEffects.syncScene(scene));
        event.register(SunNight.TYPE, (night, context) -> SunEffects.setMoonHidden(night.active()));
        event.register(SunWave.TYPE, (wave, context) -> SunWaveParticles.start(wave));
        event.register(SunShot.TYPE, (shot, context) -> RopeRenderer.startSunShot(shot));
        event.register(RailState.TYPE, (state, context) -> receiveVisualState(state));
        event.register(DartAmmo.TYPE, (ammo, context) -> AMMO_TYPES.put(ammo.player(), ammo.availableTypes()));
        event.register(TandemWhipCooldown.TYPE,
                (cooldown, context) -> tandemWhipRefireAt = System.nanoTime() + TandemWhipCooldown.DURATION_NANOS);
        event.register(CableRetraction.TYPE, (retraction, context) -> {
            RopeRenderer.retract(retraction);
            var player = Minecraft.getInstance().player;
            if (retraction.miss() && player != null && retraction.player().equals(player.getUUID())) {
                waitForMissPump = true;
            }
        });
    }

    private static void receiveVisualState(RailState state) {
        long now = System.nanoTime();
        if (state.display() == RailState.CANNOT_FIRE) {
            FAILED_AT.put(state.player(), now);
            GauntletElbow.startDryFire(state.player(), now);
            return;
        }
        var localPlayer = Minecraft.getInstance().player;
        GauntletVisual previous = VISUALS.get(state.player());
        if (state.display() == RailState.SUN_LOCKED
                && (previous == null || previous.state().display() != RailState.SUN_LOCKED)) {
            SUN_IMPACTS.put(state.player(), now);
            SunEffects.start(state.player());
        }
        if (!state.cableConnected()) RopeRenderer.clearSunShot(state.player());
        if (localPlayer != null && state.player().equals(localPlayer.getUUID())) {
            predictedLaunchUntil = 0;
            if (state.firing() || state.whipPull()) {
                waitForMissPump = false;
                missPumpIdleAt = 0;
            }
            if (state.whipPull() && !TechnoGrappleConfig.vanillaLikeEnabled()) {
                releaseWhip = new ReleaseWhip(launchPose.amount(now), now);
                launchPose = IDLE_POSE;
            }
        }
        boolean newShot = state.firing()
                && (previous == null || !previous.state().firing() || previous.state().rail() != state.rail());
        if (newShot || state.whipPull()) GauntletElbow.startGesture(state.player(), state.whipPull(), now);
        Pumps pumps = (previous == null ? Pumps.IDLE : previous.pumps()).update(state, newShot, now);
        RailIndex rails = previous == null ? LOADED_RAILS : previous.rails();
        int previousEmpty = previous == null ? RailState.DOCKED : previous.state().emptyRail();
        if (state.tandem()) {
            if (newShot) rails = rails.advance(state.rail(), now);
        } else if (state.emptyRail() == RailState.DOCKED) {
            rails = LOADED_RAILS;
        } else if (state.firing() && previousEmpty != RailState.DOCKED && previousEmpty != state.emptyRail()) {
            rails = rails.advance(previousEmpty, now);
        }
        if (waitForMissPump && localPlayer != null && state.player().equals(localPlayer.getUUID())
                && state.rail() == RailState.DOCKED) {
            PumpMotion pump = pumps.forRail(previousEmpty);
            missPumpIdleAt = pump == null ? now : pump.idleAt();
            waitForMissPump = false;
        }
        if (state.tandem() || !pumps.idle(now) || state.emptyRail() != RailState.DOCKED) {
            boolean connected = state.cableConnected();
            HazardDoor port = previous == null ? CLOSED_DOOR : previous.portDoor();
            HazardDoor starboard = previous == null ? CLOSED_DOOR : previous.starboardDoor();
            VISUALS.put(state.player(), new GauntletVisual(state, pumps, rails,
                    port.update(connected && state.rail() == RailState.PORT, now),
                    starboard.update(connected && state.rail() == RailState.STARBOARD, now)));
        } else {
            VISUALS.remove(state.player());
        }
    }

    @SubscribeEvent
    static void renderSunWhiteout(RenderGuiEvent.Pre event) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        int alpha = SunEffects.sceneWashAlpha();
        if (alpha == 0) return;
        var graphics = event.getGuiGraphics();
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (alpha << 24) | 0xFFFFFF);
        graphics.nextStratum();
    }

    @SubscribeEvent
    static void playPumpReturns(RenderFrameEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null || minecraft.isPaused()) return;

        long now = System.nanoTime();
        for (var player : level.players()) {
            ReloadMotion reload = RELOADS.get(player.getUUID());
            if (reload != null && TechnoGrapple.isGauntlet(player.getMainHandItem())) {
                float progress = reload.progress();
                if (reload.ammo.hasOutgoing() && !reload.ejected && progress >= AmmoReload.EJECT_AT
                        && progress < AmmoReload.INSERT_SOUND_AT) {
                    reload.ejected = true;
                    level.playLocalSound(player, TechnoGrapple.RELOAD_EJECT.get(), SoundSource.PLAYERS, 0.65F, 1);
                }
                if (!reload.inserted && progress >= AmmoReload.INSERT_SOUND_AT && progress < 1) {
                    reload.inserted = true;
                    level.playLocalSound(player, TechnoGrapple.RELOAD_INSERT.get(), SoundSource.PLAYERS, 0.65F, 1);
                }
            }
            GauntletVisual visual = VISUALS.get(player.getUUID());
            if (visual == null || !TechnoGrapple.isGauntlet(player.getMainHandItem())) continue;
            PumpMotion port = visual.pumps().port();
            PumpMotion starboard = visual.pumps().starboard();
            boolean portReady = port != null && port.shouldPlayReturnSound(now);
            boolean starboardReady = starboard != null && starboard.shouldPlayReturnSound(now);
            if (!portReady && !starboardReady) continue;

            VISUALS.put(player.getUUID(), visual.withPumps(new Pumps(
                    portReady ? port.markReturnSoundPlayed() : port,
                    starboardReady ? starboard.markReturnSoundPlayed() : starboard)));
            level.playLocalSound(player, TechnoGrapple.PUMP_RETURN.get(), SoundSource.PLAYERS, 0.50F, 1.0F);
        }
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        SunEffects.tick();
        SunWaveParticles.tick();
        RELOADS.values().removeIf(reload -> reload.progress() >= 1);
        long now = System.nanoTime();
        FAILED_AT.values().removeIf(start -> now - start >= FAILURE_DISPLAY);
        SUN_IMPACTS.values().removeIf(start -> now - start >= 300_000_000);
        var minecraft = Minecraft.getInstance();
        VISUALS.entrySet().removeIf(entry -> {
            GauntletVisual visual = entry.getValue();
            if (visual.state().tandem()) {
                return minecraft.level == null || minecraft.level.getPlayerByUUID(entry.getKey()) == null;
            }
            return visual.state().emptyRail() == RailState.DOCKED && visual.pumps().idle(now);
        });
        var player = minecraft.player;
        if (!Minecraft.getInstance().options.keyUse.isDown()) {
            reloadClickHeld = false;
            sunClickHeld = false;
        }
        boolean jump = Minecraft.getInstance().options.keyJump.isDown();
        boolean gliding = player != null && player.isFallFlying();
        if (jump && !wasJumpDown && wasGliding && gliding && player != null
                && TechnoGrapple.isGauntlet(player.getMainHandItem())) {
            sendGlideControl(player.getUUID(), false);
        }
        wasJumpDown = jump;
        wasGliding = gliding;
        if (player == null || !TechnoGrapple.isGauntlet(player.getMainHandItem())) resetLaunchPose();
    }

    @SubscribeEvent
    static void predictLaunch(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        var player = Minecraft.getInstance().player;
        if (player == null || !TechnoGrapple.isGauntlet(player.getMainHandItem())) return;
        if (event.isUseItem()) {
            if (SunEffects.cooling(player.getUUID())) {
                event.setCanceled(true);
                event.setSwingHand(false);
                return;
            }
            if (RopeRenderer.hasSunShot(player.getUUID())) {
                RailState sun = deployedRail(player.getUUID());
                if (!sunClickHeld && sun != null && sun.display() == RailState.SUN_LOCKED) {
                    net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(SunReleasePress.INSTANCE);
                }
                sunClickHeld = Minecraft.getInstance().options.keyUse.isDown();
                event.setCanceled(true);
                event.setSwingHand(false);
                return;
            }
            if (RELOADS.containsKey(player.getUUID())) {
                event.setCanceled(true);
                event.setSwingHand(false);
                return;
            }
            if (reloadClickHeld) {
                event.setCanceled(true);
                event.setSwingHand(false);
                return;
            }
            if (DartAmmo.reloadMode(player) != 0) {
                reloadClickHeld = Minecraft.getInstance().options.keyUse.isDown();
                return;
            }
        }
        if (player.isFallFlying() && !GLIDE_RAISED.contains(player.getUUID())
                && event.isUseItem()) {
            sendGlideControl(player.getUUID(), true);
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        if (!event.isUseItem()) return;
        long now = System.nanoTime();
        RailState deployed = deployedRail(player.getUUID());
        if ((deployed != null && deployed.firing()) || waitForMissPump
                || (missPumpIdleAt != 0 && now < missPumpIdleAt)) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        boolean grip = DartAmmo.firesGrip(player);
        boolean tandemWhipCooling = !grip && tandemWhipRefireAt != 0 && now < tandemWhipRefireAt
                && DartAmmo.mode(player.getMainHandItem()) == DartAmmo.TANDEM;
        if (player.isUsingItem() || player.isSpectator() || player.isPassenger()
                || player.getAbilities().flying
                || tandemWhipCooling
                || !TechnoGrapple.canLaunch(player.getMainHandItem())
                || DartAmmo.find(player, grip).isEmpty()
                || deployed != null) return;
        // Start on the click. The server's rail state takes over until the dart is spent.
        if (!grip && ModList.get().isLoaded("iris")) {
            net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                    IrisSunCompatibility.sunView());
        }
        predictedLaunchUntil = now + 175_000_000;
    }

    @SubscribeEvent
    static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        RELOADS.clear();
        reloadClickHeld = false;
        sunClickHeld = false;
        GLIDE_RAISED.clear();
        wasJumpDown = false;
        wasGliding = false;
        GauntletElbow.clearGestures();
        VISUALS.clear();
        FAILED_AT.clear();
        SUN_IMPACTS.clear();
        SunEffects.clear();
        SunWaveParticles.clear();
        AMMO_TYPES.clear();
        tandemWhipRefireAt = 0;
        RopeRenderer.clearRetractions();
        resetLaunchPose();
    }

    @SubscribeEvent
    static void leaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ClientLevel && event.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
            VISUALS.remove(player.getUUID());
            GauntletElbow.removeGesture(player.getUUID());
            RELOADS.remove(player.getUUID());
            GLIDE_RAISED.remove(player.getUUID());
            FAILED_AT.remove(player.getUUID());
            SUN_IMPACTS.remove(player.getUUID());
            SunEffects.remove(player.getUUID());
            RopeRenderer.clearSunShot(player.getUUID());
            AMMO_TYPES.remove(player.getUUID());
        }
    }

    private static void resetLaunchPose() {
        glideSink = IDLE_POSE;
        launchPose = IDLE_POSE;
        predictedLaunchUntil = 0;
        waitForMissPump = false;
        missPumpIdleAt = 0;
        releaseWhip = null;
        handOutlet = null;
    }

    static @Nullable RailState deployedRail(UUID player) {
        GauntletVisual visual = VISUALS.get(player);
        return visual == null || visual.state().rail() == RailState.DOCKED ? null : visual.state();
    }

    static boolean isPulling(Player player) {
        RailState state = deployedRail(player.getUUID());
        return player == Minecraft.getInstance().player && state != null && state.pulling();
    }

    static @Nullable Vec3 recentHandOutlet(int rail) {
        HandOutlet outlet = handOutlet;
        return outlet != null && System.nanoTime() - outlet.capturedAt() < 150_000_000 ? outlet.outlets().get(rail) : null;
    }

    @SubscribeEvent
    static void renderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !TechnoGrapple.isGauntlet(event.getItemStack())) return;
        // Leave the hand render event untouched when FirstPerson owns the view.
        if (ModList.get().isLoaded("firstperson")) return;
        boolean vanillaLike = TechnoGrappleConfig.vanillaLikeEnabled();
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || player.isInvisible() || player.isScoping()) return;
        ItemStack heldStack = player.getMainHandItem();
        // Vanilla still renders the outgoing gauntlet as it lowers during the slot swap.
        ItemStack renderedStack = heldStack.is(event.getItemStack().getItem()) ? heldStack : event.getItemStack();

        event.setCanceled(true);
        long now = System.nanoTime();
        boolean stowed = player.isFallFlying() && !GLIDE_RAISED.contains(player.getUUID());
        if (glideSink.active() != stowed) glideSink = new LaunchPose(stowed, glideSink.amount(now), now);
        float sink = glideSink.amount(now);
        if (sink >= 1) {
            handOutlet = null;
            return;
        }
        HumanoidArm arm = player.getMainArm();
        boolean left = arm == HumanoidArm.LEFT;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        try {
            pose.translate(0, -1.25F * sink, 0);
            applyLaunchPose(pose, player.getUUID(), left ? -1 : 1, vanillaLike);
            if (!vanillaLike) applyReloadPose(pose, player.getUUID(), left ? -1 : 1);
            gauntletArmPose = pose;
            ((ItemInHandRendererInvoker) minecraft.gameRenderer.itemInHandRenderer).technograpple$renderPlayerArm(
                    pose, event.getSubmitNodeCollector(), event.getPackedLight(), event.getEquipProgress(),
                    event.getSwingProgress(), arm);
            var avatarRenderer = minecraft.getEntityRenderDispatcher().getPlayerRenderer(player);
            avatarRenderer.getModel().getArm(arm).translateAndRotate(pose);
            pose.mulPose(Axis.XP.rotationDegrees(-90));
            pose.mulPose(Axis.YP.rotationDegrees(180));
            pose.translate(left ? -1 / 16F : 1 / 16F, 2 / 16F, -10 / 16F);
            var state = new ItemStackRenderState();
            // The hand renderer's cached stack can lag a Survival durability update.
            minecraft.getItemModelResolver().updateForLiving(state, renderedStack,
                    left ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, player);
            if (vanillaLike) captureSpriteHandOutlet(state, pose);
            else captureHandOutlet(pose, left);
            state.submit(pose, event.getSubmitNodeCollector(), event.getPackedLight(), OverlayTexture.NO_OVERLAY, 0);
        } finally {
            gauntletArmPose = null;
            pose.popPose();
        }
    }

    private static void captureHandOutlet(PoseStack pose, boolean left) {
        // Match the first-person display transform and center the cable inside the copper crossbar.
        Matrix4f view = new Matrix4f(RenderSystem.getModelViewStack()).mul(pose.last().pose())
                .translate(0, -0.875F / 16, 3 / 16F)
                .rotateY(left ? -(float) Math.PI : (float) Math.PI)
                .scale(1.125F);
        handOutlet = new HandOutlet(RopeRenderer.RailOutlets.capture(view.translate(-0.5F, -0.5F, -0.5F)),
                System.nanoTime());
    }

    private static void captureSpriteHandOutlet(ItemStackRenderState item, PoseStack pose) {
        if (item.isEmpty()) return;
        pose.pushPose();
        try {
            RopeRenderer.applyFirstLayerTransform(item, pose);
            var view = new Matrix4f(RenderSystem.getModelViewStack()).mul(pose.last().pose());
            handOutlet = new HandOutlet(RopeRenderer.RailOutlets.captureSprite(view), System.nanoTime());
        } finally {
            pose.popPose();
        }
    }

    private static void applyLaunchPose(PoseStack pose, UUID player, int side, boolean vanillaLike) {
        long now = System.nanoTime();
        RailState rail = deployedRail(player);
        boolean active = rail != null || waitForMissPump || missPumpIdleAt != 0 && now < missPumpIdleAt + MISS_POSE_HOLD
                || predictedLaunchUntil != 0 && now < predictedLaunchUntil;
        if (launchPose.active() != active) launchPose = new LaunchPose(active, launchPose.amount(now), now);
        float amount = launchPose.amount(now);
        float whip = 0;
        if (!vanillaLike && releaseWhip != null) {
            if (releaseWhip.finished(now)) {
                releaseWhip = null;
            } else {
                // Retraction can finish before the wrist does. Let this envelope settle on its own.
                amount = Math.max(amount, releaseWhip.launchAmount(now));
                whip = releaseWhip.whipAmount(now);
            }
        }
        Long failedAt = FAILED_AT.get(player);
        float bounce = vanillaLike || failedAt == null ? 0
                : (float) Math.sin(Math.PI * Math.clamp((now - failedAt) / (float) FAILURE_BOUNCE, 0, 1));
        float jolt = sunJolt(player) + 0.23F * SunEffects.tremor(player);
        if (amount == 0 && whip == 0 && bounce < 0.0001F && jolt == 0) return;

        // Offset the vanilla arm's pitch and yaw so the rails converge on the crosshair.
        float wristX = side * 0.44F;
        pose.translate(wristX, -0.48F - 0.085F * amount + 0.28F * whip - 0.035F * bounce,
                -0.78F - 0.16F * amount + 0.17F * whip + 0.025F * bounce + 0.15F * jolt);
        pose.mulPose(Axis.YP.rotationDegrees(side * 21.6F * amount));
        pose.mulPose(Axis.XP.rotationDegrees(-35.6F * amount + 49 * whip + 4 * bounce + 14 * jolt));
        pose.translate(-wristX, 0.48F, 0.78F);
    }

    private static void applyReloadPose(PoseStack pose, UUID player, int side) {
        ReloadMotion reload = RELOADS.get(player);
        if (reload == null || !reload.ammo.hasOutgoing()) return;
        float t = reload.progress();
        float flick = smoothstep(t / 0.09F) * (1 - smoothstep((t - 0.09F) / 0.16F));
        float kick = smoothstep((t - 0.19F) / 0.06F) * (1 - smoothstep((t - 0.25F) / 0.22F));
        pose.translate(0.35F * side, -0.48F, -0.78F + 0.19F * kick);
        pose.mulPose(Axis.ZP.rotationDegrees(side * (14 * flick - 6 * kick)));
        pose.mulPose(Axis.XP.rotationDegrees(8 * flick + 11 * kick));
        pose.translate(-0.35F * side, 0.48F, 0.78F);
    }

    @SubscribeEvent
    static void rollGauntletArm(RenderArmEvent event) {
        PoseStack pose = event.getPoseStack();
        if (pose != gauntletArmPose) return;

        // Rotate around the cuff's longitudinal axis so the arm and gauntlet roll together.
        var bind = event.getArmPart().getInitialPose();
        float hand = event.getArm() == HumanoidArm.RIGHT ? 1 : -1;
        float cuffCenterX = -hand / 16;
        float armTilt = hand * 0.1F; // AvatarRenderer applies this tilt to the bare arm.
        pose.translate(bind.x() / 16, bind.y() / 16, bind.z() / 16);
        pose.mulPose(Axis.ZP.rotation(armTilt));
        pose.translate(cuffCenterX, 0, 0);
        pose.mulPose(Axis.YP.rotationDegrees(hand * 45));
        pose.translate(-cuffCenterX, 0, 0);
        pose.mulPose(Axis.ZP.rotation(-armTilt));
        pose.translate(-bind.x() / 16, -bind.y() / 16, -bind.z() / 16);
    }

    static boolean hasVisibleAmmo(int types, @Nullable ItemOwner owner) {
        return (visibleAmmoTypes(owner) & types) != 0;
    }

    private static int visibleAmmoTypes(@Nullable ItemOwner owner) {
        var holder = owner == null ? null : owner.asLivingEntity();
        var local = Minecraft.getInstance().player;
        if (holder != null && holder == local) return DartAmmo.availableTypes(local);
        return holder == null ? DartAmmo.TANDEM : AMMO_TYPES.getOrDefault(holder.getUUID(), DartAmmo.TANDEM);
    }

    static RailPose railPose(ItemStack stack, @Nullable ItemOwner owner, int rail) {
        var holder = owner == null ? null : owner.asLivingEntity();
        GauntletVisual visual = holder != null && ItemStack.matches(stack, holder.getMainHandItem())
                ? VISUALS.get(holder.getUUID()) : null;
        return visual == null ? LOADED_RAILS.pose(rail, true, 0)
                : visual.rails().pose(rail, visual.state().tandem() || visual.state().emptyRail() != rail,
                        System.nanoTime());
    }

    static float hazardDoorPosition(ItemStack stack, @Nullable ItemOwner owner, int rail) {
        var holder = owner == null ? null : owner.asLivingEntity();
        GauntletVisual visual = holder != null && ItemStack.matches(stack, holder.getMainHandItem())
                ? VISUALS.get(holder.getUUID()) : null;
        if (visual == null) return 0;
        return (rail == RailState.PORT ? visual.portDoor() : visual.starboardDoor()).position(System.nanoTime());
    }

    private enum VisualProperty implements RangeSelectItemModelProperty {
        DISPLAY("hook_display"), ATTACHMENT_CELLS("attachment_cells"),
        PORT_PUMP("pump_frame_port"), STARBOARD_PUMP("pump_frame_starboard"),
        SPRITE_FRAME("vanilla_like_frame");

        private final String id;
        private final MapCodec<VisualProperty> codec = MapCodec.unit(this);

        VisualProperty(String id) {
            this.id = id;
        }

        @Override
        public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
            var holder = owner == null ? null : owner.asLivingEntity();
            UUID holderId = holder != null && ItemStack.matches(stack, holder.getMainHandItem())
                    ? holder.getUUID() : null;
            GauntletVisual visual = holderId == null ? null : VISUALS.get(holderId);
            if (this == SPRITE_FRAME) {
                int mode = DartAmmo.mode(stack);
                int ammo = visibleAmmoTypes(owner);
                long now = System.nanoTime();
                int portPulse = holderId == null ? 0 : SunEffects.pump(holderId, 0);
                int starboardPulse = holderId == null ? 0 : SunEffects.pump(holderId, 1);
                // The low bits hide the two darts; the high bits compress the two pumps.
                return spriteRailFrame(visual, mode, ammo, RailState.PORT, now, portPulse)
                        | spriteRailFrame(visual, mode, ammo, RailState.STARBOARD, now, starboardPulse) << 1;
            }
            if (holderId != null) {
                if (this == DISPLAY) {
                    int flicker = SunEffects.display(holderId);
                    if (flicker >= 0) return flicker;
                }
                if (this == PORT_PUMP || this == STARBOARD_PUMP) {
                    int pulse = SunEffects.pump(holderId, this == PORT_PUMP ? 0 : 1);
                    if (pulse > 0) return pulse;
                }
            }
            if (this == DISPLAY) {
                if (visual != null && visual.state().display() == RailState.SUN_LOCKED) return RailState.CANNOT_FIRE;
                if (visual != null && visual.state().cableConnected() && visual.state().rope()) {
                    return visual.state().firing() ? RailState.TRAVELLING : 4;
                }
                int mode = DartAmmo.mode(stack);
                int ammo = visibleAmmoTypes(owner) & mode;
                if (!TechnoGrapple.canLaunch(stack) || ammo == 0) return RailState.CANNOT_FIRE;
                Long failedAt = holder == null ? null : FAILED_AT.get(holder.getUUID());
                if (failedAt != null && System.nanoTime() - failedAt < FAILURE_DISPLAY) return RailState.CANNOT_FIRE;
                if (visual == null || visual.state().display() == RailState.IDLE) {
                    return switch (mode) {
                        case DartAmmo.GRIP -> 5;
                        case DartAmmo.TANDEM -> switch (ammo) {
                            case DartAmmo.WHIP -> RailState.TANDEM_GRIP_EMPTY;
                            case DartAmmo.GRIP -> RailState.TANDEM_WHIP_EMPTY;
                            default -> RailState.TANDEM_IDLE;
                        };
                        default -> RailState.IDLE;
                    };
                }
            }
            if (visual == null) return 0;
            return switch (this) {
                case DISPLAY -> visual.state().display();
                case ATTACHMENT_CELLS -> visual.state().attachmentCells();
                case PORT_PUMP -> Math.round(visual.pumpPosition(RailState.PORT, System.nanoTime()) * PUMP_FRAMES);
                case STARBOARD_PUMP -> Math.round(visual.pumpPosition(RailState.STARBOARD, System.nanoTime()) * PUMP_FRAMES);
                case SPRITE_FRAME -> 0; // Resolved above, including when there is no active rail state.
            };
        }

        @Override
        public MapCodec<VisualProperty> type() {
            return codec;
        }
    }

    private static int spriteRailFrame(@Nullable GauntletVisual visual, int mode, int ammo, int rail, long now,
                                       int sunPulse) {
        int frame = (ammo & DartAmmo.typeMask(DartAmmo.gripOnRail(mode, rail))) == 0 ? 1 : 0;
        float pumpPosition = sunPulse > 0 ? sunPulse / (float) PUMP_FRAMES
                : visual == null ? 0 : visual.pumpPosition(rail, now);
        if (pumpPosition >= 0.5F) frame |= 4;
        if (visual == null) return frame;
        boolean loaded = visual.state().tandem() || visual.state().emptyRail() != rail;
        RailPose pose = visual.rails().pose(rail, loaded, now);
        if (!pose.topLoaded() || pose.rolling()) frame |= 1;
        return frame;
    }
}

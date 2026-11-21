package technograpple;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.math.Axis;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import technograpple.mixin.ItemRenderLayerAccessor;
import technograpple.mixin.ItemRenderStateAccessor;

@EventBusSubscriber(modid = TechnoGrapple.ID, value = Dist.CLIENT)
public final class RopeRenderer {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            TechnoGrapple.ID, "textures/entity/grapple_rope.png");
    private static final ContextKey<List<Cable>> CABLES = new ContextKey<>(
            Identifier.fromNamespaceAndPath(TechnoGrapple.ID, "rope_ribbons"));
    private static final double HALF_WIDTH = 0.0115;
    private static final double TILE_LENGTH = 0.40;
    private static final List<RetractingCable> RETRACTIONS = new ArrayList<>();
    private static final Map<Integer, RailOutlets> BODY_OUTLETS = new HashMap<>();
    static final float MOUTH_Z = 19.98F / 16;
    private static final ContextKey<List<SunDartRender>> SUN_DARTS = new ContextKey<>(
            Identifier.fromNamespaceAndPath(TechnoGrapple.ID, "sun_darts"));
    private static final Map<UUID, FlyingSunDart> SUN_SHOTS = new HashMap<>();

    private record FlyingSunDart(SunShot shot, long startTick) {}
    private record SunDartRender(Vec3 position, Vec3 heading, ItemStackRenderState item, int light) {}

    static void startSunShot(SunShot shot) {
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null) return;
        var player = minecraft.player;
        if (shot.lockedSlot() >= 0 && player != null && player.getUUID().equals(shot.player())) {
            player.getInventory().setSelectedSlot(shot.lockedSlot());
        }
        SUN_SHOTS.put(shot.player(), new FlyingSunDart(shot, level.getGameTime()));
    }

    static boolean hasSunShot(UUID player) {
        return SUN_SHOTS.containsKey(player);
    }

    static boolean sunLocksHeldItem(UUID player) {
        var dart = SUN_SHOTS.get(player);
        return dart != null && dart.shot().lockedSlot() >= 0;
    }

    static void clearSunShot(UUID player) {
        SUN_SHOTS.remove(player);
    }

    record RailOutlets(Vec3 port, Vec3 starboard) {
        static RailOutlets capture(Matrix4fc transform) {
            return new RailOutlets(point(transform, 5.995F), point(transform, 10.045F));
        }

        static RailOutlets captureSprite(Matrix4fc transform) {
            // The item transform handles centering. The hook tips occupy columns 5 and 10.
            return new RailOutlets(spriteTip(transform, 5.5F), spriteTip(transform, 10.5F));
        }

        private static Vec3 spriteTip(Matrix4fc transform, float x) {
            Vector3f point = transform.transformPosition(new Vector3f(x / 16, 1, 0.5F));
            return new Vec3(point.x, point.y, point.z);
        }

        private static Vec3 point(Matrix4fc transform, float x) {
            Vector3f point = transform.transformPosition(new Vector3f(x / 16, 10.3F / 16, MOUTH_Z));
            return new Vec3(point.x, point.y, point.z);
        }

        Vec3 get(int rail) {
            return rail == RailState.PORT ? port : starboard;
        }
    }

    private record Ribbon(Vec3 outlet, Vec3 eye, double sag, int light) {
    }

    private record Cable(int playerId, int rail, Vec3 fallback, Vec3 eye, @Nullable Vec3 viewDirection,
                         boolean taut, int light, @Nullable RetractingCable retraction, double remaining) {
    }

    private static final class RetractingCable {
        private final ClientLevel level;
        private final CableRetraction cut;
        private final long startedAt = System.nanoTime();
        private final long duration;
        private final boolean taut;
        private @Nullable Vec3 outlet;

        private RetractingCable(ClientLevel level, CableRetraction cut, boolean taut) {
            this.level = level;
            this.cut = cut;
            this.duration = cut.durationNanos();
            this.taut = taut;
        }
    }

    public static void captureOutlet(int playerId, ItemStackRenderState item, PoseStack pose) {
        if (item.isEmpty()) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.options.getCameraType().isFirstPerson() && minecraft.player != null
                && minecraft.player.getId() == playerId && minecraft.getCameraEntity() == minecraft.player
                && !FirstPersonBody.isRendering()) return;

        pose.pushPose();
        try {
            applyFirstLayerTransform(item, pose);
            BODY_OUTLETS.put(playerId, TechnoGrappleConfig.vanillaLikeEnabled()
                    ? RailOutlets.captureSprite(pose.last().pose()) : RailOutlets.capture(pose.last().pose()));
        } finally {
            pose.popPose();
        }
    }

    static void applyFirstLayerTransform(ItemStackRenderState item, PoseStack pose) {
        var layer = ((ItemRenderStateAccessor) item).technograpple$firstLayer();
        ((ItemRenderLayerAccessor) layer).technograpple$applyTransform(pose.last());
    }

    private static final class FirstPersonBody {
        private static final @Nullable Method RENDERING = findRenderingMethod();

        private static @Nullable Method findRenderingMethod() {
            if (!ModList.get().isLoaded("firstperson")) return null;
            try {
                return Class.forName("dev.tr7zw.firstperson.api.FirstPersonAPI").getMethod("isRenderingPlayer");
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                return null;
            }
        }

        private static boolean isRendering() {
            if (RENDERING == null) return false;
            try {
                return Boolean.TRUE.equals(RENDERING.invoke(null));
            } catch (ReflectiveOperationException unavailable) {
                return false;
            }
        }
    }

    static void retract(CableRetraction cut) {
        var level = Minecraft.getInstance().level;
        RailState state = GauntletClient.deployedRail(cut.player());
        if (level != null) RETRACTIONS.add(new RetractingCable(level, cut, state != null && state.taut()));
    }

    static void clearRetractions() {
        RETRACTIONS.clear();
        SUN_SHOTS.clear();
        BODY_OUTLETS.clear();
    }

    @SubscribeEvent
    static void extract(ExtractLevelRenderStateEvent event) {
        var level = event.getLevel();
        var renderState = event.getRenderState();
        var camera = renderState.cameraRenderState;
        BODY_OUTLETS.clear();
        List<Cable> cables = new ArrayList<>();
        List<SunDartRender> sunDarts = new ArrayList<>();
        for (var player : level.players()) {
            RailState state = GauntletClient.deployedRail(player.getUUID());
            if (state == null || !state.cableConnected()
                    || !TechnoGrapple.isGauntlet(player.getMainHandItem())) continue;

            boolean firstPerson = isFirstPerson(event, player);
            float partial = firstPerson ? camera.cameraEntityPartialTicks : renderState.worldPartialTicks;
            Vec3 outlet = cableOutlet(event, player, state.rail());

            float hookPartial = renderState.worldPartialTicks;
            FlyingSunDart sun = SUN_SHOTS.get(player.getUUID());
            if (sun != null) {
                double ticks = state.firing() ? Math.max(0, level.getGameTime() - sun.startTick() + hookPartial)
                        : SunShot.RANGE / SunShot.SPEED;
                Vec3 heading = sun.shot().direction();
                Vec3 position = sun.shot().tip(ticks).subtract(heading.scale(1.71 / 16));
                Vec3 eye = position.subtract(heading.scale(1.36 / 16));
                if (event.getFrustum().isVisible(new AABB(outlet, eye).inflate(1.65))) {
                    int light = cableLight(level, outlet);
                    cables.add(new Cable(player.getId(), state.rail(), outlet, eye,
                            firstPerson ? player.getViewVector(partial) : null, true, light, null, 1));
                    var stack = new ItemStack(TechnoGrapple.HOOK.get());
                    stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(TechnoGrapple.ID,
                            state.rope() ? "electronic_rope_hook" : "electronic_hook"));
                    var item = new ItemStackRenderState();
                    Minecraft.getInstance().getItemModelResolver().updateForTopItem(item, stack,
                            ItemDisplayContext.NONE, level, null, player.getId());
                    sunDarts.add(new SunDartRender(position, heading, item, light));
                }
                continue;
            }
            if (!(level.getEntity(state.hookId()) instanceof Display.ItemDisplay hook)) continue;
            float yaw = hook.getViewYRot(hookPartial) - 180;
            float pitch = -hook.getViewXRot(hookPartial);
            Vec3 heading = Vec3.directionFromRotation(pitch, yaw);
            Vec3 up = Vec3.directionFromRotation(pitch - 90, yaw);
            Vec3 eye = hook.getPosition(hookPartial).subtract(heading.scale(1.36 / 16)).add(up.scale(0.04 / 16));
            if (!event.getFrustum().isVisible(new AABB(outlet, eye).inflate(1.65))) continue;
            Vec3 view = firstPerson ? player.getViewVector(partial) : null;
            cables.add(new Cable(player.getId(), state.rail(), outlet, eye, view, state.taut(),
                    cableLight(level, outlet.lerp(eye, 0.5)), null, 1));
        }
        extractRetractions(event, cables);
        renderState.setRenderData(CABLES, cables);
        renderState.setRenderData(SUN_DARTS, sunDarts);
    }

    private static void extractRetractions(ExtractLevelRenderStateEvent event, List<Cable> cables) {
        var level = event.getLevel();
        long now = System.nanoTime();
        var iterator = RETRACTIONS.listIterator();
        while (iterator.hasNext()) {
            RetractingCable cable = iterator.next();
            long elapsed = now - cable.startedAt;
            if (cable.level != level || elapsed >= cable.duration) {
                iterator.remove();
                continue;
            }

            CableRetraction cut = cable.cut;
            Vec3 outlet = cable.outlet;
            int playerId = -1;
            if (outlet == null) {
                var player = level.getPlayerByUUID(cut.player());
                if (player != null && TechnoGrapple.isGauntlet(player.getMainHandItem())) {
                    playerId = player.getId();
                    outlet = cableOutlet(event, player, cut.rail());
                } else {
                    outlet = cut.outlet();
                }
            }

            double remaining = 1 - elapsed / (double) cable.duration;
            remaining *= remaining;
            double midpoint = 1 - remaining * 0.5;
            cables.add(new Cable(playerId, cut.rail(), outlet, cut.eye(), null, cable.taut,
                    cableLight(level, outlet.lerp(cut.eye(), midpoint)), cable, remaining));
        }
    }

    private static int cableLight(ClientLevel level, Vec3 point) {
        return LightCoordsUtil.getLightCoords(level, BlockPos.containing(point));
    }

    private static double sag(double distance) {
        return Math.min(0.55, distance * 0.045);
    }

    private static boolean isFirstPerson(ExtractLevelRenderStateEvent event, Player player) {
        return event.getRenderState().cameraRenderState.isFirstPerson
                && player == Minecraft.getInstance().player && event.getCamera().entity() == player;
    }

    private static Vec3 cableOutlet(ExtractLevelRenderStateEvent event, Player player, int rail) {
        var renderState = event.getRenderState();
        return isFirstPerson(event, player)
                ? firstPersonOutlet(event, renderState.cameraRenderState, player.getMainArm(), rail)
                : player.getRopeHoldPosition(renderState.worldPartialTicks);
    }

    private static Vec3 firstPersonOutlet(ExtractLevelRenderStateEvent event, CameraRenderState camera,
                                          HumanoidArm arm, int rail) {
        Vec3 viewOutlet = GauntletClient.recentHandOutlet(rail);
        if (viewOutlet == null || viewOutlet.z >= -0.05) {
            int side = arm == HumanoidArm.RIGHT ? 1 : -1;
            return camera.pos.add(event.getCamera().getNearPlane(camera.hudFov)
                    .getPointOnPlane(side * 0.48F, -0.31F).scale(12));
        }

        // The hand pass has its own FOV. Match its screen position in the world projection.
        var window = Minecraft.getInstance().getWindow();
        float aspect = (float) window.getWidth() / window.getHeight();
        double scale = 1 / (Math.tan(camera.hudFov * Mth.DEG_TO_RAD / 2) * -viewOutlet.z);
        Vector3f view = new Vector3f((float) viewOutlet.x, (float) viewOutlet.y, (float) viewOutlet.z);
        float depth = camera.projectionMatrix.transformProject(view, new Vector3f()).z;
        Vector3f screen = new Vector3f((float) (viewOutlet.x * scale / aspect), (float) (viewOutlet.y * scale), depth);
        new Matrix4f(camera.projectionMatrix).invert().transformProject(screen);
        new Matrix4f(camera.viewRotationMatrix).invert().transformPosition(screen);
        return camera.pos.add(screen.x, screen.y, screen.z);
    }

    @SubscribeEvent
    static void submit(SubmitCustomGeometryEvent event) {
        var state = event.getLevelRenderState();
        List<Cable> cables = state.getRenderData(CABLES);
        if (cables == null || cables.isEmpty()) return;
        Vec3 camera = state.cameraRenderState.pos;
        var darts = state.getRenderData(SUN_DARTS);
        if (darts != null) for (var dart : darts) {
            var pose = event.getPoseStack();
            Vec3 position = dart.position().subtract(camera);
            Vec3 heading = dart.heading();
            pose.pushPose();
            try {
                pose.translate(position.x, position.y, position.z);
                pose.rotate(Axis.YP, (float) -Math.atan2(-heading.x, heading.z));
                pose.rotate(Axis.XP, (float) Math.atan2(heading.y, heading.horizontalDistance()));
                dart.item().submit(pose, event.getSubmitNodeCollector(), dart.light(), OverlayTexture.NO_OVERLAY, 0);
            } finally {
                pose.popPose();
            }
        }
        List<Ribbon> ribbons = new ArrayList<>();
        for (Cable cable : cables) {
            RailOutlets renderedOutlets = BODY_OUTLETS.get(cable.playerId());
            Vec3 outlet = renderedOutlets == null ? cable.fallback() : renderedOutlets.get(cable.rail()).add(camera);
            Vec3 eye = cable.eye();
            double distance = outlet.distanceTo(eye);
            // Suppress the initial backward segment while the dart is still emerging from the tool.
            if (cable.viewDirection() != null && distance < 1.25
                    && eye.subtract(outlet).dot(cable.viewDirection()) <= 0) continue;
            double sag = cable.taut() ? 0 : sag(distance);
            if (cable.retraction() != null) {
                cable.retraction().outlet = outlet;
                double remaining = cable.remaining();
                double start = 1 - remaining;
                outlet = outlet.lerp(eye, start).add(0, -4 * sag * start * remaining, 0);
                sag *= remaining * remaining;
            }
            if (outlet.distanceToSqr(eye) >= 0.03 * 0.03) {
                ribbons.add(new Ribbon(outlet.subtract(camera), eye.subtract(camera), sag, cable.light()));
            }
        }
        submitRibbons(event, ribbons);
    }

    private static void submitRibbons(SubmitCustomGeometryEvent event, List<Ribbon> ribbons) {
        if (ribbons.isEmpty()) return;
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.entityCutout(TEXTURE),
                (pose, vertices) -> {
                    for (Ribbon ribbon : ribbons) draw(ribbon, pose, vertices);
                });
    }

    private static void draw(Ribbon ribbon, PoseStack.Pose pose, VertexConsumer vertices) {
        Vec3 delta = ribbon.eye().subtract(ribbon.outlet());
        int segments = Mth.clamp((int) Math.ceil(delta.length() / TILE_LENGTH), 1, 256);
        Vec3 previous = ribbon.outlet();
        Vec3 previousSide = side(previous, delta.add(0, -4 * ribbon.sag(), 0));
        for (int i = 1; i <= segments; i++) {
            double t = (double) i / segments;
            Vec3 current = ribbon.outlet().add(delta.scale(t)).add(0, -4 * ribbon.sag() * t * (1 - t), 0);
            Vec3 tangent = delta.add(0, -4 * ribbon.sag() * (1 - 2 * t), 0);
            Vec3 currentSide = side(current, tangent);
            if (currentSide.dot(previousSide) < 0) currentSide = currentSide.scale(-1);

            Vec3 normal = currentSide.cross(tangent).normalize();
            vertex(pose, vertices, previous.subtract(previousSide), 0, 0, normal, ribbon.light());
            vertex(pose, vertices, previous.add(previousSide), 1, 0, normal, ribbon.light());
            vertex(pose, vertices, current.add(currentSide), 1, 1, normal, ribbon.light());
            vertex(pose, vertices, current.subtract(currentSide), 0, 1, normal, ribbon.light());
            previous = current;
            previousSide = currentSide;
        }
    }

    private static Vec3 side(Vec3 cameraRelativePosition, Vec3 tangent) {
        Vec3 side = tangent.cross(cameraRelativePosition).normalize();
        if (side.lengthSqr() == 0) {
            Vec3 axis = Math.abs(tangent.normalize().y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            side = tangent.cross(axis).normalize();
        }
        return side.scale(HALF_WIDTH);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vertices, Vec3 pos,
                               float u, float v, Vec3 normal, int light) {
        vertices.addVertex(pose, (float) pos.x, (float) pos.y, (float) pos.z)
                .setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}

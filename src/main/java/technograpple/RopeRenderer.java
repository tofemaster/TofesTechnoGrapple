package technograpple;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.math.Axis;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import javax.annotation.Nullable;

@EventBusSubscriber(modid = TechnoGrapple.ID, value = Dist.CLIENT)
public final class RopeRenderer {
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            TechnoGrapple.ID, "textures/entity/grapple_rope.png");
    private static final double HALF_WIDTH = 0.0115;
    private static final double TILE_LENGTH = 0.40;
    private static final List<RetractingCable> RETRACTIONS = new ArrayList<>();
    private static final Map<Integer, RailOutlets> BODY_OUTLETS = new HashMap<>();
    static final float MOUTH_Z = 19.98F / 16;
    private static final Map<UUID, FlyingSunDart> SUN_SHOTS = new HashMap<>();

    private record FlyingSunDart(SunShot shot, long startTick) {}
    private record SunDartRender(Vec3 position, Vec3 heading, ItemStack item, int light) {}

    static void startSunShot(SunShot shot) {
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null) return;
        var player = minecraft.player;
        if (shot.lockedSlot() >= 0 && player != null && player.getUUID().equals(shot.player())) {
            player.getInventory().selected = shot.lockedSlot();
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

    private record Cable(Vec3 outlet, Vec3 eye, @Nullable Vec3 viewDirection,
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

    public static void captureOutlet(int playerId, PoseStack pose, ItemStack stack, ItemDisplayContext context, boolean left) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.options.getCameraType().isFirstPerson() && minecraft.player != null
                && minecraft.player.getId() == playerId && minecraft.getCameraEntity() == minecraft.player
                && !FirstPersonBody.isRendering()) return;
        pose.pushPose();
        try {
            var player = minecraft.level.getEntity(playerId);
            var model = minecraft.getItemRenderer().getModel(stack, minecraft.level,
                    player instanceof net.minecraft.world.entity.LivingEntity holder ? holder : null, playerId);
            model.applyTransform(context, pose, left);
            pose.translate(-0.5F, -0.5F, -0.5F);
            BODY_OUTLETS.put(playerId, TechnoGrappleConfig.vanillaLikeEnabled()
                    ? RailOutlets.captureSprite(pose.last().pose()) : RailOutlets.capture(pose.last().pose()));
        } finally {
            pose.popPose();
        }
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
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        var level = Minecraft.getInstance().level;
        try {
            if (level != null) renderCables(event, level);
        } finally {
            BODY_OUTLETS.clear();
        }
    }

    private static void renderCables(RenderLevelStageEvent event, ClientLevel level) {
        // The held-item layer inherits the level's view matrix. Undo it before using
        // its outlet as a world position for culling, lighting and ribbon placement.
        var viewToCameraRelative = new Matrix4f(event.getPoseStack().last().pose()).invert();
        float worldPartial = event.getPartialTick();
        List<Cable> cables = new ArrayList<>();
        List<SunDartRender> sunDarts = new ArrayList<>();
        for (var player : level.players()) {
            RailState state = GauntletClient.deployedRail(player.getUUID());
            if (state == null || !state.cableConnected()
                    || !TechnoGrapple.isGauntlet(player.getMainHandItem())) continue;

            boolean firstPerson = isFirstPerson(event, player);
            Vec3 outlet = cableOutlet(event, player, state.rail(), viewToCameraRelative);

            FlyingSunDart sun = SUN_SHOTS.get(player.getUUID());
            if (sun != null) {
                double ticks = state.firing() ? Math.max(0, level.getGameTime() - sun.startTick() + worldPartial)
                        : SunShot.RANGE / SunShot.SPEED;
                Vec3 heading = sun.shot().direction();
                Vec3 position = sun.shot().tip(ticks).subtract(heading.scale(1.71 / 16));
                Vec3 eye = position.subtract(heading.scale(1.36 / 16));
                if (event.getFrustum().isVisible(new AABB(outlet, eye).inflate(1.65))) {
                    int light = cableLight(level, outlet);
                    cables.add(new Cable(outlet, eye, firstPerson ? player.getViewVector(worldPartial) : null,
                            true, light, null, 1));
                    var item = new ItemStack((state.rope() ? TechnoGrapple.ROPE_DISPLAY : TechnoGrapple.HOOK_DISPLAY).get());
                    sunDarts.add(new SunDartRender(position, heading, item, light));
                }
                continue;
            }
            if (!(level.getEntity(state.hookId()) instanceof Display.ItemDisplay hook)) continue;
            float yaw = hook.getViewYRot(worldPartial) - 180;
            float pitch = -hook.getViewXRot(worldPartial);
            Vec3 heading = Vec3.directionFromRotation(pitch, yaw);
            Vec3 up = Vec3.directionFromRotation(pitch - 90, yaw);
            Vec3 eye = hook.getPosition(worldPartial).subtract(heading.scale(1.36 / 16)).add(up.scale(0.04 / 16));
            if (!event.getFrustum().isVisible(new AABB(outlet, eye).inflate(1.65))) continue;
            Vec3 view = firstPerson ? player.getViewVector(worldPartial) : null;
            cables.add(new Cable(outlet, eye, view, state.taut(),
                    cableLight(level, outlet.lerp(eye, 0.5)), null, 1));
        }
        extractRetractions(event, level, viewToCameraRelative, cables);
        submit(event, cables, sunDarts);
    }

    private static void extractRetractions(RenderLevelStageEvent event, ClientLevel level,
                                           Matrix4fc viewToCameraRelative, List<Cable> cables) {
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
            if (outlet == null) {
                var player = level.getPlayerByUUID(cut.player());
                if (player != null && TechnoGrapple.isGauntlet(player.getMainHandItem())) {
                    outlet = cableOutlet(event, player, cut.rail(), viewToCameraRelative);
                } else {
                    outlet = cut.outlet();
                }
            }

            double remaining = 1 - elapsed / (double) cable.duration;
            remaining *= remaining;
            double midpoint = 1 - remaining * 0.5;
            cables.add(new Cable(outlet, cut.eye(), null, cable.taut,
                    cableLight(level, outlet.lerp(cut.eye(), midpoint)), cable, remaining));
        }
    }

    private static int cableLight(ClientLevel level, Vec3 point) {
        return LevelRenderer.getLightColor(level, BlockPos.containing(point));
    }

    private static double sag(double distance) {
        return Math.min(0.55, distance * 0.045);
    }

    private static boolean isFirstPerson(RenderLevelStageEvent event, Player player) {
        return !event.getCamera().isDetached()
                && player == Minecraft.getInstance().player && event.getCamera().getEntity() == player;
    }

    private static Vec3 cableOutlet(RenderLevelStageEvent event, Player player, int rail,
                                    Matrix4fc viewToCameraRelative) {
        var outlets = BODY_OUTLETS.get(player.getId());
        if (outlets != null) {
            var view = outlets.get(rail);
            var offset = viewToCameraRelative.transformPosition(new Vector3f((float) view.x, (float) view.y, (float) view.z));
            return event.getCamera().getPosition().add(offset.x, offset.y, offset.z);
        }
        return isFirstPerson(event, player)
                ? firstPersonOutlet(event, player.getMainArm(), rail)
                : player.getRopeHoldPosition(event.getPartialTick());
    }

    private static Vec3 firstPersonOutlet(RenderLevelStageEvent event, HumanoidArm arm, int rail) {
        Vec3 viewOutlet = GauntletClient.recentHandOutlet(rail);
        var camera = event.getCamera();
        var plane = camera.getNearPlane();
        if (viewOutlet == null || viewOutlet.z >= -0.05) {
            int side = arm == HumanoidArm.RIGHT ? 1 : -1;
            return camera.getPosition().add(plane.getPointOnPlane(side * 0.48F, -0.31F).scale(12));
        }
        // Both view-space axes use the same sign as the first-person hand pass.
        var offset = new Vector3f((float) -viewOutlet.x, (float) viewOutlet.y, (float) -viewOutlet.z);
        camera.rotation().transform(offset);
        return camera.getPosition().add(offset.x, offset.y, offset.z);
    }

    private static void submit(RenderLevelStageEvent event, List<Cable> cables, List<SunDartRender> darts) {
        if (cables.isEmpty()) return;
        Vec3 camera = event.getCamera().getPosition();
        var minecraft = Minecraft.getInstance();
        var buffer = minecraft.renderBuffers().bufferSource();
        for (var dart : darts) {
            var pose = event.getPoseStack();
            Vec3 position = dart.position().subtract(camera);
            Vec3 heading = dart.heading();
            pose.pushPose();
            try {
                pose.translate(position.x, position.y, position.z);
                pose.mulPose(Axis.YP.rotation((float) -Math.atan2(-heading.x, heading.z)));
                pose.mulPose(Axis.XP.rotation((float) Math.atan2(heading.y, heading.horizontalDistance())));
                minecraft.getItemRenderer().renderStatic(dart.item(), ItemDisplayContext.NONE, dart.light(),
                        OverlayTexture.NO_OVERLAY, pose, buffer, minecraft.level, 0);
            } finally {
                pose.popPose();
            }
        }
        List<Ribbon> ribbons = new ArrayList<>();
        for (Cable cable : cables) {
            Vec3 outlet = cable.outlet();
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
        var type = RenderType.entityCutoutNoCull(TEXTURE);
        var vertices = buffer.getBuffer(type);
        for (Ribbon ribbon : ribbons) draw(ribbon, event.getPoseStack().last(), vertices);
        buffer.endBatch(type);
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
        vertices.vertex(pose.pose(), (float) pos.x, (float) pos.y, (float) pos.z)
                .color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(pose.normal(), (float) normal.x, (float) normal.y, (float) normal.z).endVertex();
    }
}

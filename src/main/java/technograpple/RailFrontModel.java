package technograpple;

import java.util.List;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

record RailFrontModel(Part front, Part dart, Part ropeDart, Part leftDoor, Part rightDoor,
                      int rail, Vec3 pivot, Matrix4fc transform) implements ItemModel {
    private static final Identifier LEFT_DOOR = Identifier.fromNamespaceAndPath(
            TechnoGrapple.ID, "item/gauntlet_hazard_door_left");
    private static final Identifier RIGHT_DOOR = Identifier.fromNamespaceAndPath(
            TechnoGrapple.ID, "item/gauntlet_hazard_door_right");
    private static final float DOOR_HINGE = 0.625F / 16;
    static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("front").forGetter(Unbaked::front),
            Identifier.CODEC.fieldOf("dart").forGetter(Unbaked::dart),
            Codec.BOOL.fieldOf("port").forGetter(Unbaked::port),
            Vec3.CODEC.fieldOf("pivot").forGetter(Unbaked::pivot)
    ).apply(instance, Unbaked::new));

    @Override
    public void update(ItemStackRenderState output, ItemStack stack, ItemModelResolver resolver,
                       ItemDisplayContext displayContext, @Nullable ClientLevel level, @Nullable ItemOwner owner,
                       int seed) {
        var pose = GauntletClient.railPose(stack, owner, rail);
        boolean foil = stack.hasFoil();
        float opening = GauntletClient.hazardDoorPosition(stack, owner, rail);
        if (pose.rolling() || opening > 0 || foil) output.setAnimated();
        addPart(output, front, displayContext, pose.housingAngle(), foil);
        addDoor(output, leftDoor, displayContext, pose.housingAngle(), opening, -DOOR_HINGE, foil);
        addDoor(output, rightDoor, displayContext, pose.housingAngle(), opening, DOOR_HINGE, foil);
        boolean grip = DartAmmo.gripOnRail(DartAmmo.mode(stack), rail);
        if (!GauntletClient.hasVisibleAmmo(DartAmmo.typeMask(grip), owner)) return;
        Part loadedDart = grip ? ropeDart : dart;

        if (pose.rolling()) {
            // The remaining dart rolls up from the underside. Its replacement appears at the stop.
            addPart(output, loadedDart, displayContext, Mth.PI + pose.travelAngle(), foil);
        } else {
            if (pose.topLoaded()) addPart(output, loadedDart, displayContext, 0, foil);
            addPart(output, loadedDart, displayContext, Mth.PI, foil);
        }
    }

    private void addPart(ItemStackRenderState output, Part part, ItemDisplayContext context,
                         float angle, boolean foil) {
        addPart(output, part, context, rotatedHousing(angle), foil);
    }

    private Matrix4fc rotatedHousing(float angle) {
        float x = (float) pivot.x;
        float y = (float) pivot.y;
        return angle == 0 ? transform
                : new Matrix4f(transform).translate(x, y, 0).rotateZ(angle).translate(-x, -y, 0);
    }

    private void addDoor(ItemStackRenderState output, Part door, ItemDisplayContext context,
                         float housingAngle, float opening, float hinge, boolean foil) {
        float swing = -Math.copySign(80 * Mth.DEG_TO_RAD * opening, hinge);
        Matrix4f doorTransform = new Matrix4f(rotatedHousing(housingAngle))
                .translate((float) pivot.x + hinge, (float) pivot.y, RopeRenderer.MOUTH_Z)
                .rotateY(swing).translate(-hinge, 0, 0);
        addPart(output, door, context, doorTransform, foil);
    }

    static void addPart(ItemStackRenderState output, Part part, ItemDisplayContext context,
                                Matrix4fc transform, boolean foil) {
        output.appendModelIdentityElement(part);
        output.appendModelIdentityElement(transform);
        var layer = output.newLayer();
        part.properties().applyToLayer(layer, context);
        layer.prepareQuadList().addAll(part.quads());
        layer.setExtents(part.extents());
        if (foil) layer.setFoilType(ItemStackRenderState.FoilType.STANDARD);

        layer.setLocalTransform(transform);
    }

    record Part(List<BakedQuad> quads, ModelRenderProperties properties, Supplier<Vector3fc[]> extents) {
        static Part bake(ModelBaker baker, Identifier id) {
            var model = baker.getModel(id);
            var textures = model.getTopTextureSlots();
            var quads = model.bakeTopGeometry(textures, baker, BlockModelRotation.IDENTITY).getAll();
            return new Part(quads, ModelRenderProperties.fromResolvedModel(baker, model, textures),
                    Suppliers.memoize(() -> CuboidItemModelWrapper.computeExtents(quads)));
        }
    }

    record Unbaked(Identifier front, Identifier dart, boolean port, Vec3 pivot) implements ItemModel.Unbaked {
        private Identifier ropeDart() {
            return Identifier.fromNamespaceAndPath(TechnoGrapple.ID,
                    port ? "item/rope_hook_docked_port" : "item/rope_hook_docked_starboard");
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            resolver.markDependency(front);
            resolver.markDependency(dart);
            resolver.markDependency(ropeDart());
            resolver.markDependency(LEFT_DOOR);
            resolver.markDependency(RIGHT_DOOR);
        }

        @Override
        public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transformation) {
            var baker = context.blockModelBaker();
            return new RailFrontModel(Part.bake(baker, front), Part.bake(baker, dart),
                    Part.bake(baker, ropeDart()),
                    Part.bake(baker, LEFT_DOOR), Part.bake(baker, RIGHT_DOOR),
                    port ? RailState.PORT : RailState.STARBOARD, pivot.scale(1.0 / 16), transformation);
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}

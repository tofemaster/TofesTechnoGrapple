package technograpple;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementRotation;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.QuadTransformers;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;
import org.joml.Vector3f;

record GauntletCuboids(List<Cuboid> parts) implements IUnbakedGeometry<GauntletCuboids> {
    record Cuboid(BlockElement element, int emission) {}

    static GauntletCuboids read(JsonObject json, JsonDeserializationContext gson) {
        var parts = new ArrayList<Cuboid>();
        for (var shape : json.getAsJsonArray("cuboids")) {
            var definition = shape.getAsJsonObject().deepCopy();
            var rotation = definition.remove("rotation");
            var box = gson.<BlockElement>deserialize(definition, BlockElement.class);
            if (rotation != null) {
                box = new BlockElement(box.from, box.to, box.faces, rotation(rotation.getAsJsonObject()),
                        box.shade, box.getFaceData());
            }
            int emission = definition.has("light_emission") ? definition.get("light_emission").getAsInt() : 0;
            parts.add(new Cuboid(box, emission));
        }
        return new GauntletCuboids(List.copyOf(parts));
    }

    private static BlockElementRotation rotation(JsonObject json) {
        var pivot = json.getAsJsonArray("origin");
        var origin = new Vector3f(pivot.get(0).getAsFloat(), pivot.get(1).getAsFloat(),
                pivot.get(2).getAsFloat()).div(16);
        boolean rescale = json.has("rescale") && json.get("rescale").getAsBoolean();
        if (json.has("axis")) {
            var axis = Direction.Axis.byName(json.get("axis").getAsString());
            if (axis == null) throw new JsonParseException("Invalid cuboid rotation axis");
            return new BlockElementRotation(origin, axis, json.get("angle").getAsFloat(), rescale);
        }
        Direction.Axis axis = Direction.Axis.X;
        float angle = 0;
        for (var candidate : Direction.Axis.values()) {
            var component = json.get(candidate.getName());
            if (component == null || component.getAsFloat() == 0) continue;
            if (angle != 0) throw new JsonParseException("Expected a single-axis cuboid rotation");
            axis = candidate;
            angle = component.getAsFloat();
        }
        return new BlockElementRotation(origin, axis, angle, rescale);
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                           Function<Material, TextureAtlasSprite> sprites, ModelState state,
                           ItemOverrides overrides, ResourceLocation id) {
        var model = new SimpleBakedModel.Builder(context.useAmbientOcclusion(), context.useBlockLight(),
                context.isGui3d(), context.getTransforms(), overrides);
        model.particle(sprites.apply(context.getMaterial("particle")));
        for (var part : parts) {
            for (var entry : part.element.faces.entrySet()) {
                var face = entry.getValue();
                var sprite = sprites.apply(context.getMaterial(face.texture));
                var quad = BlockModel.bakeFace(part.element, face, sprite, entry.getKey(), state, id);
                if (part.emission > 0) QuadTransformers.settingEmissivity(part.emission).processInPlace(quad);
                model.addUnculledFace(quad);
            }
        }
        return model.build();
    }
}

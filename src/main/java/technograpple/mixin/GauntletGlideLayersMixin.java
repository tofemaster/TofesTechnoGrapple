package technograpple.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import technograpple.GauntletClient;
import technograpple.GauntletElbow;
import technograpple.GlideArmView;

@Mixin(LivingEntityRenderer.class)
abstract class GauntletGlideLayersMixin {
    @WrapOperation(method = "submit", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/model/EntityModel;setupAnim(Ljava/lang/Object;)V"))
    private void technograpple$poseGlideItem(EntityModel<?> model, Object state, Operation<Void> original) {
        original.call(model, state);
        if (model instanceof HumanoidModel<?> humanoid && state instanceof AvatarRenderState avatar
                && avatar.isFallFlying && GauntletElbow.isAiming(avatar)) {
            GauntletElbow.poseArm(humanoid, avatar);
        }
    }

    @WrapOperation(method = "submit", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V"))
    private void technograpple$placeGlideArm(SubmitNodeCollector collector, Model<?> model, Object state, PoseStack pose,
                                           RenderType type, int light, int overlay, int tint, UvMapping uv,
                                           int outline, Operation<Void> original, @Local(argsOnly = true) CameraRenderState camera) {
        if (state instanceof AvatarRenderState avatar && ((GlideArmView) avatar).technograpple$armOnly()) {
            pose.pushPose();
            try {
                technograpple$offsetArm(pose, avatar, camera);
                original.call(collector, model, state, pose, type, light, overlay, tint, uv, outline);
            } finally {
                pose.popPose();
            }
        } else {
            original.call(collector, model, state, pose, type, light, overlay, tint, uv, outline);
        }
    }

    @WrapOperation(method = "submit", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/EntityRenderState;FF)V"))
    private void technograpple$submitGlideArmLayers(RenderLayer<?, ?> layer, PoseStack pose,
                                                  SubmitNodeCollector collector, int light, EntityRenderState state,
                                                  float yaw, float pitch, Operation<Void> original,
                                                  @Local(argsOnly = true) CameraRenderState camera) {
        if (state instanceof AvatarRenderState avatar && ((GlideArmView) avatar).technograpple$armOnly()) {
            if (!(layer instanceof ItemInHandLayer<?, ?>)) return;
            pose.pushPose();
            try {
                technograpple$offsetArm(pose, avatar, camera);
                original.call(layer, pose, collector, light, state, yaw, pitch);
            } finally {
                pose.popPose();
            }
        } else {
            original.call(layer, pose, collector, light, state, yaw, pitch);
        }
    }

    @Unique
    private static void technograpple$offsetArm(PoseStack pose, AvatarRenderState state, CameraRenderState camera) {
        float side = state.mainArm == HumanoidArm.RIGHT ? 1 : -1;
        float remaining = 1 - GauntletClient.glideRaiseProgress();
        var offset = camera.orientation.transform(new Vector3f(side * 0.24F, -0.02F - 0.65F * remaining, 0.51F));
        // The entity has already been positioned and rotated. Apply a camera-relative translation.
        pose.last().pose().translateLocal(offset);
    }
}

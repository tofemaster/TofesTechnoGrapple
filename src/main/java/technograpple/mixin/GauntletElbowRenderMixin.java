package technograpple.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import technograpple.GauntletElbow;
import technograpple.GlideArmView;

@Mixin(ModelFeatureRenderer.class)
abstract class GauntletElbowRenderMixin {
    @WrapOperation(method = "renderModel", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/model/Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    private void technograpple$renderBentArm(Model<?> model, PoseStack pose, VertexConsumer buffer,
                                                  int light, int overlay, int color, Operation<Void> original,
                                                  @Local(argsOnly = true) SubmitNodeStorage.ModelSubmit<?> submit) {
        if (model instanceof HumanoidModel<?> humanoid && submit.state() instanceof AvatarRenderState avatar
                && GauntletElbow.isAiming(avatar)) {
            if (avatar.isFallFlying) GauntletElbow.poseArm(humanoid, avatar);
            var elbow = GauntletElbow.of(humanoid, avatar);
            boolean armOnly = ((GlideArmView) avatar).technograpple$armOnly();
            if (armOnly || Math.abs(elbow.forearmRotation().w) < 0.999999F) {
                GauntletElbow.RENDERING.set(elbow);
                try {
                    if (armOnly) {
                        pose.pushPose();
                        try {
                            model.root().translateAndRotate(pose);
                            elbow.arm().render(pose, buffer, light, overlay, color);
                        } finally {
                            pose.popPose();
                        }
                    } else {
                        original.call(model, pose, buffer, light, overlay, color);
                    }
                } finally {
                    GauntletElbow.RENDERING.remove();
                }
                return;
            }
        }
        original.call(model, pose, buffer, light, overlay, color);
    }
}

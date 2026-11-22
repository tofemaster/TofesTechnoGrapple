package technograpple.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import technograpple.GauntletElbow;

@Mixin(LivingEntityRenderer.class)
abstract class GauntletElbowRenderMixin {
    @Redirect(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"))
    private void technograpple$renderArm(EntityModel<?> model, PoseStack pose, VertexConsumer buffer, int light,
                                        int overlay, float red, float green, float blue, float alpha,
                                        LivingEntity entity, float yaw, float partialTick, PoseStack entityPose,
                                        MultiBufferSource entityBuffers, int entityLight) {
        if (model instanceof PlayerModel<?> skin && entity instanceof Player player && GauntletElbow.isAiming(player)) {
            GauntletElbow.RENDERING.set(GauntletElbow.of(skin, player));
            try {
                model.renderToBuffer(pose, buffer, light, overlay, red, green, blue, alpha);
            } finally {
                GauntletElbow.RENDERING.remove();
            }
        } else {
            model.renderToBuffer(pose, buffer, light, overlay, red, green, blue, alpha);
        }
    }
}

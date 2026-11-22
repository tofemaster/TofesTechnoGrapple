package technograpple.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import technograpple.GauntletElbow;
import technograpple.RopeRenderer;
import technograpple.TechnoGrapple;

@Mixin(ItemInHandLayer.class)
abstract class GauntletOutletCaptureMixin {
    @Redirect(method = "renderArmWithItem", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/model/ArmedModel;translateToHand(Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void technograpple$bendWrist(ArmedModel model, HumanoidArm arm, PoseStack pose,
                                       LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                       HumanoidArm renderedArm, PoseStack itemPose, MultiBufferSource buffers, int light) {
        model.translateToHand(arm, pose);
        if (model instanceof PlayerModel<?> skin && entity instanceof Player player
                && arm == player.getMainArm() && GauntletElbow.isAiming(player)) {
            GauntletElbow.of(skin, player).transformForearm(pose);
        }
    }

    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void technograpple$captureOutlet(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                            HumanoidArm arm, PoseStack pose, MultiBufferSource buffers, int light,
                                            CallbackInfo ci) {
        if (entity instanceof Player player && arm == player.getMainArm() && TechnoGrapple.isGauntlet(stack)) {
            RopeRenderer.captureOutlet(player.getId(), pose, stack, context, arm == HumanoidArm.LEFT);
        }
    }
}

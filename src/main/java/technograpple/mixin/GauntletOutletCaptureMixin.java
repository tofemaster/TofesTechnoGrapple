package technograpple.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import technograpple.RopeRenderer;
import technograpple.GlideArmView;
import technograpple.TechnoGrapple;

@Mixin(ItemInHandLayer.class)
abstract class GauntletOutletCaptureMixin {
    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void technograpple$hideGlidingOffhand(ArmedEntityRenderState state, ItemStackRenderState item,
                                                  ItemStack stack, HumanoidArm arm, PoseStack pose,
                                                  SubmitNodeCollector collector, int light, CallbackInfo ci) {
        if (state instanceof GlideArmView view && view.technograpple$armOnly() && arm != state.mainArm) ci.cancel();
    }

    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void technograpple$captureOutlet(ArmedEntityRenderState state, ItemStackRenderState item,
                                                  ItemStack stack, HumanoidArm arm, PoseStack pose,
                                                  SubmitNodeCollector collector, int light, CallbackInfo ci) {
        if (state instanceof AvatarRenderState avatar && arm == state.mainArm && TechnoGrapple.isGauntlet(stack)) {
            RopeRenderer.captureOutlet(avatar.id, item, pose);
        }
    }
}

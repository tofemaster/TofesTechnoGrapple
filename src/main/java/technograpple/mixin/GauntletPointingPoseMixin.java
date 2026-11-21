package technograpple.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import technograpple.GauntletElbow;

@Mixin(HumanoidModel.class)
abstract class GauntletPointingPoseMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void technograpple$pointGauntletArm(HumanoidRenderState state, CallbackInfo ci) {
        if (state instanceof AvatarRenderState avatar && !avatar.isFallFlying && GauntletElbow.isAiming(state)) {
            GauntletElbow.poseArm((HumanoidModel<?>) (Object) this, avatar);
        }
    }
}

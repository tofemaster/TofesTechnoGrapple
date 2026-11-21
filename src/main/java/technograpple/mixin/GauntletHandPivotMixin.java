package technograpple.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import technograpple.GauntletElbow;

@Mixin(PlayerModel.class)
abstract class GauntletHandPivotMixin {
    @Inject(method = "translateToHand(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("TAIL"))
    private void technograpple$followForearm(AvatarRenderState state, HumanoidArm arm, PoseStack pose,
                                                CallbackInfo ci) {
        if (arm == state.mainArm && GauntletElbow.isAiming(state)) {
            GauntletElbow.of((PlayerModel) (Object) this, state).transformForearm(pose);
        }
    }
}

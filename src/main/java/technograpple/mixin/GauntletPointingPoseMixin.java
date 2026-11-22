package technograpple.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import technograpple.GauntletElbow;

@Mixin(HumanoidModel.class)
abstract class GauntletPointingPoseMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void technograpple$poseArm(LivingEntity entity, float limbSwing, float limbAmount, float age,
                                     float yaw, float pitch, CallbackInfo ci) {
        if ((Object) this instanceof PlayerModel<?> model && entity instanceof Player player
                && GauntletElbow.isAiming(player)) {
            GauntletElbow.poseArm(model, player);
        }
    }
}

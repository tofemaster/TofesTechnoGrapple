package technograpple.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import technograpple.TechnoGrapple;

@Mixin(LocalPlayer.class)
abstract class GauntletUseAnimationMixin {
    @Inject(method = "itemUsed", at = @At("HEAD"), cancellable = true)
    private void technograpple$useCustomFirePose(InteractionHand hand, CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        // Firing has its own pose. Only actual slot changes should lower the equipped item.
        if (hand == InteractionHand.MAIN_HAND && TechnoGrapple.isGauntlet(player.getItemInHand(hand))) {
            ci.cancel();
        }
    }
}

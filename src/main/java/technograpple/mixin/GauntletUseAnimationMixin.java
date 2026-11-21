package technograpple.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import technograpple.TechnoGrapple;

@Mixin(ItemInHandRenderer.class)
abstract class GauntletUseAnimationMixin {
    @Inject(method = "itemUsed", at = @At("HEAD"), cancellable = true)
    private void technograpple$useCustomFirePose(InteractionHand hand, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        // Firing has its own pose. Only actual slot changes should lower the equipped item.
        if (hand == InteractionHand.MAIN_HAND && player != null && TechnoGrapple.isGauntlet(player.getMainHandItem())) {
            ci.cancel();
        }
    }
}

package technograpple.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import technograpple.TechnoGrapple;

@Mixin(Player.class)
abstract class GauntletGlideLockMixin {
    @Inject(method = "tryToStartFallFlying", at = @At("HEAD"), cancellable = true)
    private void technograpple$blockGlideStart(CallbackInfoReturnable<Boolean> result) {
        if (TechnoGrapple.blocksGlideStart((Player) (Object) this)) result.setReturnValue(false);
    }
}

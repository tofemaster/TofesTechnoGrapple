package technograpple.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import technograpple.TechnoGrapple;

@Mixin(LocalPlayer.class)
abstract class GauntletUseMovementMixin {
    @ModifyConstant(method = "aiStep", constant = @Constant(floatValue = 0.2F))
    private float technograpple$maintainMovement(float vanillaSlowdown) {
        var player = (LocalPlayer) (Object) this;
        return TechnoGrapple.isGauntlet(player.getUseItem()) ? 1F : vanillaSlowdown;
    }
}

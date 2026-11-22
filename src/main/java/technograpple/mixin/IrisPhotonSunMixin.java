package technograpple.mixin;

import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import technograpple.IrisSunCompatibility;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.shaderpack.ShaderPack", remap = false)
abstract class IrisPhotonSunMixin {
    @Inject(method = "lambda$new$8", at = @At("RETURN"), cancellable = true, require = 0)
    private static void technograpple$scalePhotonSun(CallbackInfoReturnable<String> result) {
        result.setReturnValue(IrisSunCompatibility.adaptShaderSource(result.getReturnValue()));
    }
}

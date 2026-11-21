package technograpple.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import technograpple.IrisSunCompatibility;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.shaderpack.ShaderPack", remap = false)
abstract class IrisPhotonSunMixin {
    @ModifyReturnValue(method = "lambda$new$8", at = @At("RETURN"), require = 0)
    private static String technograpple$scalePhotonSun(String source) {
        return IrisSunCompatibility.adaptShaderSource(source);
    }
}

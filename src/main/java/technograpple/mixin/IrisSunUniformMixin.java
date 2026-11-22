package technograpple.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import technograpple.IrisSunCompatibility;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.CelestialUniforms", remap = false)
abstract class IrisSunUniformMixin {
    @Inject(method = "addCelestialUniforms", at = @At("TAIL"), require = 0)
    private void technograpple$registerSunScale(@Coerce Object uniforms, CallbackInfo result) {
        IrisSunCompatibility.registerUniform(uniforms);
    }

}

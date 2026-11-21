package technograpple.mixin;

import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.class)
public interface DisplayRenderSettingsInvoker {
    @Invoker("setPosRotInterpolationDuration")
    void technograpple$setPosRotInterpolationDuration(int ticks);

    @Invoker("setViewRange")
    void technograpple$setViewRange(float range);
}

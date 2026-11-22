package technograpple.mixin;

import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.class)
public interface DisplayRange {
    @Invoker("setViewRange")
    void technograpple$setViewRange(float value);
}

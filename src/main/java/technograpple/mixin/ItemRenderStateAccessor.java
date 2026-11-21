package technograpple.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ItemStackRenderState.class)
public interface ItemRenderStateAccessor {
    @Invoker("firstLayer")
    ItemStackRenderState.LayerRenderState technograpple$firstLayer();
}

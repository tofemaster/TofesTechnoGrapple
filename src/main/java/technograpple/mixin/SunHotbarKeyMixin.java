package technograpple.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import technograpple.TechnoGrapple;

@Mixin(Minecraft.class)
abstract class SunHotbarKeyMixin {
    @Redirect(method = "handleKeybinds", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/player/Inventory;selected:I", opcode = Opcodes.PUTFIELD))
    private void technograpple$lockNumberKeys(Inventory inventory, int slot) {
        if (!TechnoGrapple.sunLocksHeldItem(inventory.player)) inventory.selected = slot;
    }
}

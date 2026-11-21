package technograpple.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import technograpple.TechnoGrapple;

@Mixin(AbstractContainerMenu.class)
abstract class SunContainerLockMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void technograpple$lockHeldStack(int index, int button, ContainerInput action, Player player,
                                             CallbackInfo result) {
        if (!TechnoGrapple.sunLocksHeldItem(player)) return;
        var inventory = player.getInventory();
        int held = inventory.getSelectedSlot();
        var menu = (AbstractContainerMenu) (Object) this;
        if (index >= 0 && index < menu.slots.size()) {
            var slot = menu.getSlot(index);
            if (slot.container == inventory && slot.getContainerSlot() == held) {
                result.cancel();
                return;
            }
        }
        if ((action == ContainerInput.SWAP && button == held) || action == ContainerInput.PICKUP_ALL) result.cancel();
    }
}

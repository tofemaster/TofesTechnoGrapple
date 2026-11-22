package technograpple.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import technograpple.TechnoGrapple;

@Mixin(AbstractContainerMenu.class)
abstract class SunContainerLockMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void technograpple$lockHeldStack(int index, int button, ClickType action, Player player,
                                             CallbackInfo result) {
        if (!TechnoGrapple.sunLocksHeldItem(player)) return;
        var inventory = player.getInventory();
        int held = inventory.selected;
        var menu = (AbstractContainerMenu) (Object) this;
        if (index >= 0 && index < menu.slots.size()) {
            var slot = menu.getSlot(index);
            if (slot.container == inventory && slot.getContainerSlot() == held) {
                result.cancel();
                return;
            }
        }
        if ((action == ClickType.SWAP && button == held) || action == ClickType.PICKUP_ALL) result.cancel();
    }
}

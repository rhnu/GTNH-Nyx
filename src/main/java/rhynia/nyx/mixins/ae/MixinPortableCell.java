package rhynia.nyx.mixins.ae;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import appeng.items.AEBasePortableCell;
import appeng.items.tools.powered.ToolPortableCell;

@Mixin(value = AEBasePortableCell.class, priority = 2000, remap = false)
public class MixinPortableCell {

    @ModifyReturnValue(method = "getBytes", at = @At("RETURN"))
    private int modifyBytes(int o) {
        // The capacity method is shared with other portable tools and terminals.
        return (Object) this instanceof ToolPortableCell ? 32768 : o;
    }
}

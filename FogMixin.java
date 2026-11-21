package com.ezvisuals.mixin;

import com.ezvisuals.CW;
import com.ezvisuals.FogBlur;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Fog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BackgroundRenderer.class)
public class FogMixin {
    @Inject(method = "applyFog", at = @At("RETURN"), cancellable = true, require = 0)
    private static void ez$fog(CallbackInfoReturnable<Fog> cir) {
        Fog o = cir.getReturnValue();
        if (o == null) return;
        Fog r = o;
        if (CW.fog()) {
            float[] c = CW.color();
            r = new Fog(r.start(), r.end(), r.shape(), c[0], c[1], c[2], r.alpha());
        }
        if (FogBlur.on()) r = FogBlur.apply(r);
        if (r != o) cir.setReturnValue(r);
    }
}

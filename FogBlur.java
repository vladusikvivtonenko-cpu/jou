package com.ezvisuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Fog;
import net.minecraft.entity.effect.StatusEffects;

/** Distance fog: far chunks dissolve into a soft haze. Distance, softness, color and saturation are adjustable. */
public final class FogBlur {
    private FogBlur() {}

    public static boolean on() { return Modules.FOGBLUR.enabled; }

    public static Fog apply(Fog o) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && (mc.player.isSubmergedInWater() || mc.player.isInLava()
                || mc.player.hasStatusEffect(StatusEffects.BLINDNESS))) return o;
        Module m = Modules.FOGBLUR;
        float end = Math.min(m.get("dist").f(), o.end());
        float start = end * (1f - m.get("soft").f() / 100f);
        if (start >= end) start = end - 1f;
        if (start < 0f) start = 0f;

        int mode = m.get("color").i();
        float r, g, b;
        if (mode == 0) { r = o.red(); g = o.green(); b = o.blue(); }
        else if (mode == 1) { int c = Theme.cur().accent; r = ((c >> 16) & 255) / 255f; g = ((c >> 8) & 255) / 255f; b = (c & 255) / 255f; }
        else if (mode == 2) { r = 0.85f; g = 0.88f; b = 0.95f; }
        else { r = m.get("r").f() / 100f; g = m.get("g").f() / 100f; b = m.get("b").f() / 100f; }

        float[] hsb = java.awt.Color.RGBtoHSB((int) (Math.min(1f, r) * 255), (int) (Math.min(1f, g) * 255), (int) (Math.min(1f, b) * 255), null);
        hsb[1] = Math.max(0f, Math.min(1f, hsb[1] * m.get("sat").f() / 100f));
        hsb[2] = Math.max(0f, Math.min(1f, hsb[2] * m.get("bright").f() / 100f));
        int rgb = java.awt.Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
        return new Fog(start, end, o.shape(), ((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, o.alpha());
    }
}

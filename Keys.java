package com.ezvisuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayDeque;

/** Movable keystrokes widget: W A S D, LMB / RMB with CPS, space bar. */
public final class Keys {
    private Keys() {}

    private static final ArrayDeque<Long> LC = new ArrayDeque<>(), RC = new ArrayDeque<>();
    private static boolean wasL, wasR;
    private static final float[] A = new float[7]; // W A S D LMB RMB SPACE

    private static void sample(MinecraftClient mc) {
        boolean l = mc.options.attackKey.isPressed(), r = mc.options.useKey.isPressed();
        long now = System.currentTimeMillis();
        if (l && !wasL) LC.addLast(now);
        if (r && !wasR) RC.addLast(now);
        wasL = l; wasR = r;
        while (!LC.isEmpty() && now - LC.peekFirst() > 1000) LC.pollFirst();
        while (!RC.isEmpty() && now - RC.peekFirst() > 1000) RC.pollFirst();
    }

    private static void cell(DrawContext c, int x, int y, int w, int h, float anim, String a, String b, boolean bg) {
        Theme t = Theme.cur();
        int off = bg ? 0xC82A2E3C : 0x802A2E3C;
        int on = Draw.alpha(t.accent, 0xD8);
        Draw.rrect(c, x, y, w, h, 4, Draw.lerp(off, on, anim));
        if (b == null) {
            Draw.textC(c, a, x + w / 2, y + (h - 8) / 2 + 1, 0xFFFFFFFF);
        } else {
            Draw.textC(c, a, x + w / 2, y + 4, 0xFFFFFFFF);
            Draw.scaled(c, b, x + (w - Draw.w(b) * 0.75f) / 2f, y + 13.5f, 0.75f, 0xFFDCDCEC, false);
        }
    }

    public static int[] draw(DrawContext c, MinecraftClient mc, int x, int y, boolean preview) {
        Module m = Modules.KEYS;
        sample(mc);
        boolean[] on = {
                mc.options.forwardKey.isPressed(), mc.options.leftKey.isPressed(),
                mc.options.backKey.isPressed(), mc.options.rightKey.isPressed(),
                mc.options.attackKey.isPressed(), mc.options.useKey.isPressed(),
                mc.options.jumpKey.isPressed()};
        for (int i = 0; i < 7; i++) A[i] += ((on[i] ? 1f : 0f) - A[i]) * 0.35f;
        boolean bg = m.get("bg").b();
        int k = 24, g = 3, w = 3 * k + 2 * g;
        int cy = y;
        cell(c, x + k + g, cy, k, k, A[0], "W", null, bg);
        cy += k + g;
        cell(c, x, cy, k, k, A[1], "A", null, bg);
        cell(c, x + k + g, cy, k, k, A[2], "S", null, bg);
        cell(c, x + 2 * (k + g), cy, k, k, A[3], "D", null, bg);
        cy += k + g;
        if (m.get("mouse").b()) {
            int mw = (w - g) / 2;
            boolean cps = m.get("cps").b();
            int lc = preview && LC.isEmpty() ? 7 : LC.size(), rc = preview && RC.isEmpty() ? 3 : RC.size();
            cell(c, x, cy, mw, k, A[4], "LMB", cps ? lc + " CPS" : null, bg);
            cell(c, x + mw + g, cy, w - mw - g, k, A[5], "RMB", cps ? rc + " CPS" : null, bg);
            cy += k + g;
        }
        if (m.get("space").b()) {
            cell(c, x, cy, w, 12, A[6], "", null, bg);
            int lx = x + w / 2 - 9;
            c.fill(lx, cy + 5, lx + 18, cy + 7, Draw.lerp(0xFFB4B8C8, 0xFFFFFFFF, A[6]));
            cy += 12 + g;
        }
        return new int[]{w, Math.max(1, cy - g - y)};
    }
}

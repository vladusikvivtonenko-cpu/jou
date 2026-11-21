package com.ezvisuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.Random;

/** Lightweight weather overlay drawn with plain rectangles (cheap for weak PCs): rain, snow, space, halloween, autumn. */
public final class Fx {
    private Fx() {}
    public static final String[] NAMES = {"Rain", "Snow", "Space", "Halloween", "Autumn"};
    private static final int MAX = 300, DROPS = 14;
    private static final float[] X = new float[MAX], Y = new float[MAX], VX = new float[MAX],
            VY = new float[MAX], SZ = new float[MAX], PH = new float[MAX];
    private static final int[] COL = new int[MAX];
    private static final float[] DX = new float[DROPS], DY = new float[DROPS], DR = new float[DROPS], DL = new float[DROPS];
    private static int count = 0, lastMode = -1;
    private static long lastNs = 0;
    private static final Random R = new Random();
    private static float ssX, ssY, ssLife;

    private static final int[] LEAVES = {0xFFE8731A, 0xFFC8321E, 0xFFF0B21E, 0xFF8C5A1E, 0xFFD9561C};
    private static final int[] FLIES = {0xFFFF8A2E, 0xFF9CFF5A, 0xFFC77DFF, 0xFFFFC04D};

    private static void init(int mode, int n, int sw, int sh) {
        count = n; lastMode = mode;
        for (int i = 0; i < n; i++) spawn(i, mode, sw, sh, true);
    }

    private static void spawn(int i, int mode, int sw, int sh, boolean anywhere) {
        X[i] = R.nextFloat() * sw;
        Y[i] = anywhere ? R.nextFloat() * sh : (mode == 3 ? sh + 6 : -10);
        PH[i] = R.nextFloat() * 6.28f;
        switch (mode) {
            case 0: VX[i] = -70; VY[i] = 420 + R.nextFloat() * 220; SZ[i] = 5 + R.nextInt(6); break;
            case 1: VX[i] = 0; VY[i] = 30 + R.nextFloat() * 45; SZ[i] = 1 + R.nextInt(3); break;
            case 2: VX[i] = -4 - R.nextFloat() * 6; VY[i] = 0; SZ[i] = R.nextFloat() < 0.2f ? 2 : 1; break;
            case 3: VX[i] = 0; VY[i] = -(8 + R.nextFloat() * 22); SZ[i] = 1 + R.nextInt(2);
                COL[i] = FLIES[R.nextInt(FLIES.length)]; break;
            default: VX[i] = 10 + R.nextFloat() * 25; VY[i] = 35 + R.nextFloat() * 50; SZ[i] = 2 + R.nextInt(3);
                COL[i] = LEAVES[R.nextInt(LEAVES.length)]; break;
        }
    }

    public static void render(DrawContext c, MinecraftClient mc) {
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        Module m = Modules.WEATHER;
        int mode = m.get("type").i();
        int dens = Math.min(MAX, m.get("dens").i());
        if (Modules.PERF.enabled) dens = Math.max(10, dens / 2);
        float spd = m.get("speed").f(), size = m.get("size").f();
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;
        if (mode != lastMode || dens != count) init(mode, dens, sw, sh);
        float tt = (now / 1_000_000L % 100000L) / 1000f;

        if (m.get("tint").b()) {
            switch (mode) {
                case 0: c.fillGradient(0, 0, sw, sh, 0x38141C3C, 0x1C0C1428); break;
                case 1: c.fillGradient(0, 0, sw, sh, 0x2814203C, 0x18DCE6FF); break;
                case 2: c.fill(0, 0, sw, sh, 0x30140030); break;
                case 3: c.fillGradient(0, 0, sw, sh, 0x50300060, 0x48FF3A10); break;
                default: c.fillGradient(0, 0, sw, sh, 0x10FFC864, 0x20FF7A14); break;
            }
        }
        if (m.get("title").b()) {
            String t = NAMES[mode];
            float sc = 2.2f;
            Draw.scaled(c, t, (sw - Draw.w(t) * sc) / 2f, 8, sc, 0xFFFFFFFF, true);
        }

        for (int i = 0; i < count; i++) {
            X[i] += VX[i] * dt * spd;
            Y[i] += VY[i] * dt * spd;
            if (mode == 1) X[i] += (float) Math.sin(tt * 1.5f + PH[i]) * 12f * dt;
            if (mode == 3) X[i] += (float) Math.sin(tt * 0.9f + PH[i]) * 14f * dt;
            if (mode == 4) X[i] += (float) Math.sin(tt * 2.0f + PH[i]) * 22f * dt;
            boolean out = mode == 3 ? Y[i] < -8 : Y[i] > sh;
            if (out) spawn(i, mode, sw, sh, false);
            if (X[i] < -12) X[i] = sw + 6;
            if (X[i] > sw + 12) X[i] = -6;
            int x = (int) X[i], y = (int) Y[i], s = Math.max(1, (int) (SZ[i] * size));
            if (mode == 0) {
                c.fill(x, y, x + 1, y + s / 2, 0x90B8D8FF);
                c.fill(x - 1, y + s / 2, x, y + s, 0x90B8D8FF);
            } else if (mode == 1) {
                c.fill(x, y, x + s, y + s, 0xC8FFFFFF);
                if (s >= 3) c.fill(x - 1, y + 1, x + s + 1, y + 2, 0x70FFFFFF);
            } else if (mode == 2) {
                int a = (int) (110 + 100 * Math.sin(tt * 3f + PH[i]));
                c.fill(x, y, x + s, y + s, (Math.max(40, Math.min(255, a)) << 24) | 0xE8E8FF);
            } else if (mode == 3) {
                float p = (float) (0.5 + 0.5 * Math.sin(tt * 2.5f + PH[i]));
                int a = (int) (80 + 175 * p);
                int g = s + 2;
                c.fill(x - g, y - g, x + s + g, y + s + g, Draw.alpha(COL[i], (int) (a * 0.18f)));
                c.fill(x - 1, y - 1, x + s + 1, y + s + 1, Draw.alpha(COL[i], (int) (a * 0.4f)));
                c.fill(x, y, x + s, y + s, Draw.alpha(COL[i], a));
            } else {
                float w = Math.abs((float) Math.sin(tt * 3f + PH[i]));
                int lw = Math.max(1, (int) (s * (0.5f + w)));
                c.fill(x, y, x + lw, y + Math.max(1, s - lw / 3), Draw.alpha(COL[i], 0xE6));
            }
        }

        if (mode == 0) drops(c, sw, sh, dt);

        if (mode == 2) {
            if (ssLife <= 0 && R.nextFloat() < 0.006f) {
                ssX = R.nextFloat() * sw * 0.7f; ssY = R.nextFloat() * sh * 0.4f; ssLife = 0.6f;
            }
            if (ssLife > 0) {
                ssLife -= dt;
                ssX += 520 * dt; ssY += 260 * dt;
                for (int k = 0; k < 14; k++) {
                    int a = (int) (200 * (1 - k / 14f) * Math.min(1f, ssLife * 3));
                    int px = (int) (ssX - k * 4), py = (int) (ssY - k * 2);
                    c.fill(px, py, px + 2, py + 1, (Math.max(0, a) << 24) | 0xFFFFFF);
                }
            }
        }
    }

    /** Big transparent drops on the "lens" while it rains. */
    private static void drops(DrawContext c, int sw, int sh, float dt) {
        for (int i = 0; i < DROPS; i++) {
            DL[i] -= dt;
            if (DL[i] <= 0) {
                if (R.nextFloat() < 0.04f) {
                    DX[i] = R.nextFloat() * sw; DY[i] = R.nextFloat() * sh * 0.8f;
                    DR[i] = 3 + R.nextInt(4); DL[i] = 2.5f + R.nextFloat() * 2f;
                }
                continue;
            }
            float k = Math.min(1f, DL[i] / 1.2f);
            int r = (int) DR[i], x = (int) DX[i], y = (int) DY[i];
            DY[i] += 6 * dt;
            Draw.rrect(c, x - 1, y - 1, r * 2 + 2, r * 2 + 2, r + 1, Draw.alpha(0xFFFFFF, (int) (40 * k)));
            Draw.rrect(c, x, y, r * 2, r * 2, r, Draw.alpha(0xB8D8FF, (int) (46 * k)));
            c.fill(x + r / 2, y + r / 2, x + r / 2 + 2, y + r / 2 + 1, Draw.alpha(0xFFFFFF, (int) (150 * k)));
        }
    }
}

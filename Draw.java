package com.ezvisuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

public final class Draw {
    private Draw() {}
    public static final int TEXT = 0xFFEDEDF5, SUB = 0xFF8A8A9C;

    private static MinecraftClient mc() { return MinecraftClient.getInstance(); }

    public static int alpha(int col, int a) {
        return (col & 0x00FFFFFF) | (Math.max(0, Math.min(255, a)) << 24);
    }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (int) (((a >>> 24) & 255) + (((b >>> 24) & 255) - ((a >>> 24) & 255)) * t);
        int ar = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int ag = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int ab = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return (aa << 24) | (ar << 16) | (ag << 8) | ab;
    }

    /** Smooth rounded rectangle: drawn in real screen pixels with anti-aliased corners (no blocky GUI-scale steps). */
    public static void rrect(DrawContext c, int x, int y, int w, int h, int r, int col) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 1) { c.fill(x, y, x + w, y + h, col); return; }
        int s = (int) Math.max(1, Math.min(4, Math.round(mc().getWindow().getScaleFactor())));
        MatrixStack ms = c.getMatrices();
        if (s > 1) { ms.push(); ms.scale(1f / s, 1f / s, 1f); }
        int X = x * s, Y = y * s, W = w * s, H = h * s, R = r * s;
        c.fill(X + R, Y, X + W - R, Y + R, col);
        c.fill(X, Y + R, X + W, Y + H - R, col);
        c.fill(X + R, Y + H - R, X + W - R, Y + H, col);
        int a0 = col >>> 24;
        for (int i = 0; i < R; i++) {
            double dy = R - i - 0.5;
            double left = R - Math.sqrt(Math.max(0.0, (double) R * R - dy * dy));
            int fx = (int) Math.ceil(left);
            double cov = fx - left;
            if (fx < R) {
                c.fill(X + fx, Y + i, X + R, Y + i + 1, col);
                c.fill(X + W - R, Y + i, X + W - fx, Y + i + 1, col);
                c.fill(X + fx, Y + H - 1 - i, X + R, Y + H - i, col);
                c.fill(X + W - R, Y + H - 1 - i, X + W - fx, Y + H - i, col);
            }
            if (cov > 0.04 && fx >= 1 && fx - 1 < R) {
                int ac = (col & 0x00FFFFFF) | ((int) (a0 * cov) << 24);
                c.fill(X + fx - 1, Y + i, X + fx, Y + i + 1, ac);
                c.fill(X + W - fx, Y + i, X + W - fx + 1, Y + i + 1, ac);
                c.fill(X + fx - 1, Y + H - 1 - i, X + fx, Y + H - i, ac);
                c.fill(X + W - fx, Y + H - 1 - i, X + W - fx + 1, Y + H - i, ac);
            }
        }
        if (s > 1) ms.pop();
    }

    public static void outline(DrawContext c, int x, int y, int w, int h, int col) {
        c.fill(x, y, x + w, y + 1, col);
        c.fill(x, y + h - 1, x + w, y + h, col);
        c.fill(x, y + 1, x + 1, y + h - 1, col);
        c.fill(x + w - 1, y + 1, x + w, y + h - 1, col);
    }

    public static void hgrad(DrawContext c, int x, int y, int w, int h, int c1, int c2) {
        int seg = Math.max(1, Math.min(48, w));
        for (int i = 0; i < seg; i++) {
            int sx = x + w * i / seg, ex = x + w * (i + 1) / seg;
            c.fill(sx, y, ex, y + h, lerp(c1, c2, i / (float) seg));
        }
    }

    public static int w(String s) { return mc().textRenderer.getWidth(s); }

    public static void text(DrawContext c, String s, int x, int y, int col) {
        c.drawText(mc().textRenderer, s, x, y, col, false);
    }

    public static void shadow(DrawContext c, String s, int x, int y, int col) {
        c.drawText(mc().textRenderer, s, x, y, col, true);
    }

    public static void textR(DrawContext c, String s, int xRight, int y, int col) {
        text(c, s, xRight - w(s), y, col);
    }

    public static void textC(DrawContext c, String s, int xc, int y, int col) {
        text(c, s, xc - w(s) / 2, y, col);
    }

    public static void scaled(DrawContext c, String s, float x, float y, float sc, int col, boolean sh) {
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(x, y, 0f);
        m.scale(sc, sc, 1f);
        c.drawText(mc().textRenderer, s, 0, 0, col, sh);
        m.pop();
    }

    public static void sw(DrawContext c, int x, int y, float anim, int accent) {
        rrect(c, x, y, 22, 10, 5, lerp(0xFF2A2A36, accent, anim));
        rrect(c, x + 2 + (int) (anim * 12), y + 2, 6, 6, 3, 0xFFFFFFFF);
    }
}

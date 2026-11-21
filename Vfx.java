package com.ezvisuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** World-space visuals drawn from the HUD with the manual projection: jump circles, hit flash, crosshair. */
public final class Vfx {
    private Vfx() {}

    private interface Col { int at(float f); }

    private static final List<double[]> JUMPS = new ArrayList<>();
    private static Entity hitEnt;
    private static long hitAt;

    // ---------------- events ----------------
    public static void addJump(Vec3d p) {
        if (!Modules.JUMPFX.enabled) return;
        JUMPS.add(new double[]{p.x, p.y + 0.02, p.z, System.currentTimeMillis()});
        while (JUMPS.size() > 8) JUMPS.remove(0);
    }

    public static void onHit(Entity e) {
        if (!Modules.HITFX.enabled) return;
        hitEnt = e;
        hitAt = System.currentTimeMillis();
    }

    // ---------------- helpers ----------------
    /** mode: 0 theme, 1 white, 2 red, 3 rainbow. f = position along the shape (0..1) for gradients. */
    public static int col(int mode, float f) {
        Theme t = Theme.cur();
        switch (mode) {
            case 1: return 0xFFFFFFFF;
            case 2: return 0xFFFF4D4D;
            case 3: {
                float h = ((System.currentTimeMillis() % 4000L) / 4000f + f) % 1f;
                return 0xFF000000 | MathHelper.hsvToRgb(h, 0.75f, 1f);
            }
            default: return Draw.lerp(t.accent, t.accent2, Math.max(0f, Math.min(1f, f)));
        }
    }

    private static void seg(DrawContext c, MinecraftClient mc, Vec3d a, Vec3d b, int col, int size) {
        int[] p = Proj.project(mc, a), q = Proj.project(mc, b);
        if (p == null || q == null) return;
        Proj.line(c, p[0], p[1], q[0], q[1], col, size);
    }

    private static void ring(DrawContext c, MinecraftClient mc, Vec3d ctr, double r, Col col, int segs, int size) {
        Vec3d prev = null;
        for (int i = 0; i <= segs; i++) {
            double a = 2 * Math.PI * i / segs;
            Vec3d v = new Vec3d(ctr.x + Math.cos(a) * r, ctr.y, ctr.z + Math.sin(a) * r);
            if (prev != null) seg(c, mc, prev, v, col.at(i / (float) segs), size);
            prev = v;
        }
    }

    /** Local point relative to a head: lx = right, lz = forward, y = absolute world height. */
    private static Vec3d pt(Vec3d pos, double yaw, double lx, double y, double lz) {
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw), fx = -Math.sin(yaw), fz = Math.cos(yaw);
        return new Vec3d(pos.x + lx * rx + lz * fx, y, pos.z + lx * rz + lz * fz);
    }

    private static void box(DrawContext c, MinecraftClient mc, Vec3d pos, double yaw,
                            double x0, double x1, double y0, double y1, double z0, double z1, int col) {
        Vec3d[] v = new Vec3d[8];
        for (int i = 0; i < 8; i++) {
            v[i] = pt(pos, yaw, (i & 1) == 0 ? x0 : x1, (i & 2) == 0 ? y0 : y1, (i & 4) == 0 ? z0 : z1);
        }
        for (int i = 0; i < 8; i++) {
            for (int j = i + 1; j < 8; j++) {
                if (Integer.bitCount(i ^ j) == 1) seg(c, mc, v[i], v[j], col, 1);
            }
        }
    }

    // ---------------- main render ----------------
    public static void render(DrawContext c, MinecraftClient mc, float td) {
        long now = System.currentTimeMillis();
        if (Modules.JUMPFX.enabled) jumps(c, mc, now);
        if (Modules.HITFX.enabled) hit(c, mc, td, now);
    }

    // ---------------- jump circles ----------------
    private static void jumps(DrawContext c, MinecraftClient mc, long now) {
        if (JUMPS.isEmpty()) return;
        Module m = Modules.JUMPFX;
        double max = m.get("size").d();
        double dur = m.get("time").d();
        int mode = m.get("color").i();
        for (int i = JUMPS.size() - 1; i >= 0; i--) {
            double[] j = JUMPS.get(i);
            double t = (now - j[3]) / dur;
            if (t >= 1) { JUMPS.remove(i); continue; }
            double e = 1 - (1 - t) * (1 - t);
            double r = 0.2 + max * e;
            int a = (int) (230 * (1 - t));
            Vec3d ctr = new Vec3d(j[0], j[1], j[2]);
            ring(c, mc, ctr, r, f -> Draw.alpha(col(mode, f), a), 44, 2);
            ring(c, mc, ctr, r * 0.82, f -> Draw.alpha(col(mode, f), a / 2), 36, 1);
        }
    }

    // ---------------- hit flash ----------------
    private static void hit(DrawContext c, MinecraftClient mc, float td, long now) {
        Entity e = hitEnt;
        if (e == null) return;
        double dur = Modules.HITFX.get("time").d();
        double age = now - hitAt;
        if (age > dur || e.isRemoved()) { hitEnt = null; return; }
        float f = (float) (1.0 - age / dur);
        Vec3d pos = e.getLerpedPos(td);
        double hw = e.getWidth() / 2.0 + 0.05, h = e.getHeight() + 0.05;
        Vec3d[] v = new Vec3d[8];
        for (int i = 0; i < 8; i++) {
            v[i] = pos.add((i & 1) == 0 ? -hw : hw, (i & 2) == 0 ? 0 : h, (i & 4) == 0 ? -hw : hw);
        }
        int mode = Modules.HITFX.get("color").i();
        for (int i = 0; i < 8; i++) {
            for (int j = i + 1; j < 8; j++) {
                if (Integer.bitCount(i ^ j) != 1) continue;
                seg(c, mc, v[i], v[j], Draw.alpha(col(mode, (i + j) / 14f), (int) (255 * f)), 2);
            }
        }
    }

    // ---------------- crosshair ----------------
    private static void bar(DrawContext c, int x1, int y1, int x2, int y2, int col, boolean outline) {
        if (outline) c.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xB0000000);
        c.fill(x1, y1, x2, y2, col);
    }

    public static void crosshair(DrawContext c, MinecraftClient mc, int sw, int sh) {
        Module m = Modules.CROSSHAIR;
        if (!mc.options.getPerspective().isFirstPerson()) return;
        int cx = sw / 2, cy = sh / 2;
        int style = m.get("style").i(), size = m.get("size").i(), th = m.get("thick").i();
        int gap = m.get("gap").i();
        if (m.get("dyn").b() && mc.player != null) {
            Vec3d v = mc.player.getVelocity();
            gap += (int) Math.min(5, Math.sqrt(v.x * v.x + v.z * v.z) * 25);
        }
        int col = col(m.get("color").i(), 0f);
        boolean ol = m.get("outline").b();
        int half = th / 2;
        if (style == 0 || style == 3) {
            bar(c, cx - gap - size, cy - half, cx - gap, cy - half + th, col, ol);
            bar(c, cx + gap, cy - half, cx + gap + size, cy - half + th, col, ol);
            bar(c, cx - half, cy - gap - size, cx - half + th, cy - gap, col, ol);
            bar(c, cx - half, cy + gap, cx - half + th, cy + gap + size, col, ol);
        }
        if (style == 1 || style == 3) {
            int d = th + 1;
            bar(c, cx - d / 2, cy - d / 2, cx - d / 2 + d, cy - d / 2 + d, col, ol);
        }
        if (style == 2) {
            double r = gap + size / 2.0 + 1;
            int pts = 48;
            if (ol) {
                for (int i = 0; i < pts; i++) {
                    double a = 2 * Math.PI * i / pts;
                    int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
                    c.fill(x - 1, y - 1, x + th + 1, y + th + 1, 0x90000000);
                }
            }
            for (int i = 0; i < pts; i++) {
                double a = 2 * Math.PI * i / pts;
                int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
                c.fill(x, y, x + th, y + th, col);
            }
        }
    }
}

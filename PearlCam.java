package com.ezvisuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pearl Cam: top-right panel with a top-down view of the spot where your ender pearl will land. */
public final class PearlCam {
    private PearlCam() {}

    private static final int GW = 38, GH = 20, CELL = 3;
    private static final int[] MAP = new int[GW * GH];
    private static final List<Vec3d> PATH = new ArrayList<>();
    private static boolean active;
    private static Vec3d land = Vec3d.ZERO, playerPos = Vec3d.ZERO;
    private static float secs;
    private static double dist;
    private static int mapTick;

    public static void tick(MinecraftClient mc) {
        active = false;
        if (!Modules.PEARLCAM.enabled || mc.world == null || mc.player == null) return;
        boolean others = Modules.PEARLCAM.get("others").b();
        EnderPearlEntity best = null;
        double bd = Double.MAX_VALUE;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof EnderPearlEntity pe) || pe.isRemoved()) continue;
            if (!others && pe.getOwner() != mc.player) continue;
            double d = pe.squaredDistanceTo(mc.player);
            if (d < bd) { bd = d; best = pe; }
        }
        if (best == null) return;

        Vec3d pos = best.getPos(), vel = best.getVelocity(), hit = null;
        PATH.clear();
        int t = 0;
        for (; t < 200; t++) {
            Vec3d next = pos.add(vel);
            var r = mc.world.raycast(new RaycastContext(pos, next, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, best));
            if (r.getType() != HitResult.Type.MISS) { hit = r.getPos(); break; }
            pos = next;
            if ((t & 1) == 0) PATH.add(pos);
            vel = vel.multiply(0.99).add(0, -0.03, 0);
            if (pos.y < mc.world.getBottomY() - 10) break;
        }
        land = hit != null ? hit : pos;
        playerPos = mc.player.getPos();
        secs = t / 20f;
        dist = playerPos.distanceTo(land);
        active = true;
        if (mapTick-- <= 0) { mapTick = 4; buildMap(mc); }
    }

    private static int step() { return Modules.PEARLCAM.get("scale").i() + 1; }

    private static void buildMap(MinecraftClient mc) {
        int st = step();
        int fx = (int) Math.floor(land.x), fz = (int) Math.floor(land.z), fy = (int) Math.floor(land.y);
        BlockPos.Mutable bp = new BlockPos.Mutable();
        for (int cz = 0; cz < GH; cz++) {
            for (int cx = 0; cx < GW; cx++) {
                int wx = fx + (cx - GW / 2) * st, wz = fz + (cz - GH / 2) * st;
                int col = 0xFF101018;
                for (int y = fy + 3; y >= fy - 12; y--) {
                    bp.set(wx, y, wz);
                    BlockState s = mc.world.getBlockState(bp);
                    if (s.isAir()) continue;
                    int mc0 = s.getMapColor(mc.world, bp).color;
                    if (mc0 == 0) continue;
                    float f = Math.max(0.55f, Math.min(1.1f, 0.78f + (y - fy) * 0.04f));
                    int r = Math.min(255, (int) (((mc0 >> 16) & 255) * f));
                    int g = Math.min(255, (int) (((mc0 >> 8) & 255) * f));
                    int b = Math.min(255, (int) ((mc0 & 255) * f));
                    col = 0xFF000000 | (r << 16) | (g << 8) | b;
                    break;
                }
                MAP[cz * GW + cx] = col;
            }
        }
    }

    public static int[] draw(DrawContext c, MinecraftClient mc, int x, int y, boolean preview) {
        boolean sample = false;
        if (!active) {
            if (!preview) return new int[]{0, 0};
            sample = true;
            for (int i = 0; i < MAP.length; i++) MAP[i] = ((i % GW + i / GW) & 1) == 0 ? 0xFF3C4A3A : 0xFF344232;
        }
        Theme t = Theme.cur();
        int w = GW * CELL + 8, h = 16 + GH * CELL + 6;
        Draw.rrect(c, x - 1, y - 1, w + 2, h + 2, 7, Draw.alpha(t.accent, 0x30));
        Draw.rrect(c, x, y, w, h, 6, 0xE00C0C12);
        Draw.rrect(c, x + 6, y + 4, 8, 8, 2, t.accent);
        Draw.text(c, "Pearl Cam", x + 18, y + 4, 0xFFFFFFFF);
        String info = sample ? "46M  1.6s"
                : (int) Math.round(dist) + "M  " + String.format(Locale.ROOT, "%.1fs", secs);
        Draw.textR(c, info, x + w - 6, y + 4, 0xFFC8C8DC);

        int mx = x + 4, my = y + 16;
        Draw.rrect(c, mx - 1, my - 1, GW * CELL + 2, GH * CELL + 2, 3, 0xFF000000);
        for (int cz = 0; cz < GH; cz++) {
            for (int cx = 0; cx < GW; cx++) {
                c.fill(mx + cx * CELL, my + cz * CELL, mx + cx * CELL + CELL, my + cz * CELL + CELL, MAP[cz * GW + cx]);
            }
        }
        if (!sample) {
            int st = step();
            double fx = Math.floor(land.x), fz = Math.floor(land.z);
            for (Vec3d p : PATH) {
                int px = (int) ((p.x - fx) / st * CELL) + (GW / 2) * CELL + CELL / 2;
                int pz = (int) ((p.z - fz) / st * CELL) + (GH / 2) * CELL + CELL / 2;
                if (px < 0 || pz < 0 || px >= GW * CELL - 1 || pz >= GH * CELL - 1) continue;
                c.fill(mx + px, my + pz, mx + px + 2, my + pz + 2, Draw.alpha(t.accent, 0xB0));
            }
            int px = (int) ((playerPos.x - fx) / st * CELL) + (GW / 2) * CELL;
            int pz = (int) ((playerPos.z - fz) / st * CELL) + (GH / 2) * CELL;
            if (px >= 0 && pz >= 0 && px < GW * CELL - 3 && pz < GH * CELL - 3) {
                c.fill(mx + px, my + pz, mx + px + 3, my + pz + 3, 0xFFFFFFFF);
            }
        }
        // landing marker in the center
        int lx = mx + (GW / 2) * CELL + CELL / 2, ly = my + (GH / 2) * CELL + CELL / 2;
        c.fill(lx - 5, ly, lx - 2, ly + 1, 0xFFFFFFFF);
        c.fill(lx + 3, ly, lx + 6, ly + 1, 0xFFFFFFFF);
        c.fill(lx, ly - 5, lx + 1, ly - 2, 0xFFFFFFFF);
        c.fill(lx, ly + 3, lx + 1, ly + 6, 0xFFFFFFFF);
        c.fill(lx - 1, ly - 1, lx + 2, ly + 2, t.accent);
        return new int[]{w, h};
    }
}

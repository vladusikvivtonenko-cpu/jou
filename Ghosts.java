package com.ezvisuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Target tracking: Target HUD, ghost on the target while fighting, and the kill effect (ghost flies up into the sky). */
public final class Ghosts {
    private Ghosts() {}

    private static LivingEntity target;
    private static long targetAt;
    private static Vec3d lastPos = Vec3d.ZERO;
    private static float lastH = 1.8f, lastW = 0.6f;
    private static String lastName = "";
    private static LivingEntity hpFor;
    private static float hpAnim = 1f;

    private static final class Soul { double x, y, z; float h; long at; String name; }
    private static final List<Soul> SOULS = new ArrayList<>();

    private static final String[] GHOST = {
            "....XXXX....",
            "..XXXXXXXX..",
            ".XXXXXXXXXX.",
            ".XXEEXXEEXX.",
            ".XXEEXXEEXX.",
            ".XXXXXXXXXX.",
            ".XXXXEEXXXX.",
            ".XXXXXXXXXX.",
            ".XXXXXXXXXX.",
            ".XXXXXXXXXX.",
            ".XXXXXXXXXX.",
            ".XXXXXXXXXX.",
            ".XXX.XX.XXX.",
            ".X...XX...X."
    };

    private static final String[] HEART = {
            ".XX.XX.",
            "XXXXXXX",
            "XXXXXXX",
            ".XXXXX.",
            "..XXX..",
            "...X..."
    };

    private static void heart(DrawContext c, float cx, float cy, float unit, int col, int a) {
        if (a < 8 || unit <= 0f) return;
        MatrixStack ms = c.getMatrices();
        ms.push();
        ms.translate(cx, cy, 0f);
        ms.scale(unit, unit, 1f);
        int w = HEART[0].length(), h = HEART.length;
        for (int r = 0; r < h; r++) {
            String row = HEART[r];
            int start = -1;
            for (int i = 0; i <= w; i++) {
                boolean hit = i < w && row.charAt(i) == 'X';
                if (hit && start < 0) start = i;
                if (!hit && start >= 0) {
                    c.fill(start - 3, r - 3, i - 3, r - 2, Draw.alpha(col, a));
                    start = -1;
                }
            }
        }
        c.fill(-2, -3, -1, -2, Draw.alpha(0xFFFFFFFF, a / 2));   // small shine
        ms.pop();
    }

    private static boolean anyOn() {
        return Modules.TARGETHUD.enabled || Modules.TARGETFX.enabled || Modules.KILLFX.enabled;
    }

    private static void remember(LivingEntity e) {
        lastPos = e.getPos();
        lastH = e.getHeight();
        lastW = e.getWidth();
        lastName = e.getName().getString();
    }

    // ---------------- events ----------------
    public static void onHit(Entity e) {
        if (!anyOn() || !(e instanceof LivingEntity le)) return;
        target = le;
        targetAt = System.currentTimeMillis();
        remember(le);
    }

    public static void tick(MinecraftClient mc) {
        LivingEntity t = target;
        if (t == null) return;
        if (mc.world == null || mc.player == null) { target = null; return; }
        long now = System.currentTimeMillis();
        boolean dead = t.isDead() || t.getHealth() <= 0f
                || (t.isRemoved() && t.getRemovalReason() == Entity.RemovalReason.KILLED);
        if (dead) {
            if (Modules.KILLFX.enabled && now - targetAt < 10000) {
                Soul s = new Soul();
                s.x = lastPos.x; s.y = lastPos.y; s.z = lastPos.z;
                s.h = lastH; s.at = now; s.name = lastName;
                SOULS.add(s);
                while (SOULS.size() > 6) SOULS.remove(0);
            }
            target = null;
            return;
        }
        if (t.isRemoved()) { target = null; return; }
        remember(t);
        if (now - targetAt > 15000) target = null;
    }

    /** The enemy we hit recently (null when none or too old). fade out is written to FADE[0]. */
    private static final float[] FADE = {1f};

    private static LivingEntity active(double lingerSec) {
        LivingEntity t = target;
        if (t == null || t.isRemoved() || t.isDead()) return null;
        double age = System.currentTimeMillis() - targetAt;
        double life = lingerSec * 1000.0;
        if (age > life) return null;
        FADE[0] = (float) Math.max(0, Math.min(1, (life - age) / 400.0));
        return t;
    }

    // ---------------- colors ----------------
    private static int gcol(Module m, float f) {
        int mode = m.get("color").i();
        if (mode == 5) return 0xFFFFC21A;
        if (mode == 4) {
            int r = (int) (m.get("r").d() * 2.55), g = (int) (m.get("g").d() * 2.55), b = (int) (m.get("b").d() * 2.55);
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        return Vfx.col(mode, f);
    }

    /** Pixels per block at the given world point, or -1 when it is behind the camera. */
    private static float ppb(MinecraftClient mc, Vec3d p) {
        int[] a = Proj.project(mc, p.add(0, 0.5, 0)), b = Proj.project(mc, p.add(0, -0.5, 0));
        if (a == null || b == null) return -1f;
        return Math.abs(b[1] - a[1]);
    }

    // ---------------- sprites ----------------
    private static void runs(DrawContext c, char only, boolean both, int col) {
        int w = GHOST[0].length(), h = GHOST.length;
        for (int r = 0; r < h; r++) {
            String row = GHOST[r];
            int start = -1;
            for (int i = 0; i <= w; i++) {
                char ch = i < w ? row.charAt(i) : '.';
                boolean hit = both ? ch != '.' : ch == only;
                if (hit && start < 0) start = i;
                if (!hit && start >= 0) {
                    c.fill(start - w / 2, r - h / 2, i - w / 2, r - h / 2 + 1, col);
                    start = -1;
                }
            }
        }
    }

    private static void ghost(DrawContext c, float cx, float cy, float unit, int col, int a) {
        if (a < 8) return;
        MatrixStack ms = c.getMatrices();
        ms.push();
        ms.translate(cx, cy, 0f);
        ms.scale(unit, unit, 1f);
        ms.push();
        ms.scale(1.2f, 1.2f, 1f);
        runs(c, 'X', true, Draw.alpha(col, a / 4));
        ms.pop();
        runs(c, 'X', true, Draw.alpha(col, a));
        runs(c, 'E', false, Draw.alpha(0xFF0E0E18, Math.min(255, a + 40)));
        ms.pop();
    }

    private static void silhouette(DrawContext c, float cx, float cy, float unit, int col, int a) {
        if (a < 8) return;
        MatrixStack ms = c.getMatrices();
        ms.push();
        ms.translate(cx, cy, 0f);
        ms.scale(unit, unit, 1f);
        int[][] parts = {{-4, -16, 4, -8}, {-4, -8, 4, 4}, {-8, -8, -4, 4}, {4, -8, 8, 4}, {-4, 4, 0, 16}, {0, 4, 4, 16}};
        for (int[] p : parts) {
            for (int yy = p[1]; yy < p[3]; yy += 2) {
                float k = 0.35f + 0.65f * (1f - (yy + 16) / 32f);
                c.fill(p[0], yy, p[2], Math.min(p[3], yy + 2), Draw.alpha(col, (int) (a * k)));
            }
        }
        c.fill(-3, -14, -1, -12, Draw.alpha(0xFF0E0E18, Math.min(255, a + 40)));
        c.fill(1, -14, 3, -12, Draw.alpha(0xFF0E0E18, Math.min(255, a + 40)));
        ms.pop();
    }

    // ---------------- ghost on the target ----------------
    public static void renderTarget(DrawContext c, MinecraftClient mc, float td) {
        Module m = Modules.TARGETFX;
        if (!m.enabled) return;
        LivingEntity e = active(m.get("time").d());
        if (e == null || e == mc.player) return;
        float f = FADE[0];
        float size = m.get("size").f();
        int alpha = (int) (255 * m.get("alpha").d() / 100.0 * f);
        long now = System.currentTimeMillis();
        double t = (now % 100000L) / 1000.0;
        Vec3d pos = e.getLerpedPos(td);
        float h = e.getHeight(), w = e.getWidth();

        if (m.get("style").i() == 0) {
            Vec3d ctr = pos.add(0, h * 0.55, 0);
            int[] s = Proj.project(mc, ctr);
            float pb = ppb(mc, ctr);
            if (s == null || pb < 0) return;
            float unit = pb * h * size / 12f;
            float bob = (float) Math.sin(t * 3.0) * unit * 0.8f;
            float sway = (float) Math.sin(t * 1.7) * unit * 0.6f;
            ghost(c, s[0] + sway, s[1] + bob, unit, gcol(m, 0.3f), alpha);
        } else {
            // hearts: float up around the target in a slow spiral and fade out at the top
            int n = 8;
            for (int k = 0; k < n; k++) {
                double life = (t * 0.45 + k / (double) n) % 1.0;
                double ang = t * 1.8 + k * (Math.PI * 2 / n);
                double r = w * 0.8 + 0.35;
                Vec3d p = pos.add(Math.cos(ang) * r, 0.1 + life * h * 1.15, Math.sin(ang) * r);
                int[] s = Proj.project(mc, p);
                float pb = ppb(mc, p);
                if (s == null || pb < 0) continue;
                double fade = Math.sin(Math.PI * life);
                int a = (int) (alpha * Math.min(1.0, fade * 1.6));
                if (a < 8) continue;
                float unit = Math.max(1f, pb * 0.3f * size / 7f);
                int col = m.get("color").i() == 0 ? 0xFFFF4D8D : gcol(m, k / (float) n);
                heart(c, s[0], s[1], unit, col, a);
            }
        }
    }

    // ---------------- kill effect ----------------
    public static void renderSouls(DrawContext c, MinecraftClient mc) {
        if (SOULS.isEmpty()) return;
        Module m = Modules.KILLFX;
        long now = System.currentTimeMillis();
        double dur = m.get("time").d();
        double rise = m.get("height").d();
        float size = m.get("size").f();
        boolean sil = m.get("style").i() == 1;
        for (int i = SOULS.size() - 1; i >= 0; i--) {
            Soul s = SOULS.get(i);
            double t = (now - s.at) / dur;
            if (t >= 1) { SOULS.remove(i); continue; }
            if (!m.enabled) continue;
            int style = m.get("style").i();
            if (style >= 2) { vortex(c, mc, s, t, m, style == 3, size); continue; }
            double up = rise * Math.pow(t, 1.6);
            double dx = Math.sin(t * 9) * 0.3, dz = Math.cos(t * 7) * 0.2;
            Vec3d ctr = new Vec3d(s.x + dx, s.y + s.h * 0.5 + up, s.z + dz);
            int[] p = Proj.project(mc, ctr);
            float pb = ppb(mc, ctr);
            if (p == null || pb < 0) continue;
            float fade = (float) Math.pow(1.0 - t, 1.2);
            int a = (int) (255 * m.get("alpha").d() / 100.0 * fade);
            float sc = (float) (1.0 - 0.15 * t) * size;
            int col = gcol(m, (float) t);
            if (sil) silhouette(c, p[0], p[1], pb * s.h / 32f * sc, col, a);
            else ghost(c, p[0], p[1], pb * s.h / 12f * sc, col, a);

            if (m.get("sparks").b()) {
                for (int k = 0; k < 10; k++) {
                    double fr = (k * 0.137 + t * 2.0) % 1.0;
                    Vec3d sp = ctr.add(Math.sin(k * 12.9 + s.at) * 0.35, -0.4 - fr * 1.5, Math.cos(k * 7.7 + s.at) * 0.35);
                    int[] q = Proj.project(mc, sp);
                    if (q == null) continue;
                    int qa = (int) (a * (1 - fr));
                    if (qa < 8) continue;
                    int z = Math.max(1, (int) (pb * 0.03f));
                    c.fill(q[0], q[1], q[0] + z, q[1] + z, Draw.alpha(col, qa));
                }
            }
            if (m.get("name").b() && a >= 40) {
                Draw.textC(c, s.name, p[0], (int) (p[1] - pb * s.h * 0.6f * sc - 10), Draw.alpha(0xFFFFFFFF, a));
            }
        }
    }

    /** Spinning spindle-shaped vortex around the spot where the enemy died. Evil = dark red / purple, collapsing down. */
    private static void vortex(DrawContext c, MinecraftClient mc, Soul s, double t, Module m, boolean evil, float size) {
        double fade = Math.pow(1.0 - t, 1.1);
        int base = (int) (255 * m.get("alpha").d() / 100.0 * fade);
        if (base < 8) return;
        double spin = (System.currentTimeMillis() % 100000L) / 1000.0 * (evil ? -9.0 : 11.0);
        double lift = evil ? 0.0 : Math.min(m.get("height").d(), 6.0) * Math.pow(t, 1.4) * 0.4;
        double sink = evil ? t * 0.8 : 0.0;
        int colMode = m.get("color").i();
        int n = 48;
        for (int strand = 0; strand < 2; strand++) {
            for (int k = 0; k < n; k++) {
                double f = k / (double) (n - 1);
                double ang = spin + f * 13.0 + strand * Math.PI;
                double rad = (evil ? 0.95 * (1.0 - f) + 0.15 : 0.18 + 0.8 * Math.sin(Math.PI * f)) * size;
                if (evil) rad *= 1.0 - 0.5 * t;
                double y = f * 2.3 * size + lift - sink;
                Vec3d p = new Vec3d(s.x + Math.cos(ang) * rad, s.y + y, s.z + Math.sin(ang) * rad);
                int[] q = Proj.project(mc, p);
                float pb = ppb(mc, p);
                if (q == null || pb < 0) continue;
                int col;
                if (evil) col = (k % 2 == 0) ? 0xFFB0102A : 0xFF7B2CBF;
                else col = colMode == 0 ? 0xFFFFC21A : gcol(m, (float) f);
                int a = (int) (base * (0.35 + 0.65 * Math.sin(Math.PI * f)));
                if (a < 8) continue;
                int z = Math.max(1, (int) (pb * 0.045f * size));
                if (evil) c.fill(q[0] - z - 1, q[1] - z - 1, q[0] + z + 2, q[1] + z + 2, Draw.alpha(0xFF000000, a / 2));
                c.fill(q[0] - z * 2, q[1] - z * 2, q[0] + z * 2, q[1] + z * 2, Draw.alpha(col, a / 5));
                c.fill(q[0] - z, q[1] - z, q[0] + z, q[1] + z, Draw.alpha(col, a));
            }
        }
    }

    // ---------------- target hud ----------------
    public static int[] targetHud(DrawContext c, MinecraftClient mc, int x, int y, float td, boolean preview) {
        Module m = Modules.TARGETHUD;
        LivingEntity e = active(m.get("linger").d());
        String name;
        float hp, max;
        int arm;
        double dist;
        float f;
        if (e == null) {
            if (!preview) return new int[]{0, 0};
            name = "Player"; hp = 14f; max = 20f; arm = 71; dist = 3.2; f = 1f;
            hpAnim = 0.7f;
        } else {
            f = FADE[0];
            name = e.getName().getString();
            hp = e.getHealth() + e.getAbsorptionAmount();
            max = Math.max(1f, e.getMaxHealth());
            arm = e instanceof net.minecraft.entity.player.PlayerEntity pl ? U.armorPct(pl) : -1;
            dist = mc.player.distanceTo(e);
            if (hpFor != e) { hpFor = e; hpAnim = Math.min(1f, hp / max); }
            hpAnim += (Math.min(1f, hp / max) - hpAnim) * 0.15f;
        }
        int a = (int) (255 * f);
        if (a < 8) return new int[]{0, 0};
        Theme t = Theme.cur();
        int w = 128, h = 36;
        Draw.rrect(c, x - 1, y - 1, w + 2, h + 2, 7, Draw.alpha(t.accent, (int) (0x30 * f)));
        Draw.rrect(c, x, y, w, h, 6, Draw.alpha(0x0C0C12, (int) (0xD0 * f)));
        Draw.text(c, name, x + 8, y + 6, Draw.alpha(0xFFFFFFFF, a));
        String hs = String.format(Locale.ROOT, "%.1f", hp);
        int hc = hp > 14 ? 0xFF5CFF7A : hp > 7 ? 0xFFFFD34D : 0xFFFF5555;
        Draw.textR(c, hs, x + w - 8, y + 6, Draw.alpha(hc, a));
        int bx = x + 8, bw = w - 16, by = y + 18;
        c.fill(bx, by, bx + bw, by + 5, Draw.alpha(0xFF222230, a));
        int fw = (int) (bw * Math.max(0f, Math.min(1f, hpAnim)));
        if (fw > 0) Draw.hgrad(c, bx, by, fw, 5, Draw.alpha(t.accent, a), Draw.alpha(t.accent2, a));
        String info = (m.get("armor").b() && arm >= 0 ? "Armor " + arm + "%" : "")
                + (m.get("dist").b() ? (m.get("armor").b() && arm >= 0 ? "   " : "") + String.format(Locale.ROOT, "%.1fm", dist) : "");
        if (!info.isEmpty()) Draw.scaled(c, info, x + 8, y + 26, 0.8f, Draw.alpha(0xFF9A9AB0, a), false);
        return new int[]{w, h};
    }
}

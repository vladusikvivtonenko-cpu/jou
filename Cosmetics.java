package com.ezvisuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** Cosmetics: a cut-out character (no background) that stands next to you, behind you, above your head or sits in the HUD. */
public final class Cosmetics {
    private Cosmetics() {}

    private static final String[] FILES = {"bacon", "melsrou", "mellblackfreind"};
    private static final int[][] DIM = {{262, 360}, {128, 360}, {249, 360}};
    private static final Identifier[] IDS = new Identifier[FILES.length];

    static {
        for (int i = 0; i < FILES.length; i++) IDS[i] = Identifier.of("ezvisuals", "textures/cosmetic/" + FILES[i] + ".png");
    }

    private static void blit(DrawContext c, int skin, int x, int y, int w, int h) {
        if (w < 2 || h < 2) return;
        c.drawTexture(RenderLayer::getGuiTextured, IDS[skin], x, y, 0f, 0f, w, h, DIM[skin][0], DIM[skin][1], DIM[skin][0], DIM[skin][1]);
    }

    /** Placement in the world (beside / behind / above). */
    public static void render(DrawContext c, MinecraftClient mc, float td) {
        Module m = Modules.COSMETICS;
        if (!m.enabled || mc.player == null) return;
        int place = m.get("place").i();
        if (place == 3) return;
        int skin = Math.max(0, Math.min(FILES.length - 1, m.get("skin").i()));
        float size = m.get("size").f();
        ClientPlayerEntity p = mc.player;
        Vec3d pos = p.getLerpedPos(td);
        double yaw = Math.toRadians(p.getYaw());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double side = m.get("left").b() ? -1 : 1;
        double bob = m.get("bob").b() ? Math.sin((System.currentTimeMillis() % 100000L) / 1000.0 * 2.0) * 0.07 : 0;
        Vec3d base;
        double hBlocks;
        if (place == 0) {
            base = pos.add(rx * 1.0 * side + fx * 0.2, bob, rz * 1.0 * side + fz * 0.2);
            hBlocks = 1.8 * size;
        } else if (place == 1) {
            base = pos.add(-fx * 1.2, bob, -fz * 1.2);
            hBlocks = 1.8 * size;
        } else {
            base = pos.add(0, p.getHeight() + 0.2 + bob, 0);
            hBlocks = 0.9 * size;
        }
        int[] a = Proj.project(mc, base), b = Proj.project(mc, base.add(0, hBlocks, 0));
        if (a == null || b == null) return;
        int hpx = a[1] - b[1];
        if (hpx < 6 || hpx > 1600) return;
        int wpx = Math.round(hpx * DIM[skin][0] / (float) DIM[skin][1]);
        blit(c, skin, a[0] - wpx / 2, b[1], wpx, hpx);
    }

    /** HUD block (placement "HUD (movable)"). */
    public static int[] hud(DrawContext c, int x, int y) {
        Module m = Modules.COSMETICS;
        int skin = Math.max(0, Math.min(FILES.length - 1, m.get("skin").i()));
        int h = Math.max(20, Math.round(100 * m.get("size").f()));
        int w = Math.round(h * DIM[skin][0] / (float) DIM[skin][1]);
        blit(c, skin, x, y, w, h);
        return new int[]{w, h};
    }
}

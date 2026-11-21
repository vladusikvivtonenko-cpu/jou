package com.ezvisuals;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Mandatory key entry screen: shown on the main menu until a valid key is entered. */
public final class LicenseScreen extends Screen {
    private final StringBuilder key = new StringBuilder();
    private String status = "";
    private int statusCol = 0xFFFF5566;
    private int bx, by, bw = 300, bh = 170;

    public LicenseScreen() {
        super(Text.literal("EzVisuals"));
        String m = License.message();
        if (!m.startsWith("Enter")) status = m;
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        Theme t = Theme.cur();
        c.fillGradient(0, 0, width, height, 0xFF07070D, 0xFF17112E);
        bx = (width - bw) / 2;
        by = (height - bh) / 2;
        for (int i = 3; i >= 1; i--) {
            Draw.rrect(c, bx - i * 5, by - i * 5 + 4, bw + i * 10, bh + i * 10, 14 + i * 4, Draw.alpha(t.accent, 0x0C));
        }
        Draw.rrect(c, bx - 1, by - 1, bw + 2, bh + 2, 13, Draw.alpha(t.accent, 0x70));
        Draw.rrect(c, bx, by, bw, bh, 12, t.bg);

        String title = "EzVisuals";
        Draw.scaled(c, title, bx + (bw - Draw.w(title) * 2f) / 2f, by + 12, 2f, 0xFFFFFFFF, true);
        Draw.textC(c, "Enter your license key", bx + bw / 2, by + 40, Draw.SUB);

        int fx = bx + 16, fy = by + 56, fw = bw - 32;
        Draw.rrect(c, fx - 1, fy - 1, fw + 2, 22, 7, Draw.alpha(t.accent, 0x50));
        Draw.rrect(c, fx, fy, fw, 20, 6, 0xFF1B1B26);
        String shown;
        int col = 0xFFEDEDF5;
        if (key.length() == 0) { shown = "EZV-XXXXX-XXXXX-XXXXX-XXXXX"; col = Draw.SUB; }
        else shown = key + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        Draw.text(c, shown, fx + 8, fy + 6, col);

        int by2 = fy + 32;
        boolean h1 = over(mx, my, fx, by2, 150, 20), h2 = over(mx, my, bx + bw - 16 - 100, by2, 100, 20);
        Draw.rrect(c, fx, by2, 150, 20, 6, h1 ? Draw.lerp(t.accent, 0xFFFFFFFF, 0.15f) : t.accent);
        Draw.textC(c, "Activate", fx + 75, by2 + 6, 0xFFFFFFFF);
        Draw.rrect(c, bx + bw - 16 - 100, by2, 100, 20, 6, h2 ? 0xFF2E2E3C : 0xFF23232F);
        Draw.textC(c, "Quit game", bx + bw - 16 - 50, by2 + 6, Draw.SUB);

        if (!status.isEmpty()) Draw.textC(c, status, bx + bw / 2, by + bh - 46, statusCol);
        Draw.textC(c, License.CONTACT, bx + bw / 2, by + bh - 24, Draw.SUB);
        Draw.textC(c, "Ctrl+V pastes the key", bx + bw / 2, by + bh - 12, 0xFF55556A);
    }

    private static boolean over(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void submit() {
        String err = License.activate(key.toString());
        if (err == null) {
            if (client != null) client.setScreen(client.world == null ? new TitleScreen() : null);
        } else {
            status = err;
            statusCol = 0xFFFF5566;
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int fx = bx + 16, by2 = by + 56 + 32;
        if (over((int) mx, (int) my, fx, by2, 150, 20)) { submit(); return true; }
        if (over((int) mx, (int) my, bx + bw - 16 - 100, by2, 100, 20)) {
            if (client != null) client.scheduleStop();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scan, int mods) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) { submit(); return true; }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (key.length() > 0) key.setLength(key.length() - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) return true; // cannot be skipped
        if (keyCode == GLFW.GLFW_KEY_V && Screen.hasControlDown() && client != null) {
            String clip = client.keyboard.getClipboard();
            if (clip != null) {
                for (char ch : clip.trim().toCharArray()) {
                    if (key.length() >= 32) break;
                    if (Character.isLetterOrDigit(ch) || ch == '-') key.append(Character.toUpperCase(ch));
                }
            }
            return true;
        }
        return super.keyPressed(keyCode, scan, mods);
    }

    @Override
    public boolean charTyped(char chr, int mods) {
        if ((Character.isLetterOrDigit(chr) || chr == '-') && key.length() < 32) {
            key.append(Character.toUpperCase(chr));
            return true;
        }
        return super.charTyped(chr, mods);
    }
}

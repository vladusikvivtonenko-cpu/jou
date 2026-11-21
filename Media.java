package com.ezvisuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;

/**
 * Media player widget (Windows): shows the track that plays on your PC (Spotify, browser, YouTube, ...)
 * and controls it through the Windows media session. Buttons work while a screen is open (chat, inventory),
 * or use the keys from Controls.
 */
public final class Media {
    private Media() {}

    private static final boolean WIN = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    private static Process proc;
    private static OutputStream out;
    private static volatile String title = "", artist = "", status = "", app = "";
    private static volatile double pos, end;
    private static volatile long posAt;
    private static volatile boolean has;
    private static long nextTry;

    private static boolean local() { return Modules.MEDIA.get("source").i() == 1; }

    // button rectangles: prev, toggle, next, vol-, vol+  {x, y, w, h}
    private static final int[][] BTN = new int[5][4];
    private static long drawnAt;

    private static final String SCRIPT = """
            [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
            $ErrorActionPreference = 'Stop'
            Add-Type -AssemblyName System.Runtime.WindowsRuntime
            Add-Type -MemberDefinition '[DllImport("user32.dll")] public static extern void keybd_event(byte bVk, byte bScan, uint dwFlags, UIntPtr dwExtraInfo);' -Name K -Namespace W
            $asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
            function Await($op, $type) { $m = $asTaskGeneric.MakeGenericMethod($type); $t = $m.Invoke($null, @($op)); $t.Wait(-1) | Out-Null; $t.Result }
            function B64($s) { if ($s -eq $null) { return '' }; [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes([string]$s)) }
            function Key($vk) { [W.K]::keybd_event($vk, 0, 0, [UIntPtr]::Zero); [W.K]::keybd_event($vk, 0, 2, [UIntPtr]::Zero) }
            [void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
            [void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]
            $mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
            $cmd = [Console]::In.ReadLineAsync()
            while ($true) {
              try {
                $s = $mgr.GetCurrentSession()
                if ($cmd.IsCompleted) {
                  $line = $cmd.Result
                  if ($line -eq $null) { break }
                  $cmd = [Console]::In.ReadLineAsync()
                  switch ($line.Trim()) {
                    'toggle' { if ($s -ne $null) { [void](Await ($s.TryTogglePlayPauseAsync()) ([bool])) } }
                    'next'   { if ($s -ne $null) { [void](Await ($s.TrySkipNextAsync()) ([bool])) } }
                    'prev'   { if ($s -ne $null) { [void](Await ($s.TrySkipPreviousAsync()) ([bool])) } }
                    'volup'  { Key 0xAF }
                    'voldown'{ Key 0xAE }
                  }
                }
                if ($s -eq $null) { [Console]::Out.WriteLine('NONE') }
                else {
                  $p = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
                  $tl = $s.GetTimelineProperties()
                  $st = [string]$s.GetPlaybackInfo().PlaybackStatus
                  $len = ($tl.EndTime - $tl.StartTime).TotalSeconds
                  $ps = ($tl.Position - $tl.StartTime).TotalSeconds
                  [Console]::Out.WriteLine('INFO|' + (B64 $p.Title) + '|' + (B64 $p.Artist) + '|' + $ps.ToString([Globalization.CultureInfo]::InvariantCulture) + '|' + $len.ToString([Globalization.CultureInfo]::InvariantCulture) + '|' + $st + '|' + (B64 $s.SourceAppUserModelId))
                }
                [Console]::Out.Flush()
              } catch { [Console]::Out.WriteLine('NONE'); [Console]::Out.Flush() }
              Start-Sleep -Milliseconds 400
            }
            """;

    // ---------------- process ----------------
    private static void start() {
        try {
            Path f = Config.dir().resolve("media.ps1");
            Files.writeString(f, "\uFEFF" + SCRIPT, StandardCharsets.UTF_8);
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-File", f.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            proc = p;
            out = p.getOutputStream();
            Thread t = new Thread(() -> read(p), "ezvisuals-media");
            t.setDaemon(true);
            t.start();
        } catch (Exception e) {
            proc = null;
            nextTry = System.currentTimeMillis() + 10000;
        }
    }

    private static void read(Process p) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("NONE")) { has = false; continue; }
                if (!line.startsWith("INFO|")) continue;
                String[] a = line.split("\\|", -1);
                if (a.length < 7) continue;
                title = dec(a[1]); artist = dec(a[2]);
                pos = num(a[3]); end = num(a[4]);
                status = a[5]; app = dec(a[6]);
                posAt = System.currentTimeMillis();
                has = true;
            }
        } catch (IOException ignored) {
        }
        has = false;
    }

    private static String dec(String s) {
        try { return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8); } catch (Exception e) { return ""; }
    }

    private static double num(String s) {
        try { double d = Double.parseDouble(s); return Double.isFinite(d) ? Math.max(0, d) : 0; } catch (Exception e) { return 0; }
    }

    private static void stopProc() {
        Process p = proc;
        proc = null;
        out = null;
        has = false;
        if (p != null) p.destroy();
    }

    public static void stop() {
        stopProc();
        Music.stop();
    }

    private static void send(String cmd) {
        if (local()) {
            switch (cmd) {
                case "toggle" -> Music.toggle();
                case "next" -> Music.next();
                case "prev" -> Music.prev();
                case "volup" -> Music.changeVolume(0.1f);
                case "voldown" -> Music.changeVolume(-0.1f);
                default -> { }
            }
            return;
        }
        OutputStream o = out;
        if (o == null) return;
        try {
            o.write((cmd + "\n").getBytes(StandardCharsets.UTF_8));
            o.flush();
        } catch (IOException ignored) {
        }
        if (cmd.equals("toggle")) {
            status = "Playing".equals(status) ? "Paused" : "Playing";
            pos = shownPos();
            posAt = System.currentTimeMillis();
        }
    }

    // ---------------- tick / keys ----------------
    public static void tick(MinecraftClient mc) {
        boolean on = Modules.MEDIA.enabled;
        if (!on || local() || !WIN) {
            if (proc != null) stopProc();
        } else if ((proc == null || !proc.isAlive()) && System.currentTimeMillis() >= nextTry) {
            if (proc != null) nextTry = System.currentTimeMillis() + 5000;
            else start();
        }
        if (!on) return;
        if (EzVisualsPremium.mediaPrev == null) return;
        while (EzVisualsPremium.mediaPrev.wasPressed()) send("prev");
        while (EzVisualsPremium.mediaToggle.wasPressed()) send("toggle");
        while (EzVisualsPremium.mediaNext.wasPressed()) send("next");
    }

    // ---------------- click ----------------
    /** Returns true when the click hit a media button (the click is then consumed). */
    public static boolean click(double mx, double my) {
        if (!Modules.MEDIA.enabled || System.currentTimeMillis() - drawnAt > 300) return false;
        int n = Modules.MEDIA.get("vol").b() ? 5 : 3;
        for (int i = 0; i < n; i++) {
            int[] b = BTN[i];
            if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) {
                send(i == 0 ? "prev" : i == 1 ? "toggle" : i == 2 ? "next" : i == 3 ? "voldown" : "volup");
                return true;
            }
        }
        return false;
    }

    // ---------------- draw ----------------
    private static double shownPos() {
        double p = pos;
        if ("Playing".equals(status)) p += (System.currentTimeMillis() - posAt) / 1000.0;
        return end > 0 ? Math.min(p, end) : p;
    }

    private static String fit(String s, int maxW) {
        if (Draw.w(s) <= maxW) return s;
        while (s.length() > 1 && Draw.w(s + "...") > maxW) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    private static String fmt(double sec) {
        int s = (int) sec;
        return (s / 60) + ":" + String.format(Locale.ROOT, "%02d", s % 60);
    }

    private static void button(DrawContext c, int i, int x, int y, int w, int h, boolean hover, int bg) {
        BTN[i][0] = x; BTN[i][1] = y; BTN[i][2] = w; BTN[i][3] = h;
        Draw.rrect(c, x, y, w, h, 4, hover ? Draw.lerp(bg, 0xFFFFFFFF, 0.18f) : bg);
    }

    public static int[] draw(DrawContext c, MinecraftClient mc, int x, int y, boolean preview) {
        boolean loc = local();
        if (!loc && !WIN) {
            if (!preview) return new int[]{0, 0};
        }
        boolean live = loc ? Music.active() : (WIN && has);
        if (!live && !preview && !Modules.MEDIA.get("idle").b()) return new int[]{0, 0};
        drawnAt = System.currentTimeMillis();
        Theme t = Theme.cur();
        int w = 150, h = 70;
        boolean vol = Modules.MEDIA.get("vol").b();
        Draw.rrect(c, x - 1, y - 1, w + 2, h + 2, 7, Draw.alpha(t.accent, 0x30));
        Draw.rrect(c, x, y, w, h, 6, 0xE00C0C12);

        String ti, ar, st = status;
        double p, e;
        if (live && loc) { ti = Music.title(); ar = Music.artist(); p = Music.posSec(); e = Music.endSec(); st = Music.paused() ? "Paused" : "Playing"; }
        else if (live) { ti = title.isEmpty() ? "Unknown track" : title; ar = artist.isEmpty() ? app : artist; p = shownPos(); e = end; }
        else if (preview) { ti = "Group of blood"; ar = "Kino"; p = 55; e = 287; st = "Playing"; }
        else if (loc) { ti = Music.error().isEmpty() ? "No track playing" : Music.error(); ar = "Put mp3 / wav in ezvisuals/music, press play"; p = 0; e = 0; st = ""; }
        else { ti = WIN ? "No media playing" : "Windows only"; ar = WIN ? "Start music or a video on your PC" : ""; p = 0; e = 0; st = ""; }

        Draw.text(c, fit(ti, w - 16), x + 8, y + 6, live || preview ? 0xFFFFFFFF : Draw.SUB);
        Draw.text(c, fit(ar, w - 16), x + 8, y + 17, Draw.SUB);

        int bx = x + 8, bw = w - 16, by = y + 30;
        c.fill(bx, by, bx + bw, by + 3, 0xFF2A2A38);
        if (e > 0) {
            int fw = (int) (bw * Math.min(1.0, p / e));
            if (fw > 0) Draw.hgrad(c, bx, by, fw, 3, t.accent, t.accent2);
            c.fill(bx + fw - 1, by - 1, bx + fw + 2, by + 4, 0xFFFFFFFF);
        }
        Draw.scaled(c, fmt(p), bx, y + 36, 0.75f, Draw.SUB, false);
        String et = e > 0 ? fmt(e) : "--:--";
        Draw.scaled(c, et, bx + bw - Draw.w(et) * 0.75f, y + 36, 0.75f, Draw.SUB, false);

        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        double mx = -1, my = -1;
        if (mc.currentScreen != null) {
            mx = mc.mouse.getX() * sw / (double) mc.getWindow().getWidth();
            my = mc.mouse.getY() * sh / (double) mc.getWindow().getHeight();
        }
        int ry = y + 48, bh = 15, cx = x + (vol ? 42 : w / 2);
        boolean[] hv = new boolean[5];
        int[][] pos0 = {{cx - 34, ry, 20, bh}, {cx - 10, ry, 20, bh}, {cx + 14, ry, 20, bh},
                {x + w - 38, ry, 14, bh}, {x + w - 22, ry, 14, bh}};
        for (int i = 0; i < 5; i++) {
            int[] r = pos0[i];
            hv[i] = mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
        }
        int dark = 0xFF1E202C;
        button(c, 0, pos0[0][0], ry, 20, bh, hv[0], dark);
        button(c, 1, pos0[1][0], ry, 20, bh, hv[1], t.accent);
        button(c, 2, pos0[2][0], ry, 20, bh, hv[2], dark);
        // prev icon: bar + left triangle
        int ix = pos0[0][0] + 6, iy = ry + 4;
        c.fill(ix, iy, ix + 1, iy + 7, 0xFFFFFFFF);
        for (int i = 0; i < 7; i++) { int tw = i < 4 ? i + 1 : 7 - i; c.fill(ix + 7 - tw, iy + i, ix + 8, iy + i + 1, 0xFFFFFFFF); }
        // next icon: right triangle + bar
        ix = pos0[2][0] + 6;
        for (int i = 0; i < 7; i++) { int tw = i < 4 ? i + 1 : 7 - i; c.fill(ix + 1, iy + i, ix + 1 + tw, iy + i + 1, 0xFFFFFFFF); }
        c.fill(ix + 8, iy, ix + 9, iy + 7, 0xFFFFFFFF);
        // play / pause
        ix = pos0[1][0] + 7;
        if ("Playing".equals(st)) {
            c.fill(ix + 1, iy, ix + 3, iy + 7, 0xFFFFFFFF);
            c.fill(ix + 5, iy, ix + 7, iy + 7, 0xFFFFFFFF);
        } else {
            for (int i = 0; i < 7; i++) { int tw = i < 4 ? i + 1 : 7 - i; c.fill(ix + 1, iy + i, ix + 1 + tw * 2, iy + i + 1, 0xFFFFFFFF); }
        }
        if (vol) {
            Draw.text(c, "Vol", x + 8, ry + 4, Draw.SUB);
            button(c, 3, pos0[3][0], ry, 14, bh, hv[3], dark);
            button(c, 4, pos0[4][0], ry, 14, bh, hv[4], dark);
            c.fill(pos0[3][0] + 4, ry + 7, pos0[3][0] + 10, ry + 8, 0xFFFFFFFF);
            c.fill(pos0[4][0] + 4, ry + 7, pos0[4][0] + 10, ry + 8, 0xFFFFFFFF);
            c.fill(pos0[4][0] + 6, ry + 5, pos0[4][0] + 7, ry + 10, 0xFFFFFFFF);
        }
        return new int[]{w, h};
    }
}

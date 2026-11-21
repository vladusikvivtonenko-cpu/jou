package com.ezvisuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.util.Formatting;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * License keys. The mod contains only SHA-256 hashes of the valid keys (licenses.txt), never the keys themselves.
 * A timed key starts counting when it is first entered. Without a valid key all modules are switched off.
 */
public final class License {
    private License() {}

    /** Shown on the key screen. Put your Telegram / Discord here. */
    public static final String CONTACT = "Buy a key: write to the seller";

    private static final String SALT = "ezv1|", MAC_SALT = "ezv1-state|";
    private static final long DAY = 86_400_000L;
    private static final Map<String, Integer> TABLE = new HashMap<>();

    private record State(String hash, long act, long seen) {}

    private static volatile boolean ok;
    private static volatile String message = "Enter your license key";
    private static String hash = "";
    private static int days;
    private static long activatedAt, lastSeen, lastSave;
    private static int ticks;
    private static boolean warned;
    private static List<Boolean> backup;

    public static boolean ok() { return ok; }

    public static String message() { return message; }

    public static void init() {
        try (InputStream in = License.class.getResourceAsStream("/assets/ezvisuals/licenses.txt")) {
            if (in != null) {
                for (String l : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                    int i = l.indexOf(':');
                    if (i > 0) TABLE.put(l.substring(i + 1).trim(), Integer.parseInt(l.substring(0, i).trim()));
                }
            }
        } catch (Exception ignored) {
        }
        State s = readBest();
        if (s != null && TABLE.containsKey(s.hash())) {
            hash = s.hash();
            days = TABLE.get(hash);
            activatedAt = s.act();
            lastSeen = s.seen();
        }
        evaluate();
    }

    // ---------------- checks ----------------
    private static void evaluate() {
        if (hash.isEmpty()) { ok = false; message = "Enter your license key"; return; }
        long now = System.currentTimeMillis();
        if (now < lastSeen - 600_000L) {
            ok = false;
            message = "System clock was changed. Fix the date or contact the seller";
            return;
        }
        if (days > 0 && now >= activatedAt + days * DAY) { ok = false; message = "License expired"; return; }
        if (now > lastSeen) lastSeen = now;
        ok = true;
        message = "Licensed: " + remaining(now);
        if (now - lastSave > 60_000L) save();
    }

    private static String remaining(long now) {
        if (days == 0) return "Lifetime";
        long left = Math.max(0, activatedAt + days * DAY - now);
        return (left / DAY) + " d " + ((left % DAY) / 3_600_000L) + " h left";
    }

    /** @return null when the key was accepted, otherwise the reason. */
    public static String activate(String input) {
        String n = normalize(input);
        if (n == null) return "Wrong key format. Example: EZV-XXXXX-XXXXX-XXXXX-XXXXX";
        String h = sha(SALT + n);
        Integer d = TABLE.get(h);
        if (d == null) return "Invalid key";
        long now = System.currentTimeMillis();
        State ex = readBest();
        long act = now, seen = now;
        if (ex != null && ex.hash().equals(h)) {   // same key again: the timer is never reset
            act = Math.min(ex.act(), now);
            seen = Math.max(ex.seen(), now);
        }
        hash = h;
        days = d;
        activatedAt = act;
        lastSeen = seen;
        evaluate();
        if (!ok) return message;
        save();
        restore();
        return null;
    }

    private static String normalize(String in) {
        if (in == null) return null;
        String t = in.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (t.startsWith("EZV") && t.length() == 23) t = t.substring(3);
        return t.length() == 20 ? t : null;
    }

    // ---------------- every tick ----------------
    public static void tick(MinecraftClient mc) {
        if (++ticks % 20 == 0) evaluate();
        if (ok) {
            if (backup != null) restore();
            return;
        }
        suppress();
        if (Music.active()) Music.stop();
        // Mandatory: the key screen is forced on the main menu (and in a world) until a valid key is entered
        if (mc.getOverlay() == null && (mc.currentScreen == null || mc.currentScreen instanceof TitleScreen)) {
            mc.setScreen(new LicenseScreen());
        }
        if (!warned && mc.player != null) {
            warned = true;
            Cmd.sayLater("License needed: " + message + ". Type .license key <your key>", Formatting.RED);
        }
    }

    /** Switch every module off (remembering what was on) while there is no license. */
    private static void suppress() {
        if (backup == null) {
            backup = new ArrayList<>();
            for (Module m : Modules.ALL) backup.add(m.enabled);
        }
        for (Module m : Modules.ALL) m.enabled = false;
    }

    private static void restore() {
        if (backup == null) return;
        int i = 0;
        for (Module m : Modules.ALL) {
            if (i < backup.size()) m.enabled = backup.get(i);
            i++;
        }
        backup = null;
    }

    // ---------------- storage ----------------
    private static List<Path> paths() {
        List<Path> l = new ArrayList<>();
        l.add(Config.dir().resolve("license.dat"));
        l.add(Path.of(System.getProperty("user.home", "."), ".ezv_license"));
        return l;
    }

    private static String fp() {
        return sha(System.getProperty("os.name", "") + "|" + System.getProperty("user.name", "") + "|"
                + System.getProperty("os.arch", "")).substring(0, 16);
    }

    private static void save() {
        lastSave = System.currentTimeMillis();
        String body = hash + ";" + activatedAt + ";" + lastSeen + ";" + fp();
        String enc = Base64.getEncoder().encodeToString((body + ";" + sha(MAC_SALT + body)).getBytes(StandardCharsets.UTF_8));
        for (Path p : paths()) {
            try { Files.writeString(p, enc, StandardCharsets.UTF_8); } catch (Exception ignored) { }
        }
    }

    private static State read(Path p) {
        try {
            if (!Files.exists(p)) return null;
            String raw = new String(Base64.getDecoder().decode(Files.readString(p, StandardCharsets.UTF_8).trim()), StandardCharsets.UTF_8);
            String[] a = raw.split(";");
            if (a.length != 5) return null;
            String body = a[0] + ";" + a[1] + ";" + a[2] + ";" + a[3];
            if (!sha(MAC_SALT + body).equals(a[4]) || !fp().equals(a[3])) return null;
            return new State(a[0], Long.parseLong(a[1]), Long.parseLong(a[2]));
        } catch (Exception e) {
            return null;
        }
    }

    private static State readBest() {
        State best = null;
        List<State> all = new ArrayList<>();
        for (Path p : paths()) {
            State s = read(p);
            if (s == null) continue;
            all.add(s);
            if (best == null || s.seen() > best.seen()) best = s;
        }
        if (best == null) return null;
        long act = best.act();
        for (State s : all) if (s.hash().equals(best.hash())) act = Math.min(act, s.act());
        return new State(best.hash(), act, best.seen());
    }

    private static String sha(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "";
        }
    }
}

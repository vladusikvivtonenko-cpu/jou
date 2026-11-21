package com.ezvisuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Chat commands, typed in the normal chat and never sent to the server:
 *   .cfg save name   .cfg load name   .cfg delete name   .cfg list   .cfg reset
 *   .hud             opens the HUD position editor
 */
public final class Cmd {
    private Cmd() {}

    public static boolean openEditor;
    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N}_\\-]{1,32}");

    /** @return true if the message was an ezvisuals command (it is then not sent to the server). */
    public static boolean onChat(String msg) {
        if (msg == null) return false;
        String s = msg.trim();
        if (s.length() < 2 || s.charAt(0) != '.') return false;
        String[] a = s.substring(1).trim().split("\\s+");
        String head = a[0].toLowerCase(Locale.ROOT);
        if (head.equals("cfg") || head.equals("config")) {
            cfg(a);
            return true;
        }
        if (head.equals("hud")) {
            openEditor = true;
            return true;
        }
        if (head.equals("license") || head.equals("lic")) {
            license(a);
            return true;
        }
        if (head.equals("music") || head.equals("m")) {
            music(a);
            return true;
        }
        return false;
    }

    /** Can be called from any thread. */
    public static void sayLater(String s, Formatting color) {
        MinecraftClient.getInstance().execute(() -> say(s, color));
    }

    private static void license(String[] a) {
        if (a.length >= 3 && a[1].equalsIgnoreCase("key")) {
            String err = License.activate(a[2]);
            say(err == null ? "License activated: " + License.message() : err, err == null ? Formatting.GREEN : Formatting.RED);
            return;
        }
        say(License.ok() ? License.message() : License.message() + ". Use .license key <your key>",
                License.ok() ? Formatting.GREEN : Formatting.RED);
    }

    private static void music(String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "help";
        switch (sub) {
            case "list" -> {
                List<String> n = Music.refresh();
                if (n.isEmpty()) say("No songs. Put .mp3 / .wav files into: " + Music.dir(), Formatting.YELLOW);
                else for (int i = 0; i < n.size(); i++) say((i + 1) + ". " + n.get(i), Formatting.WHITE);
            }
            case "play" -> {
                if (a.length < 3) { Music.toggle(); return; }
                String q = String.join(" ", java.util.Arrays.copyOfRange(a, 2, a.length));
                if (q.matches("\\d{1,4}")) {
                    Music.refresh();
                    Music.playIndex(Integer.parseInt(q) - 1);
                } else if (!Music.playByName(q)) {
                    say("No song with that name. Type .music list", Formatting.RED);
                }
            }
            case "pause", "resume", "toggle" -> Music.toggle();
            case "next" -> Music.next();
            case "prev", "back" -> Music.prev();
            case "stop" -> Music.stop();
            case "vol", "volume" -> {
                if (a.length < 3) { say("Volume: " + Music.volumePct() + "%  (.music vol 0-100)", Formatting.WHITE); return; }
                try {
                    Music.setVolume(Integer.parseInt(a[2]) / 100f);
                    say("Volume: " + Music.volumePct() + "%", Formatting.GREEN);
                } catch (NumberFormatException e) {
                    say("Write a number from 0 to 100", Formatting.RED);
                }
            }
            case "url", "link" -> {
                if (a.length < 3 || !a[2].toLowerCase(Locale.ROOT).startsWith("http")) {
                    say(".music url <direct link to an mp3 file or mp3 radio stream>", Formatting.GRAY);
                    return;
                }
                Music.playUrl(a[2]);
            }
            case "folder" -> say("Put .mp3 / .wav files here: " + Music.dir(), Formatting.WHITE);
            default -> say(".music list | play <number or name> | pause | next | prev | stop | vol <0-100> | url <mp3 link> | folder", Formatting.GRAY);
        }
    }

    private static void say(String s, Formatting color) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        MutableText t = Text.literal("[Ezvisuals] ").formatted(Formatting.LIGHT_PURPLE)
                .append(Text.literal(s).formatted(color));
        mc.player.sendMessage(t, false);
    }

    private static void usage() {
        say(".cfg save <name>   .cfg load <name>   .cfg delete <name>   .cfg list   .cfg reset", Formatting.GRAY);
    }

    private static String clean(String raw) {
        String n = raw.trim();
        String low = n.toLowerCase(Locale.ROOT);
        if (low.endsWith(".cfg") || low.endsWith(".json")) n = n.substring(0, n.lastIndexOf('.'));
        return NAME.matcher(n).matches() ? n : null;
    }

    private static void cfg(String[] a) {
        if (a.length < 2) { usage(); return; }
        String sub = a[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                List<String> l = Config.listNamed();
                say(l.isEmpty() ? "No configs yet. Use .cfg save <name>" : "Configs: " + String.join(", ", l), Formatting.WHITE);
            }
            case "reset" -> {
                Config.reset();
                say("Everything reset to defaults", Formatting.YELLOW);
            }
            case "save", "load", "delete", "del", "remove" -> {
                if (a.length < 3) { usage(); return; }
                String name = clean(a[2]);
                if (name == null) {
                    say("Bad name. Use letters, digits, _ or - (max 32)", Formatting.RED);
                    return;
                }
                if (sub.equals("save")) {
                    boolean existed = Config.existsNamed(name);
                    boolean ok = Config.saveNamed(name);
                    say(ok ? (existed ? "Config overwritten: " : "Config saved: ") + name : "Could not save config",
                            ok ? Formatting.GREEN : Formatting.RED);
                } else if (sub.equals("load")) {
                    if (!Config.existsNamed(name)) { say("No config named " + name, Formatting.RED); return; }
                    boolean ok = Config.loadNamed(name);
                    say(ok ? "Config loaded: " + name : "Config file is broken: " + name,
                            ok ? Formatting.GREEN : Formatting.RED);
                } else {
                    say(Config.deleteNamed(name) ? "Config deleted: " + name : "No config named " + name,
                            Formatting.YELLOW);
                }
            }
            default -> usage();
        }
    }
}

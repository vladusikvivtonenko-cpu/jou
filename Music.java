package com.ezvisuals;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** In-game music player: plays .mp3 / .wav files from config/ezvisuals/music and direct mp3 links. */
public final class Music {
    private Music() {}

    private static final List<Path> FILES = new ArrayList<>();
    private static int index = -1;
    private static volatile int gen = 0;
    private static volatile boolean paused, active;
    private static volatile String title = "", artist = "", error = "";
    private static volatile long posMs, totalMs;
    private static volatile float volume = 0.7f;

    public static Path dir() {
        Path d = Config.dir().resolve("music");
        try { Files.createDirectories(d); } catch (Exception ignored) {}
        return d;
    }

    // ---------------- state for the widget ----------------
    public static boolean active() { return active; }
    public static boolean paused() { return paused; }
    public static String title() { return title; }
    public static String artist() { return artist; }
    public static String error() { return error; }
    public static double posSec() { return posMs / 1000.0; }
    public static double endSec() { return totalMs / 1000.0; }
    public static int volumePct() { return Math.round(volume * 100f); }

    // ---------------- playlist ----------------
    public static synchronized List<String> refresh() {
        FILES.clear();
        try (Stream<Path> s = Files.list(dir())) {
            s.filter(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return Files.isRegularFile(p) && (n.endsWith(".mp3") || n.endsWith(".wav"));
            }).sorted().forEach(FILES::add);
        } catch (Exception ignored) {
        }
        List<String> names = new ArrayList<>();
        for (Path p : FILES) names.add(p.getFileName().toString());
        return names;
    }

    public static synchronized void playIndex(int i) {
        if (FILES.isEmpty()) refresh();
        if (FILES.isEmpty()) { error = "No songs. Put .mp3 / .wav files into ezvisuals/music"; active = false; return; }
        int n = FILES.size();
        index = ((i % n) + n) % n;
        Path f = FILES.get(index);
        String name = f.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        int dash = name.indexOf(" - ");
        if (dash > 0) { artist = name.substring(0, dash).trim(); title = name.substring(dash + 3).trim(); }
        else { artist = "In-game music"; title = name; }
        launch(f, null);
    }

    public static synchronized boolean playByName(String q) {
        refresh();
        String low = q.toLowerCase(Locale.ROOT);
        for (int i = 0; i < FILES.size(); i++) {
            if (FILES.get(i).getFileName().toString().toLowerCase(Locale.ROOT).contains(low)) { playIndex(i); return true; }
        }
        return false;
    }

    public static void playUrl(String url) {
        String name = url;
        try {
            URL u = new URL(url);
            String path = u.getPath();
            name = path != null && path.length() > 1 ? path.substring(path.lastIndexOf('/') + 1) : u.getHost();
            if (name.isEmpty()) name = u.getHost();
            artist = "Stream / link";
        } catch (Exception e) {
            artist = "Stream / link";
        }
        title = name;
        launch(null, url);
    }

    public static void toggle() {
        if (!active) { playIndex(index < 0 ? 0 : index); return; }
        paused = !paused;
    }

    public static void next() { playIndex(index + 1); }

    public static void prev() { playIndex(index - 1); }

    public static void stop() {
        gen++;
        active = false;
        paused = false;
    }

    public static void changeVolume(float d) { setVolume(volume + d); }

    public static void setVolume(float v) { volume = Math.max(0f, Math.min(1f, v)); }

    // ---------------- playback thread ----------------
    private static void launch(Path file, String url) {
        final int my = ++gen;
        active = true;
        paused = false;
        posMs = 0;
        totalMs = 0;
        error = "";
        Thread t = new Thread(() -> {
            boolean finished = false;
            try {
                if (file != null && file.toString().toLowerCase(Locale.ROOT).endsWith(".wav")) playWav(file, my);
                else playMp3(file, url, my);
                finished = true;
            } catch (Throwable e) {
                if (my == gen) error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            if (my != gen) return;
            if (finished && file != null && FILES.size() > 1) playIndex(index + 1);
            else active = false;
        }, "ezvisuals-music");
        t.setDaemon(true);
        t.start();
    }

    private static void waitWhilePaused(SourceDataLine line, int my) throws InterruptedException {
        if (!paused || my != gen) return;
        line.stop();
        while (paused && my == gen) Thread.sleep(40);
        line.start();
    }

    private static void playMp3(Path file, String url, int my) throws Exception {
        long size = 0;
        InputStream raw;
        if (file != null) {
            size = Files.size(file);
            raw = Files.newInputStream(file);
        } else {
            URLConnection con = new URL(url).openConnection();
            con.setConnectTimeout(10000);
            con.setReadTimeout(15000);
            con.setRequestProperty("User-Agent", "Mozilla/5.0");
            raw = con.getInputStream();
        }
        try (InputStream in = new BufferedInputStream(raw, 1 << 16)) {
            Bitstream bs = new Bitstream(in);
            Decoder dec = new Decoder();
            SourceDataLine line = null;
            double ms = 0;
            boolean first = true;
            try {
                Header h;
                while (my == gen && (h = bs.readFrame()) != null) {
                    if (first) {
                        first = false;
                        if (size > 0) totalMs = (long) h.total_ms((int) Math.min(size, Integer.MAX_VALUE));
                    }
                    SampleBuffer sb = (SampleBuffer) dec.decodeFrame(h, bs);
                    if (line == null) {
                        AudioFormat f = new AudioFormat(dec.getOutputFrequency(), 16, dec.getOutputChannels(), true, false);
                        line = AudioSystem.getSourceDataLine(f);
                        line.open(f, 1 << 16);
                        line.start();
                    }
                    short[] pcm = sb.getBuffer();
                    int len = sb.getBufferLength();
                    byte[] b = new byte[len * 2];
                    float v = volume;
                    for (int i = 0; i < len; i++) {
                        short s = (short) (pcm[i] * v);
                        b[i * 2] = (byte) s;
                        b[i * 2 + 1] = (byte) (s >> 8);
                    }
                    waitWhilePaused(line, my);
                    if (my != gen) break;
                    line.write(b, 0, b.length);
                    ms += h.ms_per_frame();
                    posMs = (long) ms;
                    bs.closeFrame();
                }
                if (line != null && my == gen) line.drain();
            } finally {
                if (line != null) { line.stop(); line.close(); }
                try { bs.close(); } catch (Exception ignored) {}
            }
        }
    }

    private static void playWav(Path file, int my) throws Exception {
        try (AudioInputStream a0 = AudioSystem.getAudioInputStream(file.toFile())) {
            AudioFormat src = a0.getFormat();
            AudioFormat tgt = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, src.getSampleRate(), 16,
                    src.getChannels(), src.getChannels() * 2, src.getSampleRate(), false);
            long frames = a0.getFrameLength();
            if (frames > 0) totalMs = (long) (frames * 1000.0 / src.getSampleRate());
            try (AudioInputStream a = AudioSystem.getAudioInputStream(tgt, a0)) {
                SourceDataLine line = AudioSystem.getSourceDataLine(tgt);
                line.open(tgt, 1 << 16);
                line.start();
                try {
                    byte[] buf = new byte[4096];
                    int n;
                    long bytes = 0;
                    while (my == gen && (n = a.read(buf, 0, buf.length)) > 0) {
                        n &= ~1;
                        float v = volume;
                        for (int i = 0; i + 1 < n; i += 2) {
                            short s = (short) ((buf[i] & 0xFF) | (buf[i + 1] << 8));
                            s = (short) (s * v);
                            buf[i] = (byte) s;
                            buf[i + 1] = (byte) (s >> 8);
                        }
                        waitWhilePaused(line, my);
                        if (my != gen) break;
                        line.write(buf, 0, n);
                        bytes += n;
                        posMs = (long) (bytes / (double) tgt.getFrameSize() * 1000.0 / tgt.getSampleRate());
                    }
                    if (my == gen) line.drain();
                } finally {
                    line.stop();
                    line.close();
                }
            }
        }
    }
}

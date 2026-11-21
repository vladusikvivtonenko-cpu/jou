package com.ezvisuals;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class Modules {
    private Modules() {}
    public static final List<Module> ALL = new ArrayList<>();
    private static Module add(Module m) { ALL.add(m); return m; }

    // ---------------- COMBAT ----------------
    public static final Module PVP = add(new Module("pvpsafe", "PvP Safe", "Combat",
            "Combat timer: shows if it is safe to log out, blocks command binds in combat", true,
            Setting.num("time", "Combat time (s)", 15, 5, 60, 1),
            Setting.bool("block", "Block commands in combat", true),
            Setting.bool("hud", "Show status", true)));

    public static final Module FASTSWAP = add(new Module("fastswap", "Fast Swap", "Combat",
            "Key puts the item (trap, disorient...) into your hand. You use it yourself. Set names in Binds tab", true,
            Setting.bool("inv", "Search whole inventory", true)));

    public static final Module CMDBIND = add(new Module("cmdbind", "Command Binds", "Combat",
            "Run /home /hub /darena etc. on a key. Set in Binds tab", true));

    public static final Module TOTEM = add(new Module("totem", "Totem Helper", "Combat",
            "Totem counter, offhand warning and optional auto swap", true,
            Setting.bool("hud", "Show counter", true),
            Setting.bool("auto", "Auto swap to offhand", false),
            Setting.num("hp", "Swap at HP", 6, 2, 19, 1)));

    public static final Module FT = add(new Module("fthelper", "FT Helper", "Combat",
            "Circle around you: plast (kelp), disorient (ender eye), freeze (snowball), crossbow. Potions: landing circle", true,
            Setting.bool("text", "Show radius text", true),
            Setting.num("potion", "Potion radius (landing)", 4, 1, 20, 0.5),
            Setting.num("plast", "Plast radius", 3, 1, 15, 0.5),
            Setting.num("disor", "Disorientation radius", 10, 1, 25, 0.5),
            Setting.num("freeze", "Freeze snowball radius", 7, 1, 15, 0.5),
            Setting.num("cross", "Crossbow radius", 5, 1, 15, 0.5)));

    public static final Module PREDICT = add(new Module("predict", "Predictions", "Combat",
            "Trajectory of bow, crossbow, trident, pearl, snowball, potions", true,
            Setting.bool("bow", "Bow", true),
            Setting.bool("cross", "Crossbow", true),
            Setting.bool("trident", "Trident", true),
            Setting.bool("thr", "Pearl / snowball / egg / potion", true),
            Setting.bool("marker", "Landing marker + distance", true),
            Setting.mode("color", "Color", 0, "Theme", "White", "Red")));

    // ---------------- MOVEMENT ----------------
    public static final Module BRIDGE = add(new Module("bridge", "Full Bridge", "Movement",
            "Auto places blocks at the edge while you walk (hold blocks)", false,
            Setting.num("delay", "Place delay (ticks)", 2, 1, 10, 1),
            Setting.bool("sneak", "Only while sneaking", false),
            Setting.bool("swing", "Swing hand", true)));

    public static final Module ZOOM = add(new Module("zoom", "Zoom", "Movement",
            "Zoom view. Click the bind button: left = set key, right = hold/toggle", false,
            Setting.num("fov", "Zoom FOV", 30, 30, 70, 1)).bind(GLFW.GLFW_KEY_C, true));

    public static final Module ELYTRA = add(new Module("elytra", "Elytra Swap", "Movement",
            "Key swaps your chestplate with an elytra from the hotbar and back. Click the bind button to set the key", true).bind(GLFW.GLFW_KEY_G, false));

    public static final Module SPRINT = add(new Module("sprint", "Auto Sprint", "Movement",
            "Keeps you sprinting while moving forward", false));

    public static final Module AUTOSHIFT = add(new Module("autoshift", "Auto Shift", "Movement",
            "Presses Shift (sneak) for you: hold it all the time or tap it. Bind a key on the button", false,
            Setting.mode("mode", "Mode", 0, "Hold", "Tap"),
            Setting.num("delay", "Tap delay (ticks)", 2, 1, 10, 1),
            Setting.bool("moving", "Only while moving", false)));

    // ---------------- VISUALS ----------------
    public static final Module NOFIRE = add(new Module("nofire", "No Fire", "Visuals",
            "Hides the fire overlay on your screen", false));

    public static final Module NOSHAKE = add(new Module("noshake", "No Shake", "Visuals",
            "Disables the hurt camera shake", false));

    public static final Module ESP = add(new Module("esp", "Player Glow", "Visuals",
            "Outline glow on other players", false));

    public static final Module NAMETAGS = add(new Module("nametags", "Armor Name Tags", "Visuals",
            "Name tags with HP, armor percent and equipment", true,
            Setting.num("dist", "Max distance", 64, 8, 128, 4),
            Setting.bool("items", "Show items", true),
            Setting.bool("hp", "Show HP", true)));

    public static final Module ITEM_HL = add(new Module("itemhl", "Item Highlight", "Visuals",
            "Colored highlight of swords, pickaxes, axes, bows, totems in hotbar", true,
            Setting.bool("fill", "Color fill", true),
            Setting.bool("pulse", "Pulse", true)));

    public static final Module WEATHER = add(new Module("weather", "Weather FX", "Visuals",
            "Screen weather: rain, snow, space, halloween, autumn leaves. Pick the effect and tune it", false,
            Setting.mode("type", "Effect", 0, Fx.NAMES),
            Setting.num("dens", "Density", 80, 10, 300, 10),
            Setting.num("speed", "Speed", 1.0, 0.3, 3.0, 0.1),
            Setting.num("size", "Particle size", 1.0, 0.5, 2.5, 0.1),
            Setting.bool("tint", "Screen tint", true),
            Setting.bool("title", "Show effect name on top", false)));

    public static final Module VIEWMODEL = add(new Module("viewmodel", "Swimming Animation", "Visuals",
            "Position, rotation and size of your hands / sword / items (sliders)", false,
            Setting.num("mx", "Main hand X", 0, -1.5, 1.5, 0.05),
            Setting.num("my", "Main hand Y", 0, -1.5, 1.5, 0.05),
            Setting.num("mz", "Main hand Z", 0, -1.5, 1.5, 0.05),
            Setting.num("mrx", "Main tilt X", 0, -180, 180, 1),
            Setting.num("mry", "Main turn Y", 0, -180, 180, 1),
            Setting.num("mrz", "Main roll Z", 0, -180, 180, 1),
            Setting.num("ms", "Main size", 1, 0.2, 2, 0.05),
            Setting.num("ox", "Off hand X", 0, -1.5, 1.5, 0.05),
            Setting.num("oy", "Off hand Y", 0, -1.5, 1.5, 0.05),
            Setting.num("oz", "Off hand Z", 0, -1.5, 1.5, 0.05),
            Setting.num("orx", "Off tilt X", 0, -180, 180, 1),
            Setting.num("ory", "Off turn Y", 0, -180, 180, 1),
            Setting.num("orz", "Off roll Z", 0, -180, 180, 1),
            Setting.num("os", "Off size", 1, 0.2, 2, 0.05)));

    public static final Module CUSTOMWORLD = add(new Module("customworld", "Custom Worlds", "Visuals",
            "Custom sky color, time of day and fog (client side only)", false,
            Setting.mode("preset", "Preset", 0, "Custom", "Blood", "Night", "Sunset", "Void"),
            Setting.num("r", "Sky red %", 65, 0, 100, 1),
            Setting.num("g", "Sky green %", 2, 0, 100, 1),
            Setting.num("b", "Sky blue %", 2, 0, 100, 1),
            Setting.bool("time", "Custom time of day", true),
            Setting.num("t", "Time (0-24000)", 13200, 0, 24000, 100),
            Setting.bool("fog", "Tint fog", true)));

    public static final Module FOGBLUR = add(new Module("fogblur", "Fog Blur", "Visuals",
            "Soft fog: far chunks dissolve into a haze. Set distance, softness, color and saturation", true,
            Setting.num("dist", "Distance (blocks)", 40, 8, 256, 4),
            Setting.num("soft", "Softness %", 80, 5, 100, 5),
            Setting.mode("color", "Color", 0, "Sky (auto)", "Theme", "White", "Custom"),
            Setting.num("r", "Custom red %", 70, 0, 100, 1),
            Setting.num("g", "Custom green %", 75, 0, 100, 1),
            Setting.num("b", "Custom blue %", 90, 0, 100, 1),
            Setting.num("sat", "Saturation %", 100, 0, 250, 5),
            Setting.num("bright", "Brightness %", 100, 20, 150, 5)));

    public static final Module COSMETICS = add(new Module("cosmetics", "Cosmetics", "Visuals",
            "A character with no background stands next to you, behind you or above your head, or sits in a movable HUD block. Pick the character", false,
            Setting.mode("skin", "Cosmetic", 0, "Bacon (Roblox)", "Melsrou", "Mellblackfreind"),
            Setting.mode("place", "Placement", 0, "Beside you", "Behind you", "Above head", "HUD (movable)"),
            Setting.num("size", "Size", 1.0, 0.4, 3.0, 0.1),
            Setting.bool("left", "Left side", false),
            Setting.bool("bob", "Float up and down", true)));

    public static final Module JUMPFX = add(new Module("jumpfx", "Jump Circles", "Visuals",
            "Expanding ring under your feet every time you jump", false,
            Setting.num("size", "Max radius", 1.6, 0.8, 4.0, 0.1),
            Setting.num("time", "Duration (ms)", 800, 300, 2000, 100),
            Setting.mode("color", "Color", 0, "Theme", "White", "Red", "Rainbow")));

    public static final Module HITFX = add(new Module("hitfx", "Hit Color", "Visuals",
            "Colored flash box around the target you hit", false,
            Setting.mode("color", "Color", 2, "Theme", "White", "Red", "Rainbow"),
            Setting.num("time", "Duration (ms)", 450, 150, 1500, 50)));

    public static final Module TARGETFX = add(new Module("targetfx", "Target ESP", "Visuals",
            "Effect on the enemy you are hitting: a transparent ghost or floating hearts. Pick style and color theme", false,
            Setting.mode("style", "Style", 0, "Ghost", "Hearts"),
            Setting.mode("color", "Color theme", 0, "Theme", "White", "Red", "Rainbow", "Custom"),
            Setting.num("r", "Custom red %", 40, 0, 100, 1),
            Setting.num("g", "Custom green %", 80, 0, 100, 1),
            Setting.num("b", "Custom blue %", 100, 0, 100, 1),
            Setting.num("alpha", "Opacity %", 55, 10, 100, 5),
            Setting.num("size", "Size", 1.0, 0.5, 2.0, 0.1),
            Setting.num("time", "Show for (s)", 4, 1, 10, 1)));

    public static final Module KILLFX = add(new Module("killfx", "Kill Effect", "Visuals",
            "When you kill an enemy: his transparent ghost flies into the sky, or a yellow vortex / evil dark vortex spins around him (Vortex is gold unless you pick another color)", false,
            Setting.mode("style", "Style", 0, "Ghost", "Silhouette", "Vortex", "Evil vortex"),
            Setting.mode("color", "Color theme", 0, "Theme", "White", "Red", "Rainbow", "Custom", "Gold"),
            Setting.num("r", "Custom red %", 40, 0, 100, 1),
            Setting.num("g", "Custom green %", 80, 0, 100, 1),
            Setting.num("b", "Custom blue %", 100, 0, 100, 1),
            Setting.num("alpha", "Opacity %", 55, 10, 100, 5),
            Setting.num("size", "Size", 1.0, 0.5, 2.0, 0.1),
            Setting.num("height", "Fly height (blocks)", 12, 3, 30, 1),
            Setting.num("time", "Duration (ms)", 2200, 800, 5000, 100),
            Setting.bool("sparks", "Soul sparks", true),
            Setting.bool("name", "Show enemy name", true)));

    public static final Module ASPECT = add(new Module("aspect", "Aspect Ratio", "Visuals",
            "Stretches the game picture (1.33 = 4:3, 1.78 = 16:9)", false,
            Setting.num("ratio", "Ratio (width / height)", 1.33, 0.5, 3.0, 0.01)));

    // ---------------- HUD ----------------
    public static final Module WATERMARK = add(new Module("watermark", "Watermark", "HUD",
            "Ezvisuals pill at the top center: ping, FPS (+ optional TPS, coords)", true,
            Setting.bool("ping", "Ping", true),
            Setting.bool("fps", "FPS", true),
            Setting.bool("tps", "TPS", false),
            Setting.bool("xyz", "Coordinates", false),
            Setting.bool("name", "Nickname", false)));

    public static final Module ARMOR_HUD = add(new Module("armorhud", "Armor HUD", "HUD",
            "Armor durability percent (total and per piece). Movable", true,
            Setting.mode("layout", "Layout", 0, "Vertical", "Horizontal")));

    public static final Module POTION_HUD = add(new Module("potionhud", "Potion HUD", "HUD",
            "Active effects with remaining time. Movable", true));

    public static final Module COOLDOWNS = add(new Module("cooldowns", "Cooldowns", "HUD",
            "Item cooldowns from the whole inventory (pearl, chorus, totem...). Movable", true));

    public static final Module CD_DISORIENT = add(new Module("cd_disorient", "Disorient CD (Дезка)", "HUD",
            "Separate movable timer for the disorient item. Pick the item in settings", true,
            Setting.mode("item", "Item", 0, CdItems.NAMES),
            Setting.mode("label", "Label", 0, "Preset", "Item name"),
            Setting.bool("ready", "Show READY", false)));

    public static final Module CD_TRAP = add(new Module("cd_trap", "Trap CD (Трапка)", "HUD",
            "Separate movable timer for the trap item. Pick the item in settings", true,
            Setting.mode("item", "Item", 1, CdItems.NAMES),
            Setting.mode("label", "Label", 0, "Preset", "Item name"),
            Setting.bool("ready", "Show READY", false)));

    public static final Module TARGETHUD = add(new Module("targethud", "Target HUD", "HUD",
            "Name, health bar, armor and distance of the enemy you hit. Movable", false,
            Setting.num("linger", "Show for (s)", 4, 1, 10, 1),
            Setting.bool("armor", "Show armor %", true),
            Setting.bool("dist", "Show distance", true)));

    public static final Module KEYS = add(new Module("keystrokes", "Keystrokes", "HUD",
            "W A S D, LMB / RMB with CPS and space bar on screen. Movable", false,
            Setting.bool("mouse", "Mouse buttons", true),
            Setting.bool("cps", "Show CPS", true),
            Setting.bool("space", "Space bar", true),
            Setting.bool("bg", "Solid keys", true)));

    public static final Module PEARLCAM = add(new Module("pearlcam", "Pearl Cam", "HUD",
            "Top-right panel: when your ender pearl flies it shows a top-down view of the landing spot, distance and flight time. Movable", false,
            Setting.mode("scale", "Zoom", 0, "Close", "Far"),
            Setting.bool("others", "Also enemy pearls", false)));

    public static final Module MEDIA = add(new Module("media", "Media Player", "HUD",
            "Music player. In-game files plays mp3 / wav from config/ezvisuals/music (chat: .music list / play / url). PC media controls Spotify, YouTube etc (Windows). Click the buttons while chat or inventory is open, or use the keys in Controls. Movable", false,
            Setting.mode("source", "Source", 1, "PC media (Windows)", "In-game files / links"),
            Setting.bool("vol", "Volume buttons", true),
            Setting.bool("idle", "Show when nothing plays", true)));

    public static final Module LOOK = add(new Module("lookslots", "Look Slots", "HUD",
            "Equipment of the player you are looking at", false));

    public static final Module CROSSHAIR = add(new Module("crosshair", "Crosshair", "HUD",
            "Custom crosshair: style, size, gap, color", false,
            Setting.mode("style", "Style", 0, "Cross", "Dot", "Circle", "Cross + Dot"),
            Setting.num("size", "Size", 5, 1, 20, 1),
            Setting.num("gap", "Gap", 3, 0, 12, 1),
            Setting.num("thick", "Thickness", 1, 1, 4, 1),
            Setting.mode("color", "Color", 0, "Theme", "White", "Red", "Rainbow"),
            Setting.bool("dyn", "Expand when moving", true),
            Setting.bool("outline", "Outline", true),
            Setting.bool("hide", "Hide vanilla crosshair", true)));

    public static final Module PANELS = add(new Module("panels", "HUD Panels", "HUD",
            "Dark panels behind HUD blocks (off = clean text only)", false,
            Setting.num("alpha", "Opacity %", 60, 10, 100, 5)));

    // ---------------- OPTIMIZE ----------------
    public static final Module PERF = add(new Module("perf", "Performance Mode", "Optimize",
            "Lowers heavy video options for weak PCs, restores them when turned off", false,
            Setting.mode("particles", "Particles", 2, "All", "Decreased", "Minimal"),
            Setting.bool("clouds", "Disable clouds", true),
            Setting.bool("shadows", "Disable entity shadows", true),
            Setting.bool("fast", "Fast graphics", true),
            Setting.num("dist", "Entity distance", 0.5, 0.5, 1.0, 0.1),
            Setting.bool("blend", "No biome blend", true)));

    public static final Module AUTO_Q = add(new Module("autoq", "Auto Quality", "Optimize",
            "Turns off Weather FX and name tags when FPS drops", true,
            Setting.num("fps", "Min FPS", 30, 15, 60, 5)));
}

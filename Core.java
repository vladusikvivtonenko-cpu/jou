package com.ezvisuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

public final class Core {
    private Core() {}

    public static int combatTicks = 0;
    private static boolean zoomActive, espWas, jumpWasGround;
    private static int savedFov;
    private static int bridgeCd, totemCd, shiftTick;
    private static boolean shiftForced;

    public static void startCombat() {
        if (Modules.PVP.enabled) combatTicks = Modules.PVP.get("time").i() * 20;
    }

    public static int totemCount(ClientPlayerEntity p) {
        int n = 0;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.main.size(); i++) {
            ItemStack s = inv.main.get(i);
            if (s.isOf(Items.TOTEM_OF_UNDYING)) n += s.getCount();
        }
        if (p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) n += p.getOffHandStack().getCount();
        return n;
    }

    public static void tick(MinecraftClient mc) {
        while (EzVisualsPremium.menuKey.wasPressed()) {
            if (mc.currentScreen == null) mc.setScreen(License.ok() ? new PremiumGUI() : new LicenseScreen());
        }
        if (Cmd.openEditor && mc.currentScreen == null) {
            Cmd.openEditor = false;
            mc.setScreen(License.ok() ? new HudEditor(null) : new LicenseScreen());
        }
        Perf.tick(mc);
        Media.tick(mc);
        if (mc.player == null || mc.world == null) return;

        ClientPlayerEntity p = mc.player;
        long win = mc.getWindow().getHandle();
        boolean free = mc.currentScreen == null;

        // ---- module binds ----
        for (Module m : Modules.ALL) {
            if (m.bind < 0) continue;
            boolean down = InputUtil.isKeyPressed(win, m.bind);
            if (m == Modules.ELYTRA) {   // the key swaps, it does not toggle the module
                if (free && down && !m.prevDown && m.enabled) elytraSwap(mc, p);
                m.prevDown = down;
                continue;
            }
            if (free) {
                if (m.hold) m.enabled = down;
                else if (down && !m.prevDown) m.enabled = !m.enabled;
            } else if (m.hold && m.enabled) {
                m.enabled = false;
            }
            m.prevDown = down;
        }

        // ---- command + fast swap binds ----
        for (Binds.Action a : Binds.COMMANDS) {
            boolean d = a.bind >= 0 && InputUtil.isKeyPressed(win, a.bind);
            if (free && d && !a.prev && Modules.CMDBIND.enabled) runCommand(mc, a);
            a.prev = d;
        }
        for (Binds.Action a : Binds.SWAPS) {
            boolean d = a.bind >= 0 && InputUtil.isKeyPressed(win, a.bind);
            if (free && d && !a.prev) fastSwap(mc, a);
            a.prev = d;
        }

        // ---- combat timer ----
        if (combatTicks > 0) combatTicks--;
        if (Modules.PVP.enabled && p.hurtTime > 0) {
            for (PlayerEntity o : mc.world.getPlayers()) {
                if (o != p && o.squaredDistanceTo(p) < 144) { startCombat(); break; }
            }
        }

        // ---- zoom ----
        if (Modules.ZOOM.enabled && free) {
            if (!zoomActive) { savedFov = mc.options.getFov().getValue(); zoomActive = true; }
            mc.options.getFov().setValue(Modules.ZOOM.get("fov").i());
        } else if (zoomActive) {
            mc.options.getFov().setValue(savedFov);
            zoomActive = false;
        }

        // ---- player glow ----
        if (Modules.ESP.enabled) {
            for (PlayerEntity o : mc.world.getPlayers()) if (o != p) o.setGlowing(true);
            espWas = true;
        } else if (espWas) {
            for (PlayerEntity o : mc.world.getPlayers()) if (o != p) o.setGlowing(false);
            espWas = false;
        }

        // ---- auto sprint ----
        if (Modules.SPRINT.enabled && mc.options.forwardKey.isPressed() && !p.isSneaking()
                && !p.horizontalCollision && !p.isUsingItem() && p.getHungerManager().getFoodLevel() > 6) {
            p.setSprinting(true);
        }

        // ---- jump circles ----
        boolean og = p.isOnGround();
        if (jumpWasGround && !og && p.getVelocity().y > 0.2) Vfx.addJump(p.getPos());
        jumpWasGround = og;

        autoShift(mc, p, win, free);
        Ghosts.tick(mc);
        PearlCam.tick(mc);
        bridge(mc, p);
        totem(mc, p);
    }

    public static void shutdown(MinecraftClient mc) {
        if (zoomActive && mc.options != null) {
            mc.options.getFov().setValue(savedFov);
            zoomActive = false;
        }
        Media.stop();
        Perf.shutdown(mc);
        Config.saveMain();
    }

    // ---------------- commands ----------------
    private static void runCommand(MinecraftClient mc, Binds.Action a) {
        sendCommand(mc, a.text);
    }

    public static void sendCommand(MinecraftClient mc, String text) {
        String cmd = text.trim();
        if (cmd.isEmpty() || mc.player == null) return;
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        if (Modules.PVP.enabled && Modules.PVP.get("block").b() && combatTicks > 0) {
            mc.player.sendMessage(Text.literal("[ezvisuals] Blocked in combat (" + (combatTicks / 20 + 1) + "s)"), true);
            return;
        }
        mc.player.networkHandler.sendChatCommand(cmd);
    }

    // ---------------- fast swap ----------------
    /** Puts the item into the hand. It is NOT used: the player uses it manually. */
    private static void fastSwap(MinecraftClient mc, Binds.Action a) {
        if (!Modules.FASTSWAP.enabled || mc.interactionManager == null) return;
        String kw = a.text.trim().toLowerCase(Locale.ROOT);
        if (kw.isEmpty()) return;
        PlayerInventory inv = mc.player.getInventory();
        // 1) item in the hotbar: just select that slot
        for (int i = 0; i < 9; i++) {
            if (swapMatch(inv.getStack(i), kw)) { inv.selectedSlot = i; return; }
        }
        // 2) item in the rest of the inventory: swap it into the selected hotbar slot
        if (Modules.FASTSWAP.get("inv").b()) {
            for (int i = 9; i < 36; i++) {
                if (swapMatch(inv.getStack(i), kw)) {
                    mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, i, inv.selectedSlot,
                            SlotActionType.SWAP, mc.player);
                    return;
                }
            }
        }
        mc.player.sendMessage(Text.literal("[ezvisuals] Item not found: " + kw), true);
    }

    private static boolean swapMatch(ItemStack s, String kw) {
        if (s.isEmpty()) return false;
        String n = s.getName().getString().toLowerCase(Locale.ROOT);
        String id = Registries.ITEM.getId(s.getItem()).getPath();
        return n.contains(kw) || id.contains(kw);
    }

    // ---------------- elytra swap ----------------
    private static boolean isChestArmor(ItemStack s) {
        if (s.isEmpty() || s.isOf(Items.ELYTRA)) return false;
        EquippableComponent eq = s.get(DataComponentTypes.EQUIPPABLE);
        return eq != null && eq.slot() == EquipmentSlot.CHEST;
    }

    /** Swaps the worn chest piece with an elytra (or back with a chestplate) taken from the hotbar. */
    private static void elytraSwap(MinecraftClient mc, ClientPlayerEntity p) {
        if (mc.interactionManager == null) return;
        PlayerInventory inv = p.getInventory();
        boolean wearing = p.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
        int found = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getStack(i);
            if (wearing ? isChestArmor(st) : st.isOf(Items.ELYTRA)) { found = i; break; }
        }
        if (found < 0) {
            p.sendMessage(Text.literal("[ezvisuals] " + (wearing ? "No chestplate in hotbar" : "No elytra in hotbar")), true);
            return;
        }
        // slot 6 = chest armor slot of the player screen handler; button = hotbar index
        mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, 6, found, SlotActionType.SWAP, p);
    }

    // ---------------- full bridge ----------------
    private static void bridge(MinecraftClient mc, ClientPlayerEntity p) {
        if (!Modules.BRIDGE.enabled || mc.currentScreen != null || mc.interactionManager == null) return;
        if (bridgeCd > 0) { bridgeCd--; return; }
        if (!p.isOnGround()) return;
        if (Modules.BRIDGE.get("sneak").b() && !p.isSneaking()) return;
        if (!(p.getMainHandStack().getItem() instanceof BlockItem)) return;
        Vec3d v = p.getVelocity();
        if (v.x * v.x + v.z * v.z < 0.0004) return;
        Direction dir = Direction.getFacing(v.x, 0, v.z);
        BlockPos below = BlockPos.ofFloored(p.getPos()).down();
        if (mc.world.getBlockState(below).isAir()) return;
        BlockPos target = below.offset(dir);
        if (!mc.world.getBlockState(target).isReplaceable()) return;
        double off = (dir.getOffsetX() != 0 ? p.getX() - (below.getX() + 0.5) : p.getZ() - (below.getZ() + 0.5))
                * (dir.getOffsetX() + dir.getOffsetZ());
        if (off < 0.2) return;
        Vec3d hit = new Vec3d(below.getX() + 0.5 + dir.getOffsetX() * 0.5, below.getY() + 0.5,
                below.getZ() + 0.5 + dir.getOffsetZ() * 0.5);
        BlockHitResult bhr = new BlockHitResult(hit, dir, below, false);
        ActionResult r = mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, bhr);
        if (r.isAccepted()) {
            if (Modules.BRIDGE.get("swing").b()) p.swingHand(Hand.MAIN_HAND);
            bridgeCd = Modules.BRIDGE.get("delay").i();
        }
    }

    // ---------------- auto shift ----------------
    private static void autoShift(MinecraftClient mc, ClientPlayerEntity p, long win, boolean free) {
        Module m = Modules.AUTOSHIFT;
        if (m.enabled && free) {
            boolean moving = mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed()
                    || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed();
            boolean press = true;
            if (m.get("mode").i() == 1) {
                int d = m.get("delay").i();
                shiftTick = (shiftTick + 1) % (d * 2);
                press = shiftTick < d;
            }
            if (m.get("moving").b() && !moving) press = false;
            mc.options.sneakKey.setPressed(press);
            shiftForced = true;
        } else if (shiftForced) {
            var k = KeyBindingHelper.getBoundKeyOf(mc.options.sneakKey);
            boolean real = k.getCategory() == InputUtil.Type.KEYSYM && InputUtil.isKeyPressed(win, k.getCode());
            mc.options.sneakKey.setPressed(real);
            shiftForced = false;
            shiftTick = 0;
        }
    }

    // ---------------- totem helper ----------------
    private static void totem(MinecraftClient mc, ClientPlayerEntity p) {
        if (!Modules.TOTEM.enabled || !Modules.TOTEM.get("auto").b() || mc.interactionManager == null) return;
        if (totemCd > 0) { totemCd--; return; }
        if (p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;
        if (p.getHealth() + p.getAbsorptionAmount() > Modules.TOTEM.get("hp").d()) return;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.main.size(); i++) {
            if (inv.main.get(i).isOf(Items.TOTEM_OF_UNDYING)) {
                int slot = i < 9 ? 36 + i : i;
                mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, slot, 40, SlotActionType.SWAP, p);
                totemCd = 8;
                return;
            }
        }
    }
}

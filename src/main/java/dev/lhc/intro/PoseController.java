package dev.lhc.intro;

import dev.lhc.intro.Types.PoseType;
import dev.lhc.intro.config.LhcConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Vanilla-only poses, built from things every client already renders:
 * crouch, arm swings, spin, riding an invisible seat, and item-use animations (shield, bow, trident, spyglass).
 * For true custom animations (arms crossed etc.) use the externalPoseCommands hook with Emotecraft or similar.
 */
final class PoseController {
    private final Session s;
    private final ServerPlayer p;
    private final PoseType type;
    private final Vec3 base;
    private final float baseYaw;
    private final int total;
    private final float startYaw;

    private ArmorStand seat;
    private ItemStack savedMain, savedOff;
    private boolean propsApplied, stopped;
    private float danceYaw;
    private int danceDir = 1;

    PoseController(Session s, ServerPlayer p, PoseType type, Vec3 base, float baseYaw, int totalTicks) {
        this.s = s; this.p = p; this.type = type; this.base = base; this.baseYaw = baseYaw;
        this.total = totalTicks; this.startYaw = p.getYRot();
    }

    void start() {
        p.setSprinting(false);
        LhcConfig cfg = LhcConfig.get();
        switch (type) {
            case SWORD_SALUTE -> { props(Items.NETHERITE_SWORD, Items.SHIELD); p.startUsingItem(InteractionHand.OFF_HAND); }
            case POINT -> { props(Items.BOW, null); p.startUsingItem(InteractionHand.MAIN_HAND); }
            case FLEX -> { props(Items.TRIDENT, null); p.startUsingItem(InteractionHand.MAIN_HAND); }
            case LOOK_AROUND -> { props(Items.SPYGLASS, null); p.startUsingItem(InteractionHand.MAIN_HAND); }
            case SIT, MEDITATE -> {
                seat = Compat.spawnStand(s.level, base.x, base.y + cfg.seatOffsetY, base.z);
                Compat.mount(p, seat);
            }
            default -> { }
        }
        external(type.name());
    }

    void tick(int t) {
        float turn = Mth.clamp(t / 12f, 0f, 1f);                       // ease from walking yaw into the pose yaw
        float yaw = startYaw + Mth.wrapDegrees(baseYaw - startYaw) * turn;
        float pitch = 0;
        double y = 0;

        switch (type) {
            case ARMS_CROSSED -> { yaw += (float) Math.sin(t * .06) * 6; pitch = (float) Math.sin(t * .04) * 3; }
            case WAVE -> { if (t % 8 == 0) p.swing(InteractionHand.MAIN_HAND, true); yaw += (float) Math.sin(t * .15) * 10; pitch = -4; }
            case BOW -> {
                float k = Mth.clamp(t / 18f, 0, 1) * (1 - Mth.clamp((t - (total - 40)) / 18f, 0, 1));
                k = k * k * (3 - 2 * k);
                pitch = 52 * k;
                p.setShiftKeyDown(k > .4f);
            }
            case SWORD_SALUTE -> pitch = -8;
            case DANCE -> {
                if (t % 50 == 0 && t > 0) danceDir = -danceDir;
                danceYaw += 9 * danceDir;
                yaw += danceYaw;
                y = Math.abs(Math.sin(t * .32)) * .28;
                p.setShiftKeyDown((t / 5) % 2 == 0);
                if (t % 6 == 0) p.swing((t / 6) % 2 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, true);
            }
            case SIT -> { yaw += (float) Math.sin(t * .05) * 5; pitch = 4; }
            case POINT -> { yaw += (float) Math.sin(t * .09) * 7; pitch = -5; }
            case FLEX -> { y = Math.max(0, Math.sin(t * .2)) * .15; p.setShiftKeyDown((t / 12) % 2 == 1); }
            case MEDITATE -> {
                float rise = Mth.clamp(t / 50f, 0, 1); rise = rise * rise * (3 - 2 * rise);
                y = rise * .7 + Math.sin(t * .08) * .08;
                yaw += t * 1.2f; pitch = 8;
            }
            case LOOK_AROUND -> { yaw += (float) Math.sin(t * .07) * 75; pitch = (float) Math.sin(t * .11) * 14 - 4; }
        }

        if (seat != null) {                                            // seated poses: move the seat, never teleport the rider
            Compat.move(seat, new Vec3(base.x, base.y + LhcConfig.get().seatOffsetY + y, base.z), yaw, 0);
            p.setYRot(yaw); p.setXRot(pitch); p.setYHeadRot(yaw);
        } else {
            Compat.place(p, base.x, base.y + y, base.z, yaw, pitch);
        }
    }

    void stop() {
        if (stopped) return;
        stopped = true;
        p.stopUsingItem();
        p.setShiftKeyDown(false);
        if (propsApplied) {
            p.setItemSlot(EquipmentSlot.MAINHAND, savedMain);
            p.setItemSlot(EquipmentSlot.OFFHAND, savedOff);
        }
        if (seat != null) { p.stopRiding(); seat.discard(); seat = null; }
        external(type.name() + ":stop");
    }

    // ---- helpers -----------------------------------------------------------------------------
    /** Temporarily swaps held items. Restored in stop() and on disconnect. */
    private void props(Item main, Item off) {
        savedMain = p.getItemBySlot(EquipmentSlot.MAINHAND).copy();
        savedOff = p.getItemBySlot(EquipmentSlot.OFFHAND).copy();
        propsApplied = true;
        if (main != null) p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(main));
        if (off != null) p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(off));
    }

    private void external(String key) {
        String cmd = LhcConfig.get().externalPoseCommands.get(key);
        if (cmd == null || cmd.isBlank()) return;
        var server = s.server;
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(),
                cmd.replace("{player}", p.getScoreboardName()));
    }
}

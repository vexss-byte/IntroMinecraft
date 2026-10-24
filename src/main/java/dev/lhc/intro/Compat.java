package dev.lhc.intro;

import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Every Minecraft call that has changed signature between recent versions lives here.
 * If a future 26.x snapshot breaks the build, the fix is almost always in THIS file only.
 */
public final class Compat {
    private Compat() {}

    /** /lhc requires op level 2 (gamemaster). Older versions: src -> src.hasPermission(2) */
    public static Predicate<CommandSourceStack> gamemaster() {
        return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
    }

    /** Per-tick, same-dimension placement of a player (also blocks the client's own movement). */
    public static void place(ServerPlayer p, double x, double y, double z, float yaw, float pitch) {
        p.connection.teleport(x, y, z, yaw, pitch);
    }

    /** Cross-dimension capable teleport (used for setup/restore). */
    public static void teleportAcross(ServerPlayer p, ServerLevel l, double x, double y, double z, float yaw, float pitch) {
        p.teleportTo(l, x, y, z, Set.of(), yaw, pitch, true);
    }

    public static GameType gameMode(ServerPlayer p) {
        return p.gameMode.getGameModeForPlayer();
    }

    /** Invisible, gravity-free armor stand. Used as the cinematic camera body and as a seat. */
    public static ArmorStand spawnStand(ServerLevel l, double x, double y, double z) {
        ArmorStand s = EntityType.ARMOR_STAND.create(l, EntitySpawnReason.COMMAND);
        s.setInvisible(true);
        s.setNoGravity(true);
        s.setInvulnerable(true);
        s.setSilent(true);
        s.setPos(x, y, z);
        l.addFreshEntity(s);
        return s;
    }

    public static void move(Entity e, Vec3 pos, float yaw, float pitch) {
        e.setPos(pos.x, pos.y, pos.z);
        e.setYRot(yaw);
        e.setXRot(pitch);
        if (e instanceof LivingEntity le) {
            le.setYHeadRot(yaw);
            le.setYBodyRot(yaw);
        }
    }

    public static void mount(ServerPlayer p, Entity vehicle) {
        p.startRiding(vehicle, true, true);
    }

    public static void setCamera(ServerPlayer viewer, Entity cameraEntity) {
        viewer.setCamera(cameraEntity);
    }
}

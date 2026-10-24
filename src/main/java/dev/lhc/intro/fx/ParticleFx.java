package dev.lhc.intro.fx;

import dev.lhc.intro.Types.ParticleType;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Three hooks per effect:
 *  trail()  every tick while the member is walking in (p = feet position)
 *  aura()   every tick while posing
 *  burst()  once when the pose starts
 */
public final class ParticleFx {
    private ParticleFx() {}

    // ---- low-level helpers -------------------------------------------------------------------
    private static void at(ServerLevel l, ParticleOptions o, double x, double y, double z, int n, double dx, double dy, double dz, double sp) {
        l.sendParticles(o, x, y, z, n, dx, dy, dz, sp);
    }

    /** count = 0 means (dx,dy,dz) is an exact velocity. */
    private static void vel(ServerLevel l, ParticleOptions o, double x, double y, double z, double vx, double vy, double vz) {
        l.sendParticles(o, x, y, z, 0, vx, vy, vz, 1.0);
    }

    private static void radial(ServerLevel l, ParticleOptions o, Vec3 c, int n, double speed, double lift) {
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            vel(l, o, c.x, c.y + 0.2, c.z, Math.cos(a) * speed, lift, Math.sin(a) * speed);
        }
    }

    private static void ring(ServerLevel l, ParticleOptions o, Vec3 c, double r, int n, double y, double phase, double up) {
        for (int i = 0; i < n; i++) {
            double a = phase + i * Math.PI * 2 / n;
            vel(l, o, c.x + Math.cos(a) * r, c.y + y, c.z + Math.sin(a) * r, 0, up, 0);
        }
    }

    private static void spiral(ServerLevel l, ParticleOptions o, Vec3 c, double r, int k, double spin, double h, int strands) {
        double y = ((k * 0.045) % 1.0) * h;
        for (int s = 0; s < strands; s++) {
            double a = k * spin + s * Math.PI * 2 / strands;
            at(l, o, c.x + Math.cos(a) * r, c.y + y, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    private static ParticleOptions rainbow(int k, int offset) {
        int rgb = Mth.hsvToRgb(((k * 9 + offset) % 360) / 360f, 0.85f, 1f);
        return new DustParticleOptions(rgb, 1.3f);
    }

    private static void note(ServerLevel l, double x, double y, double z, int colorIdx) {
        vel(l, ParticleTypes.NOTE, x, y, z, (colorIdx % 25) / 24.0, 0, 0);   // dx encodes the note colour
    }

    private static void arc(ServerLevel l, Vec3 c, double reach) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double a = r.nextDouble() * Math.PI * 2;
        Vec3 from = c.add(0, 1.0, 0);
        Vec3 to = c.add(Math.cos(a) * reach, r.nextDouble() * 2.0 - 0.2, Math.sin(a) * reach);
        int seg = 9;
        for (int i = 0; i <= seg; i++) {
            double t = i / (double) seg;
            Vec3 p = from.lerp(to, t);
            double j = (i == 0 || i == seg) ? 0 : 0.28;
            at(l, ParticleTypes.ELECTRIC_SPARK, p.x + (r.nextDouble() - .5) * j, p.y + (r.nextDouble() - .5) * j,
                    p.z + (r.nextDouble() - .5) * j, 1, 0, 0, 0, 0);
        }
    }

    // ---- appear / vanish ---------------------------------------------------------------------
    public static void appear(ServerLevel l, Vec3 p) {
        at(l, ParticleTypes.CLOUD, p.x, p.y + 1, p.z, 25, .4, .6, .4, 0.05);
        at(l, ParticleTypes.END_ROD, p.x, p.y + 1, p.z, 25, .3, .8, .3, 0.12);
    }

    public static void vanish(ServerLevel l, Vec3 p) {
        at(l, ParticleTypes.POOF, p.x, p.y + 1, p.z, 40, .4, .8, .4, 0.08);
        at(l, ParticleTypes.END_ROD, p.x, p.y + 1, p.z, 30, .4, 1.0, .4, 0.15);
    }

    // ---- entrance trail ----------------------------------------------------------------------
    public static void trail(ParticleType t, ServerLevel l, Vec3 p, int k) {
        double x = p.x, y = p.y, z = p.z;
        switch (t) {
            case HEART_SWIRL -> { if (k % 3 == 0) at(l, ParticleTypes.HEART, x, y + .3, z, 1, .3, .1, .3, 0);
                spiral(l, ParticleTypes.HEART, p, .8, k, .35, 2.0, 2); }
            case FIRE_SPARKS -> { at(l, ParticleTypes.FLAME, x, y + .1, z, 4, .2, .05, .2, .01);
                if (k % 4 == 0) at(l, ParticleTypes.LAVA, x, y + .3, z, 1, .3, .1, .3, 0); }
            case SNOWFLAKES -> { at(l, ParticleTypes.SNOWFLAKE, x, y + 1, z, 6, .5, .8, .5, 0.01);
                at(l, ParticleTypes.CLOUD, x, y + .1, z, 1, .15, 0, .15, 0.01); }
            case ENDER_PORTAL -> at(l, ParticleTypes.PORTAL, x, y + 1, z, 12, .5, 1.0, .5, 0.4);
            case RAINBOW_TRAIL -> { for (int i = 0; i < 4; i++) at(l, rainbow(k, i * 20), x, y + .15 + i * .05, z, 2, .15, .02, .15, 0); }
            case FLOWER_BURST -> { at(l, ParticleTypes.CHERRY_LEAVES, x, y + .8, z, 3, .4, .6, .4, 0.01);
                if (k % 5 == 0) at(l, ParticleTypes.HAPPY_VILLAGER, x, y + .6, z, 2, .4, .4, .4, 0); }
            case STAR_SPIRAL -> { spiral(l, ParticleTypes.END_ROD, p, .7, k, .5, 2.2, 3);
                at(l, ParticleTypes.GLOW, x, y + .2, z, 1, .2, .1, .2, 0); }
            case BUBBLE_FOUNTAIN -> { at(l, ParticleTypes.BUBBLE, x, y + .5, z, 6, .35, .5, .35, 0.15);
                at(l, ParticleTypes.SPLASH, x, y + .1, z, 4, .25, 0, .25, 0.1); }
            case LIGHTNING_ARC -> { at(l, ParticleTypes.ELECTRIC_SPARK, x, y + .8, z, 5, .4, .7, .4, 0.2);
                if (k % 6 == 0) arc(l, p, 1.8); }
            case NOTE_RAIN -> { if (k % 2 == 0) note(l, x + Math.cos(k * .5) * .6, y + 2.2, z + Math.sin(k * .5) * .6, k); }
            case TOTEM_BURST -> at(l, ParticleTypes.TOTEM_OF_UNDYING, x, y + .6, z, 3, .3, .5, .3, 0.2);
            case SOUL_VORTEX -> { at(l, ParticleTypes.SOUL, x, y + .2, z, 2, .2, .1, .2, 0.02);
                spiral(l, ParticleTypes.SOUL_FIRE_FLAME, p, .6, k, .4, 1.8, 2); }
            default -> { }
        }
    }

    // ---- pose aura ---------------------------------------------------------------------------
    public static void aura(ParticleType t, ServerLevel l, Vec3 p, int k) {
        double x = p.x, y = p.y, z = p.z;
        switch (t) {
            case HEART_SWIRL -> { spiral(l, ParticleTypes.HEART, p, .9, k, .22, 2.4, 3);
                if (k % 20 == 0) radial(l, ParticleTypes.HEART, p.add(0, .8, 0), 10, .12, .05); }
            case FIRE_SPARKS -> { ring(l, ParticleTypes.FLAME, p, 1.0, 10, .1, k * .15, .06);
                at(l, ParticleTypes.LAVA, x, y + 1, z, 1, 1.0, .5, 1.0, 0);
                if (k % 25 == 0) at(l, ParticleTypes.EXPLOSION, x, y + 1.2, z, 1, .8, .5, .8, 0); }
            case SNOWFLAKES -> { at(l, ParticleTypes.SNOWFLAKE, x, y + 3.2, z, 10, 1.3, .2, 1.3, 0.02);
                at(l, ParticleTypes.WHITE_ASH, x, y + 1.5, z, 4, 1.2, 1.2, 1.2, 0); }
            case ENDER_PORTAL -> { ring(l, ParticleTypes.PORTAL, p, 1.6, 8, 1.0, k * .1, 0);
                at(l, ParticleTypes.REVERSE_PORTAL, x, y + 1, z, 6, .4, 1.0, .4, 0.03); }
            case RAINBOW_TRAIL -> { spiral(l, rainbow(k, 0), p, .85, k, .3, 2.4, 1);
                spiral(l, rainbow(k, 180), p, .85, k + 20, .3, 2.4, 1);
                spiral(l, rainbow(k, 90), p, 1.1, k, -.2, 2.4, 1); }
            case FLOWER_BURST -> { at(l, ParticleTypes.CHERRY_LEAVES, x, y + 3, z, 6, 1.4, .2, 1.4, 0.01);
                at(l, ParticleTypes.SPORE_BLOSSOM_AIR, x, y + 1.5, z, 2, 1.2, 1.2, 1.2, 0); }
            case STAR_SPIRAL -> { double r = .4 + ((k % 60) / 60.0) * 1.4;
                for (int s = 0; s < 3; s++) { double a = k * .4 + s * Math.PI * 2 / 3;
                    at(l, ParticleTypes.END_ROD, x + Math.cos(a) * r, y + .2 + (k % 60) / 60.0 * 2.3, z + Math.sin(a) * r, 1, 0, 0, 0, 0); } }
            case BUBBLE_FOUNTAIN -> { vel(l, ParticleTypes.BUBBLE, x, y + .2, z, 0.0, 0.5 + (k % 7) * .04, 0.0);
                at(l, ParticleTypes.BUBBLE, x, y + .3, z, 4, .15, .1, .15, .35);
                ring(l, ParticleTypes.SPLASH, p, 1.1, 6, .1, k * .2, .02);
                at(l, ParticleTypes.BUBBLE_POP, x, y + 1.5, z, 2, 1, 1, 1, 0); }
            case LIGHTNING_ARC -> { if (k % 5 == 0) arc(l, p, 2.4);
                at(l, ParticleTypes.ELECTRIC_SPARK, x, y + 1, z, 4, .7, 1.0, .7, 0.3);
                if (k % 50 == 0) l.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.5f, 1.4f); }
            case NOTE_RAIN -> { ring(l, ParticleTypes.NOTE, p, 1.2, 1, 2.0 + Math.sin(k * .2) * .3, k * .3, 0);
                if (k % 3 == 0) note(l, x + Math.cos(k * .3) * 1.2, y + 2.0 + Math.sin(k * .2) * .3, z + Math.sin(k * .3) * 1.2, k / 2); }
            case TOTEM_BURST -> { at(l, ParticleTypes.TOTEM_OF_UNDYING, x, y + .1, z, 4, .4, .1, .4, 0.9);
                ring(l, ParticleTypes.TOTEM_OF_UNDYING, p, 1.0, 4, .1, k * .2, .5); }
            case SOUL_VORTEX -> { for (int i = 0; i < 3; i++) { double hh = ((k * .05 + i * .33) % 1.0) * 2.8;
                    double r = .3 + hh * .45, a = k * .35 + i * 2.1;
                    at(l, ParticleTypes.SOUL_FIRE_FLAME, x + Math.cos(a) * r, y + hh, z + Math.sin(a) * r, 1, 0, 0, 0, 0); }
                if (k % 4 == 0) at(l, ParticleTypes.SOUL, x, y + .3, z, 1, .6, .2, .6, .02); }
            default -> { }
        }
    }

    // ---- pose-start burst --------------------------------------------------------------------
    public static void burst(ParticleType t, ServerLevel l, Vec3 p) {
        Vec3 c = p.add(0, .2, 0);
        l.playSound(null, p.x, p.y, p.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.0f);
        switch (t) {
            case HEART_SWIRL -> radial(l, ParticleTypes.HEART, c, 24, .25, .08);
            case FIRE_SPARKS -> { radial(l, ParticleTypes.FLAME, c, 36, .3, .12); at(l, ParticleTypes.LAVA, p.x, p.y + 1, p.z, 25, .6, .5, .6, 0);
                at(l, ParticleTypes.EXPLOSION, p.x, p.y + 1, p.z, 2, .4, .3, .4, 0); }
            case SNOWFLAKES -> { radial(l, ParticleTypes.SNOWFLAKE, c, 40, .22, .1); at(l, ParticleTypes.CLOUD, p.x, p.y + 1, p.z, 20, .6, .6, .6, .05); }
            case ENDER_PORTAL -> { radial(l, ParticleTypes.REVERSE_PORTAL, c, 40, .35, .1); at(l, ParticleTypes.PORTAL, p.x, p.y + 1, p.z, 80, .5, 1, .5, 1.0); }
            case RAINBOW_TRAIL -> { for (int i = 0; i < 48; i++) { double a = i * Math.PI * 2 / 48;
                    vel(l, rainbow(i, 0), c.x, c.y + .5, c.z, Math.cos(a) * .3, .12, Math.sin(a) * .3); } }
            case FLOWER_BURST -> { radial(l, ParticleTypes.CHERRY_LEAVES, c, 40, .3, .3); at(l, ParticleTypes.HAPPY_VILLAGER, p.x, p.y + 1, p.z, 30, .8, .8, .8, 0); }
            case STAR_SPIRAL -> { radial(l, ParticleTypes.FIREWORK, c, 36, .28, .15); radial(l, ParticleTypes.END_ROD, c, 24, .15, .3); }
            case BUBBLE_FOUNTAIN -> { radial(l, ParticleTypes.BUBBLE, c, 36, .25, .35); at(l, ParticleTypes.SPLASH, p.x, p.y + .5, p.z, 40, .6, .3, .6, .2); }
            case LIGHTNING_ARC -> { for (int i = 0; i < 8; i++) arc(l, p, 3.0);
                l.playSound(null, p.x, p.y, p.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8f, 1.2f);
                at(l, ParticleTypes.END_ROD, p.x, p.y + 2, p.z, 30, .1, 2.0, .1, .02); }
            case NOTE_RAIN -> { for (int i = 0; i < 24; i++) { double a = i * Math.PI * 2 / 24; note(l, p.x + Math.cos(a) * 1.5, p.y + 1.2 + (i % 3) * .5, p.z + Math.sin(a) * 1.5, i); } }
            case TOTEM_BURST -> { radial(l, ParticleTypes.TOTEM_OF_UNDYING, c, 40, .45, .35); at(l, ParticleTypes.TOTEM_OF_UNDYING, p.x, p.y + 1, p.z, 60, .3, .8, .3, .9); }
            case SOUL_VORTEX -> { radial(l, ParticleTypes.SOUL_FIRE_FLAME, c, 36, .2, .2); at(l, ParticleTypes.SOUL, p.x, p.y + 1, p.z, 30, .5, 1, .5, .06); }
            default -> { }
        }
    }
}

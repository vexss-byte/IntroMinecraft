package dev.lhc.intro.camera;

import dev.lhc.intro.Types.CameraStyle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Pure maths camera. Each tick: update(focus, travelYaw) returns the camera pose.
 * - a "shot" = a style + length + parameters; progress t (0..1) drives zoom/crane/orbit moves
 * - switching shots blends smoothly (smoothstep) from the previous camera pose
 * - the focus point is low-pass filtered so tracking shots never jitter
 */
public final class CameraRig {
    public record State(Vec3 pos, float yaw, float pitch) {}

    private State cur, from;
    private int blendLen, blendT;
    private CameraStyle style = CameraStyle.ZOOM_IN;
    private int shotTick, shotLen = 1;
    private boolean frozen;
    private double dist = 5, height = 1.8;
    private float frontYaw, orbitDeg = 200;
    private Vec3 anchor, focus;

    public State current() { return cur; }

    /** Hard cut to an exact pose (used by the opening orbit). */
    public void snap(State s) { cur = s; from = s; blendLen = 0; blendT = 0; }

    public void resetFocus() { focus = null; }

    public void shot(CameraStyle st, int len, double dist, double height, float frontYaw,
                     Vec3 anchor, float orbitDeg, int blendTicks, boolean frozen) {
        this.style = st; this.shotLen = Math.max(1, len); this.shotTick = 0;
        this.dist = dist; this.height = height; this.frontYaw = frontYaw;
        this.anchor = anchor; this.orbitDeg = orbitDeg;
        this.from = cur; this.blendLen = blendTicks; this.blendT = 0; this.frozen = frozen;
    }

    public void unfreeze() { frozen = false; }

    public State update(Vec3 focusTarget, float travelYaw) {
        focus = (focus == null) ? focusTarget : focus.lerp(focusTarget, 0.3);
        float t = Mth.clamp(shotTick / (float) shotLen, 0f, 1f);
        State tgt = target(t, travelYaw);
        State out = tgt;
        if (from == null) from = tgt;
        if (blendT < blendLen) {
            blendT++;
            out = lerp(from, tgt, smooth(blendT / (float) blendLen));
        }
        if (!frozen) shotTick++;
        cur = out;
        return out;
    }

    // ---- shot maths --------------------------------------------------------------------------
    private State target(float t, float travelYaw) {
        float e = smooth(t);
        Vec3 f = focus.add(0, 0.9, 0);          // chest height
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 pos, look = f.add(0, 0.3, 0);

        switch (style) {
            case TRACK_BEHIND -> pos = f.subtract(dir(travelYaw).scale(dist)).add(0, height * 0.6, 0);
            case TRACK_FRONT -> pos = f.add(dir(travelYaw).scale(dist * 1.1)).add(0, height * 0.4, 0);
            case TRACK_SIDE -> pos = f.add(dir(travelYaw + 90).scale(dist * 1.05)).add(0, height * 0.45, 0);
            case PAN_STATIC -> pos = anchor != null ? anchor
                    : f.add(dir(frontYaw + 35).scale(dist * 1.6)).add(0, height * 0.5, 0);
            case ZOOM_IN -> {
                double d = Mth.lerp(e, dist * 2.4, dist * 0.85);
                pos = f.add(dir(frontYaw + 18 * (1 - e)).scale(d)).add(0, height * (0.35 + 0.35 * (1 - e)), 0);
            }
            case ZOOM_OUT -> {
                double d = Mth.lerp(e, dist * 0.7, dist * 2.2);
                pos = f.add(dir(frontYaw - 14 * e).scale(d)).add(0, height * (0.3 + 0.4 * e), 0);
            }
            case CRANE_DOWN -> pos = f.add(dir(frontYaw).scale(dist * 1.3))
                    .add(0, Mth.lerp(e, height * 3.2, height * 0.2), 0);
            case ORBIT -> {
                double ang = frontYaw + orbitDeg * e - orbitDeg * 0.35;
                pos = f.add(dir(ang).scale(dist * 1.1)).add(0, height * 0.35, 0);
            }
            case LOW_HERO -> {
                double d = Mth.lerp(e, dist * 1.0, dist * 0.7);
                pos = new Vec3(f.x, f.y - 0.9 + 0.25, f.z).add(dir(frontYaw + 12).scale(d));
                look = f.add(0, 0.5, 0);
            }
            case TOP_DOWN -> {
                return new State(f.add(up.scale(height * 6)), travelYaw, 88f);
            }
            default -> pos = f.add(dir(frontYaw).scale(dist)).add(0, height * 0.4, 0);
        }
        return lookAt(pos, look);
    }

    // ---- static helpers ----------------------------------------------------------------------
    public static Vec3 dir(double yawDeg) {
        double r = Math.toRadians(yawDeg);
        return new Vec3(-Math.sin(r), 0, Math.cos(r));
    }

    public static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3 - 2 * x);
    }

    public static State lookAt(Vec3 pos, Vec3 tgt) {
        double dx = tgt.x - pos.x, dy = tgt.y - pos.y, dz = tgt.z - pos.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        return new State(pos, yaw, pitch);
    }

    /** Opening: top-down orbit around the stage. t = 0..1 across the whole revolution. */
    public static State orbit(Vec3 center, double radius, double height, float t, double degrees) {
        double ang = Math.toRadians(degrees * smooth(t));
        Vec3 pos = center.add(Math.cos(ang) * radius, height, Math.sin(ang) * radius);
        return lookAt(pos, center);
    }

    private static State lerp(State a, State b, float k) {
        float dy = Mth.wrapDegrees(b.yaw - a.yaw);
        return new State(a.pos.lerp(b.pos, k), a.yaw + dy * k, Mth.lerp(k, a.pitch, b.pitch));
    }
}

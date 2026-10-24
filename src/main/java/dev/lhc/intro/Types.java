package dev.lhc.intro;

/** All configurable "type" enums in one place. Names are what you type in commands / config. */
public final class Types {
    private Types() {}

    public enum CameraStyle {
        TRACK_BEHIND,   // follows behind the player
        TRACK_FRONT,    // leads in front of the player, looking back
        TRACK_SIDE,     // dollies alongside the player
        PAN_STATIC,     // fixed tripod that pans to follow the player
        ZOOM_IN,        // slow push-in
        ZOOM_OUT,       // slow pull-back reveal
        CRANE_DOWN,     // high crane shot descending to eye level
        ORBIT,          // partial orbit around the player
        LOW_HERO,       // low angle, looking up, slow push-in
        TOP_DOWN        // straight overhead
    }

    /** speed = blocks/second, hop = jump height + hop length (blocks), cam = default entrance camera */
    public enum EntranceType {
        WALK(4.3, 0.0, 0.0, false, CameraStyle.TRACK_BEHIND),
        STROLL(2.6, 0.0, 0.0, false, CameraStyle.TRACK_SIDE),
        RUN(5.6, 0.0, 0.0, true, CameraStyle.TRACK_SIDE),
        SPRINT(8.5, 0.0, 0.0, true, CameraStyle.TRACK_FRONT),
        JUMP(4.0, 1.0, 2.6, false, CameraStyle.CRANE_DOWN),
        BUNNY(6.5, 0.55, 2.2, true, CameraStyle.PAN_STATIC);

        public final double bps, hopHeight, hopLength;
        public final boolean sprint;
        public final CameraStyle defaultCamera;

        EntranceType(double bps, double hopHeight, double hopLength, boolean sprint, CameraStyle cam) {
            this.bps = bps; this.hopHeight = hopHeight; this.hopLength = hopLength;
            this.sprint = sprint; this.defaultCamera = cam;
        }
    }

    public enum PoseType {
        ARMS_CROSSED, WAVE, BOW, SWORD_SALUTE, DANCE, SIT, POINT, FLEX, MEDITATE, LOOK_AROUND
    }

    public enum ParticleType {
        NONE, HEART_SWIRL, FIRE_SPARKS, SNOWFLAKES, ENDER_PORTAL, RAINBOW_TRAIL, FLOWER_BURST,
        STAR_SPIRAL, BUBBLE_FOUNTAIN, LIGHTNING_ARC, NOTE_RAIN, TOTEM_BURST, SOUL_VORTEX
    }

    // Auto-assignment rotation so consecutive members always differ.
    public static final EntranceType[] ENTRANCE_ROTATION = {
        EntranceType.WALK, EntranceType.RUN, EntranceType.JUMP, EntranceType.SPRINT,
        EntranceType.BUNNY, EntranceType.STROLL
    };
    public static final CameraStyle[] POSE_CAMERA_ROTATION = {
        CameraStyle.ZOOM_IN, CameraStyle.ORBIT, CameraStyle.CRANE_DOWN, CameraStyle.LOW_HERO, CameraStyle.ZOOM_OUT
    };

    public static ParticleType rotatingParticle(int i) {
        ParticleType[] v = ParticleType.values();
        return v[1 + (i % (v.length - 1))];
    }
}

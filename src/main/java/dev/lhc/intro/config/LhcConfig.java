package dev.lhc.intro.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.lhc.intro.LhcMod;
import dev.lhc.intro.Types.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Everything lives in config/lhc_intro.json. Commands edit it and save automatically. */
public class LhcConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("lhc_intro.json");
    private static LhcConfig instance = new LhcConfig();

    // ---- texts -------------------------------------------------------------------------------
    public String introTitle = "LAPER HARDCORE";
    public String introSubtitle = "PERKENALAN MEMBER";
    public String defaultRole = "LAPER HARDCORE";
    public String defaultDescription = "Welcome to the squad!";
    public String defaultQuote = "Let's gooo!";

    // ---- stage -------------------------------------------------------------------------------
    public Spot center = new Spot();               // center stage; yaw = direction the member FACES when posing
    public Opening opening = new Opening();
    public Timing timing = new Timing();
    public CameraDefaults camera = new CameraDefaults();

    /** Y offset (relative to ground) of the invisible seat for SIT / MEDITATE. Tweak until it looks right. */
    public double seatOffsetY = -1.8;

    /** All non-member players online become spectator viewers of the cinematic. */
    public boolean includeAllNonMembersAsViewers = true;

    /**
     * Hook for external animation mods (Emotecraft etc). Key = POSE name, value = console command,
     * {player} is replaced by the member's name. Key "POSE:stop" runs when the pose ends.
     * Example: "ARMS_CROSSED": "emotes play {player} arms_crossed"  (use whatever your mod's server command is)
     */
    public Map<String, String> externalPoseCommands = new LinkedHashMap<>();

    public List<Member> members = new ArrayList<>();

    // ---- nested types ------------------------------------------------------------------------
    public static class Spot {
        public boolean set = false;
        public String dimension = "minecraft:overworld";
        public double x, y, z;
        public float yaw;

        public Vec3 vec() { return new Vec3(x, y, z); }

        public static Spot of(ServerLevel l, Vec3 p, float yaw) {
            Spot s = new Spot();
            s.set = true; s.dimension = Dim.id(l);
            s.x = p.x; s.y = p.y; s.z = p.z; s.yaw = yaw;
            return s;
        }
    }

    public static class Opening {
        public boolean enabled = true;
        public double radius = 14;       // orbit radius around center
        public double height = 30;       // orbit height above center
        public int orbitTicks = 260;     // one full revolution
        public double degrees = 360;     // how far to go around
        public int zoomTicks = 70;       // glide from orbit down to the first member
    }

    public static class Timing {
        public int prepareTicks = 30;       // member appears at start point, camera settles
        public int poseTicks = 160;         // default pose duration (8s)
        public int exitTicks = 14;          // lingering shot after the member vanishes
        public int betweenBlendTicks = 26;  // camera glide between members
    }

    public static class CameraDefaults {
        public double distance = 5.0;
        public double height = 1.8;
        public double orbitDegrees = 200;
    }

    public static class Member {
        public String name;
        public String role, description, quote;
        public Spot start = new Spot();
        public List<Spot> waypoints = new ArrayList<>();   // optional extra path points between start and center
        public EntranceType entrance;
        public PoseType pose;
        public ParticleType particle;
        public CameraStyle entranceCamera;   // null = default for the entrance type
        public CameraStyle poseCamera;       // null = rotating default
        public Double speed;                 // blocks/second override
        public Double poseSeconds;           // pose duration override
        public Double cameraDistance, cameraHeight;
    }

    // ---- helpers -----------------------------------------------------------------------------
    public static LhcConfig get() { return instance; }

    public Member find(String name) {
        for (Member m : members) if (m.name.equalsIgnoreCase(name)) return m;
        return null;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                LhcConfig c = GSON.fromJson(Files.readString(FILE), LhcConfig.class);
                if (c != null) instance = c;
            } else {
                instance.save();
            }
        } catch (Exception e) {
            LhcMod.LOG.error("Could not read lhc_intro.json, keeping previous settings", e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(this));
        } catch (IOException e) {
            LhcMod.LOG.error("Could not save lhc_intro.json", e);
        }
    }

    /** Dimension id helpers (stored as "minecraft:overworld" in the config). */
    public static final class Dim {
        public static String id(ServerLevel l) {
            String s = l.dimension().toString();           // ResourceKey[minecraft:dimension / minecraft:overworld]
            int i = s.lastIndexOf("/ "), j = s.lastIndexOf(']');
            return (i >= 0 && j > i) ? s.substring(i + 2, j).trim() : s;
        }

        public static ServerLevel find(MinecraftServer srv, String id) {
            for (ServerLevel l : srv.getAllLevels()) if (id(l).equals(id)) return l;
            return srv.overworld();
        }
    }
}

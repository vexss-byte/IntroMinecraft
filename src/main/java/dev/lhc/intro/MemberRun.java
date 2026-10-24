package dev.lhc.intro;

import dev.lhc.intro.Types.*;
import dev.lhc.intro.config.LhcConfig;
import dev.lhc.intro.config.LhcConfig.Member;
import dev.lhc.intro.config.LhcConfig.Spot;
import dev.lhc.intro.fx.ParticleFx;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

/** Everything about ONE member's turn: path walking, hopping, saving/restoring the player, name tag. */
final class MemberRun {
    static final String TEAM = "lhc_star";

    final Session s;
    final Member m;
    final ServerPlayer star;
    final int number, total;
    final ServerLevel level;

    final List<Vec3> pts = new ArrayList<>();
    double[] cum;
    double pathLen;
    final EntranceType et;
    final double bpt;            // blocks per tick
    final int poseTicks;
    final PoseType poseType;
    final ParticleType particle;

    double traveled;
    float yaw;
    PoseController pose;

    // saved state
    private ServerLevel prevLevel;
    private Vec3 prevPos;
    private float prevYaw, prevPitch;
    private GameType prevMode;
    private boolean prevInvuln, restored;
    private PlayerTeam prevTeam;

    MemberRun(Session s, Member m, ServerPlayer star, int number, int total) {
        this.s = s; this.m = m; this.star = star; this.number = number; this.total = total;
        LhcConfig cfg = s.cfg;
        this.level = s.level;

        pts.add(m.start.vec());
        for (Spot w : m.waypoints) pts.add(w.vec());
        pts.add(cfg.center.vec());
        cum = new double[pts.size()];
        for (int i = 1; i < pts.size(); i++) cum[i] = cum[i - 1] + pts.get(i).distanceTo(pts.get(i - 1));
        pathLen = cum[cum.length - 1];

        et = m.entrance != null ? m.entrance : EntranceType.WALK;
        bpt = (m.speed != null ? m.speed : et.bps) / 20.0;
        poseTicks = (int) (m.poseSeconds != null ? m.poseSeconds * 20 : cfg.timing.poseTicks);
        poseType = m.pose != null ? m.pose : PoseType.values()[(number - 1) % PoseType.values().length];
        particle = m.particle != null ? m.particle : Types.rotatingParticle(number - 1);
        yaw = segmentYaw(0);
    }

    int entranceTicks() { return (int) Math.ceil(pathLen / bpt) + 1; }

    Vec3 startPos() { return pts.get(0); }
    Vec3 centerPos() { return pts.get(pts.size() - 1); }

    // ---- lifecycle ---------------------------------------------------------------------------
    void setup() {
        prevLevel = (ServerLevel) star.level();
        prevPos = star.position();
        prevYaw = star.getYRot();
        prevPitch = star.getXRot();
        prevMode = Compat.gameMode(star);
        prevInvuln = star.isInvulnerable();

        star.setGameMode(GameType.ADVENTURE);
        star.setInvulnerable(true);
        applyNameTag();

        Vec3 p = startPos();
        Compat.teleportAcross(star, level, p.x, p.y, p.z, yaw, 0);
        ParticleFx.appear(level, p);
    }

    void restore() {
        if (restored) return;
        restored = true;
        if (pose != null) pose.stop();
        star.setSprinting(false);
        star.setShiftKeyDown(false);
        removeNameTag();
        star.setInvulnerable(prevInvuln);
        star.setGameMode(prevMode);
        Compat.teleportAcross(star, prevLevel, prevPos.x, prevPos.y, prevPos.z, prevYaw, prevPitch);
    }

    // ---- entrance ----------------------------------------------------------------------------
    void hold() {
        Vec3 p = startPos();
        Compat.place(star, p.x, p.y, p.z, yaw, 0);
    }

    /** @return true when the center has been reached */
    boolean stepEntrance(int tick) {
        if (tick == 0) star.setSprinting(et.sprint);
        traveled = Math.min(pathLen, traveled + bpt);
        boolean done = traveled >= pathLen - 1e-6;

        Vec3 pos = sample(traveled);
        double hop = 0;
        if (et.hopHeight > 0 && !done) {
            double u = (traveled / et.hopLength) % 1.0;
            double fade = Math.min(1.0, (pathLen - traveled) / et.hopLength);   // land softly at the center
            hop = et.hopHeight * 4 * u * (1 - u) * fade;
        }
        float target = segmentYaw(traveled);
        yaw += Mth.wrapDegrees(target - yaw) * 0.25f;

        Compat.place(star, pos.x, pos.y + hop, pos.z, yaw, 0);
        ParticleFx.trail(particle, level, pos, tick);
        return done;
    }

    void beginPose() {
        star.setSprinting(false);
        pose = new PoseController(s, star, poseType, centerPos(), s.cfg.center.yaw, poseTicks);
        pose.start();
        ParticleFx.burst(particle, level, centerPos());
    }

    void tickPose(int t) {
        pose.tick(t);
        ParticleFx.aura(particle, level, centerPos(), t);
    }

    // ---- path maths --------------------------------------------------------------------------
    private Vec3 sample(double d) {
        for (int i = 1; i < pts.size(); i++) {
            if (d <= cum[i] || i == pts.size() - 1) {
                double seg = cum[i] - cum[i - 1];
                double t = seg < 1e-9 ? 1 : Mth.clamp((d - cum[i - 1]) / seg, 0, 1);
                return pts.get(i - 1).lerp(pts.get(i), t);
            }
        }
        return pts.get(0);
    }

    private float segmentYaw(double d) {
        for (int i = 1; i < pts.size(); i++) {
            if (d <= cum[i] || i == pts.size() - 1) {
                Vec3 a = pts.get(i - 1), b = pts.get(i);
                if (a.distanceToSqr(b) < 1e-9) continue;
                return (float) Math.toDegrees(Math.atan2(-(b.x - a.x), b.z - a.z));
            }
        }
        return s.cfg.center.yaw;
    }

    // ---- name tag: [03] Name, gold, via a scoreboard team prefix (pure vanilla) ----------------
    private void applyNameTag() {
        Scoreboard sb = s.server.getScoreboard();
        PlayerTeam team = sb.getPlayerTeam(TEAM);
        if (team == null) team = sb.addPlayerTeam(TEAM);
        prevTeam = (PlayerTeam) star.getTeam();
        team.setPlayerPrefix(Component.literal(String.format("[%02d] ", number)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        team.setColor(ChatFormatting.GOLD);
        team.setNameTagVisibility(Team.Visibility.ALWAYS);
        sb.addPlayerToPlayerTeam(star.getScoreboardName(), team);
    }

    private void removeNameTag() {
        Scoreboard sb = s.server.getScoreboard();
        PlayerTeam team = sb.getPlayerTeam(TEAM);
        if (prevTeam != null && prevTeam != team) sb.addPlayerToPlayerTeam(star.getScoreboardName(), prevTeam);
        else if (team != null) sb.removePlayerFromTeam(star.getScoreboardName(), team);
        if (team != null) sb.removePlayerTeam(team);
    }
}

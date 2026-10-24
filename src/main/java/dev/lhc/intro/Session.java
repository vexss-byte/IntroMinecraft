package dev.lhc.intro;

import dev.lhc.intro.Types.CameraStyle;
import dev.lhc.intro.camera.CameraRig;
import dev.lhc.intro.camera.CameraRig.State;
import dev.lhc.intro.config.LhcConfig;
import dev.lhc.intro.config.LhcConfig.Member;
import dev.lhc.intro.config.LhcConfig.Dim;
import dev.lhc.intro.fx.ParticleFx;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** One running intro. Ticked from IntroDirector. */
final class Session {
    enum Phase { ORBIT, PREPARE, ENTRANCE, POSE, EXIT, DONE }

    private static final class Viewer {
        GameType mode; Vec3 pos; float yaw, pitch; ServerLevel level; boolean applied;
    }

    final MinecraftServer server;
    final LhcConfig cfg = LhcConfig.get();
    final ServerLevel level;
    private final List<Integer> order;
    private final ServerPlayer director;
    private final Map<UUID, Viewer> viewers = new LinkedHashMap<>();
    private final ArmorStand cam;
    private final CameraRig rig = new CameraRig();

    private Phase phase;
    private int pt, cursor;
    private MemberRun run;
    private boolean firstAfterOpening;
    private State lastOrbit;

    Session(MinecraftServer server, ServerPlayer director, List<Integer> order, boolean opening) {
        this.server = server; this.director = director; this.order = order;
        this.level = Dim.find(server, cfg.center.dimension);

        Vec3 c = cfg.center.vec();
        State first = CameraRig.orbit(c, cfg.opening.radius, cfg.opening.height, 0, cfg.opening.degrees);
        cam = Compat.spawnStand(level, first.pos().x, first.pos().y, first.pos().z);

        for (ServerPlayer pl : server.getPlayerList().getPlayers()) {
            boolean isMember = cfg.find(pl.getScoreboardName()) != null;
            boolean wanted = pl == director || cfg.includeAllNonMembersAsViewers;
            if (wanted && !isMember) viewers.put(pl.getUUID(), new Viewer());
        }

        if (opening && cfg.opening.enabled) {
            phase = Phase.ORBIT; pt = 0;
            rig.snap(first);
            firstAfterOpening = true;
        } else {
            beginMember();
        }
    }

    // ---- main tick ---------------------------------------------------------------------------
    /** @return true when finished */
    boolean tick() {
        switch (phase) {
            case ORBIT -> tickOrbit();
            case PREPARE -> tickPrepare();
            case ENTRANCE -> tickEntrance();
            case POSE -> tickPose();
            case EXIT -> tickExit();
            default -> { }
        }
        syncViewers();
        return phase == Phase.DONE;
    }

    private void tickOrbit() {
        LhcConfig.Opening o = cfg.opening;
        if (pt == 0) {
            broadcast(new ClientboundSetTitlesAnimationPacket(15, o.orbitTicks - 30, 30));
            broadcast(new ClientboundSetSubtitleTextPacket(Component.literal(cfg.introSubtitle).withStyle(ChatFormatting.WHITE)));
            broadcast(new ClientboundSetTitleTextPacket(Component.literal(cfg.introTitle).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        }
        State st = CameraRig.orbit(cfg.center.vec(), o.radius, o.height, pt / (float) o.orbitTicks, o.degrees);
        rig.snap(st);
        applyCam(st);
        if (++pt >= o.orbitTicks) { lastOrbit = st; beginMember(); }
    }

    private void tickPrepare() {
        run.hold();
        camUpdate(run.startPos(), run.yaw);
        int need = firstAfterOpeningFlag ? cfg.opening.zoomTicks : cfg.timing.prepareTicks;
        if (++pt >= need) {
            phase = Phase.ENTRANCE; pt = 0;
            rig.unfreeze();
        }
    }

    private boolean firstAfterOpeningFlag;

    private void tickEntrance() {
        boolean done = run.stepEntrance(pt);
        camUpdate(run.star.position(), run.yaw);
        pt++;
        if (done) startPose();
    }

    private void tickPose() {
        run.tickPose(pt);
        camUpdate(run.centerPos(), cfg.center.yaw);
        if (pt % 40 == 0) fallbackActionBar();
        if (++pt >= run.poseTicks) endPose();
    }

    private void tickExit() {
        camUpdate(run.centerPos(), cfg.center.yaw);
        if (++pt >= cfg.timing.exitTicks) { run = null; cursor++; beginMember(); }
    }

    // ---- member flow -------------------------------------------------------------------------
    private void beginMember() {
        while (true) {
            if (cursor >= order.size()) { phase = Phase.DONE; return; }
            int idx = order.get(cursor);
            Member m = cfg.members.get(idx);
            ServerPlayer star = server.getPlayerList().getPlayerByName(m.name);
            if (star == null) {
                tell(Component.literal("[LHC] " + m.name + " is offline, skipping.").withStyle(ChatFormatting.YELLOW));
                cursor++;
                continue;
            }
            run = new MemberRun(this, m, star, idx + 1, cfg.members.size());
            break;
        }

        run.setup();
        phase = Phase.PREPARE; pt = 0;
        firstAfterOpeningFlag = firstAfterOpening && lastOrbit != null;

        Member m = run.m;
        double dist = m.cameraDistance != null ? m.cameraDistance : cfg.camera.distance;
        double height = m.cameraHeight != null ? m.cameraHeight : cfg.camera.height;
        CameraStyle style = m.entranceCamera != null ? m.entranceCamera : run.et.defaultCamera;

        // PAN_STATIC tripod: beside the middle of the path
        Vec3 a = run.startPos(), b = run.centerPos();
        float pathYaw = (float) Math.toDegrees(Math.atan2(-(b.x - a.x), b.z - a.z));
        Vec3 anchor = a.lerp(b, 0.5).add(CameraRig.dir(pathYaw + 90).scale(dist * 2.4)).add(0, height * 0.8 + 0.9, 0);

        int blend;
        if (firstAfterOpeningFlag) { rig.snap(lastOrbit); blend = cfg.opening.zoomTicks; }
        else { blend = cfg.timing.betweenBlendTicks; }
        firstAfterOpening = false;

        rig.resetFocus();
        rig.shot(style, run.entranceTicks(), dist, height, cfg.center.yaw, anchor,
                (float) cfg.camera.orbitDegrees, blend, true);
    }

    private void startPose() {
        phase = Phase.POSE; pt = 0;
        run.beginPose();
        card(true);
        long stay = Math.max(20, run.poseTicks - 20);
        broadcast(new ClientboundSetTitlesAnimationPacket(10, (int) stay, 10));
        broadcast(new ClientboundSetTitleTextPacket(Component.literal(" ")));
        String quote = run.m.quote != null ? run.m.quote : cfg.defaultQuote;
        broadcast(new ClientboundSetSubtitleTextPacket(
                Component.literal("\u201C" + quote + "\u201D").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC)));

        Member m = run.m;
        double dist = m.cameraDistance != null ? m.cameraDistance : cfg.camera.distance;
        double height = m.cameraHeight != null ? m.cameraHeight : cfg.camera.height;
        CameraStyle style = m.poseCamera != null ? m.poseCamera
                : Types.POSE_CAMERA_ROTATION[(run.number - 1) % Types.POSE_CAMERA_ROTATION.length];
        Vec3 anchor = cfg.center.vec().add(CameraRig.dir(cfg.center.yaw + 35).scale(dist * 1.6)).add(0, height * 0.5 + 0.9, 0);
        rig.shot(style, run.poseTicks, dist, height, cfg.center.yaw, anchor, (float) cfg.camera.orbitDegrees, 28, false);
    }

    private void endPose() {
        card(false);
        broadcast(new ClientboundClearTitlesPacket(true));
        phase = Phase.EXIT; pt = 0;
        Vec3 c = run.centerPos();
        ParticleFx.vanish(level, c);
        run.restore();
    }

    void skipMember() {
        if (run == null || phase == Phase.ORBIT || phase == Phase.EXIT) return;
        card(false);
        broadcast(new ClientboundClearTitlesPacket(true));
        run.restore();
        run = null; cursor++;
        beginMember();
    }

    void onDisconnect(ServerPlayer p) {
        if (run != null && run.star == p) {
            run.restore();
            run = null; cursor++;
            if (phase != Phase.DONE) { card(false); beginMember(); }
        }
        Viewer v = viewers.remove(p.getUUID());
        if (v != null && v.applied) restoreViewer(p, v);
    }

    void finish() {
        if (run != null) run.restore();
        card(false);
        broadcast(new ClientboundClearTitlesPacket(true));
        for (Map.Entry<UUID, Viewer> e : viewers.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null && e.getValue().applied) restoreViewer(p, e.getValue());
        }
        if (cam != null) cam.discard();
        tell(Component.literal("[LHC] Intro finished.").withStyle(ChatFormatting.GREEN));
    }

    // ---- camera + viewers --------------------------------------------------------------------
    private void camUpdate(Vec3 focus, float travelYaw) {
        applyCam(rig.update(focus, travelYaw));
    }

    private void applyCam(State st) {
        Compat.move(cam, st.pos(), st.yaw(), st.pitch());
    }

    private void syncViewers() {
        for (Map.Entry<UUID, Viewer> e : viewers.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) continue;
            Viewer v = e.getValue();
            if (!v.applied) {
                v.applied = true;
                v.mode = Compat.gameMode(p);
                v.pos = p.position(); v.yaw = p.getYRot(); v.pitch = p.getXRot();
                v.level = (ServerLevel) p.level();
                p.setGameMode(GameType.SPECTATOR);
                if (v.level != level) Compat.teleportAcross(p, level, cam.getX(), cam.getY(), cam.getZ(), 0, 0);
            }
            if (p.getCamera() != cam) Compat.setCamera(p, cam);   // re-assert so Shift can't escape
        }
    }

    private void restoreViewer(ServerPlayer p, Viewer v) {
        Compat.setCamera(p, p);
        p.setGameMode(v.mode);
        Compat.teleportAcross(p, v.level, v.pos.x, v.pos.y, v.pos.z, v.yaw, v.pitch);
    }

    // ---- overlays ----------------------------------------------------------------------------
    private void card(boolean show) {
        if (run == null) return;
        Member m = run.m;
        String role = m.role != null ? m.role : cfg.defaultRole;
        String desc = m.description != null ? m.description : cfg.defaultDescription;
        InfoCardPayload payload = new InfoCardPayload(show, run.number, run.total, m.name, role, desc);
        for (UUID id : viewers.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && ServerPlayNetworking.canSend(p, InfoCardPayload.TYPE)) ServerPlayNetworking.send(p, payload);
        }
        if (show) fallbackActionBar();
    }

    /** Players without the client mod get the card as an action-bar line instead. */
    private void fallbackActionBar() {
        if (run == null) return;
        Member m = run.m;
        String role = m.role != null ? m.role : cfg.defaultRole;
        String desc = m.description != null ? m.description : cfg.defaultDescription;
        Component line = Component.literal(String.format("#%02d %s", run.number, m.name)).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
                .append(Component.literal("  " + role + "  ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(desc).withStyle(ChatFormatting.GRAY));
        for (UUID id : viewers.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && !ServerPlayNetworking.canSend(p, InfoCardPayload.TYPE))
                p.connection.send(new ClientboundSetActionBarTextPacket(line));
        }
    }

    private void broadcast(Packet<?> pk) {
        for (UUID id : viewers.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) p.connection.send(pk);
        }
    }

    private void tell(Component c) {
        if (director != null) director.sendSystemMessage(c);
    }
}

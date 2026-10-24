package dev.lhc.intro;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import dev.lhc.intro.config.LhcConfig;

/** Static facade: owns the (single) running session. */
public final class IntroDirector {
    private static Session session;

    private IntroDirector() {}

    public static boolean running() { return session != null; }

    /** @param fromIndex 0-based index into the member list; onlyIndex >= 0 plays just that member. */
    public static String start(MinecraftServer server, ServerPlayer director, int fromIndex, int onlyIndex, boolean opening) {
        if (session != null) return "An intro is already running (/lhc stop).";
        LhcConfig cfg = LhcConfig.get();
        if (!cfg.center.set) return "Center not set. Stand on stage and run /lhc setcenter.";
        if (cfg.members.isEmpty()) return "No members yet. Use /lhc addplayer <name>.";

        List<Integer> order = new ArrayList<>();
        if (onlyIndex >= 0) order.add(onlyIndex);
        else for (int i = Math.max(0, fromIndex); i < cfg.members.size(); i++) order.add(i);
        if (order.isEmpty()) return "Nothing to play from that index.";

        session = new Session(server, director, order, opening);
        return null;
    }

    public static void tick(MinecraftServer server) {
        if (session == null) return;
        try {
            if (session.tick()) { session.finish(); session = null; }
        } catch (Throwable t) {
            LhcMod.LOG.error("Intro crashed, restoring players", t);
            try { session.finish(); } catch (Throwable ignored) { }
            session = null;
        }
    }

    public static void stop() {
        if (session == null) return;
        try { session.finish(); } finally { session = null; }
    }

    public static void skip() { if (session != null) session.skipMember(); }

    public static void onDisconnect(ServerPlayer p) { if (session != null) session.onDisconnect(p); }
}

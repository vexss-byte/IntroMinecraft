package dev.lhc.intro;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.lhc.intro.Types.*;
import dev.lhc.intro.config.LhcConfig;
import dev.lhc.intro.config.LhcConfig.Member;
import dev.lhc.intro.config.LhcConfig.Spot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class LhcCommands {
    private LhcCommands() {}

    private static Spot pendingStart;
    private static final List<Spot> pendingWaypoints = new ArrayList<>();

    private static final SuggestionProvider<CommandSourceStack> MEMBERS = (c, b) ->
            SharedSuggestionProvider.suggest(LhcConfig.get().members.stream().map(m -> m.name), b);
    private static final SuggestionProvider<CommandSourceStack> FIELDS = (c, b) ->
            SharedSuggestionProvider.suggest(List.of("role", "desc", "quote", "entrance", "pose", "particle",
                    "camera", "posecamera", "speed", "poseseconds", "camdist", "camheight"), b);

    private static <E extends Enum<E>> SuggestionProvider<CommandSourceStack> enums(Class<E> c) {
        return (ctx, b) -> SharedSuggestionProvider.suggest(Arrays.stream(c.getEnumConstants()).map(Enum::name), b);
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("lhc").requires(Compat.gamemaster())
            .then(Commands.literal("setcenter").executes(LhcCommands::setCenter))
            .then(Commands.literal("setentrance").executes(LhcCommands::setEntrance))
            .then(Commands.literal("addwaypoint").executes(c -> addWaypoint(c, null))
                .then(Commands.argument("name", StringArgumentType.word()).suggests(MEMBERS)
                    .executes(c -> addWaypoint(c, StringArgumentType.getString(c, "name")))))
            .then(Commands.literal("clearwaypoints")
                .then(Commands.argument("name", StringArgumentType.word()).suggests(MEMBERS).executes(LhcCommands::clearWaypoints)))
            .then(Commands.literal("setstart")
                .then(Commands.argument("name", StringArgumentType.word()).suggests(MEMBERS).executes(LhcCommands::setStart)))
            .then(Commands.literal("addplayer")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(c -> addPlayer(c, null, null, null))
                    .then(Commands.argument("entrance", StringArgumentType.word()).suggests(enums(EntranceType.class))
                        .executes(c -> addPlayer(c, a(c, "entrance"), null, null))
                        .then(Commands.argument("pose", StringArgumentType.word()).suggests(enums(PoseType.class))
                            .executes(c -> addPlayer(c, a(c, "entrance"), a(c, "pose"), null))
                            .then(Commands.argument("particle", StringArgumentType.word()).suggests(enums(ParticleType.class))
                                .executes(c -> addPlayer(c, a(c, "entrance"), a(c, "pose"), a(c, "particle"))))))))
            .then(Commands.literal("removeplayer")
                .then(Commands.argument("name", StringArgumentType.word()).suggests(MEMBERS).executes(LhcCommands::removePlayer)))
            .then(Commands.literal("set")
                .then(Commands.argument("name", StringArgumentType.word()).suggests(MEMBERS)
                    .then(Commands.argument("field", StringArgumentType.word()).suggests(FIELDS)
                        .then(Commands.argument("value", StringArgumentType.greedyString()).executes(LhcCommands::set)))))
            .then(Commands.literal("list").executes(LhcCommands::list))
            .then(Commands.literal("playintro").executes(c -> play(c, 0, -1, true))
                .then(Commands.argument("from", IntegerArgumentType.integer(1))
                    .executes(c -> play(c, IntegerArgumentType.getInteger(c, "from") - 1, -1, true))))
            .then(Commands.literal("preview")
                .then(Commands.argument("name", StringArgumentType.word()).suggests(MEMBERS).executes(LhcCommands::preview)))
            .then(Commands.literal("skip").executes(c -> { IntroDirector.skip(); return 1; }))
            .then(Commands.literal("stop").executes(c -> { IntroDirector.stop(); ok(c, "Intro stopped."); return 1; }))
            .then(Commands.literal("reloadconfig").executes(c -> { LhcConfig.load(); ok(c, "Config reloaded."); return 1; })));
    }

    // ---- stage setup -------------------------------------------------------------------------
    private static int setCenter(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        LhcConfig cfg = LhcConfig.get();
        cfg.center = Spot.of((ServerLevel) p.level(), p.position(), p.getYRot());
        cfg.save();
        ok(c, String.format("Center set to %.1f %.1f %.1f (members will FACE yaw %.0f when posing).", p.getX(), p.getY(), p.getZ(), p.getYRot()));
        return 1;
    }

    private static int setEntrance(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        pendingStart = Spot.of((ServerLevel) p.level(), p.position(), p.getYRot());
        pendingWaypoints.clear();
        ok(c, "Entrance start stored for the NEXT /lhc addplayer. Add path bends with /lhc addwaypoint.");
        return 1;
    }

    private static int addWaypoint(CommandContext<CommandSourceStack> c, String name) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Spot s = Spot.of((ServerLevel) p.level(), p.position(), p.getYRot());
        if (name == null) {
            pendingWaypoints.add(s);
            ok(c, "Waypoint " + pendingWaypoints.size() + " queued for the next member.");
        } else {
            Member m = member(name);
            m.waypoints.add(s);
            LhcConfig.get().save();
            ok(c, "Waypoint added to " + m.name + " (now " + m.waypoints.size() + ").");
        }
        return 1;
    }

    private static int clearWaypoints(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Member m = member(a(c, "name"));
        m.waypoints.clear();
        LhcConfig.get().save();
        ok(c, "Waypoints cleared for " + m.name + ".");
        return 1;
    }

    private static int setStart(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Member m = member(a(c, "name"));
        m.start = Spot.of((ServerLevel) p.level(), p.position(), p.getYRot());
        LhcConfig.get().save();
        ok(c, "Entrance start of " + m.name + " moved to your position.");
        return 1;
    }

    // ---- members -----------------------------------------------------------------------------
    private static int addPlayer(CommandContext<CommandSourceStack> c, String ent, String pose, String part) throws CommandSyntaxException {
        LhcConfig cfg = LhcConfig.get();
        String name = a(c, "name");
        if (cfg.find(name) != null) throw err("Member " + name + " already exists.");

        Member m = new Member();
        int idx = cfg.members.size();
        m.name = name;
        m.entrance = ent != null ? parse(EntranceType.class, ent) : Types.ENTRANCE_ROTATION[idx % Types.ENTRANCE_ROTATION.length];
        m.pose = pose != null ? parse(PoseType.class, pose) : PoseType.values()[idx % PoseType.values().length];
        m.particle = part != null ? parse(ParticleType.class, part) : Types.rotatingParticle(idx);
        m.role = cfg.defaultRole; m.description = cfg.defaultDescription; m.quote = cfg.defaultQuote;

        if (pendingStart != null) m.start = pendingStart;
        else if (idx > 0) m.start = cfg.members.get(idx - 1).start;       // reuse the previous start
        else throw err("Set an entrance first: stand where the member should appear and run /lhc setentrance.");
        m.waypoints = new ArrayList<>(pendingWaypoints);
        pendingStart = null; pendingWaypoints.clear();

        cfg.members.add(m);
        cfg.save();
        ok(c, String.format("Added #%02d %s: %s / %s / %s", idx + 1, name, m.entrance, m.pose, m.particle));
        return 1;
    }

    private static int removePlayer(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Member m = member(a(c, "name"));
        LhcConfig.get().members.remove(m);
        LhcConfig.get().save();
        ok(c, "Removed " + m.name + ".");
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Member m = member(a(c, "name"));
        String f = a(c, "field").toLowerCase(), v = a(c, "value").trim();
        try {
            switch (f) {
                case "role" -> m.role = v;
                case "desc" -> m.description = v;
                case "quote" -> m.quote = v;
                case "entrance" -> m.entrance = parse(EntranceType.class, v);
                case "pose" -> m.pose = parse(PoseType.class, v);
                case "particle" -> m.particle = parse(ParticleType.class, v);
                case "camera" -> m.entranceCamera = parse(CameraStyle.class, v);
                case "posecamera" -> m.poseCamera = parse(CameraStyle.class, v);
                case "speed" -> m.speed = Double.parseDouble(v);
                case "poseseconds" -> m.poseSeconds = Double.parseDouble(v);
                case "camdist" -> m.cameraDistance = Double.parseDouble(v);
                case "camheight" -> m.cameraHeight = Double.parseDouble(v);
                default -> throw err("Unknown field. Try: role desc quote entrance pose particle camera posecamera speed poseseconds camdist camheight");
            }
        } catch (NumberFormatException e) {
            throw err("'" + v + "' is not a number.");
        }
        LhcConfig.get().save();
        ok(c, m.name + "." + f + " = " + v);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        LhcConfig cfg = LhcConfig.get();
        ok(c, "Center: " + (cfg.center.set ? String.format("%.0f %.0f %.0f", cfg.center.x, cfg.center.y, cfg.center.z) : "NOT SET")
                + " | members: " + cfg.members.size());
        int i = 1;
        for (Member m : cfg.members)
            ok(c, String.format("#%02d %s [%s] %s/%s/%s cam=%s/%s wp=%d", i++, m.name, m.role, m.entrance, m.pose, m.particle,
                    m.entranceCamera, m.poseCamera, m.waypoints.size()));
        return 1;
    }

    // ---- playback ----------------------------------------------------------------------------
    private static int play(CommandContext<CommandSourceStack> c, int from, int only, boolean opening) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayer();
        String error = IntroDirector.start(c.getSource().getServer(), p, from, only, opening);
        if (error != null) throw err(error);
        ok(c, "Intro started. /lhc skip, /lhc stop.");
        return 1;
    }

    private static int preview(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        LhcConfig cfg = LhcConfig.get();
        Member m = member(a(c, "name"));
        return play(c, 0, cfg.members.indexOf(m), false);
    }

    // ---- helpers -----------------------------------------------------------------------------
    private static String a(CommandContext<CommandSourceStack> c, String n) { return StringArgumentType.getString(c, n); }

    private static Member member(String name) throws CommandSyntaxException {
        Member m = LhcConfig.get().find(name);
        if (m == null) throw err("No member called " + name + ".");
        return m;
    }

    private static <E extends Enum<E>> E parse(Class<E> cls, String s) throws CommandSyntaxException {
        try { return Enum.valueOf(cls, s.toUpperCase()); }
        catch (IllegalArgumentException e) { throw err("Unknown value '" + s + "'. Options: " + Arrays.toString(cls.getEnumConstants())); }
    }

    private static CommandSyntaxException err(String msg) {
        return new SimpleCommandExceptionType(Component.literal(msg)).create();
    }

    private static void ok(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal("[LHC] " + msg), false);
    }
}

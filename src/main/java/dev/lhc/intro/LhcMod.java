package dev.lhc.intro;

import dev.lhc.intro.config.LhcConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LhcMod implements ModInitializer {
    public static final String ID = "lhc_intro";
    public static final Logger LOG = LoggerFactory.getLogger(ID);

    @Override
    public void onInitialize() {
        LhcConfig.load();
        // If your IDE marks playS2C() as renamed, use the clientbound-play equivalent.
        PayloadTypeRegistry.playS2C().register(InfoCardPayload.TYPE, InfoCardPayload.CODEC);
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> LhcCommands.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(IntroDirector::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> IntroDirector.onDisconnect(handler.getPlayer()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> IntroDirector.stop());
        LOG.info("LHC Member Intro loaded");
    }
}

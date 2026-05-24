package net.tpa;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.tpa.command.TpaCommand;
import net.tpa.command.TpAcceptCommand;
import net.tpa.command.TpDenyCommand;
import net.tpa.command.TpCancelCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TpaMod implements DedicatedServerModInitializer {
    public static final String MOD_ID = "tpa";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeServer() {
        LOGGER.info("TPA mod initializing...");

        // Register tick handler (for countdown + request expiry)
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);

        // Register all commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            TpaCommand.register(dispatcher);
            TpAcceptCommand.register(dispatcher);
            TpDenyCommand.register(dispatcher);
            TpCancelCommand.register(dispatcher);
        });

        LOGGER.info("TPA mod initialized! Commands: /tpa, /tpaccept, /tpadeny, /tpacancel");
    }

    private void onServerTick(MinecraftServer server) {
        // Tick countdown tasks
        TpaCountdown.tickAll(server);
        // Tick request expiry (auto-deny after 60 seconds)
        TpaConfig.tickExpireRequests(server);
    }
}

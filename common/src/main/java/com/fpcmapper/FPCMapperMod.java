package com.fpcmapper;

import com.fpcmapper.compat.Compat;
import com.fpcmapper.dev.RegistryDumper;
import com.fpcmapper.hud.HudLines;
import com.fpcmapper.hud.StatsHud;
import com.fpcmapper.network.ChunkSender;
import com.fpcmapper.util.CollectorConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FPCMapperMod implements ClientModInitializer {

    public static final String VERSION = "1.0.0";
    public static final String AUTHOR = "Punchy";
    public static final String GROUP = "Fruit Punch Collective";
    public static final String DISCORD = "discord.gg/gCxWmzzVP8";

    public static final Logger LOGGER = LoggerFactory.getLogger("FPCMapper");
    public static CollectorConfig config;
    public static ChunkSender sender;

    private static final String TARGET_DOMAIN = "6b6t.org";

    private static final KeyMapping.Category KEY_CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("fpcmapper", "keys"));
    private static KeyMapping toggleHudKey;

    @Override
    public void onInitializeClient() {
        config = CollectorConfig.load();
        sender = new ChunkSender(config);

        RegistryDumper.register();
        registerHud();

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> sender.onDisconnect());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            LOGGER.info("Flushing upload queue");
            sender.shutdown();
        });

        LOGGER.info(" ____                   _           ");
        LOGGER.info("|  _ \\ _   _ _ __   ___| |__  _   _ ");
        LOGGER.info("| |_) | | | | '_ \\ / __| '_ \\| | | |");
        LOGGER.info("|  __/| |_| | | | | (__| | | | |_| |");
        LOGGER.info("|_|    \\__,_|_| |_|\\___|_| |_|\\__, |");
        LOGGER.info("                              |___/ ");
        LOGGER.info(" FPCMapper v{} by {} | {}", VERSION, AUTHOR, GROUP);
        LOGGER.info(" {} | Target: {} | Radius: ±{}", DISCORD, config.targetServer, config.captureRadius);
        LOGGER.info(" Uploading to: {}", config.serverUrl);
    }

    private void registerHud() {
        toggleHudKey = Compat.registerKey(new KeyMapping(
                "key.fpcmapper.togglehud",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_APOSTROPHE,
                KEY_CATEGORY
        ));
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("fpcmapper", "stats_hud"),
                new StatsHud()
        );
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleHudKey.consumeClick()) {
                HudLines.visible = !HudLines.visible;
            }
        });
    }

    public static String shouldCapture(int chunkX, int chunkZ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getCurrentServer() == null) return null;

        String address = mc.getCurrentServer().ip.toLowerCase();
        boolean onTarget = address.contains(TARGET_DOMAIN)
                || (!config.targetServer.isBlank() && address.contains(config.targetServer.toLowerCase()));
        if (!onTarget) return null;

        if (mc.level.getDifficulty() != Difficulty.HARD) return null;
        if (!mc.level.dimension().equals(Level.OVERWORLD)) return null;

        int blockX = chunkX * 16;
        int blockZ = chunkZ * 16;
        long r = config.captureRadius;
        if (blockX < -r || blockX >= r || blockZ < -r || blockZ >= r) return null;

        return "minecraft:overworld";
    }
}

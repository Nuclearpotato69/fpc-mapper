package com.fpcmapper.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class CollectorConfig {

    public static final long MAX_RADIUS = 500_000L;

    public String serverUrl = "https://chunks.punchy39.qzz.io";
    public String authToken = "testingthing";
    public long captureRadius = MAX_RADIUS;
    public int senderThreads = 4;
    public int maxRetries = 5;
    public String targetServer = "6b6t.org";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger("FPCMapper");

    public static CollectorConfig load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("fpcmapper.json");

        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                CollectorConfig cfg = GSON.fromJson(reader, CollectorConfig.class);
                if (cfg != null) {
                    cfg.captureRadius = Math.max(0, Math.min(cfg.captureRadius, MAX_RADIUS));
                    LOGGER.info("Loaded config from {}", file);
                    return cfg;
                }
            } catch (Exception e) {
                LOGGER.warn("Could not read config, using defaults: {}", e.getMessage());
            }
        }

        CollectorConfig defaults = new CollectorConfig();
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(defaults, writer);
            LOGGER.info("Wrote default config to {}", file);
        } catch (Exception e) {
            LOGGER.warn("Could not write default config: {}", e.getMessage());
        }
        return defaults;
    }
}

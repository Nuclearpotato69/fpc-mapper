package com.fpcmapper.dev;

import com.fpcmapper.FPCMapperMod;
import com.fpcmapper.compat.Compat;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RegistryDumper {

    private static final int PROTOCOL_VERSION = Compat.PROTOCOL_VERSION;

    private RegistryDumper() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(Compat.literal("fpcdumpregistries")
                        .executes(ctx -> {
                            run(ctx.getSource());
                            return 1;
                        })));
    }

    private static void run(FabricClientCommandSource source) {
        try {
            Path outDir = FabricLoader.getInstance().getGameDir().resolve("fpcmapper-registries");
            Files.createDirectories(outDir);

            int blockCount = dumpBlockStates(outDir.resolve("blockstates.json"));
            int biomeCount = dumpBiomes(source, outDir.resolve("biome_properties.json"));

            String message = "Dumped " + blockCount + " block states and " +
                    biomeCount + " biomes to " + outDir;
            FPCMapperMod.LOGGER.info(message);
            source.sendFeedback(Component.literal(message));
        } catch (Exception e) {
            FPCMapperMod.LOGGER.error("Registry dump failed", e);
            source.sendError(Component.literal("Registry dump failed: " + e));
        }
    }

    private static int dumpBlockStates(Path outFile) throws IOException {
        List<Map<String, Object>> entries = new ArrayList<>();

        for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", Block.BLOCK_STATE_REGISTRY.getId(state));
            entry.put("name", blockStateName(state));
            entry.put("lightLevel", state.getLightEmission());
            entries.add(entry);
        }
        entries.sort(Comparator.comparingInt(e -> (Integer) e.get("id")));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "blockstates");
        root.put("protocolVersion", PROTOCOL_VERSION);
        root.put("entries", entries);
        writeJson(outFile, root);
        return entries.size();
    }

    private static String blockStateName(BlockState state) {
        StringBuilder sb = new StringBuilder();
        sb.append(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
        Collection<Property<?>> props = state.getProperties();
        if (!props.isEmpty()) {
            sb.append('[');
            boolean first = true;
            for (Property<?> prop : props) {
                if (!first) sb.append(',');
                sb.append(prop.getName()).append('=').append(propertyValue(state, prop));
                first = false;
            }
            sb.append(']');
        }
        return sb.toString();
    }

    private static <T extends Comparable<T>> String propertyValue(BlockState state, Property<T> prop) {
        return prop.getName(state.getValue(prop));
    }

    private static int dumpBiomes(FabricClientCommandSource source, Path outFile) throws IOException {
        if (Compat.level(source) == null) {
            throw new IllegalStateException(
                    "No level loaded — join any world (singleplayer is fine) before running this command");
        }
        Registry<Biome> registry = Compat.level(source).registryAccess().lookupOrThrow(Registries.BIOME);

        List<Map<String, Object>> entries = new ArrayList<>();
        registry.listElements().forEach(holder -> {
            Biome biome = holder.value();
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("name", holder.key().identifier().getPath());
            e.put("temperature", biome.getBaseTemperature());
            e.put("downfall", readDownfall(biome));
            entries.add(e);
        });

        entries.sort(Comparator.comparing(e -> (String) e.get("name")));

        List<Map<String, Object>> ordered = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            Map<String, Object> src = entries.get(i);
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("id", i);
            e.put("name", src.get("name"));
            e.put("downfall", src.get("downfall"));
            e.put("temperature", src.get("temperature"));
            ordered.add(e);
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "biome_properties");
        root.put("protocolVersion", PROTOCOL_VERSION);
        root.put("entries", ordered);
        writeJson(outFile, root);
        return ordered.size();
    }

    private static float readDownfall(Biome biome) {
        try {
            Field field = Biome.class.getDeclaredField("climateSettings");
            field.setAccessible(true);
            Object climateSettings = field.get(biome);
            return (float) climateSettings.getClass().getMethod("downfall").invoke(climateSettings);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not read Biome.climateSettings.downfall() via reflection", e);
        }
    }

    private static void writeJson(Path outFile, Object root) throws IOException {
        var gson = new GsonBuilder().setPrettyPrinting().create();
        try (Writer w = Files.newBufferedWriter(outFile, StandardCharsets.UTF_8)) {
            gson.toJson(root, w);
        }
    }
}

package com.fpcmapper.util;

import com.fpcmapper.compat.Compat;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

public class ChunkNbtSerializer {

    public static byte[] serializeChunk(LevelChunk chunk) throws IOException {
        CompoundTag root = new CompoundTag();

        root.putInt("xPos", Compat.chunkX(chunk.getPos()));
        root.putInt("zPos", Compat.chunkZ(chunk.getPos()));
        root.putInt("yPos", chunk.getMinSectionY());
        root.putLong("LastUpdate", System.currentTimeMillis() / 1000L);
        root.putString("Status", "minecraft:full");
        root.putString("CollectedBy", "Punchy | Fruit Punch Collective | discord.gg/gCxWmzzVP8");

        CompoundTag heightmaps = new CompoundTag();
        for (Map.Entry<Heightmap.Types, Heightmap> entry : chunk.getHeightmaps()) {
            Heightmap.Types type = entry.getKey();
            if (!type.keepAfterWorldgen()) continue;
            heightmaps.putLongArray(type.getSerializationKey(), entry.getValue().getRawData());
        }
        root.put("Heightmaps", heightmaps);

        LevelChunkSection[] sections = chunk.getSections();
        ListTag sectionList = new ListTag();
        int minSectionY = chunk.getMinSectionY();
        boolean hasAnyBlocks = false;

        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir()) continue;

            CompoundTag sectionNbt = new CompoundTag();
            sectionNbt.putByte("Y", (byte)(minSectionY + i));
            sectionNbt.put("block_states", serializeBlockStates(section));
            sectionNbt.put("biomes", serializeBiomes(section));

            sectionList.add(sectionNbt);
            hasAnyBlocks = true;
        }

        if (!hasAnyBlocks) return null;

        root.put("sections", sectionList);

        ListTag blockEntities = new ListTag();
        chunk.getBlockEntities().forEach((blockPos, be) -> {
            CompoundTag beNbt = be.saveWithFullMetadata(chunk.getLevel().registryAccess());
            if (beNbt != null) blockEntities.add(beNbt);
        });
        root.put("block_entities", blockEntities);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            NbtIo.write(root, new DataOutputStream(gzip));
        }
        return baos.toByteArray();
    }

    private static CompoundTag serializeBiomes(LevelChunkSection section) {
        CompoundTag nbt = new CompoundTag();

        List<String> paletteList = new ArrayList<>();
        Map<String, Integer> paletteIndex = new HashMap<>(16);
        short[] indices = new short[64];

        for (int y = 0; y < 4; y++) {
            for (int z = 0; z < 4; z++) {
                for (int x = 0; x < 4; x++) {
                    Holder<Biome> holder = section.getNoiseBiome(x, y, z);

                    String biomeStr = holder.unwrapKey()
                            .map(key -> key.identifier().toString())
                            .orElse("minecraft:plains");
                    int idx = paletteIndex.computeIfAbsent(biomeStr, k -> {
                        paletteList.add(k);
                        return paletteList.size() - 1;
                    });
                    indices[y * 16 + z * 4 + x] = (short) idx;
                }
            }
        }

        ListTag palette = new ListTag();
        for (String biomeStr : paletteList) {
            palette.add(StringTag.valueOf(biomeStr));
        }
        nbt.put("palette", palette);

        if (paletteList.size() > 1) {
            nbt.putLongArray("data", packIndices(indices, 64, paletteList.size()));
        }

        return nbt;
    }

    private static CompoundTag serializeBlockStates(LevelChunkSection section) {
        CompoundTag nbt = new CompoundTag();

        List<String> paletteList = new ArrayList<>();
        Map<String, Integer> paletteIndex = new HashMap<>(64);
        short[] indices = new short[4096];

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockState state = section.getBlockState(x, y, z);
                    String id = serializeBlockState(state);
                    int idx = paletteIndex.computeIfAbsent(id, k -> {
                        paletteList.add(k);
                        return paletteList.size() - 1;
                    });
                    indices[y * 256 + z * 16 + x] = (short) idx;
                }
            }
        }

        ListTag palette = new ListTag();
        for (String stateStr : paletteList) {
            palette.add(parseBlockStateString(stateStr));
        }
        nbt.put("palette", palette);

        if (paletteList.size() > 1) {
            nbt.putLongArray("data", packIndices(indices, 4096, paletteList.size()));
        }

        return nbt;
    }

    private static String serializeBlockState(BlockState state) {
        StringBuilder sb = new StringBuilder(64);

        sb.append(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        Collection<Property<?>> props = state.getProperties();
        if (!props.isEmpty()) {
            sb.append('[');
            boolean first = true;
            for (Property<?> prop : props) {
                if (!first) sb.append(',');
                sb.append(prop.getName()).append('=').append(getPropertyValue(state, prop));
                first = false;
            }
            sb.append(']');
        }
        return sb.toString();
    }

    private static <T extends Comparable<T>> String getPropertyValue(BlockState state, Property<T> prop) {
        return prop.getName(state.getValue(prop));
    }

    private static CompoundTag parseBlockStateString(String stateStr) {
        CompoundTag nbt = new CompoundTag();
        int bracketIdx = stateStr.indexOf('[');
        if (bracketIdx < 0) {
            nbt.putString("Name", stateStr);
        } else {
            nbt.putString("Name", stateStr.substring(0, bracketIdx));
            String propStr = stateStr.substring(bracketIdx + 1, stateStr.length() - 1);
            CompoundTag props = new CompoundTag();
            for (String kv : propStr.split(",")) {
                String[] parts = kv.split("=", 2);
                if (parts.length == 2) props.putString(parts[0], parts[1]);
            }
            nbt.put("Properties", props);
        }
        return nbt;
    }

    private static long[] packIndices(short[] indices, int count, int paletteSize) {
        int bitsPerEntry = Math.max(4, Integer.SIZE - Integer.numberOfLeadingZeros(paletteSize - 1));
        int entriesPerLong = 64 / bitsPerEntry;
        int arraySize = (count + entriesPerLong - 1) / entriesPerLong;
        long[] data = new long[arraySize];
        for (int i = 0; i < count; i++) {
            int longIdx   = i / entriesPerLong;
            int bitOffset = (i % entriesPerLong) * bitsPerEntry;
            data[longIdx] |= ((long)(indices[i] & 0xFFFF)) << bitOffset;
        }
        return data;
    }
}

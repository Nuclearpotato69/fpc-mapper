package com.fpcmapper.compat;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;

public final class Compat {

    public static final int PROTOCOL_VERSION = 774;

    private Compat() {}

    public static int chunkX(ChunkPos pos) {
        return pos.x;
    }

    public static int chunkZ(ChunkPos pos) {
        return pos.z;
    }

    public static KeyMapping registerKey(KeyMapping key) {
        return KeyBindingHelper.registerKeyBinding(key);
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
        return ClientCommandManager.literal(name);
    }

    public static ClientLevel level(FabricClientCommandSource source) {
        return source.getWorld();
    }
}

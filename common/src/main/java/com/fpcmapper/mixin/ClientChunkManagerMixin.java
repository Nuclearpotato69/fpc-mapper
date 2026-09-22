package com.fpcmapper.mixin;

import com.fpcmapper.FPCMapperMod;
import com.fpcmapper.util.ChunkNbtSerializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Mixin(ClientChunkCache.class)
public class ClientChunkManagerMixin {

    private static final ExecutorService SERIALIZE_POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "FPCMapper-Serialize");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    @Inject(method = "replaceWithPacketData", at = @At("RETURN"))
    private void onChunkLoaded(int x, int z, FriendlyByteBuf buf, Map<?, ?> heightmaps,
                               Consumer<?> blockEntities, CallbackInfoReturnable<LevelChunk> cir) {
        LevelChunk chunk = cir.getReturnValue();
        if (chunk == null) return;

        String dimension = FPCMapperMod.shouldCapture(x, z);
        if (dimension == null) return;

        Minecraft mc = Minecraft.getInstance();
        String playerName = mc.player != null ? mc.player.getName().getString() : "";

        SERIALIZE_POOL.submit(() -> {
            try {
                byte[] nbt = ChunkNbtSerializer.serializeChunk(chunk);
                if (nbt == null) return;
                FPCMapperMod.sender.enqueue(x, z, nbt, playerName, dimension);
            } catch (Exception e) {
                FPCMapperMod.LOGGER.warn("Failed to capture chunk ({},{}): {}", x, z, e.getMessage());
            }
        });
    }
}

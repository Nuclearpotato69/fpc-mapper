package com.fpcmapper.hud;

import com.fpcmapper.FPCMapperMod;
import net.minecraft.client.Minecraft;

import java.util.List;

public final class HudLines {

    public static volatile boolean visible = false;

    public static final int X = 4;
    public static final int Y = 4;
    public static final int BACKGROUND = 0x90000000;
    public static final int TITLE = 0xFFFFFFFF;
    public static final int BODY = 0xFFAAAAAA;

    private HudLines() {}

    public static List<String> current() {
        if (!visible || FPCMapperMod.sender == null) return List.of();

        boolean active = FPCMapperMod.shouldCapture(0, 0) != null;
        long failed = FPCMapperMod.sender.getSessionFail();

        return List.of(
                "FPC Mapper",
                active ? "§aMapping active" : "§7Not on target server",
                "Sent this session: " + FPCMapperMod.sender.getSessionSuccess()
                        + (failed > 0 ? " §c(" + failed + " failed)" : ""),
                "Sent total: " + FPCMapperMod.sender.getTotalSuccess(),
                "Queued: " + FPCMapperMod.sender.getQueueDepth()
        );
    }

    public static int widest(List<String> lines) {
        var font = Minecraft.getInstance().font;
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.width(line.replaceAll("§.", "")));
        }
        return width;
    }
}

package com.fpcmapper.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

public class StatsHud implements HudElement {

    @Override
    public void render(GuiGraphics gfx, DeltaTracker tracker) {
        List<String> lines = HudLines.current();
        if (lines.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        int lineHeight = mc.font.lineHeight + 1;
        int width = HudLines.widest(lines);

        gfx.fill(HudLines.X - 2, HudLines.Y - 2, HudLines.X + width + 2,
                HudLines.Y + lines.size() * lineHeight + 1, HudLines.BACKGROUND);
        for (int i = 0; i < lines.size(); i++) {
            gfx.drawString(mc.font, lines.get(i), HudLines.X, HudLines.Y + i * lineHeight,
                    i == 0 ? HudLines.TITLE : HudLines.BODY);
        }
    }
}

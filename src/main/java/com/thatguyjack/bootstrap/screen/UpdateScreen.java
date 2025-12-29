package com.thatguyjack.bootstrap.screen;

import com.thatguyjack.bootstrap.BootstrapClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class UpdateScreen extends Screen {
    public UpdateScreen() {
        super(Text.literal("Updating GodSMP Mods"));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected void init() {
        this.clearChildren();

        if (BootstrapClient.restartRequired) {
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Quit & Apply Updates"), b -> {
                try {
                    BootstrapClient.startApplyAndQuit();
                } catch (Exception e) {
                    BootstrapClient.errorText = "Failed to start apply script: " + e.getMessage();
                    BootstrapClient.restartRequired = false;
                }
            }).dimensions(this.width / 2 - 110, this.height / 2 + 55, 220, 20).build());
        }

        if (BootstrapClient.errorText != null) {
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Quit"), b -> {
                MinecraftClient.getInstance().scheduleStop();
            }).dimensions(this.width / 2 - 60, this.height / 2 + 55, 120, 20).build());
        }
    }

    @Override
    public void tick() {
        boolean needButtons = BootstrapClient.restartRequired || BootstrapClient.errorText != null;
        boolean hasButtons = !this.children().isEmpty();
        if (needButtons && !hasButtons) init();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);

        int cx = this.width / 2;
        int y = this.height / 2 - 70;

        if (BootstrapClient.errorText != null) {
            ctx.drawCenteredTextWithShadow(this.textRenderer, "Update failed", cx, y, 0xFF5555);
            drawMultiline(ctx, BootstrapClient.errorText, cx, y + 18, 0xFFFFFF);
            return;
        }

        if (BootstrapClient.restartRequired) {
            ctx.drawCenteredTextWithShadow(this.textRenderer, "Updates downloaded.", cx, y, 0xFFFFFF);
            ctx.drawCenteredTextWithShadow(this.textRenderer, "Click to quit and apply.", cx, y + 15, 0xFFFFFF);
            return;
        }

        ctx.drawCenteredTextWithShadow(this.textRenderer, "Updating...", cx, y, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(this.textRenderer, BootstrapClient.statusLine, cx, y + 15, 0xAAAAAA);

        int barW = 260, barH = 12;
        int bx = cx - barW / 2;
        int by = y + 42;

        float p = clamp01(BootstrapClient.progress01);

        ctx.fill(bx, by, bx + barW, by + barH, 0xFF2A2A2A);
        ctx.fill(bx, by, bx + (int)(barW * p), by + barH, 0xFFAAAAAA);
        ctx.drawBorder(bx, by, barW, barH, 0xFF777777);
    }

    private static float clamp01(float v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }

    private void drawMultiline(DrawContext ctx, String text, int centerX, int startY, int color) {
        String[] lines = text.split("\n");
        int y = startY;
        for (String line : lines) {
            ctx.drawCenteredTextWithShadow(this.textRenderer, line, centerX, y, color);
            y += 10;
        }
    }
}
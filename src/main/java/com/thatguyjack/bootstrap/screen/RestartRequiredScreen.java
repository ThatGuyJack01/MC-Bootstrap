package com.thatguyjack.bootstrap.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class RestartRequiredScreen extends Screen {
    private final String details;

    RestartRequiredScreen(String details) {
        super(Text.literal("Mods Updated"));
        this.details = details;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Quit to Apply Updates"), btn -> {
            MinecraftClient.getInstance().scheduleStop();
        }).dimensions(cx - 100, this.height / 2 + 20, 200, 20).build());
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, "Your modpack has been updated.", this.width / 2, this.height / 2 - 30, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, "Restart is required.", this.width / 2, this.height / 2 - 15, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, details, this.width / 2, this.height / 2, 0xAAAAAA);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // force acknowledgment
    }
}

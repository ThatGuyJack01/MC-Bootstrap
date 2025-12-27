package com.thatguyjack.bootstrap.screen;

import net.minecraft.client.MinecraftClient;

public final class BootstrapUi {
    public static void showRestartRequiredScreen(MinecraftClient client, String details) {
        client.setScreen(new RestartRequiredScreen(details));
    }
}

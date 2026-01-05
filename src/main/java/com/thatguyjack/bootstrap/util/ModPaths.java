package com.thatguyjack.bootstrap.util;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.nio.file.Path;
import java.util.List;

public final class ModPaths {
    private ModPaths() {}

    public static Path getThisModJar(String modId) {
        ModContainer mc = FabricLoader.getInstance().getModContainer(modId)
                .orElseThrow(() -> new IllegalStateException("Missing mod container for " + modId));

        List<Path> paths = mc.getOrigin().getPaths();
        if (paths.isEmpty()) throw new IllegalStateException("No origin paaths for " + modId);

        return paths.get(0).toAbsolutePath().normalize();
    }
}

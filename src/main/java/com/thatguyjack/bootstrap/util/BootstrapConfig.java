package com.thatguyjack.bootstrap.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.Path;

public class BootstrapConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String manifestUrl = "https://raw.githubusercontent.com/ThatGuyJack01/GodSMP-Pack/refs/heads/main/manifest.json";
    private static final String STAGING_DIR_NAME = "godsmp_staging";
    private static final String CONFIG_DIR_NAME = "godsmp_bootstrap";
    private static final String INSTALLED_FILE_NAME = "installed.json";

    public boolean autoUpdate = true;

    public static BootstrapConfig loadOrCreate(Path path) {
        BootstrapConfig cfg = BootstrapIO.readJson(path, BootstrapConfig.class);
        if (cfg == null) {
            cfg = new BootstrapConfig();
            BootstrapIO.writeJson(path, cfg, GSON);
        }
        return cfg;
    }
}

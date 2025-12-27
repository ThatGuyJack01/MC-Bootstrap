package com.thatguyjack.bootstrap;

import com.google.gson.Gson;
import com.thatguyjack.bootstrap.screen.BootstrapUi;
import com.thatguyjack.bootstrap.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class BootstrapClient implements ClientModInitializer {
    public static final String MODID = "bootstrap";
    public static final Gson GSON = new Gson();

    @Override
    public void onInitializeClient() {
        CompletableFuture.runAsync(() -> {
            try {
                runBootstrap();
            } catch (Exception e) {
                BootstrapLog.error("Bootstrap update failed", e);
                // Optional: show a UI warning next tick
            }
        });
    }

    private void runBootstrap() throws Exception {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path modsDir = gameDir.resolve("mods");
        Path cfgDir  = gameDir.resolve("config").resolve(MODID);
        Path stagingDir = cfgDir.resolve("staging");

        BootstrapIO.ensureDir(modsDir);
        BootstrapIO.ensureDir(cfgDir);
        BootstrapIO.ensureDir(stagingDir);

        BootstrapConfig cfg = BootstrapConfig.loadOrCreate(cfgDir.resolve("config.json"));

        if (!cfg.autoUpdate) {
            BootstrapLog.info("autoUpdate disabled; skipping.");
            return;
        }

        Manifest manifest = ManifestFetcher.fetch(cfg.manifestUrl);

        InstalledState installed = InstalledState.loadOrCreate(cfgDir.resolve("installed.json"));

        UpdatePlan plan = Updater.computePlan(modsDir, stagingDir, manifest, installed);

        if (!plan.hasWork()) {
            BootstrapLog.info("No updates required.");
            return;
        }

        Updater.apply(plan);

        for (UpdatePlan.DownloadTask t : plan.downloads) {
            installed.ownedFiles.add(t.mod.fileName);
        }

        for (var del : plan.deletes) {
            installed.ownedFiles.remove(del.getFileName().toString());
        }

        installed.save(cfgDir.resolve("installed.json"));

        // Schedule UI + exit on main thread
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            BootstrapUi.showRestartRequiredScreen(client, plan.summaryText());
            // Alternative: client.scheduleStop(); (or System.exit(0) as last resort)
        });
    }
}

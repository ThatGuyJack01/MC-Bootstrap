package com.thatguyjack.bootstrap;

import com.google.gson.Gson;
import com.thatguyjack.bootstrap.screen.UpdateScreen;
import com.thatguyjack.bootstrap.util.ApplyScript;
import com.thatguyjack.bootstrap.util.InstalledState;
import com.thatguyjack.bootstrap.util.Manifest;
import com.thatguyjack.bootstrap.util.Updater;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class BootstrapClient implements ClientModInitializer {
    public static final Gson GSON = new Gson();

    // ---- CONFIG ----
    private static final String MANIFEST_URL =
            "https://raw.githubusercontent.com/ThatGuyJack01/GodSMP-Pack/main/manifest.json";

    // ---- STATE (read by UpdateScreen) ----
    public static volatile boolean busy = false;
    public static volatile boolean restartRequired = false;
    public static volatile String statusLine = "";
    public static volatile float progress01 = 0f;
    public static volatile String errorText = null;

    private static Path gameDir;
    private static Path modsDir;
    private static Path stagingDir;
    private static Path stagingModsDir;
    private static Path cfgDir;
    private static Path installedPath;
    private static Path deleteListPath;
    private static Path stagedInstalledPath;
    private static Path applyBatPath;

    @Override
    public void onInitializeClient() {
        gameDir = FabricLoader.getInstance().getGameDir();
        modsDir = gameDir.resolve("mods");
        stagingDir = gameDir.resolve("godsmp_staging");
        stagingModsDir = stagingDir.resolve("mods");
        cfgDir = gameDir.resolve("config").resolve("godsmp_bootstrap");
        installedPath = cfgDir.resolve("installed.json");
        deleteListPath = stagingDir.resolve("delete.txt");
        stagedInstalledPath = stagingDir.resolve("installed.json");

        // Force screen while busy/restart/error
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!busy && !restartRequired && errorText == null) return;
            if (!(client.currentScreen instanceof UpdateScreen)) {
                client.setScreen(new UpdateScreen());
            }
        });

        CompletableFuture.runAsync(() -> {
            try {
                busy = true;
                restartRequired = false;
                errorText = null;
                statusLine = "Preparing...";
                progress01 = 0f;

                Files.createDirectories(stagingModsDir);
                Files.createDirectories(cfgDir);

                // Load old installed state
                InstalledState oldState = loadInstalled();

                statusLine = "Fetching manifest...";
                Manifest manifest = Updater.fetchManifest(MANIFEST_URL);

                // Stage all mods (download+verify into staging)
                boolean stageChanged = Updater.stageAllMods(manifest, stagingModsDir,
                        (done, total, line) -> {
                            statusLine = line;
                            progress01 = total <= 0 ? 0f : (done / (float) total);
                        });

                // Compute desired filenames
                Set<String> desired = (manifest.mods == null ? List.<Manifest.ModEntry>of() : manifest.mods)
                        .stream()
                        .filter(m -> m != null && m.required && m.fileName != null && !m.fileName.isBlank())
                        .map(m -> m.fileName)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

                // Compute deletes = previously owned - desired
                List<String> deletes = new ArrayList<>();
                for (String owned : oldState.ownedFiles) {
                    if (!desired.contains(owned)) deletes.add(owned);
                }

                // Write delete list for batch script
                Files.createDirectories(stagingDir);
                Files.write(deleteListPath, deletes, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                // Write new installed.json into staging (batch will copy it into config/)
                InstalledState newState = new InstalledState();
                newState.packId = manifest.packId;
                newState.packVersion = manifest.packVersion;
                newState.ownedFiles = new ArrayList<>(desired);

                Files.writeString(stagedInstalledPath, GSON.toJson(newState), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                // If nothing changed AND no deletes AND staged mods already identical, allow normal play.
                boolean anything = stageChanged || !deletes.isEmpty();

                busy = false;
                progress01 = 1f;

                if (anything) {
                    statusLine = "Updates staged. Restart required.";
                    restartRequired = true;

                    // Write the apply script
                    applyBatPath = ApplyScript.writeBatch(gameDir);
                } else {
                    // Up to date, do nothing
                    MinecraftClient.getInstance().execute(() -> {
                        MinecraftClient client = MinecraftClient.getInstance();
                        if (client.currentScreen instanceof UpdateScreen) client.setScreen(null);
                    });
                }
            } catch (Exception e) {
                busy = false;
                errorText = (e.getMessage() == null ? e.toString() : e.getMessage());
            }
        });
    }

    private static InstalledState loadInstalled() {
        try {
            if (!Files.exists(installedPath)) return new InstalledState();
            String json = Files.readString(installedPath, StandardCharsets.UTF_8);
            InstalledState st = GSON.fromJson(json, InstalledState.class);
            return (st == null) ? new InstalledState() : st;
        } catch (Exception ignored) {
            return new InstalledState();
        }
    }

    /** Called from the UpdateScreen button. */
    public static void startApplyAndQuit() throws Exception {
        if (applyBatPath == null) applyBatPath = ApplyScript.writeBatch(gameDir);

        long pid = ProcessHandle.current().pid();
        ApplyScript.runBatchDetached(applyBatPath, pid);

        // Quit MC
        MinecraftClient.getInstance().scheduleStop();
    }
}
package com.thatguyjack.bootstrap;

import com.google.gson.Gson;
import com.thatguyjack.bootstrap.screen.UpdateScreen;
import com.thatguyjack.bootstrap.util.InstalledState;
import com.thatguyjack.bootstrap.util.Manifest;
import com.thatguyjack.bootstrap.util.ModPaths;
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

    public static final String MANIFEST_URL =
            "https://raw.githubusercontent.com/ThatGuyJack01/GodSMP-Pack/main/manifest.json";

    public static volatile boolean busy = false;
    public static volatile boolean restartRequired = false;
    public static volatile String statusLine = "";
    public static volatile float progress01 = 0f;
    public static volatile String errorText = null;


    private static Path gameDir;
    private static Path modsDir;
    private static Path stagingDir;
    private static Path stagingModsDir;
    private static Path installedPath;
    private static Path deleteListPath;
    private static Path stagedInstalledPath;

    @Override
    public void onInitializeClient() {
        gameDir = FabricLoader.getInstance().getGameDir();
        modsDir = gameDir.resolve("mods");
        stagingDir = gameDir.resolve("godsmp_staging");
        stagingModsDir = stagingDir.resolve("mods");
        deleteListPath = stagingDir.resolve("delete.txt");
        stagedInstalledPath = stagingDir.resolve("installed.json");

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

                InstalledState oldState = loadInstalled();

                statusLine = "Fetching manifest...";
                Manifest manifest = Updater.fetchManifest(MANIFEST_URL);

                boolean stageChanged = Updater.stageAllMods(manifest, stagingModsDir,
                        (done, total, line) -> {
                            statusLine = line;
                            progress01 = total <= 0 ? 0f : (done / (float) total);
                        });

                Set<String> desired = (manifest.mods == null ? List.<Manifest.ModEntry>of() : manifest.mods)
                        .stream()
                        .filter(m -> m != null && m.required && m.fileName != null && !m.fileName.isBlank())
                        .map(m -> m.fileName)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

                List<String> deletes = new ArrayList<>();
                for (String owned : oldState.ownedFiles) {
                    if (owned == null) continue;
                    owned = owned.trim();

                    if (owned.isEmpty()) continue;

                    if (owned.toLowerCase().startsWith("fabric-api")) continue;

                    if (!owned.endsWith(".jar")) continue;
                    if (!desired.contains(owned)) deletes.add(owned);
                }

                Files.createDirectories(stagingDir);
                Files.write(deleteListPath, deletes, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                InstalledState newState = new InstalledState();
                newState.packId = manifest.packId;
                newState.packVersion = manifest.packVersion;
                newState.ownedFiles = new ArrayList<>(desired);

                Files.writeString(stagedInstalledPath, GSON.toJson(newState), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                boolean anything = stageChanged || !deletes.isEmpty();

                busy = false;
                progress01 = 1f;

                if (anything) {
                    statusLine = "Updates staged. Restart required.";
                    restartRequired = true;
                } else {
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

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (BootstrapClient.restartRequired) {
                    BootstrapClient.startApplyAndQuit();
                }
            } catch (Exception ignored) {}
        }));
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

    public static void startApplyAndQuit() throws Exception {
        long pid = ProcessHandle.current().pid();

        String javaHome = System.getProperty("java.home");
        Path javaExe = Path.of(javaHome, "bin", isWindows() ? "java.exe" : "java");

        Path bootstrapJar = ModPaths.getThisModJar(Bootstrap.MOD_ID);

        String gameDirStr = gameDir.toAbsolutePath().normalize().toString();

        ProcessBuilder pb = new ProcessBuilder(
                javaExe.toString(),
                "-cp", bootstrapJar.toString(),
                "com.thatguyjack.bootstrap.util.ApplierMain",
                "--gameDir", gameDirStr,
                "--pid", Long.toString(pid)
        );

        pb.directory(gameDir.toFile());
        pb.redirectErrorStream(true);

        pb.inheritIO();

        pb.start();

        MinecraftClient.getInstance().scheduleStop();
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("win");
    }
}
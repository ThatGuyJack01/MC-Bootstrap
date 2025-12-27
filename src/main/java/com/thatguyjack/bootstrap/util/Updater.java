package com.thatguyjack.bootstrap.util;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

public final class Updater {
    public static UpdatePlan computePlan(Path modsDir, Path stagingDir, Manifest manifest, InstalledState installed) throws Exception {
        UpdatePlan plan = new UpdatePlan();

        Set<String> wantedFiles = new HashSet<>();

        for (Manifest.ManifestMod mm : manifest.mods) {
            if (mm == null || mm.fileName == null || mm.url == null || mm.sha256 == null) {
                throw new IllegalStateException("Manifest contains a mod with missing fields (fileName/url/sha256).");
            }

            wantedFiles.add(mm.fileName);

            Path target = modsDir.resolve(mm.fileName);
            boolean needsDownload = true;

            if (Files.exists(target)) {
                String currentHash = Sha256.ofFile(target);
                if (mm.sha256.equalsIgnoreCase(currentHash)) {
                    needsDownload = false;
                } else {
                    BootstrapLog.info("Hash mismatch for " + mm.fileName + " -> will update");
                }
            } else {
                BootstrapLog.info("Missing " + mm.fileName + " -> will install");
            }

            if (needsDownload) {
                Path staging = stagingDir.resolve(mm.fileName + ".download");
                plan.downloads.add(new UpdatePlan.DownloadTask(mm, target, staging));
            }
        }

        for (String owned : new HashSet<>(installed.ownedFiles)) {
            if (!wantedFiles.contains(owned)) {
                Path p = modsDir.resolve(owned);
                plan.deletes.add(p);
            }
        }

        return plan;
    }

    public static void apply(UpdatePlan plan) throws Exception {
        for (UpdatePlan.DownloadTask task : plan.downloads) {
            downloadVerifyAndInstall(task);
        }

        for (Path p : plan.deletes) {
            try {
                if (Files.exists(p)) {
                    BootstrapLog.info("Deleting old owned mod: " + p.getFileName());
                    Files.delete(p);
                }
            } catch (Exception e) {
                BootstrapLog.warn("Failed to delete " + p.getFileName() + ": " + e.getMessage());
            }
        }

        for (UpdatePlan.DownloadTask task : plan.downloads) {

        }
    }

    private static void downloadVerifyAndInstall(UpdatePlan.DownloadTask task) throws Exception {
        Manifest.ManifestMod mm = task.mod;

        BootstrapLog.info("Downloading " + mm.fileName);

        Files.deleteIfExists(task.stagingFile);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(mm.url))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

        HttpResponse<InputStream> res = ManifestFetcher.client().send(req, HttpResponse.BodyHandlers.ofInputStream());

        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IllegalStateException("Download failed for " + mm.fileName + ": HTTP " + res.statusCode());
        }

        try (InputStream in = res.body()) {
            Files.copy(in, task.stagingFile, StandardCopyOption.REPLACE_EXISTING);
        }

        String stagedHash = Sha256.ofFile(task.stagingFile);
        if (!mm.sha256.equalsIgnoreCase(stagedHash)) {
            Files.deleteIfExists(task.stagingFile);
            throw new IllegalStateException("SHA-256 mismatch for " + mm.fileName + " (expected " + mm.sha256 + " got " + stagedHash + ")");
        }

        // Atomic-ish replace: move staging into place.
        // On Windows, REPLACE_EXISTING is generally fine.
        BootstrapLog.info("Installing " + mm.fileName);

        Files.move(task.stagingFile, task.targetJar, StandardCopyOption.REPLACE_EXISTING);
    }
}

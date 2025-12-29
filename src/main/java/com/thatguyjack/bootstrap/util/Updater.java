package com.thatguyjack.bootstrap.util;

import com.google.gson.JsonSyntaxException;
import com.thatguyjack.bootstrap.BootstrapClient;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

public final class Updater {
    private Updater() {}

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public interface ProgressCb {
        void onProgress(int done, int total, String status);
    }

    public static Manifest fetchManifest(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new IOException("Manifest HTTP " + res.statusCode());
        try {
            return BootstrapClient.GSON.fromJson(res.body(), Manifest.class);
        } catch (JsonSyntaxException e) {
            throw new IOException("Manifest JSON parse error: " + e.getMessage(), e);
        }
    }

    /** Returns true if anything changed compared to what we previously owned. */
    public static boolean stageAllMods(Manifest manifest, Path stagingModsDir, ProgressCb cb) throws Exception {
        Files.createDirectories(stagingModsDir);

        List<Manifest.ModEntry> list = manifest.mods == null ? List.of() : manifest.mods;
        int total = list.size();
        int done = 0;

        boolean changedAny = false;

        for (Manifest.ModEntry m : list) {
            if (m == null || !m.required) { done++; continue; }
            if (m.fileName == null || m.fileName.isBlank()) { done++; continue; }
            if (m.url == null || m.url.isBlank()) throw new IOException("Missing url for " + m.fileName);
            if (m.sha256 == null || m.sha256.isBlank()) throw new IOException("Missing sha256 for " + m.fileName);

            cb.onProgress(done, total, "Downloading " + m.fileName);

            Path out = stagingModsDir.resolve(m.fileName);
            Path part = stagingModsDir.resolve(m.fileName + ".part");

            downloadToFile(m.url, part);

            cb.onProgress(done, total, "Verifying " + m.fileName);
            String h = sha256(part);
            if (!m.sha256.equalsIgnoreCase(h)) {
                Files.deleteIfExists(part);
                throw new IOException("SHA256 mismatch for " + m.fileName);
            }

            // If file exists and identical hash, no change
            if (Files.exists(out)) {
                String existing = sha256(out);
                if (!existing.equalsIgnoreCase(h)) changedAny = true;
            } else {
                changedAny = true;
            }

            cb.onProgress(done, total, "Staging " + m.fileName);
            Files.move(part, out, StandardCopyOption.REPLACE_EXISTING);

            done++;
            cb.onProgress(done, total, "Staged " + m.fileName);
        }

        cb.onProgress(total, total, "Staging complete");
        return changedAny;
    }

    public static void downloadToFile(String url, Path out) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<Path> res = HTTP.send(req, HttpResponse.BodyHandlers.ofFile(out));
        if (res.statusCode() != 200) throw new IOException("Download HTTP " + res.statusCode() + " for " + url);
    }

    public static String sha256(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(file)) {
            byte[] buf = new byte[1024 * 1024];
            int r;
            while ((r = in.read(buf)) != -1) md.update(buf, 0, r);
        }
        byte[] dig = md.digest();
        StringBuilder sb = new StringBuilder(dig.length * 2);
        for (byte b : dig) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
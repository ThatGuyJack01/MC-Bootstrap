package com.thatguyjack.bootstrap.util;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BootstrapIO {

    public static void ensureDir(Path p) throws Exception {
        if (!Files.exists(p)) Files.createDirectories(p);
    }

    public static <T> T readJson(Path path, Class<T> clazz) {
        try {
            if (!Files.exists(path)) return null;
            try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                return new Gson().fromJson(br, clazz);
            }
        } catch (Exception e) {
            BootstrapLog.warn("Failed to read json " + path.getFileName() + ": " + e.getMessage());
            return null;
        }
    }

    public static void writeJson(Path path, Object obj, Gson gson) {
        try {
            if (path.getParent() != null) ensureDir(path.getParent());
            try (BufferedWriter bw = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                gson.toJson(obj, bw);
            }
        } catch (Exception e) {
            BootstrapLog.warn("Failed to write json " + path.getFileName() + ": " + e.getMessage());
        }
    }
}

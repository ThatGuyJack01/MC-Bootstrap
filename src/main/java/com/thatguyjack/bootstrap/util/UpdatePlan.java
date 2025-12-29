package com.thatguyjack.bootstrap.util;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class UpdatePlan {
    public final List<DownloadTask> downloads = new ArrayList<>();
    public final List<Path> deletes = new ArrayList<>();

    public boolean hasWork() {
        return !downloads.isEmpty() || !deletes.isEmpty();
    }

    public String summaryText() {
        return "Downloads: " + downloads.size() + " | Deletes: " + deletes.size();
    }

    public static final class DownloadTask {
        public final Manifest.ModEntry mod;
        public final Path targetJar;
        public final Path stagingFile;

        public DownloadTask(Manifest.ModEntry mod, Path targetJar, Path stagingFile) {
            this.mod = mod;
            this.targetJar = targetJar;
            this.stagingFile = stagingFile;
        }
    }
}
package com.thatguyjack.bootstrap.util;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class ApplierMain {

    private static final boolean DEBUG = false;

    private static final int LOCK_RETRY_COUNT = 40;
    private static final long LOCK_RETRY_SLEEP_MS = 250;
    private static final long POST_PID_EXIT_SLEEP_MS = 2500;

    public static void main(String[] args) {
        Path gameDir = null;
        Path stagingDir = null;
        Path logPath = null;

        try {
            Args a = Args.parse(args);
            gameDir = a.gameDir.toAbsolutePath().normalize();
            stagingDir = gameDir.resolve("godsmp_staging");
            Files.createDirectories(stagingDir);

            logPath = DEBUG ? stagingDir.resolve("godsmp_apply_log.txt") : null;

            log(logPath, "=== GodSMP Applier start ===");
            log(logPath, "gameDir=" + gameDir);
            log(logPath, "pid=" + a.pid);

            waitForPidExit(a.pid, logPath);
            Thread.sleep(POST_PID_EXIT_SLEEP_MS);

            Path stagingModsDir = stagingDir.resolve("mods");
            Path deleteList = stagingDir.resolve("delete.txt");
            Path newInstalled = stagingDir.resolve("installed.json");

            Path modsDir = gameDir.resolve("mods");

            if (!Files.isDirectory(modsDir)) {
                throw new IllegalStateException("mods folder not found: " + modsDir);
            }
            if (!Files.isDirectory(stagingModsDir)) {
                throw new IllegalStateException("staging mods folder missing: " + stagingModsDir);
            }

            int jarsBefore = countJars(modsDir);
            log(logPath, "jars_before=" + jarsBefore);

            Set<String> deleteSet = readDeleteSet(deleteList);
            log(logPath, "deleteSet=" + deleteSet);

            int deletedOk = 0;
            int deletedFail = 0;

            for (String name : deleteSet) {
                Path target = modsDir.resolve(name).normalize();
                if (!target.startsWith(modsDir)) {
                    log(logPath, "SKIP_DELETE suspicious path: " + target);
                    continue;
                }

                boolean ok = retryFileOp(logPath, "DELETE " + name, () -> {
                    Files.deleteIfExists(target);
                });

                if (ok) deletedOk++; else deletedFail++;
            }

            int copiedOk = 0;
            int copiedFail = 0;

            List<Path> stagedJars = listJars(stagingModsDir);
            log(logPath, "staged_jars=" + stagedJars.size());

            for (Path src : stagedJars) {
                String name = src.getFileName().toString();
                if (name.toLowerCase().startsWith("fabric-api")) {
                    log(logPath, "SKIP_COPY fabric-api: " + name);
                    continue;
                }

                Path dest = modsDir.resolve(name);
                Path tmp = modsDir.resolve(name + ".tmp");

                boolean ok = retryFileOp(logPath, "COPY " + name, () -> {
                    Files.copy(src, tmp, StandardCopyOption.REPLACE_EXISTING);
                    try {
                        Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    } catch (AtomicMoveNotSupportedException ex) {
                        Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                });

                if (ok) copiedOk++; else copiedFail++;
            }

            if (Files.exists(newInstalled)) {
                log(logPath, "staged installed.json present: " + newInstalled);
            } else {
                log(logPath, "WARN: staged installed.json missing: " + newInstalled);
            }


            int jarsAfter = countJars(modsDir);
            log(logPath, "jars_after_apply=" + jarsAfter);

            Thread.sleep(5000);
            int jarsAfter5 = countJars(modsDir);
            log(logPath, "jars_after_5s=" + jarsAfter5);

            log(logPath, "deleted_ok=" + deletedOk + " deleted_fail=" + deletedFail);
            log(logPath, "copied_ok=" + copiedOk + " copied_fail=" + copiedFail);

            if (jarsAfter5 < jarsAfter) {
                log(logPath, "!!! WARNING: jar count dropped AFTER apply finished. This indicates an external process (launcher/AV) modified the mods folder.");
            }

            log(logPath, "=== GodSMP Applier end ===");

            if (deletedFail > 0 || copiedFail > 0) System.exit(2);

        } catch (Exception e) {
            try {
                if (logPath != null) {
                    log(logPath, "FATAL: " + e);
                    for (StackTraceElement el : e.getStackTrace()) {
                        log(logPath, "  at " + el);
                    }
                } else if (gameDir != null && stagingDir != null) {
                    Files.writeString(stagingDir.resolve("godsmp_apply_error.txt"),
                            "Apply failed: " + e + System.lineSeparator(),
                            StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                }
            } catch (Exception ignored) {}

            System.exit(1);
        }
    }

    private static void waitForPidExit(long pid, Path logPath) throws InterruptedException {
        if (pid <= 0) return;
        log(logPath, "Waiting for PID to exit: " + pid);
        while (ProcessHandle.of(pid).isPresent()) {
            Thread.sleep(500);
        }
        log(logPath, "PID exited: " + pid);
    }

    private static int countJars(Path dir) throws Exception {
        try (var s = Files.list(dir)) {
            return (int) s.filter(p -> Files.isRegularFile(p)
                            && p.getFileName().toString().toLowerCase().endsWith(".jar"))
                    .count();
        }
    }

    private static List<Path> listJars(Path dir) throws Exception {
        try (var s = Files.list(dir)) {
            List<Path> out = new ArrayList<>();
            s.forEach(p -> {
                if (Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".jar")) {
                    out.add(p);
                }
            });
            out.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()));
            return out;
        }
    }

    private static Set<String> readDeleteSet(Path deleteList) throws Exception {
        Set<String> set = new LinkedHashSet<>();
        if (!Files.exists(deleteList)) return set;

        for (String line : Files.readAllLines(deleteList, StandardCharsets.UTF_8)) {
            if (line == null) continue;
            String name = line.trim();
            if (name.isEmpty()) continue;
            
            if (!name.endsWith(".jar")) continue;
            if (name.contains("/") || name.contains("\\") || name.contains("..")) continue;

            if (name.toLowerCase().startsWith("fabric-api")) continue;

            set.add(name);
        }
        return set;
    }

    @FunctionalInterface
    private interface IOAction { void run() throws Exception; }

    private static boolean retryFileOp(Path logPath, String label, IOAction action) throws InterruptedException {
        Exception last = null;

        for (int attempt = 1; attempt <= LOCK_RETRY_COUNT; attempt++) {
            try {
                action.run();
                if (attempt > 1) log(logPath, label + " succeeded after retry " + attempt);
                return true;
            } catch (Exception e) {
                last = e;

                if (isWindowsFileInUse(e) && attempt < LOCK_RETRY_COUNT) {
                    Thread.sleep(LOCK_RETRY_SLEEP_MS);
                    continue;
                }

                log(logPath, label + " FAILED: " + e);
                return false;
            }
        }

        log(logPath, label + " FAILED after retries: " + last);
        return false;
    }

    private static boolean isWindowsFileInUse(Exception e) {
        if (!(e instanceof FileSystemException)) return false;
        String msg = e.getMessage();
        if (msg == null) return false;
        msg = msg.toLowerCase(Locale.ROOT);
        return msg.contains("being used by another process") || msg.contains("in use");
    }

    private static void log(Path logPath, String msg) {
        if (!DEBUG || logPath  == null) return;
        try {
            Files.writeString(
                    logPath,
                    msg + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (Exception ignored) {}
    }

    private static final class Args {
        final Path gameDir;
        final long pid;

        private Args(Path gameDir, long pid) {
            this.gameDir = gameDir;
            this.pid = pid;
        }

        static Args parse(String[] args) {
            Path gameDir = null;
            Long pid = null;

            for (int i = 0; i < args.length; i++) {
                String k = args[i];
                if ("--gameDir".equals(k) && i + 1 < args.length) gameDir = Paths.get(args[++i]);
                else if ("--pid".equals(k) && i + 1 < args.length) pid = Long.parseLong(args[++i]);
            }

            if (gameDir == null || pid == null) {
                throw new IllegalArgumentException("Usage: --gameDir <path> --pid <pid>");
            }
            return new Args(gameDir.toAbsolutePath().normalize(), pid);
        }
    }
}

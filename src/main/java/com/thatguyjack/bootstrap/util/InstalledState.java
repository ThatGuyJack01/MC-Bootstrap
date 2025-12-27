package com.thatguyjack.bootstrap.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public final class InstalledState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public Set<String> ownedFiles = new HashSet<>();

    public static InstalledState loadOrCreate(Path path) {
        InstalledState st = BootstrapIO.readJson(path, InstalledState.class);
        if (st == null) {
            st = new InstalledState();
            BootstrapIO.writeJson(path, st, GSON);
        }
        return st;
    }

    public void save(Path path) {
        BootstrapIO.writeJson(path, this, GSON);
    }
}

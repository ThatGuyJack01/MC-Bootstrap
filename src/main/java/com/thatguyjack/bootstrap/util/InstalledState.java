package com.thatguyjack.bootstrap.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;

public final class InstalledState {
    public String packId;
    public String packVersion;
    public List<String> ownedFiles = new ArrayList<>();
}

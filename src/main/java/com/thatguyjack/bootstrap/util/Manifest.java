package com.thatguyjack.bootstrap.util;

import java.util.List;

public final class Manifest {
    public String packId;
    public String packVersion;
    public List<ModEntry> mods;

    public static final class ModEntry {
        public String id;
        public String fileName;
        public String url;
        public String sha256;
        public boolean required = true;
    }
}

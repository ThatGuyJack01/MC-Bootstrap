package com.thatguyjack.bootstrap.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BootstrapLog {
    private static final Logger LOG = LoggerFactory.getLogger("bootstrap");

    public static void info(String msg) { LOG.info(msg); }
    public static void warn(String msg) { LOG.warn(msg); }
    public static void error(String msg, Throwable t) { LOG.error(msg, t); }
}

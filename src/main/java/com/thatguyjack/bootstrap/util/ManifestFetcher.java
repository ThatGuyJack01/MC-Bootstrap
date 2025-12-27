package com.thatguyjack.bootstrap.util;

import com.google.gson.Gson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class ManifestFetcher {
    private static final Gson GSON = new Gson();

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static Manifest fetch(String url) throws Exception {
        BootstrapLog.info("Fetching manifest: " + url);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        HttpResponse<String> res = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IllegalStateException("Manifest fetch failed: HTTP " + res.statusCode());
        }

        Manifest m = GSON.fromJson(res.body(), Manifest.class);
        if (m == null || m.mods == null) {
            throw new IllegalStateException("Manifest is missing 'mods'");
        }
        return m;
    }

    static HttpClient client() {
        return CLIENT;
    }
}

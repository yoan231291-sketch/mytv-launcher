package com.yoan.mytv;

import android.net.Uri;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;

/** Lista corta de servidores y rutas de anuncios que usa el reproductor embebido de YouTube. */
final class AdBlock {
    private static final String[] HOSTS = {
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "googletagservices.com",
        "imasdk.googleapis.com", "pubads.g.doubleclick.net", "adservice.google.com", "googletagmanager.com",
        "amazon-adsystem.com", "adsafeprotected.com", "moatads.com", "scorecardresearch.com"
    };
    private static final String[] YT_PATHS = {
        "/pagead/", "/api/stats/ads", "/api/stats/atr", "/ptracking", "/get_midroll_info", "/youtubei/v1/log_event", "/generate_204"
    };

    static boolean isAd(Uri u) {
        String host = u.getHost();
        if (host == null) return false;
        for (String h : HOSTS) if (host.equals(h) || host.endsWith("." + h)) return true;
        if (host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com")) {
            String path = u.getPath() == null ? "" : u.getPath();
            for (String p : YT_PATHS) if (path.startsWith(p)) return true;
        }
        return false;
    }

    static WebResourceResponse EMPTY() {
        return new WebResourceResponse("text/plain", "utf-8", 204, "No Content", null, new ByteArrayInputStream(new byte[0]));
    }
}

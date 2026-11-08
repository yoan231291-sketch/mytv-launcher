package com.yoan.mytv;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.service.notification.NotificationListenerService;

import org.json.JSONObject;

import java.util.List;

/** Qué se está viendo: app al frente y título de lo que suena (de la sesión de medios de la app). */
final class NowPlaying {
    /** Necesario para leer las sesiones de medios (se activa por ADB: cmd notification allow_listener). */
    public static class Listener extends NotificationListenerService { }

    static JSONObject info(HomeWatcherService svc, boolean on) {
        JSONObject j = new JSONObject();
        try {
            j.put("on", on);
            String fg = null; try { fg = svc.foreground(); } catch (Exception ignored) { }
            if (fg != null) { j.put("pkg", fg).put("app", label(svc, fg)); }
            MediaSessionManager m = (MediaSessionManager) svc.getSystemService(Context.MEDIA_SESSION_SERVICE);
            List<MediaController> list = m.getActiveSessions(new ComponentName(svc, Listener.class));
            for (MediaController c : list) {
                PlaybackState st = c.getPlaybackState(); MediaMetadata md = c.getMetadata();
                if (md == null) continue;
                String title = md.getString(MediaMetadata.METADATA_KEY_TITLE);
                if (title == null || title.isEmpty()) title = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
                if (title == null || title.isEmpty()) continue;
                j.put("title", title).put("from", label(svc, c.getPackageName()))
                 .put("playing", st != null && st.getState() == PlaybackState.STATE_PLAYING);
                String sub = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
                if (sub != null) j.put("sub", sub);
                break;
            }
        } catch (Exception e) { try { j.put("note", "sin permiso de sesiones"); } catch (Exception ignored) { } }
        return j;
    }

    private static String label(Context c, String pkg) {
        if (pkg.equals(c.getPackageName())) return "MyTV (cámaras)";
        try { PackageManager pm = c.getPackageManager(); ApplicationInfo ai = pm.getApplicationInfo(pkg, 0); return pm.getApplicationLabel(ai).toString(); }
        catch (Exception e) { return pkg; }
    }
}

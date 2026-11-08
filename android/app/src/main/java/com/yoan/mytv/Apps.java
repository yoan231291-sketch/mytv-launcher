package com.yoan.mytv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Apps instaladas: listar (con ícono) y abrir. Lo usan la página de MyTV y el control remoto del teléfono. */
final class Apps {
    /** Abre la primera app instalada de una lista "pkg1,pkg2". */
    static boolean launch(Context c, String pkgs) {
        PackageManager pm = c.getPackageManager();
        for (String p : pkgs.split(",")) {
            Intent i = pm.getLeanbackLaunchIntentForPackage(p.trim());
            if (i == null) i = pm.getLaunchIntentForPackage(p.trim());
            if (i != null) {
                HomeWatcherService.allowAmazonHomeFor(5000);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                return true;
            }
        }
        return false;
    }

    /** Primer paquete instalado de una lista "pkg1,pkg2" (o null). */
    static String installed(Context c, String pkgs) {
        for (String p : pkgs.split(",")) {
            try { c.getPackageManager().getPackageInfo(p.trim(), 0); return p.trim(); } catch (Exception ignored) { }
        }
        return null;
    }

    /** JSON con las apps que se pueden abrir: [{pkg, label, icon(data:url)}]. */
    static String list(Context c) { return list(c, true); }

    /** Igual que list() pero sin íconos (rápido; para las órdenes de voz). */
    static String labels(Context c) { return list(c, false); }

    static String list(Context c, boolean icons) {
        PackageManager pm = c.getPackageManager();
        JSONArray out = new JSONArray();
        Set<String> seen = new HashSet<>();
        seen.add(c.getPackageName());
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            List<ResolveInfo> list = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0);
            for (ResolveInfo r : list) {
                String pkg = r.activityInfo.packageName;
                if (!seen.add(pkg)) continue;
                try {
                    JSONObject o = new JSONObject();
                    o.put("pkg", pkg);
                    o.put("label", r.loadLabel(pm).toString());
                    if (icons) {
                        Drawable d = r.activityInfo.applicationInfo.loadBanner(pm);
                        if (d == null) d = r.loadIcon(pm);
                        o.put("icon", MainActivity.toDataUrl(d));
                    }
                    out.put(o);
                } catch (Exception ignored) { }
            }
        }
        return out.toString();
    }
}

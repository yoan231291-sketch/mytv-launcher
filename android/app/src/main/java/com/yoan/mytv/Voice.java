package com.yoan.mytv;

import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Órdenes de voz del teléfono (como el botón de Alexa del control): el teléfono convierte la voz en texto
 * y aquí se decide qué hacer en la TV. Español e inglés. Ej.: "pon Netflix", "busca recetas en YouTube",
 * "sube el volumen", "apaga la tele", "pon la cámara de Tokio", "pausa".
 * Devuelve una lista de órdenes del control: {"k","v"} pares, o un mensaje si no entendió.
 */
final class Voice {
    static final class Plan { final String[][] cmds; final String said; Plan(String said, String[]... c) { this.said = said; cmds = c; } }

    static String norm(String s) {
        s = Normalizer.normalize(s.toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.replaceAll("[¿?¡!.,]", " ").replaceAll("\\s+", " ").trim();
    }

    static Plan plan(String raw, String appsJson, String citiesCsv) {
        String t = norm(raw).replaceFirst("^(oye |hey )?alexa,? ", "");
        Matcher m;
        // encendido
        if (t.matches(".*\\b(apaga|apagar|turn off|power off)\\b.*")) return new Plan("Apagando la TV", new String[]{"power_off", ""});
        if (t.matches(".*\\b(prende|prender|enciende|encender|turn on|power on)\\b.*(tv|tele|television|pantalla).*")) return new Plan("Prendiendo la TV", new String[]{"power_on", ""});
        // volumen
        m = Pattern.compile("\\b(sube|subir|aumenta|turn up|volume up|mas volumen)\\b.*?(\\d+)?$").matcher(t);
        if (t.matches(".*\\b(sube|subir|aumenta|turn up|volume up|mas volumen)\\b.*")) { int n = num(t, 3); return new Plan("Subiendo volumen", rep("volup", n)); }
        if (t.matches(".*\\b(baja|bajar|disminuye|turn down|volume down|menos volumen)\\b.*")) { int n = num(t, 3); return new Plan("Bajando volumen", rep("voldown", n)); }
        if (t.matches(".*\\b(silencia|silencio|mute|quita el sonido|pon el sonido)\\b.*")) return new Plan("Silencio", new String[]{"mute", ""});
        // reproducción
        if (t.matches("^(pausa|pausar|pause|para|deten|stop|detente)( .*)?$")) return new Plan("Pausa", new String[]{"playpause", ""});
        if (t.matches("^(play|reproduce|continua|sigue|resume|dale play)$")) return new Plan("Reproducir", new String[]{"playpause", ""});
        if (t.matches(".*\\b(adelanta|forward|skip)\\b.*")) return new Plan("Adelantando", new String[]{"ff", ""});
        if (t.matches(".*\\b(retrocede|regresa el video|rewind)\\b.*")) return new Plan("Retrocediendo", new String[]{"rew", ""});
        // navegación
        if (t.matches("^(ve al |ir al |vuelve al )?(inicio|casa|home|go home)$")) return new Plan("Inicio", new String[]{"home", ""});
        if (t.matches("^(atras|regresa|vuelve|go back|back)$")) return new Plan("Atrás", new String[]{"back", ""});
        // vista de 4 ciudades
        if (t.matches(".*\\b(2|dos|two|4|cuatro|four|varias) (ciudades|camaras|cities|cameras)\\b.*|.*\\bmosaico\\b.*")) return new Plan("2 ciudades en vivo", new String[]{"mosaic", "1"});
        if (t.matches(".*\\b(una|1|one) (ciudad|camara|city)( sola)?$")) return new Plan("Una ciudad", new String[]{"mosaic", "0"});
        // ciudades / cámaras
        m = Pattern.compile("\\b(?:camara|ciudad|camera|city)(?: de| en| of)? (.+)$").matcher(t);
        if (m.find()) { String id = city(m.group(1), citiesCsv); if (id != null) return new Plan("Cámara de " + m.group(1), new String[]{"city", id}); }
        // buscar en YouTube
        m = Pattern.compile("^(?:busca|buscar|search|search for|pon|ponme|reproduce|play|quiero ver|muestrame)(?: (?:videos? de|musica de|la cancion|el video de))? (.+?) (?:en|on) youtube$").matcher(t);
        if (m.find()) return new Plan("Buscando en YouTube: " + m.group(1), new String[]{"text_yt", m.group(1)});
        // abrir app
        m = Pattern.compile("^(?:abre|abrir|open|pon|ponme|launch|inicia|ve a|ir a|quiero ver) (?:la app de |la aplicacion de |el |la )?(.+)$").matcher(t);
        if (m.find()) {
            String target = m.group(1);
            String pkg = app(target, appsJson);
            if (pkg != null) return new Plan("Abriendo " + target, new String[]{"app", pkg});
            String id = city(target, citiesCsv);
            if (id != null) return new Plan("Cámara de " + target, new String[]{"city", id});
            if (!t.startsWith("abre") && !t.startsWith("open")) return new Plan("Buscando en YouTube: " + target, new String[]{"text_yt", target});
        }
        m = Pattern.compile("^(?:busca|buscar|search|search for) (.+)$").matcher(t);
        if (m.find()) return new Plan("Buscando en YouTube: " + m.group(1), new String[]{"text_yt", m.group(1)});
        return new Plan(null);
    }

    private static int num(String t, int def) {
        Matcher m = Pattern.compile("(\\d+)").matcher(t); if (m.find()) return Math.max(1, Math.min(20, Integer.parseInt(m.group(1))));
        if (t.contains("mucho") || t.contains("a lot")) return 8;
        if (t.contains("poco") || t.contains("un poquito") || t.contains("a little")) return 2;
        return def;
    }
    private static String[][] rep(String k, int n) { String[][] r = new String[n][]; for (int i = 0; i < n; i++) r[i] = new String[]{k, ""}; return r; }

    /** Busca la app por nombre (lista JSON de Apps.list). */
    private static String app(String name, String appsJson) {
        String want = norm(name).replace("+", " plus").replace("disney plus", "disney").trim();
        String best = null; int bestScore = 0;
        try {
            org.json.JSONArray a = new org.json.JSONArray(appsJson);
            for (int i = 0; i < a.length(); i++) {
                org.json.JSONObject o = a.getJSONObject(i);
                String label = norm(o.optString("label")).replace("+", " plus");
                int sc = 0;
                if (label.equals(want)) sc = 100;
                else if (label.startsWith(want) || want.startsWith(label)) sc = 80;
                else if (label.contains(want) || want.contains(label)) sc = 60;
                if (sc > bestScore) { bestScore = sc; best = o.optString("pkg"); }
            }
        } catch (Exception ignored) { }
        return best;
    }

    /** Ciudad por nombre: "id:Nombre;id:Nombre". */
    private static String city(String name, String csv) {
        String want = norm(name);
        if (csv == null) return null;
        for (String part : csv.split(";")) {
            int i = part.indexOf(':'); if (i < 0) continue;
            String id = part.substring(0, i), label = norm(part.substring(i + 1));
            if (label.equals(want) || label.contains(want) || want.contains(label)) return id;
        }
        return null;
    }
}

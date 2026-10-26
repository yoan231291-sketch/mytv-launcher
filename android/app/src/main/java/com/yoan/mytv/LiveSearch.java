package com.yoan.mytv;

import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Búsqueda de videos en vivo en YouTube (filtro "En vivo"), sin API key ni dependencias. */
final class LiveSearch {
    private static final Pattern ITEM = Pattern.compile("\"videoRenderer\":\\{\"videoId\":\"([\\w-]{11})\".{0,1500}?\"title\":\\{\"runs\":\\[\\{\"text\":\"(.*?)\"", Pattern.DOTALL);
    // Recopilaciones, noticias, música, recorridos grabados: no son cámaras fijas
    private static final Pattern BAD = Pattern.compile("pre-?recorded|top live|webcam tour|cams? around the world|\\b\\d{3,4} (top )?live|protest|news|lofi|music|relaxing|radio|replay|highlights", Pattern.CASE_INSENSITIVE);

    static JSONArray search(String query) {
        JSONArray out = new JSONArray();
        try {
            URL url = new URL("https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8") + "&sp=EgJAAQ%253D%253D");
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(10000); c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Safari/537.36");
            c.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            c.setRequestProperty("Cookie", "CONSENT=YES+1; SOCS=CAI");
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[16384]; int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            Matcher m = ITEM.matcher(bos.toString("UTF-8"));
            Set<String> ids = new LinkedHashSet<>();
            while (m.find() && ids.size() < 8) if (!BAD.matcher(m.group(2)).find()) ids.add(m.group(1));
            for (String id : ids) out.put(id);
        } catch (Exception ignored) { }
        return out;
    }
}

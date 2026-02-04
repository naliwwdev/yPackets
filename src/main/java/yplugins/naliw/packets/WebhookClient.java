package yplugins.naliw.packets;

import org.bukkit.configuration.file.FileConfiguration;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class WebhookClient {
    private final YPacketsPlugin plugin;

    public WebhookClient(YPacketsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isConfigured() {
        FileConfiguration cfg = plugin.getConfig();
        String url = cfg.getString("webhook-url", "");
        return url != null && !url.trim().isEmpty();
    }

    public void postJson(String json) throws Exception {
        String urlStr = plugin.getConfig().getString("webhook-url", "");
        if (urlStr == null || urlStr.trim().isEmpty()) {
            throw new IllegalStateException("webhook-url is empty");
        }

        HttpURLConnection con = (HttpURLConnection) new URL(urlStr).openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setConnectTimeout(8000);
        con.setReadTimeout(12000);
        con.setRequestProperty("Content-Type", "application/json; charset=utf-8");

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        con.setFixedLengthStreamingMode(bytes.length);

        OutputStream os = con.getOutputStream();
        try {
            os.write(bytes);
        } finally {
            os.close();
        }

        int code = con.getResponseCode();
        if (code < 200 || code >= 300) {
            String body = readBody(con);
            throw new IllegalStateException("Webhook HTTP " + code + (body.isEmpty() ? "" : (": " + body)));
        }
    }

    private static String readBody(HttpURLConnection con) {
        InputStream is = null;
        try {
            is = con.getErrorStream();
            if (is == null) {
                is = con.getInputStream();
            }
            if (is == null) {
                return "";
            }

            byte[] buf = new byte[4096];
            int read;
            StringBuilder sb = new StringBuilder();
            while ((read = is.read(buf)) != -1) {
                sb.append(new String(buf, 0, read, StandardCharsets.UTF_8));
                if (sb.length() > 4000) {
                    break;
                }
            }
            return sb.toString().trim();
        } catch (Throwable ignored) {
            return "";
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}

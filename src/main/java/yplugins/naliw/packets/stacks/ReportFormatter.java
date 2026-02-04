package yplugins.naliw.packets.stacks;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import yplugins.naliw.packets.YPacketsPlugin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportFormatter {
    public static String toWebhookJson(YPacketsPlugin plugin, String requester, StackSampler.SampleResult result) {
        Map<String, String> pluginPrefixes = buildPluginPrefixMap();
        Map<String, Long> pluginHits = rankPlugins(pluginPrefixes, result);

        Instant now = Instant.now();
        String iso = now.toString();
        String local = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(now);

        String tpsAnsi = tpsAnsi(
                plugin.getTickMonitor().getTps1m(),
                plugin.getTickMonitor().getMspt1m(),
                plugin.getTickMonitor().getDelayedTicks1m()
        );

        StringBuilder summary = new StringBuilder();
        summary.append("Solicitado por: ").append(requester == null ? "?" : requester);
        summary.append(" | ").append(local);

        StringBuilder topPlugins = new StringBuilder();
        appendTopPlugins(topPlugins, pluginHits, result.getSamples(), 6);

        StringBuilder topStacks = new StringBuilder();
        appendTopStacks(topStacks, result, 5);

        StringBuilder extra = new StringBuilder();
        appendExtraInfo(extra);

        String title = "yPackets Lag Report";
        String description = summary.toString();

        String fieldTps = "```ansi\n" + tpsAnsi + "\n```";
        String fieldPlugins = topPlugins.length() == 0 ? "Sem plugins detectados pelo stack (heuristica)." : topPlugins.toString();
        String fieldStacks = topStacks.length() == 0 ? "Sem stacks." : topStacks.toString();
        String fieldExtra = extra.length() == 0 ? "-" : extra.toString();

        StringBuilder json = new StringBuilder();
        json.append("{\"content\":\"\",\"embeds\":[{");
        json.append("\"title\":\"").append(escapeJson(title)).append("\",");
        json.append("\"description\":\"").append(escapeJson(description)).append("\",");
        json.append("\"timestamp\":\"").append(escapeJson(iso)).append("\",");
        json.append("\"color\":").append(colorForTps(plugin.getTickMonitor().getTps1m())).append(",");
        json.append("\"fields\":[");
        json.append("{\"name\":\"TPS / MSPT\",\"value\":\"").append(escapeJson(truncate(fieldTps, 950))).append("\",\"inline\":false},");
        json.append("{\"name\":\"Plugins (provavel)\",\"value\":\"").append(escapeJson(truncate(fieldPlugins, 950))).append("\",\"inline\":true},");
        json.append("{\"name\":\"Extra\",\"value\":\"").append(escapeJson(truncate(fieldExtra, 950))).append("\",\"inline\":true},");
        json.append("{\"name\":\"Top stacks\",\"value\":\"").append(escapeJson(truncate(fieldStacks, 950))).append("\",\"inline\":false}");
        json.append("]");
        json.append("}]}");

        return json.toString();
    }

    private static String format(double v) {
        return String.format(java.util.Locale.US, "%.2f", v);
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }

        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\':
                    out.append("\\\\");
                    break;
                case '"':
                    out.append("\\\"");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                    break;
            }
        }
        return out.toString();
    }

    private static String tpsAnsi(double tps, double mspt, long delayedTicks1m) {
        String tpsColor;
        if (tps >= 19.5) {
            tpsColor = "\u001b[2;32m";
        } else if (tps >= 18.0) {
            tpsColor = "\u001b[2;33m";
        } else {
            tpsColor = "\u001b[2;31m";
        }

        String msptColor;
        if (mspt <= 50.0) {
            msptColor = "\u001b[2;32m";
        } else if (mspt <= 60.0) {
            msptColor = "\u001b[2;33m";
        } else {
            msptColor = "\u001b[2;31m";
        }

        String reset = "\u001b[0m";
        return "TPS: " + tpsColor + format(tps) + reset + "\n" +
                "MSPT: " + msptColor + format(mspt) + reset + "\n" +
                "DelayedTicks(1m): " + delayedTicks1m;
    }

    private static void appendExtraInfo(StringBuilder out) {
        try {
            int online = getOnlineCount();
            int max = Bukkit.getMaxPlayers();
            out.append("Online: ").append(online).append("/").append(max).append("\n");
        } catch (Throwable ignored) {
        }

        try {
            out.append("Server: ").append(Bukkit.getName()).append(" ").append(Bukkit.getVersion()).append("\n");
        } catch (Throwable ignored) {
        }

        try {
            out.append("Java: ").append(System.getProperty("java.version")).append("\n");
        } catch (Throwable ignored) {
        }

        try {
            Runtime rt = Runtime.getRuntime();
            long used = rt.totalMemory() - rt.freeMemory();
            long max = rt.maxMemory();
            out.append("Mem: ").append(formatMb(used)).append("/").append(formatMb(max)).append(" MB\n");
        } catch (Throwable ignored) {
        }

        try {
            out.append("Plugins: ").append(Bukkit.getPluginManager().getPlugins().length).append("\n");
        } catch (Throwable ignored) {
        }
    }

    private static String formatMb(long bytes) {
        return String.valueOf(bytes / (1024L * 1024L));
    }

    private static int getOnlineCount() {
        Object onlinePlayers = Bukkit.getOnlinePlayers();
        if (onlinePlayers == null) {
            return 0;
        }

        if (onlinePlayers instanceof java.util.Collection) {
            return ((java.util.Collection<?>) onlinePlayers).size();
        }

        if (onlinePlayers.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(onlinePlayers);
        }

        return 0;
    }

    private static int colorForTps(double tps) {
        if (tps >= 19.5) {
            return 0x57F287;
        }
        if (tps >= 18.0) {
            return 0xFEE75C;
        }
        return 0xED4245;
    }

    private static Map<String, String> buildPluginPrefixMap() {
        Plugin[] plugins = Bukkit.getPluginManager().getPlugins();
        Map<String, String> map = new HashMap<String, String>();
        for (Plugin p : plugins) {
            String main = p.getDescription() == null ? null : p.getDescription().getMain();
            if (main == null) {
                continue;
            }
            int idx = main.lastIndexOf('.');
            if (idx <= 0) {
                continue;
            }
            String prefix = main.substring(0, idx + 1);
            map.put(prefix, p.getName());
        }
        return map;
    }

    private static Map<String, Long> rankPlugins(Map<String, String> pluginPrefixes, StackSampler.SampleResult result) {
        Map<String, Long> hits = new HashMap<String, Long>();

        for (StackSampler.Hotspot h : result.getHotspots()) {
            String pluginName = detectPluginFromKey(pluginPrefixes, h.getKey());
            if (pluginName == null) {
                continue;
            }
            Long cur = hits.get(pluginName);
            hits.put(pluginName, cur == null ? h.getHits() : (cur + h.getHits()));
        }
        return hits;
    }

    private static String detectPluginFromKey(Map<String, String> pluginPrefixes, String key) {
        if (key == null) {
            return null;
        }

        String[] parts = key.split("\\s\\|\\s");
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            int sharp = p.indexOf('#');
            String className = sharp == -1 ? p : p.substring(0, sharp);

            for (Map.Entry<String, String> e : pluginPrefixes.entrySet()) {
                if (className.startsWith(e.getKey())) {
                    return e.getValue();
                }
            }
        }
        return null;
    }

    private static void appendTopPlugins(StringBuilder out, Map<String, Long> pluginHits, long totalSamples, int limit) {
        if (pluginHits.isEmpty()) {
            return;
        }

        List<Map.Entry<String, Long>> entries = new ArrayList<Map.Entry<String, Long>>(pluginHits.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String, Long>>() {
            @Override
            public int compare(Map.Entry<String, Long> a, Map.Entry<String, Long> b) {
                return Long.compare(b.getValue(), a.getValue());
            }
        });

        int count = Math.min(limit, entries.size());
        for (int i = 0; i < count; i++) {
            Map.Entry<String, Long> e = entries.get(i);
            double pct = totalSamples <= 0 ? 0.0 : (100.0 * ((double) e.getValue() / (double) totalSamples));
            out.append(i + 1).append(". ").append(e.getKey()).append(" - ").append(format(pct)).append("%\n");
        }
    }

    private static void appendTopStacks(StringBuilder out, StackSampler.SampleResult result, int limit) {
        List<StackSampler.Hotspot> hs = result.getHotspots();
        int count = Math.min(limit, hs.size());
        for (int i = 0; i < count; i++) {
            StackSampler.Hotspot h = hs.get(i);
            double pct = (result.getSamples() == 0) ? 0.0 : (100.0 * ((double) h.getHits() / (double) result.getSamples()));
            out.append(i + 1).append(") ").append(format(pct)).append("% - ");
            out.append(trimStackKey(h.getKey())).append("\n");
        }
    }

    private static String trimStackKey(String key) {
        if (key == null) {
            return "";
        }
        int idx = key.indexOf(" | ");
        if (idx == -1) {
            return key;
        }
        return key.substring(0, idx);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) {
            return "";
        }
        if (s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, Math.max(0, maxLen - 14)) + "... (truncated)";
    }
}

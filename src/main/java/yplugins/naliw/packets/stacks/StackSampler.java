package yplugins.naliw.packets.stacks;

import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class StackSampler {
    public static final class SampleResult {
        private final long samples;
        private final List<Hotspot> hotspots;

        public SampleResult(long samples, List<Hotspot> hotspots) {
            this.samples = samples;
            this.hotspots = hotspots;
        }

        public long getSamples() {
            return samples;
        }

        public List<Hotspot> getHotspots() {
            return hotspots;
        }
    }

    public static final class Hotspot {
        private final String key;
        private final long hits;

        public Hotspot(String key, long hits) {
            this.key = key;
            this.hits = hits;
        }

        public String getKey() {
            return key;
        }

        public long getHits() {
            return hits;
        }
    }

    private final Plugin plugin;
    private final Thread serverThread;
    private final ScheduledExecutorService executor;

    public StackSampler(Plugin plugin, Thread serverThread) {
        this.plugin = plugin;
        this.serverThread = serverThread;
        this.executor = Executors.newSingleThreadScheduledExecutor();
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    public SampleResult sampleBlocking(int intervalMs, int durationSeconds) throws InterruptedException {
        final long endAt = System.currentTimeMillis() + (durationSeconds * 1000L);
        final Map<String, Long> counts = new HashMap<String, Long>();
        long samples = 0L;

        while (System.currentTimeMillis() < endAt) {
            StackTraceElement[] trace = serverThread.getStackTrace();
            String key = buildKey(trace);
            Long cur = counts.get(key);
            counts.put(key, cur == null ? 1L : (cur + 1L));
            samples++;
            Thread.sleep(Math.max(1, intervalMs));
        }

        List<Hotspot> hotspots = new ArrayList<Hotspot>();
        for (Map.Entry<String, Long> e : counts.entrySet()) {
            hotspots.add(new Hotspot(e.getKey(), e.getValue()));
        }

        Collections.sort(hotspots, new Comparator<Hotspot>() {
            @Override
            public int compare(Hotspot a, Hotspot b) {
                return Long.compare(b.hits, a.hits);
            }
        });

        if (hotspots.size() > 15) {
            hotspots = new ArrayList<Hotspot>(hotspots.subList(0, 15));
        }

        return new SampleResult(samples, hotspots);
    }

    public void sampleAsync(final int intervalMs, final int durationSeconds, final SampleCallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    SampleResult res = sampleBlocking(intervalMs, durationSeconds);
                    callback.onSuccess(res);
                } catch (Throwable t) {
                    callback.onError(t);
                }
            }
        });
    }

    private String buildKey(StackTraceElement[] trace) {
        if (trace == null || trace.length == 0) {
            return "<empty>";
        }

        int max = Math.min(12, trace.length);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < max; i++) {
            StackTraceElement el = trace[i];
            if (i > 0) {
                sb.append(" | ");
            }
            sb.append(el.getClassName()).append("#").append(el.getMethodName());
        }
        return sb.toString();
    }

    public interface SampleCallback {
        void onSuccess(SampleResult result);

        void onError(Throwable error);
    }
}

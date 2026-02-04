package yplugins.naliw.packets.stacks;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class TickMonitor {
    private final Plugin plugin;
    private int taskId = -1;

    private long lastTickNano = -1L;
    private double tps1m = 20.0;
    private double mspt1m = 50.0;
    private long delayedTicks1m = 0L;

    private long windowStartNano;
    private long windowTicks;
    private long windowTotalTickNano;
    private long windowDelayedTicks;

    public TickMonitor(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (taskId != -1) {
            return;
        }

        windowStartNano = System.nanoTime();
        windowTicks = 0L;
        windowTotalTickNano = 0L;
        windowDelayedTicks = 0L;

        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                long now = System.nanoTime();
                if (lastTickNano != -1L) {
                    long dt = now - lastTickNano;
                    windowTotalTickNano += dt;
                    if (dt > 50_000_000L) {
                        windowDelayedTicks++;
                    }
                }
                lastTickNano = now;
                windowTicks++;

                long elapsed = now - windowStartNano;
                if (elapsed >= 60_000_000_000L && windowTicks > 1) {
                    double seconds = elapsed / 1_000_000_000.0;
                    double tps = Math.min(20.0, windowTicks / seconds);
                    double avgTickNs = (double) windowTotalTickNano / (double) (windowTicks - 1);

                    tps1m = tps;
                    mspt1m = avgTickNs / 1_000_000.0;
                    delayedTicks1m = windowDelayedTicks;

                    windowStartNano = now;
                    windowTicks = 0L;
                    windowTotalTickNano = 0L;
                    windowDelayedTicks = 0L;
                }
            }
        }, 1L, 1L);
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    public double getTps1m() {
        return tps1m;
    }

    public double getMspt1m() {
        return mspt1m;
    }

    public long getDelayedTicks1m() {
        return delayedTicks1m;
    }
}

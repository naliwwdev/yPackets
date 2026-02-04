package yplugins.naliw.packets;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import yplugins.naliw.packets.comandos.YPacketsCommand;
import yplugins.naliw.packets.stacks.StackSampler;
import yplugins.naliw.packets.stacks.TickMonitor;

import java.util.Objects;

public class YPacketsPlugin extends JavaPlugin {
    private TickMonitor tickMonitor;
    private StackSampler stackSampler;
    private WebhookClient webhookClient;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.webhookClient = new WebhookClient(this);
        this.tickMonitor = new TickMonitor(this);
        this.stackSampler = new StackSampler(this, Thread.currentThread());

        tickMonitor.start();

        PluginCommand command = getCommand("ypackets");
        Objects.requireNonNull(command, "Command ypackets not found in plugin.yml");
        command.setExecutor(new YPacketsCommand(this));
    }

    @Override
    public void onDisable() {
        if (tickMonitor != null) {
            tickMonitor.stop();
        }
        if (stackSampler != null) {
            stackSampler.shutdown();
        }
    }

    public TickMonitor getTickMonitor() {
        return tickMonitor;
    }

    public StackSampler getStackSampler() {
        return stackSampler;
    }

    public WebhookClient getWebhookClient() {
        return webhookClient;
    }
}

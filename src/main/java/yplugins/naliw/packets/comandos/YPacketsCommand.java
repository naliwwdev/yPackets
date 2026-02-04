package yplugins.naliw.packets.comandos;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import yplugins.naliw.packets.stacks.ReportFormatter;
import yplugins.naliw.packets.stacks.StackSampler;
import yplugins.naliw.packets.YPacketsPlugin;

public class YPacketsCommand implements CommandExecutor {
    private final YPacketsPlugin plugin;

    public YPacketsCommand(YPacketsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ypackets.admin")) {
            sender.sendMessage(ChatColor.RED + "Sem permissao.");
            return true;
        }

        if (args.length == 0 || !"report".equalsIgnoreCase(args[0])) {
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " report");
            return true;
        }

        if (!plugin.getWebhookClient().isConfigured()) {
            sender.sendMessage(ChatColor.RED + "Configura webhook-url em config.yml.");
            return true;
        }

        final int intervalMs = plugin.getConfig().getInt("profile.sample-interval-ms", 50);
        final int durationSeconds = plugin.getConfig().getInt("profile.sample-duration-seconds", 10);

        sender.sendMessage(ChatColor.GREEN + "Gerando report (" + durationSeconds + "s)...");

        final String requester = sender.getName();
        plugin.getStackSampler().sampleAsync(intervalMs, durationSeconds, new StackSampler.SampleCallback() {
            @Override
            public void onSuccess(StackSampler.SampleResult result) {
                try {
                    String json = ReportFormatter.toWebhookJson(plugin, requester, result);
                    plugin.getWebhookClient().postJson(json);
                } catch (Throwable t) {
                    plugin.getLogger().warning("Falha ao enviar webhook: " + t.getMessage());
                }
            }

            @Override
            public void onError(Throwable error) {
                plugin.getLogger().warning("Falha ao gerar report: " + error.getMessage());
            }
        });

        sender.sendMessage(ChatColor.GREEN + "Enviando no webhook...");
        return true;
    }
}

package perch.tags;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TagsCommand implements CommandExecutor, TabCompleter {
    private final Main plugin;

    public TagsCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        // Open menu if no args are provided
        if (args.length == 0) {
            plugin.getGUIManager().openMainMenu(player);
            return true;
        }

        // Admin Reload Logic
        if (args[0].equalsIgnoreCase("reload")) {
            if (!player.hasPermission("perchtags.reload")) {
                player.sendMessage(ColorUtils.translate("&cNo permission."));
                return true;
            }
            plugin.reloadPlugin();
            player.sendMessage(ColorUtils.translate("&aPlugin reloaded!"));
            return true;
        }

        // Debug Clear Favorites Logic
        if (args[0].equalsIgnoreCase("clearfavorites")) {
            if (!player.hasPermission("perchtags.clearfavorites")) {
                player.sendMessage(ColorUtils.translate("&cNo permission."));
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(ColorUtils.translate("&cUsage: /tags clearfavorites <player>"));
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target != null) {
                plugin.getGUIManager().clearFavorites(target);
                player.sendMessage(ColorUtils.translate("&aCleared favorites for " + target.getName()));
            } else {
                player.sendMessage(ColorUtils.translate("&cPlayer not found."));
            }
            return true;
        }

        // Fallback: If they typed something else, just open the menu
        plugin.getGUIManager().openMainMenu(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("perchtags.reload")) completions.add("reload");
            if (sender.hasPermission("perchtags.clearfavorites")) completions.add("clearfavorites");
        }
        return completions.stream().filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase())).collect(Collectors.toList());
    }
}
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
import java.util.Locale;
import java.util.stream.Collectors;

public class TagsCommand implements CommandExecutor, TabCompleter {
    private final Main plugin;
    private final AddTagCommand addTagCommand;

    public TagsCommand(Main plugin, AddTagCommand addTagCommand) {
        this.plugin = plugin;
        this.addTagCommand = addTagCommand;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            plugin.getGUIManager().openMainMenu(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("add")) {
            return addTagCommand.handle(player, args);
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!player.hasPermission("perchtags.reload")) {
                player.sendMessage(ColorUtils.translate("&cNo permission."));
                return true;
            }
            plugin.reloadPlugin();
            player.sendMessage(ColorUtils.translate("&aPlugin reloaded!"));
            return true;
        }

        if (args[0].equalsIgnoreCase("clearfavorites")) {
            if (!player.hasPermission("perchtags.clearfavorites")) {
                player.sendMessage(ColorUtils.translate("&cNo permission."));
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(ColorUtils.translate("&cUsage: /perchtags clearfavorites <player>"));
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

        plugin.getGUIManager().openMainMenu(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            if (sender.hasPermission("perchtags.add") || sender.hasPermission("perchtags.admin")) completions.add("add");
            if (sender.hasPermission("perchtags.reload")) completions.add("reload");
            if (sender.hasPermission("perchtags.clearfavorites")) completions.add("clearfavorites");
            return filterPrefix(completions, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            return addTagCommand.tabCompleteCategories(args[1]);
        }

        return completions;
    }

    private List<String> filterPrefix(List<String> list, String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return list.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p)).collect(Collectors.toList());
    }
}

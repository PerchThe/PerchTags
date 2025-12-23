package perch.tags;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class TagExpansion extends PlaceholderExpansion {
    private final Main plugin;

    public TagExpansion(Main plugin) { this.plugin = plugin; }

    @Override public @NotNull String getIdentifier() { return "perchtags"; }
    @Override public @NotNull String getAuthor() { return "Perch"; }
    @Override public @NotNull String getVersion() { return "1.0"; }
    @Override public boolean persist() { return true; }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        if (params.equalsIgnoreCase("tag")) {
            String active = plugin.getGUIManager().getActiveTag(player);
            if (active == null || active.isEmpty()) return "";
            return " " + ColorUtils.translate(active);
        }
        return null;
    }
}
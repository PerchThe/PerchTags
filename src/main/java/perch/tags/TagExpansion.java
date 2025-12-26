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
            String stored = plugin.getGUIManager().getActiveTagId(player);
            if (stored == null || stored.isEmpty()) return "";

            Tag tag = plugin.getTagManager().getTagById(stored);
            String raw = tag != null ? tag.getDisplay() : stored;

            boolean noSpace = raw.startsWith("-");
            String cleaned = noSpace ? raw.substring(1) : raw;

            return (noSpace ? "" : " ") + ColorUtils.translate(cleaned);
        }

        if (params.startsWith("category_unlocked:")) {
            String arg = params.substring("category_unlocked:".length()).trim();
            String display = plugin.getTagManager().findCategoryDisplay(arg);
            if (display == null) return "0";
            return String.valueOf(plugin.getTagManager().countUnlockedTags(player, display));
        }

        if (params.startsWith("category_total:")) {
            String arg = params.substring("category_total:".length()).trim();
            String display = plugin.getTagManager().findCategoryDisplay(arg);
            if (display == null) return "0";
            return String.valueOf(plugin.getTagManager().countTotalTags(display));
        }

        return null;
    }
}

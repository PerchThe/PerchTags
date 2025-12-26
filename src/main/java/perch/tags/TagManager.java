package perch.tags;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TagManager {
    private final Main plugin;
    private final Map<String, List<Tag>> categories = new LinkedHashMap<>();
    private final Map<String, Tag> tagsById = new HashMap<>();
    private final Map<String, Material> categoryIcons = new HashMap<>();
    private final Map<String, Integer> categorySlots = new HashMap<>();
    private final Map<String, Integer> categoryMenuSizes = new HashMap<>();
    private final Map<String, Integer> categoryBackSlots = new HashMap<>();
    private final Map<String, List<String>> categoryDescriptions = new HashMap<>();
    private final Map<String, ConfigurationSection> categoryDecos = new HashMap<>();
    private final Map<String, String> categoryTypes = new HashMap<>();
    private final Map<String, String> categoryMenuTitles = new HashMap<>();
    private final Map<String, Integer> startSlots = new HashMap<>(), endSlots = new HashMap<>(), nextSlots = new HashMap<>(), prevSlots = new HashMap<>();

    public TagManager(Main plugin) {
        this.plugin = plugin;
    }

    public void load() {
        categories.clear();
        tagsById.clear();
        categoryIcons.clear();
        categorySlots.clear();
        categoryDescriptions.clear();
        categoryMenuSizes.clear();
        categoryBackSlots.clear();
        categoryDecos.clear();
        categoryTypes.clear();
        categoryMenuTitles.clear();
        startSlots.clear();
        endSlots.clear();
        nextSlots.clear();
        prevSlots.clear();

        File dir = new File(plugin.getDataFolder(), "categories");
        if (!dir.exists() || !dir.isDirectory()) {
            dir.mkdirs();
            return;
        }

        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null) return;

        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        int totalTags = 0;

        for (File file : files) {
            String fileName = file.getName();
            String categoryId = fileName.substring(0, fileName.length() - 4).toLowerCase();

            FileConfiguration config;
            try {
                config = loadYamlStrict(file);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load YAML: " + fileName + " (" + e.getMessage() + ")");
                continue;
            }

            String display = config.getString("category-display", categoryId);

            categoryTypes.put(display, config.getString("type", "DEFAULT").toUpperCase());
            categoryDescriptions.put(display, config.getStringList("description"));
            categoryMenuTitles.put(display, config.getString("menu-title", display));

            List<String> defaultDesc = config.getStringList("default-description");

            categoryIcons.put(display, getSafeMaterial(config.getString("icon"), Material.BOOK));
            categorySlots.put(display, config.getInt("main-gui-slot", -1));
            categoryMenuSizes.put(display, config.getInt("size", 54));
            categoryBackSlots.put(display, config.getInt("back-button-slot", 0));
            prevSlots.put(display, config.getInt("prev-page-slot", 1));
            nextSlots.put(display, config.getInt("next-page-slot", 8));
            categoryDecos.put(display, config.getConfigurationSection("decorations"));
            startSlots.put(display, config.getInt("start-page-slot", 10));
            endSlots.put(display, config.getInt("end-page-slot", 43));

            List<Tag> tagsList = new ArrayList<>();
            ConfigurationSection section = config.getConfigurationSection("tags");

            if (section != null) {
                for (String rawPermKey : section.getKeys(false)) {
                    ConfigurationSection permSection = section.getConfigurationSection(rawPermKey);
                    if (permSection == null) continue;

                    String permissionNode = rawPermKey.replace("-", ".");
                    String finalPerm = categoryId + "." + permissionNode;

                    for (String key : permSection.getKeys(false)) {
                        if (key.toLowerCase().startsWith("display")) {
                            String tagDisplay = permSection.getString(key);
                            if (tagDisplay == null) continue;

                            String uniqueId = finalPerm + ":" + key.toLowerCase();
                            List<String> tagDesc = permSection.getStringList("description");
                            List<String> finalDesc = (tagDesc == null || tagDesc.isEmpty()) ? defaultDesc : tagDesc;
                            Material item = getSafeMaterial(permSection.getString("item"), Material.NAME_TAG);

                            Tag tag = new Tag(tagDisplay, uniqueId, String.join("\n", finalDesc), display, item);
                            tagsList.add(tag);
                            tagsById.put(uniqueId, tag);
                            totalTags++;
                        }
                    }
                }
            }

            categories.put(display, tagsList);
        }

        plugin.getLogger().info("PerchTags loaded " + totalTags + " tags across " + categories.size() + " categories");
    }

    private FileConfiguration loadYamlStrict(File file) throws IOException, InvalidConfigurationException {
        YamlConfiguration cfg = new YamlConfiguration();
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        cfg.loadFromString(content);
        return cfg;
    }

    private Material getSafeMaterial(String name, Material fallback) {
        if (name == null) return fallback;
        Material m = Material.matchMaterial(name.toUpperCase());
        return (m == null) ? fallback : m;
    }

    public List<Tag> getTags(String category) {
        return categories.getOrDefault(category, new ArrayList<>());
    }

    public Tag getTagById(String uniqueId) {
        if (uniqueId == null) return null;
        return tagsById.get(uniqueId);
    }

    public int countTotalTags(String categoryDisplay) {
        return getTags(categoryDisplay).size();
    }

    public int countUnlockedTags(Player player, String categoryDisplay) {
        List<Tag> tags = getTags(categoryDisplay);
        int count = 0;
        for (Tag t : tags) {
            String basePerm = t.getPermission().split(":")[0];
            if (player.isOp() || player.hasPermission(basePerm)) count++;
        }
        return count;
    }

    public String findCategoryDisplay(String input) {
        if (input == null) return null;

        String norm = input.replace('_', ' ').trim();
        if (categories.containsKey(norm)) return norm;

        for (String key : categories.keySet()) {
            if (key.equalsIgnoreCase(norm)) return key;
            String plain = ChatColor.stripColor(ColorUtils.translate(key));
            if (plain != null && plain.equalsIgnoreCase(norm)) return key;
        }

        return null;
    }

    public String getMenuTitle(String categoryDisplay) {
        return categoryMenuTitles.getOrDefault(categoryDisplay, categoryDisplay);
    }

    public List<String> getCategoryDescription(String display) { return categoryDescriptions.getOrDefault(display, new ArrayList<>()); }
    public String getCategoryType(String display) { return categoryTypes.getOrDefault(display, "DEFAULT"); }
    public Map<String, List<Tag>> getCategories() { return categories; }
    public Material getIcon(String category) { return categoryIcons.getOrDefault(category, Material.BOOK); }
    public int getSlot(String category) { return categorySlots.getOrDefault(category, -1); }
    public int getMenuSize(String category) { return categoryMenuSizes.getOrDefault(category, 54); }
    public int getBackSlot(String category) { return categoryBackSlots.getOrDefault(category, 0); }
    public ConfigurationSection getDecos(String category) { return categoryDecos.get(category); }
    public int getStartSlot(String cat) { return startSlots.getOrDefault(cat, 10); }
    public int getEndSlot(String cat) { return endSlots.getOrDefault(cat, 43); }
    public int getNextSlot(String cat) { return nextSlots.getOrDefault(cat, 8); }
    public int getPrevSlot(String cat) { return prevSlots.getOrDefault(cat, 1); }
    public int getItemsPerPage(String cat) { return (getEndSlot(cat) - getStartSlot(cat)) + 1; }
}

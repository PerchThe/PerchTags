package perch.tags;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class GUIManager implements Listener {
    private final Main plugin;
    private final NamespacedKey categoryKey, activeTagKey, favoriteKey, pageKey, tagIdKey, navActionKey;

    public GUIManager(Main plugin) {
        this.plugin = plugin;
        this.categoryKey = new NamespacedKey(plugin, "category_id");
        this.activeTagKey = new NamespacedKey(plugin, "active_tag");
        this.favoriteKey = new NamespacedKey(plugin, "favorite_tags");
        this.pageKey = new NamespacedKey(plugin, "current_page");
        this.tagIdKey = new NamespacedKey(plugin, "tag_unique_id");
        this.navActionKey = new NamespacedKey(plugin, "nav_action");
    }

    public static class PerchMenuHolder implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { return null; }
    }

    public void closeAllMenus() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Inventory top = p.getOpenInventory().getTopInventory();
            if (top != null && top.getHolder() instanceof PerchMenuHolder) p.closeInventory();
        }
    }

    private boolean hasPapi() {
        return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    private String format(Player player, String raw) {
        if (raw == null) return null;
        String s = raw;
        if (hasPapi()) s = PlaceholderAPI.setPlaceholders(player, s);
        return ColorUtils.translate(s);
    }

    private List<String> format(Player player, List<String> raw) {
        List<String> out = new ArrayList<>();
        for (String line : raw) {
            String f = format(player, line);
            if (f != null) out.add(f);
        }
        return out;
    }

    private String stripNoSpacePrefix(String raw) {
        if (raw == null) return null;
        return raw.startsWith("-") ? raw.substring(1) : raw;
    }

    private String cfgString(String path, String fallback) {
        String v = plugin.getConfig().getString(path);
        return v == null ? fallback : v;
    }

    public void openMainMenu(Player player) {
        int size = plugin.getConfig().getInt("main-menu.size", 27);
        Inventory gui = Bukkit.createInventory(new PerchMenuHolder(), size, format(player, plugin.getConfig().getString("main-menu.title")));

        applyDecorations(player, gui, plugin.getConfig().getConfigurationSection("main-menu.decorations"), size);
        renderClearButton(player, gui, size);

        plugin.getTagManager().getCategories().forEach((name, tags) -> {
            ItemStack item = new ItemStack(plugin.getTagManager().getIcon(name));
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(format(player, name));

                List<String> catDesc = plugin.getTagManager().getCategoryDescription(name);
                if (!catDesc.isEmpty()) {
                    int unlocked = plugin.getTagManager().countUnlockedTags(player, name);
                    int total = plugin.getTagManager().countTotalTags(name);

                    List<String> loreRaw = new ArrayList<>();
                    for (String line : catDesc) loreRaw.add(line.replace("{unlocked}", String.valueOf(unlocked)).replace("{total}", String.valueOf(total)));

                    meta.setLore(format(player, loreRaw));
                }

                meta.getPersistentDataContainer().set(categoryKey, PersistentDataType.STRING, name);
                item.setItemMeta(meta);
                int slot = plugin.getTagManager().getSlot(name);
                if (slot >= 0 && slot < size) gui.setItem(slot, item);
            }
        });

        player.openInventory(gui);
    }

    public void openTagMenu(Player player, String categoryName, int page) {
        int size = plugin.getTagManager().getMenuSize(categoryName);
        String title = plugin.getTagManager().getMenuTitle(categoryName);
        Inventory gui = Bukkit.createInventory(new PerchMenuHolder(), size, format(player, title));

        applyDecorations(player, gui, plugin.getTagManager().getDecos(categoryName), size);
        renderClearButton(player, gui, size);

        player.getPersistentDataContainer().set(pageKey, PersistentDataType.INTEGER, page);
        player.getPersistentDataContainer().set(categoryKey, PersistentDataType.STRING, categoryName);

        List<String> playerFavorites = getFavorites(player);
        List<Tag> tagsToDisplay = new ArrayList<>();

        if (plugin.getTagManager().getCategoryType(categoryName).equals("FAVORITES")) {
            for (String id : playerFavorites) {
                Tag t = plugin.getTagManager().getTagById(id);
                if (t != null) tagsToDisplay.add(t);
            }
        } else {
            List<Tag> tags = plugin.getTagManager().getTags(categoryName);
            if (tags != null) {
                for (Tag t : tags) {
                    String basePerm = t.getPermission().split(":")[0];
                    if (player.hasPermission(basePerm) || player.isOp()) tagsToDisplay.add(t);
                }
            }
        }

        String activeId = getActiveTagId(player);

        String spacerLine = cfgString("gui-tag-status.spacer", "");
        String favLine = cfgString("gui-tag-status.favourited", "&8⭐ &fFavourited");
        String eqLine = cfgString("gui-tag-status.equipped", "&8» &fEquipped");

        int start = plugin.getTagManager().getStartSlot(categoryName);
        int end = plugin.getTagManager().getEndSlot(categoryName);
        int per = plugin.getTagManager().getItemsPerPage(categoryName);
        int startIdx = page * per;
        int endIdx = Math.min(startIdx + per, tagsToDisplay.size());
        int slot = start;

        for (int i = startIdx; i < endIdx; i++) {
            Tag tag = tagsToDisplay.get(i);
            boolean isEquipped = tag.getPermission().equals(activeId);
            boolean isFav = playerFavorites.contains(tag.getPermission());

            ItemStack item = new ItemStack(isEquipped ? Material.NETHER_STAR : tag.getIcon());
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(format(player, stripNoSpacePrefix(tag.getDisplay())));
                if (isFav) {
                    meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }

                List<String> loreRaw = new ArrayList<>();
                for (String line : tag.getDescription().split("\n")) loreRaw.add("&#AAAAAA" + line);

                if (isFav || isEquipped) {
                    loreRaw.add(spacerLine);
                    if (isFav) loreRaw.add(favLine);
                    if (isEquipped) loreRaw.add(eqLine);
                }

                meta.setLore(format(player, loreRaw));
                meta.getPersistentDataContainer().set(tagIdKey, PersistentDataType.STRING, tag.getPermission());
                item.setItemMeta(meta);
                if (slot <= end) gui.setItem(slot++, item);
            }
        }

        handleNavigation(player, gui, categoryName, page, endIdx, tagsToDisplay.size());
        player.openInventory(gui);
    }

    private void handleNavigation(Player player, Inventory gui, String cat, int page, int endIdx, int total) {
        Material backMat = getSafeMaterial(plugin.getConfig().getString("items.back-button"), Material.ARROW);
        gui.setItem(plugin.getTagManager().getBackSlot(cat),
                createNavItem(player, backMat, plugin.getConfig().getString("gui-text.back-button", "&#FFBB00&lBack"), "back"));

        Material prevMat = getSafeMaterial(plugin.getConfig().getString("items.prev-page-item"), Material.PAPER);
        Material nextMat = getSafeMaterial(plugin.getConfig().getString("items.next-page-item"), Material.PAPER);

        if (page > 0) {
            gui.setItem(plugin.getTagManager().getPrevSlot(cat),
                    createNavItem(player, prevMat, plugin.getConfig().getString("gui-text.prev-page", "&#FFBB00Previous Page"), "prev"));
        }
        if (endIdx < total) {
            gui.setItem(plugin.getTagManager().getNextSlot(cat),
                    createNavItem(player, nextMat, plugin.getConfig().getString("gui-text.next-page", "&#FFBB00Next Page"), "next"));
        }
    }

    private ItemStack createNavItem(Player player, Material m, String n, String action) {
        ItemStack i = new ItemStack(m);
        ItemMeta mt = i.getItemMeta();
        if (mt != null) {
            mt.setDisplayName(format(player, n));
            mt.getPersistentDataContainer().set(navActionKey, PersistentDataType.STRING, action);
            i.setItemMeta(mt);
        }
        return i;
    }

    private void renderClearButton(Player player, Inventory gui, int size) {
        ConfigurationSection clearBtn = plugin.getConfig().getConfigurationSection("clear-tag-button");
        if (clearBtn == null) return;

        int slot = clearBtn.getInt("slot", 49);
        if (slot >= 0 && slot < size) {
            Material mat = getSafeMaterial(clearBtn.getString("material"), Material.BARRIER);
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(format(player, clearBtn.getString("display-name", "&#FF5555&lClear Tag")));
                meta.setLore(format(player, clearBtn.getStringList("lore")));
                meta.getPersistentDataContainer().set(navActionKey, PersistentDataType.STRING, "clear");
                item.setItemMeta(meta);
            }
            gui.setItem(slot, item);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PerchMenuHolder)) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        ItemMeta meta = clicked.getItemMeta();
        String action = meta.getPersistentDataContainer().get(navActionKey, PersistentDataType.STRING);
        String category = player.getPersistentDataContainer().get(categoryKey, PersistentDataType.STRING);
        int currentPage = player.getPersistentDataContainer().getOrDefault(pageKey, PersistentDataType.INTEGER, 0);

        if (action != null) {
            switch (action) {
                case "back" -> openMainMenu(player);
                case "next" -> openTagMenu(player, category, currentPage + 1);
                case "prev" -> openTagMenu(player, category, Math.max(0, currentPage - 1));
                case "clear" -> { removeActiveTag(player); player.closeInventory(); }
            }
            if (!action.equals("clear")) playSound(player, "click");
            return;
        }

        if (meta.getPersistentDataContainer().has(tagIdKey, PersistentDataType.STRING)) {
            String uniqueId = meta.getPersistentDataContainer().get(tagIdKey, PersistentDataType.STRING);
            if (uniqueId == null) return;

            if (event.isShiftClick()) {
                toggleFavorite(player, uniqueId);
                openTagMenu(player, category, currentPage);
            } else {
                String activeId = getActiveTagId(player);
                if (uniqueId.equals(activeId)) return;
                setActiveTagId(player, uniqueId);
                openTagMenu(player, category, currentPage);
            }
            return;
        }

        if (meta.getPersistentDataContainer().has(categoryKey, PersistentDataType.STRING)) {
            String selectedCat = meta.getPersistentDataContainer().get(categoryKey, PersistentDataType.STRING);
            openTagMenu(player, selectedCat, 0);
            playSound(player, "click");
        }
    }

    public void toggleFavorite(Player player, String id) {
        Set<String> favs = new LinkedHashSet<>(getFavorites(player));
        if (!favs.add(id)) favs.remove(id);
        player.getPersistentDataContainer().set(favoriteKey, PersistentDataType.STRING, String.join(",", favs));
        playSound(player, favs.contains(id) ? "click" : "remove");
    }

    public void clearFavorites(Player player) {
        player.getPersistentDataContainer().remove(favoriteKey);
        playSound(player, "remove");
    }

    public void removeActiveTag(Player player) {
        player.getPersistentDataContainer().remove(activeTagKey);
        playSound(player, "remove");
        player.sendMessage(ColorUtils.translate(plugin.getConfig().getString("messages.tag-removed")));
    }

    private void playSound(Player p, String key) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("sounds." + key);
        if (s != null) p.playSound(p.getLocation(), Sound.valueOf(s.getString("sound")), (float)s.getDouble("volume"), (float)s.getDouble("pitch"));
    }

    private void applyDecorations(Player player, Inventory gui, ConfigurationSection s, int size) {
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            Material m = getSafeMaterial(k, Material.GRAY_STAINED_GLASS_PANE);
            ItemStack i = new ItemStack(m);
            ItemMeta meta = i.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(format(player, s.getString(k + ".name", " ")));
                i.setItemMeta(meta);
            }
            for (int slot : s.getIntegerList(k + ".slots")) if (slot >= 0 && slot < size) gui.setItem(slot, i);
        }
    }

    private Material getSafeMaterial(String name, Material fallback) {
        if (name == null) return fallback;
        Material m = Material.matchMaterial(name.toUpperCase());
        return (m == null) ? fallback : m;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof PerchMenuHolder) e.setCancelled(true);
    }

    public void setActiveTagId(Player p, String uniqueId) {
        p.getPersistentDataContainer().set(activeTagKey, PersistentDataType.STRING, uniqueId);
        playSound(p, "equip");

        Tag tag = plugin.getTagManager().getTagById(uniqueId);
        String shown = tag != null ? tag.getDisplay() : uniqueId;
        shown = stripNoSpacePrefix(shown);

        p.sendMessage(ColorUtils.translate(plugin.getConfig().getString("messages.tag-set").replace("%tag%", ColorUtils.translate(shown))));
    }

    public String getActiveTagId(Player p) {
        return p.getPersistentDataContainer().getOrDefault(activeTagKey, PersistentDataType.STRING, "");
    }

    public List<String> getFavorites(Player player) {
        String data = player.getPersistentDataContainer().get(favoriteKey, PersistentDataType.STRING);
        if (data == null || data.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(data.split(",")));
    }
}

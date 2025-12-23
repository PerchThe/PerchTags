package perch.tags;

import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;

public class Main extends JavaPlugin {
    private static Main instance;
    private TagManager tagManager;
    private GUIManager guiManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        createFolders();

        this.tagManager = new TagManager(this);
        this.tagManager.load();

        this.guiManager = new GUIManager(this);
        getServer().getPluginManager().registerEvents(this.guiManager, this);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new TagExpansion(this).register();
        }

        TagsCommand tagsCommand = new TagsCommand(this);
        getCommand("tags").setExecutor(tagsCommand);
        getCommand("tags").setTabCompleter(tagsCommand);
    }

    public void reloadPlugin() {
        reloadConfig();
        this.tagManager.load();
    }

    private void createFolders() {
        if (!getDataFolder().exists()) getDataFolder().mkdir();
        File dir = new File(getDataFolder(), "categories");
        if (!dir.exists()) dir.mkdirs();

        if (!(new File(dir, "custom.yml")).exists()) saveResource("categories/custom.yml", false);
        if (!(new File(dir, "favourites.yml")).exists()) saveResource("categories/favourites.yml", false); // Restored
    }

    public static Main getInstance() { return instance; }
    public TagManager getTagManager() { return tagManager; }
    public GUIManager getGUIManager() { return guiManager; }
}
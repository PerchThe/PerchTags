package perch.tags;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class Main extends JavaPlugin {
    private static Main instance;
    private TagManager tagManager;
    private GUIManager guiManager;
    private AddTagCommand addTagCommand;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        createFolders();

        this.tagManager = new TagManager(this);
        this.tagManager.load();

        this.guiManager = new GUIManager(this);
        getServer().getPluginManager().registerEvents(this.guiManager, this);

        this.addTagCommand = new AddTagCommand(this);
        getServer().getPluginManager().registerEvents(this.addTagCommand, this);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new TagExpansion(this).register();
        }

        TagsCommand tagsCommand = new TagsCommand(this, this.addTagCommand);

        PluginCommand cmd = getCommand("tags");
        if (cmd == null) {
            getLogger().severe("Command 'tags' is missing from plugin.yml. Plugin will be disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        cmd.setExecutor(tagsCommand);
        cmd.setTabCompleter(tagsCommand);
    }

    @Override
    public void onDisable() {
        if (this.guiManager != null) this.guiManager.closeAllMenus();
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
        if (!(new File(dir, "favourites.yml")).exists()) saveResource("categories/favourites.yml", false);
    }

    public static Main getInstance() { return instance; }
    public TagManager getTagManager() { return tagManager; }
    public GUIManager getGUIManager() { return guiManager; }
    public AddTagCommand getAddTagCommand() { return addTagCommand; }
}

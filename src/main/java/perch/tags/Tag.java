package perch.tags;

import org.bukkit.Material;

public class Tag {
    private final String display;
    private final String permission;
    private final String description;
    private final String category;
    private final Material icon;

    public Tag(String display, String permission, String description, String category, Material icon) {
        this.display = display;
        this.permission = permission;
        this.description = description;
        this.category = category;
        this.icon = icon;
    }

    public String getDisplay() { return display; }
    public String getPermission() { return permission; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public Material getIcon() { return icon; }
}
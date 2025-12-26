package perch.tags;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AddTagCommand implements Listener {
    private final Main plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Pattern yamlKeyPattern = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private final Pattern playerNamePattern = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private final Map<UUID, LastSeen> lastSeen = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldownUntilMillis = new ConcurrentHashMap<>();
    private final long doneCooldownMillis = 1500L;
    private final int customDisplayMax = 50;

    public AddTagCommand(Main plugin) {
        this.plugin = plugin;
    }

    public boolean isCapturing(Player player) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();
        Session s = sessions.get(uuid);
        if (s != null) return true;
        Long until = cooldownUntilMillis.get(uuid);
        if (until == null) return false;
        if (System.currentTimeMillis() <= until) return true;
        cooldownUntilMillis.remove(uuid);
        return false;
    }

    public boolean handle(Player player, String[] args) {
        if (!player.hasPermission("perchtags.add") && !player.hasPermission("perchtags.admin")) {
            send(player, "no-permission", "&cNo permission.");
            return true;
        }

        if (args.length < 3) {
            send(player, "usage-add", "&cUsage: /tags add <category> <yamlkey>");
            return true;
        }

        String category = args[1].trim();
        String yamlKey = args[2].trim();

        if (category.isEmpty() || yamlKey.isEmpty()) {
            send(player, "usage-add", "&cUsage: /tags add <category> <yamlkey>");
            return true;
        }

        boolean isCustom = category.equalsIgnoreCase("custom");

        if (isCustom) {
            if (!playerNamePattern.matcher(yamlKey).matches()) {
                send(player, "invalid-player", "&cInvalid player name. Use 3-16 characters: letters, numbers, underscore.");
                return true;
            }
        } else {
            if (!yamlKeyPattern.matcher(yamlKey).matches()) {
                send(player, "invalid-yamlkey", "&cInvalid yamlkey. Use only letters, numbers, _ and -.");
                return true;
            }
        }

        File categoryFile = getCategoryFile(category);
        if (!categoryFile.exists()) {
            send(player, "invalid-category", "&cInvalid category. Must match an existing categories/<name>.yml file.");
            return true;
        }

        UUID uuid = player.getUniqueId();

        if (sessions.containsKey(uuid)) {
            send(player, "already-active", "&cYou already have an active add session. Type &eDONE&c or &eCANCEL&c in chat.");
            return true;
        }

        if (!isCustom) {
            try {
                if (yamlKeyExists(categoryFile, yamlKey)) {
                    send(player, "yamlkey-exists", "&cThat entry already exists in &e{category}&c.", Map.of("category", category));
                    return true;
                }
            } catch (IOException e) {
                send(player, "read-failed", "&cFailed to read category file.");
                return true;
            }
        }

        sessions.put(uuid, new Session(category, yamlKey, isCustom));
        lastSeen.remove(uuid);
        cooldownUntilMillis.remove(uuid);

        send(player, "capture-start-1", "&aNow paste/type the tag display string in chat.");
        send(player, "capture-start-2", "&7Send multiple messages if needed. Type &eDONE&7 to save, &eCANCEL&7 to abort.");
        return true;
    }

    public List<String> tabCompleteCategories(String prefix) {
        List<String> cats = listCategoryIds();
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return cats.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p)).collect(Collectors.toList());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPaperChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Session session = sessions.get(uuid);
        if (session == null) return;

        event.setCancelled(true);
        event.viewers().clear();

        String msg = extractRawSignedMessage(event);
        if (msg == null) msg = "";

        if (isDuplicate(uuid, msg)) return;

        handleCaptured(player, session, msg);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        sessions.remove(uuid);
        lastSeen.remove(uuid);
        cooldownUntilMillis.remove(uuid);
    }

    private boolean isDuplicate(UUID uuid, String msg) {
        long now = System.nanoTime();
        LastSeen prev = lastSeen.get(uuid);
        if (prev != null) {
            long ageNanos = now - prev.timeNanos;
            if (ageNanos >= 0 && ageNanos < 250_000_000L) {
                if (prev.message != null && prev.message.equals(msg)) {
                    return true;
                }
            }
        }
        lastSeen.put(uuid, new LastSeen(msg, now));
        return false;
    }

    private void beginCooldown(UUID uuid) {
        cooldownUntilMillis.put(uuid, System.currentTimeMillis() + doneCooldownMillis);
    }

    private void endSessionNextTick(UUID uuid) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            sessions.remove(uuid);
            lastSeen.remove(uuid);
        });
    }

    private void handleCaptured(Player player, Session session, String msg) {
        String safe = msg == null ? "" : msg;
        String trimmed = safe.trim();
        UUID uuid = player.getUniqueId();

        if (trimmed.equalsIgnoreCase("cancel")) {
            beginCooldown(uuid);
            endSessionNextTick(uuid);
            send(player, "cancelled", "&cCancelled. Nothing was saved.");
            return;
        }

        if (trimmed.equalsIgnoreCase("done")) {
            beginCooldown(uuid);
            endSessionNextTick(uuid);
            String content;
            synchronized (session) {
                content = session.buffer.toString();
            }
            Bukkit.getScheduler().runTask(plugin, () -> finish(player, session, content));
            return;
        }

        synchronized (session) {
            session.buffer.append(safe);
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            int total = sessionLength(session);
            send(player, "chunk-received", "&7Chunk received. Total: &e{total}&7. Type &eDONE&7 to save.", Map.of("total", String.valueOf(total)));
        });
    }

    private String extractRawSignedMessage(AsyncChatEvent event) {
        try {
            Method signedMessageMethod = event.getClass().getMethod("signedMessage");
            Object signedMessage = signedMessageMethod.invoke(event);
            if (signedMessage == null) return null;
            Method messageMethod = signedMessage.getClass().getMethod("message");
            Object raw = messageMethod.invoke(signedMessage);
            return raw == null ? null : raw.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void finish(Player sender, Session session, String content) {
        String finalContent = content == null ? "" : content.trim();
        if (finalContent.isEmpty()) {
            send(sender, "no-content", "&cNo content provided. Nothing was saved.");
            return;
        }

        File categoryFile = getCategoryFile(session.categoryId);
        if (!categoryFile.exists()) {
            send(sender, "category-missing", "&cCategory file no longer exists. Nothing was saved.");
            return;
        }

        try {
            if (session.isCustom) {
                String displayKey = findNextCustomDisplayKey(categoryFile, session.yamlKey);
                appendOrInsertCustomDisplay(categoryFile, session.yamlKey, displayKey, finalContent);

                plugin.reloadPlugin();

                String targetName = session.yamlKey;
                OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(targetName);
                if (target == null) target = Bukkit.getPlayerExact(targetName);
                if (target == null) target = Bukkit.getOfflinePlayer(targetName);

                String perm = "custom." + targetName;
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + targetName + " permission set " + perm);

                send(sender, "saved-custom-1", "&aAdded custom tag entry for &e{player}&a.", Map.of("player", targetName));
                send(sender, "saved-custom-2", "&7Granted: &f{perm}", Map.of("perm", perm));
                send(sender, "saved-custom-3", "&7Saved under: &f{key}", Map.of("key", displayKey));
                return;
            }

            if (yamlKeyExists(categoryFile, session.yamlKey)) {
                send(sender, "yamlkey-exists-generic", "&cThat entry already exists. Nothing was saved.");
                return;
            }

            appendTagToBottom(categoryFile, session.yamlKey, finalContent);
        } catch (IOException e) {
            send(sender, "write-failed", "&cFailed to write category file.");
            return;
        }

        plugin.reloadPlugin();

        String perm = session.categoryId + "." + session.yamlKey.replace("-", ".");
        send(sender, "saved-1", "&aAdded tag &e{yamlkey}&a to &e{category}&a.", Map.of("yamlkey", session.yamlKey, "category", session.categoryId));
        send(sender, "saved-2", "&7Permission: &f{perm}", Map.of("perm", perm));
    }

    private String findNextCustomDisplayKey(File file, String playerKey) throws IOException {
        String normalized = Files.readString(file.toPath(), StandardCharsets.UTF_8).replace("\r\n", "\n").replace("\r", "\n");
        String basePath = "\n  " + playerKey + ":\n";
        if (!normalized.contains(basePath) && !normalized.startsWith("tags:\n  " + playerKey + ":\n") && !normalized.contains("\ntags:\n  " + playerKey + ":\n")) {
            return "display";
        }

        String firstNeedle = "\n    display:";
        int playerIdx = normalized.indexOf("\n  " + playerKey + ":\n");
        if (playerIdx == -1 && normalized.contains("\ntags:\n  " + playerKey + ":\n")) playerIdx = normalized.indexOf("\ntags:\n  " + playerKey + ":\n") + "\ntags:".length();
        if (playerIdx == -1 && normalized.startsWith("tags:\n  " + playerKey + ":\n")) playerIdx = 0;
        if (playerIdx == -1) return "display";

        int searchFrom = playerIdx;
        if (!normalized.substring(searchFrom).contains(firstNeedle)) return "display";
        if (!customKeyExists(normalized, playerKey, "display")) return "display";

        for (int i = 1; i <= customDisplayMax; i++) {
            String k = "display" + i;
            if (!customKeyExists(normalized, playerKey, k)) return k;
        }

        return "display" + (customDisplayMax + 1);
    }

    private boolean customKeyExists(String normalized, String playerKey, String displayKey) {
        String needle = "\n    " + displayKey + ":";
        int idx = normalized.indexOf("\n  " + playerKey + ":");
        if (idx == -1) return false;
        int start = idx;
        int end = normalized.length();
        for (int i = idx + 1; i < normalized.length(); i++) {
            if (normalized.charAt(i) == '\n') {
                int lineStart = i + 1;
                if (lineStart >= normalized.length()) break;
                String rest = normalized.substring(lineStart);
                int nl = rest.indexOf('\n');
                String line = nl == -1 ? rest : rest.substring(0, nl);
                if (line.isBlank()) continue;
                String t = line.stripLeading();
                if (t.startsWith("#")) continue;
                int indent = line.length() - t.length();
                if (indent < 2) {
                    end = lineStart;
                    break;
                }
            }
        }
        String section = normalized.substring(start, end);
        return section.contains(needle) || section.contains("\n    " + displayKey + ": ");
    }

    private void appendOrInsertCustomDisplay(File file, String playerKey, String displayKey, String display) throws IOException {
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        String normalized = content.replace("\r\n", "\n").replace("\r", "\n");

        String escaped = escapeDoubleQuoted(display);

        if (!normalized.contains("\ntags:") && !normalized.startsWith("tags:")) {
            String out = normalized;
            if (!out.endsWith("\n")) out += "\n";
            out += "\ntags:\n  " + playerKey + ":\n    " + displayKey + ": \"" + escaped + "\"\n";
            Files.writeString(file.toPath(), out, StandardCharsets.UTF_8);
            return;
        }

        if (!normalized.contains("\n  " + playerKey + ":\n") && !normalized.startsWith("tags:\n  " + playerKey + ":\n") && !normalized.contains("\ntags:\n  " + playerKey + ":\n")) {
            String block = "  " + playerKey + ":\n    " + displayKey + ": \"" + escaped + "\"\n";
            insertBlockUnderTags(file, normalized, block);
            return;
        }

        String[] lines = normalized.split("\n", -1);

        int playerLineIndex = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].equals("  " + playerKey + ":")) {
                playerLineIndex = i;
                break;
            }
        }

        if (playerLineIndex == -1) {
            String block = "  " + playerKey + ":\n    " + displayKey + ": \"" + escaped + "\"\n";
            insertBlockUnderTags(file, normalized, block);
            return;
        }

        int insertLineIndex = lines.length;
        for (int i = playerLineIndex + 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) continue;
            String t = line.stripLeading();
            if (t.startsWith("#")) continue;
            int indent = line.length() - t.length();
            if (indent < 4) {
                insertLineIndex = i;
                break;
            }
        }

        List<String> out = new ArrayList<>(Arrays.asList(lines));
        out.add(insertLineIndex, "    " + displayKey + ": \"" + escaped + "\"");
        Files.writeString(file.toPath(), String.join("\n", out), StandardCharsets.UTF_8);
    }

    private void insertBlockUnderTags(File file, String normalized, String block) throws IOException {
        String[] lines = normalized.split("\n", -1);
        int tagsLineIndex = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].equals("tags:")) {
                tagsLineIndex = i;
                break;
            }
        }

        if (tagsLineIndex == -1) {
            String out = normalized;
            if (!out.endsWith("\n")) out += "\n";
            out += "\ntags:\n" + block;
            Files.writeString(file.toPath(), out, StandardCharsets.UTF_8);
            return;
        }

        int endLineIndex = lines.length;
        for (int i = tagsLineIndex + 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) continue;
            String t = line.stripLeading();
            if (t.startsWith("#")) continue;
            int indent = line.length() - t.length();
            if (indent < 2) {
                endLineIndex = i;
                break;
            }
        }

        int chars = 0;
        for (int i = 0; i < endLineIndex; i++) {
            chars += lines[i].length();
            chars += 1;
        }

        int insertPos = Math.min(chars, normalized.length());

        String before = normalized.substring(0, insertPos);
        String after = normalized.substring(insertPos);

        String outBefore = before;
        if (!outBefore.endsWith("\n")) outBefore += "\n";
        if (!outBefore.endsWith("\n\n") && !after.startsWith("\n")) outBefore += "\n";

        String out = outBefore + block + after;
        Files.writeString(file.toPath(), out, StandardCharsets.UTF_8);
    }

    private int sessionLength(Session session) {
        synchronized (session) {
            return session.buffer.length();
        }
    }

    private File getCategoryFile(String categoryId) {
        File dir = new File(plugin.getDataFolder(), "categories");
        return new File(dir, categoryId + ".yml");
    }

    private List<String> listCategoryIds() {
        File dir = new File(plugin.getDataFolder(), "categories");
        if (!dir.exists() || !dir.isDirectory()) return new ArrayList<>();
        File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return new ArrayList<>();
        return Arrays.stream(files)
                .map(File::getName)
                .map(n -> n.substring(0, n.length() - 4))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    private boolean yamlKeyExists(File file, String yamlKey) throws IOException {
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        String normalized = content.replace("\r\n", "\n").replace("\r", "\n");
        String needle = "\n  " + yamlKey + ":";
        if (normalized.contains(needle)) return true;
        if (normalized.startsWith("tags:\n  " + yamlKey + ":")) return true;
        return normalized.contains("\ntags:\n  " + yamlKey + ":");
    }

    private void appendTagToBottom(File file, String yamlKey, String display) throws IOException {
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        String normalized = content.replace("\r\n", "\n").replace("\r", "\n");

        String escaped = escapeDoubleQuoted(display);
        String block = "  " + yamlKey + ":\n" + "    display: \"" + escaped + "\"\n";

        if (!normalized.contains("\ntags:") && !normalized.startsWith("tags:")) {
            String out = normalized;
            if (!out.endsWith("\n")) out += "\n";
            out += "\ntags:\n" + block;
            Files.writeString(file.toPath(), out, StandardCharsets.UTF_8);
            return;
        }

        int tagsIdx = normalized.indexOf("\ntags:");
        if (tagsIdx == -1 && normalized.startsWith("tags:")) tagsIdx = 0;
        int tagsLineStart = tagsIdx == 0 ? 0 : tagsIdx + 1;

        String[] lines = normalized.split("\n", -1);

        int tagsLineIndex = 0;
        if (tagsLineStart != 0) {
            int count = 0;
            for (int i = 0; i < tagsLineStart; i++) if (normalized.charAt(i) == '\n') count++;
            tagsLineIndex = count;
        }

        int endLineIndex = lines.length;
        for (int i = tagsLineIndex + 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) continue;
            String t = line.stripLeading();
            if (t.startsWith("#")) continue;
            int indent = line.length() - t.length();
            if (indent < 2) {
                endLineIndex = i;
                break;
            }
        }

        int chars = 0;
        for (int i = 0; i < endLineIndex; i++) {
            chars += lines[i].length();
            chars += 1;
        }

        int insertPos = Math.min(chars, normalized.length());

        String before = normalized.substring(0, insertPos);
        String after = normalized.substring(insertPos);

        String outBefore = before;
        if (!outBefore.endsWith("\n")) outBefore += "\n";
        if (!outBefore.endsWith("\n\n") && !after.startsWith("\n")) outBefore += "\n";

        String out = outBefore + block + after;
        Files.writeString(file.toPath(), out, StandardCharsets.UTF_8);
    }

    private String escapeDoubleQuoted(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void send(Player player, String key, String fallback) {
        String raw = plugin.getConfig().getString("messages." + key, fallback);
        player.sendMessage(ColorUtils.translate(raw));
    }

    private void send(Player player, String key, String fallback, Map<String, String> placeholders) {
        String raw = plugin.getConfig().getString("messages." + key, fallback);
        String msg = raw;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            msg = msg.replace("{" + e.getKey() + "}", e.getValue());
        }
        player.sendMessage(ColorUtils.translate(msg));
    }

    private static final class Session {
        private final String categoryId;
        private final String yamlKey;
        private final boolean isCustom;
        private final StringBuilder buffer = new StringBuilder();

        private Session(String categoryId, String yamlKey, boolean isCustom) {
            this.categoryId = categoryId;
            this.yamlKey = yamlKey;
            this.isCustom = isCustom;
        }
    }

    private static final class LastSeen {
        private final String message;
        private final long timeNanos;

        private LastSeen(String message, long timeNanos) {
            this.message = message;
            this.timeNanos = timeNanos;
        }
    }
}

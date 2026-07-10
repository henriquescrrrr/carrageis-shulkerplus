package pt.henrique.shulkerplus.i18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import pt.henrique.shulkerplus.ShulkerPlus;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-player language manager (MiniMessage). Loads every lang/*.yml, resolves the
 * locale per player via PlayerSettingsHook and falls back to the config locale.
 */
public class LangManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final ShulkerPlus plugin;
    private final PlayerSettingsHook settingsHook;
    private final Map<String, FileConfiguration> languages = new HashMap<>();
    private String defaultLocale;
    private String defaultPrefix;

    public LangManager(ShulkerPlus plugin, PlayerSettingsHook settingsHook) {
        this.plugin = plugin;
        this.settingsHook = settingsHook;
        load();
    }

    /** (Re)load all language files. Called on enable and on /shulkerplus reload. */
    public void load() {
        languages.clear();
        defaultLocale = plugin.getConfig().getString("locale", "pt_PT");
        saveLangFileIfAbsent("lang/en_US.yml");
        saveLangFileIfAbsent("lang/pt_PT.yml");

        File langDir = new File(plugin.getDataFolder(), "lang");
        if (langDir.isDirectory()) {
            File[] files = langDir.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files != null) {
                for (File file : files) {
                    String locale = file.getName().replace(".yml", "");
                    FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                    InputStream jar = plugin.getResource("lang/" + file.getName());
                    if (jar != null) {
                        cfg.setDefaults(YamlConfiguration.loadConfiguration(
                                new InputStreamReader(jar, StandardCharsets.UTF_8)));
                    }
                    languages.put(locale, cfg);
                }
            }
        }

        if (!languages.containsKey(defaultLocale)) {
            plugin.getLogger().warning("Language " + defaultLocale + ".yml not found, falling back to pt_PT.");
            InputStream fb = plugin.getResource("lang/pt_PT.yml");
            if (fb != null) {
                languages.put("pt_PT", YamlConfiguration.loadConfiguration(
                        new InputStreamReader(fb, StandardCharsets.UTF_8)));
                defaultLocale = "pt_PT";
            }
        }

        defaultPrefix = messages(defaultLocale).getString("prefix", "");
        plugin.getLogger().info("[Lang] Loaded " + languages.size() + " language(s): " + languages.keySet());
    }

    public String getPlayerLocale(Player player) {
        if (settingsHook != null && settingsHook.isAvailable()) {
            String lang = settingsHook.getLanguage(player);
            if (lang != null && languages.containsKey(lang)) return lang;
        }
        return defaultLocale;
    }

    public Component get(Player player, String key, Map<String, String> replacements) {
        String locale = getPlayerLocale(player);
        return MM.deserialize(prefix(locale) + raw(locale, key, replacements));
    }

    public Component get(Player player, String key) {
        return get(player, key, null);
    }

    public Component get(String key, Map<String, String> replacements) {
        return MM.deserialize(prefix(defaultLocale) + raw(defaultLocale, key, replacements));
    }

    public Component get(String key) {
        return get(key, null);
    }

    /** Send a prefixed message; player context uses their locale, console uses the default. */
    public void send(CommandSender sender, String key, Map<String, String> replacements) {
        if (sender instanceof Player player) sender.sendMessage(get(player, key, replacements));
        else sender.sendMessage(get(key, replacements));
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, null);
    }

    private FileConfiguration messages(String locale) {
        FileConfiguration cfg = languages.get(locale);
        if (cfg != null) return cfg;
        cfg = languages.get(defaultLocale);
        return cfg != null ? cfg : new YamlConfiguration();
    }

    private String prefix(String locale) {
        return messages(locale).getString("prefix", defaultPrefix);
    }

    private String raw(String locale, String key, Map<String, String> replacements) {
        String msg = messages(locale).getString(key);
        if (msg == null) msg = messages(defaultLocale).getString(key, "<red>Missing message: " + key + "</red>");
        if (replacements != null) {
            for (Map.Entry<String, String> e : replacements.entrySet()) {
                msg = msg.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        return msg;
    }

    private void saveLangFileIfAbsent(String path) {
        File target = new File(plugin.getDataFolder(), path);
        if (!target.exists()) plugin.saveResource(path, false);
    }
}

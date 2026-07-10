package pt.henrique.shulkerplus;

import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import pt.henrique.shulkerplus.hook.CombatLogHook;
import pt.henrique.shulkerplus.i18n.LangManager;
import pt.henrique.shulkerplus.i18n.PlayerSettingsHook;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ShulkerPlus - open shulker boxes from the hand without placing them.
 * Dupe-safe: the box is pinned to its slot while open and written back exactly once.
 */
public final class ShulkerPlus extends JavaPlugin {

    private final Map<UUID, ShulkerSession> sessions = new ConcurrentHashMap<>();
    private final CombatLogHook combatLogHook = new CombatLogHook();
    private final PlayerSettingsHook settingsHook = new PlayerSettingsHook();
    private NamespacedKey sessionKey;
    private LangManager lang;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        sessionKey = new NamespacedKey(this, "session");
        combatLogHook.init(getLogger());
        settingsHook.init(getLogger());
        lang = new LangManager(this, settingsHook);
        getServer().getPluginManager().registerEvents(new ShulkerListener(this), this);
        getLogger().info("ShulkerPlus enabled - sneak + right-click (air) opens a held shulker.");
    }

    @Override
    public void onDisable() {
        for (ShulkerSession session : sessions.values()) {
            session.writeBack();
            Player player = getServer().getPlayer(session.getPlayerId());
            if (player != null) {
                player.closeInventory();
            }
        }
        sessions.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("shulkerplus.admin")) {
                lang.send(sender, "no-permission");
                return true;
            }
            reloadConfig();
            lang.load();
            lang.send(sender, "reloaded");
            return true;
        }
        lang.send(sender, "version", Map.of("version", getDescription().getVersion()));
        return true;
    }

    public Map<UUID, ShulkerSession> getSessions() {
        return sessions;
    }

    public CombatLogHook getCombatLogHook() {
        return combatLogHook;
    }

    public NamespacedKey getSessionKey() {
        return sessionKey;
    }

    public LangManager getLang() {
        return lang;
    }
}

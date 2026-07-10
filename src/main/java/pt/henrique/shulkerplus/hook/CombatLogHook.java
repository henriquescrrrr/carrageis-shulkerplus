package pt.henrique.shulkerplus.hook;

import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Reflection-based hook into the CombatLOG API (same pattern as the
 * BuildersDream hook): {@code pt.henrique.combatlog.api.CombatLogApi} is
 * registered in the Bukkit ServicesManager; the only method we need is
 * {@code isInCombat(UUID)}. When CombatLOG is absent the hook is a no-op
 * and shulkers can always be opened.
 */
public final class CombatLogHook {

    private boolean available = false;
    private Object apiInstance;
    private Method isInCombatMethod;

    public void init(Logger logger) {
        try {
            Class<?> apiClass = Class.forName("pt.henrique.combatlog.api.CombatLogApi");
            var reg = Bukkit.getServicesManager().getRegistration(apiClass);
            if (reg == null || reg.getProvider() == null) {
                logger.info("[Hook] CombatLOG API não registada — abrir shulkers em combate fica permitido.");
                return;
            }
            apiInstance = reg.getProvider();
            isInCombatMethod = apiClass.getMethod("isInCombat", UUID.class);
            available = true;
            logger.info("[Hook] CombatLOG ligado — shulkers bloqueadas em combate.");
        } catch (ClassNotFoundException e) {
            logger.info("[Hook] CombatLOG não instalado — abrir shulkers em combate fica permitido.");
        } catch (ReflectiveOperationException e) {
            logger.warning("[Hook] Falha a ligar ao CombatLOG: " + e.getMessage());
        }
    }

    public boolean isInCombat(UUID playerId) {
        if (!available) {
            return false;
        }
        try {
            return (boolean) isInCombatMethod.invoke(apiInstance, playerId);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}

package pt.henrique.shulkerplus;

import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

/**
 * Opens held shulkers and enforces the dupe-safety rules while one is open:
 * the source item cannot move, shulkers cannot nest, BuildersDream dream
 * items cannot enter, and the contents are written back exactly once.
 */
public final class ShulkerListener implements Listener {

    /** BuildersDream tags creative-service items with this key — they must never enter shulkers. */
    private static final NamespacedKey DREAM_ITEM_KEY = NamespacedKey.fromString("buildersdream:dream_item");

    private final ShulkerPlus plugin;

    public ShulkerListener(ShulkerPlus plugin) {
        this.plugin = plugin;
    }

    // ──────────────────────────────────────────────────────────────
    // Opening
    // ──────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || !Tag.SHULKER_BOXES.isTagged(item.getType())) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("shulkerplus.use")) {
            return;
        }
        if (plugin.getConfig().getBoolean("require-sneak", true) && !player.isSneaking()) {
            return;
        }
        event.setCancelled(true);

        if (plugin.getSessions().containsKey(player.getUniqueId())) {
            player.sendMessage(plugin.getLang().get(player, "already-open"));
            return;
        }
        // Purpur allows oversized stacks; writing contents back into a stack
        // of N boxes would multiply the items N times.
        if (item.getAmount() != 1) {
            player.sendMessage(plugin.getLang().get(player, "stacked"));
            return;
        }
        if (!plugin.getConfig().getBoolean("allow-in-combat", false)
                && plugin.getCombatLogHook().isInCombat(player.getUniqueId())) {
            player.sendMessage(plugin.getLang().get(player, "in-combat"));
            return;
        }
        if (!(item.getItemMeta() instanceof BlockStateMeta)) {
            return;
        }

        ShulkerSession session = new ShulkerSession(plugin, player, item, player.getInventory().getHeldItemSlot());
        plugin.getSessions().put(player.getUniqueId(), session);
        if (player.openInventory(session.getInventory()) == null) {
            // Open refused (rare: player state won't allow a window). Roll back so the
            // player isn't stuck with a phantom "already open" session and a tagged box.
            plugin.getSessions().remove(player.getUniqueId());
            session.writeBack();
            return;
        }
        playSound(player, Sound.BLOCK_SHULKER_BOX_OPEN);
    }

    // ──────────────────────────────────────────────────────────────
    // Guards while open
    // ──────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        // A tagged box must never move, no matter whose inventory shows it
        // (creative clone, other containers, the works).
        if (hasSessionTag(event.getCurrentItem()) || hasSessionTag(event.getCursor())) {
            event.setCancelled(true);
            return;
        }

        if (!(event.getView().getTopInventory().getHolder() instanceof ShulkerSession session)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        Inventory clicked = event.getClickedInventory();
        boolean clickedTop = clicked == event.getView().getTopInventory();
        boolean clickedBottom = clicked == player.getInventory();

        // The open box is pinned to its hotbar slot.
        if (clickedBottom && event.getSlot() == session.getSourceSlot()) {
            event.setCancelled(true);
            return;
        }
        if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() == session.getSourceSlot()) {
            event.setCancelled(true);
            return;
        }

        // What is about to land inside the shulker GUI?
        ItemStack incoming = null;
        if (clickedTop) {
            incoming = switch (event.getClick()) {
                case NUMBER_KEY -> player.getInventory().getItem(event.getHotbarButton());
                case SWAP_OFFHAND -> player.getInventory().getItemInOffHand();
                default -> event.getCursor();
            };
        } else if (clickedBottom && event.getClick().isShiftClick()) {
            incoming = event.getCurrentItem();
        }
        if (incoming == null || incoming.isEmpty()) {
            return;
        }
        if (Tag.SHULKER_BOXES.isTagged(incoming.getType())) {
            event.setCancelled(true);
            player.sendMessage(plugin.getLang().get(player, "no-nesting"));
        } else if (isDreamItem(incoming)) {
            event.setCancelled(true);
            player.sendMessage(plugin.getLang().get(player, "no-dream-items"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ShulkerSession session)) {
            return;
        }
        boolean touchesTop = event.getRawSlots().stream().anyMatch(raw -> raw < 27);
        boolean touchesSource = event.getRawSlots().stream()
                .anyMatch(raw -> raw >= 27 && event.getView().convertSlot(raw) == session.getSourceSlot());
        ItemStack dragged = event.getOldCursor();
        if (touchesSource
                || (touchesTop && (Tag.SHULKER_BOXES.isTagged(dragged.getType()) || isDreamItem(dragged)))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (plugin.getSessions().containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (!hasSessionTag(item)) {
            return;
        }
        if (plugin.getSessions().containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        } else {
            stripSessionTag(item);
            event.getItemDrop().setItemStack(item);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (hasSessionTag(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Write-back
    // ──────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShulkerSession session)) {
            return;
        }
        boolean alreadyClosed = session.isClosed();
        if (!session.writeBack() && !alreadyClosed) {
            plugin.getLogger().warning("Write-back falhou para " + event.getPlayer().getName()
                    + " — item da sessão não encontrado no inventário.");
        }
        plugin.getSessions().remove(event.getPlayer().getUniqueId());
        if (!alreadyClosed) {
            playSound((Player) event.getPlayer(), Sound.BLOCK_SHULKER_BOX_CLOSE);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        ShulkerSession session = plugin.getSessions().remove(event.getPlayer().getUniqueId());
        if (session != null) {
            session.writeBack();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        ShulkerSession session = plugin.getSessions().remove(event.getEntity().getUniqueId());
        if (session != null) {
            session.writeBackOnDeath(event.getEntity(), event.getDrops());
        }
    }

    /** Crash leftovers: a session tag with no live session is stale — strip it. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        for (ItemStack item : event.getPlayer().getInventory().getContents()) {
            if (hasSessionTag(item)) {
                stripSessionTag(item);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Utility
    // ──────────────────────────────────────────────────────────────

    private boolean hasSessionTag(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().getKeys().contains(plugin.getSessionKey());
    }

    private void stripSessionTag(ItemStack item) {
        item.editMeta(meta -> meta.getPersistentDataContainer().remove(plugin.getSessionKey()));
    }

    private static boolean isDreamItem(ItemStack item) {
        return DREAM_ITEM_KEY != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().getKeys().contains(DREAM_ITEM_KEY);
    }

    private void playSound(Player player, Sound sound) {
        if (plugin.getConfig().getBoolean("play-sounds", true)) {
            player.playSound(player.getLocation(), sound, 0.5f, 1.0f);
        }
    }
}

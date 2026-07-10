package pt.henrique.shulkerplus;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

/**
 * One open hand-shulker: the 27-slot GUI plus everything needed to find the
 * source item again and write the contents back exactly once.
 */
public final class ShulkerSession implements InventoryHolder {

    private final ShulkerPlus plugin;
    private final UUID playerId;
    private final UUID sessionId;
    private final int sourceSlot;
    private final Inventory inventory;
    private boolean closed = false;

    public ShulkerSession(ShulkerPlus plugin, Player player, ItemStack box, int sourceSlot) {
        this.plugin = plugin;
        this.playerId = player.getUniqueId();
        this.sessionId = UUID.randomUUID();
        this.sourceSlot = sourceSlot;

        Component title = box.getItemMeta().hasDisplayName()
                ? box.getItemMeta().displayName()
                : Component.translatable(box.getType().translationKey());
        this.inventory = Bukkit.createInventory(this, 27, title);

        BlockStateMeta meta = (BlockStateMeta) box.getItemMeta();
        ShulkerBox state = (ShulkerBox) meta.getBlockState();
        inventory.setContents(state.getInventory().getContents());

        // Pin the physical item to this session.
        meta.getPersistentDataContainer().set(plugin.getSessionKey(), PersistentDataType.STRING, sessionId.toString());
        box.setItemMeta(meta);
    }

    /**
     * Writes the GUI contents back into the source item and removes the
     * session tag. Idempotent: only the first call does anything.
     *
     * @return true if a matching item was found and updated
     */
    public boolean writeBack() {
        if (closed) {
            return true;
        }
        closed = true;
        Player player = Bukkit.getPlayer(playerId);
        ItemStack item = player != null ? findTagged(player.getInventory().getContents()) : null;
        return item != null && applyTo(item);
    }

    /** Write-back variant for death: the box may already sit in the drops list. */
    public void writeBackOnDeath(Player player, List<ItemStack> drops) {
        if (closed) {
            return;
        }
        closed = true;
        ItemStack item = findTagged(drops.toArray(new ItemStack[0]));
        if (item == null) {
            item = findTagged(player.getInventory().getContents());
        }
        if (item != null) {
            applyTo(item);
        }
    }

    private ItemStack findTagged(ItemStack[] candidates) {
        for (ItemStack candidate : candidates) {
            if (isSessionItem(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** True when the item carries THIS session's tag. */
    public boolean isSessionItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        String tag = item.getItemMeta().getPersistentDataContainer()
                .get(plugin.getSessionKey(), PersistentDataType.STRING);
        return sessionId.toString().equals(tag);
    }

    private boolean applyTo(ItemStack item) {
        ItemMeta rawMeta = item.getItemMeta();
        // Defensive: the tag should only ever live on a shulker box, but never
        // cast blindly — a mismatched block-state would throw inside the close
        // handler and abort the write-back. Fail closed instead.
        if (!(rawMeta instanceof BlockStateMeta meta) || !(meta.getBlockState() instanceof ShulkerBox state)) {
            return false;
        }
        state.getInventory().setContents(inventory.getContents());
        meta.setBlockState(state);
        meta.getPersistentDataContainer().remove(plugin.getSessionKey());
        item.setItemMeta(meta);
        return true;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public int getSourceSlot() {
        return sourceSlot;
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

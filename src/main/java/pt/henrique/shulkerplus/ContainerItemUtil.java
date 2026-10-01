package pt.henrique.shulkerplus;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BundleContents;
import io.papermc.paper.datacomponent.item.ChargedProjectiles;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import io.papermc.paper.datacomponent.item.SulfurCubeContent;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Resolves the stacks carried inside another stack.
 * <p>
 * As of 26.2 an {@link ItemStack} can hold other stacks in four ways, none of
 * which share an API:
 * <ol>
 *   <li>Shulker boxes and other block-entity items: {@link BlockStateMeta} over a
 *       container block state, or the {@code minecraft:container} component.</li>
 *   <li>Bundles: {@link BundleMeta#getItems()} / {@code minecraft:bundle_contents}.</li>
 *   <li>{@code SULFUR_CUBE_BUCKET}: {@code minecraft:sulfur_cube_content}, read through
 *       {@link SulfurCubeContent#absorbedItem()}. Neither a {@link BlockStateMeta} nor a
 *       {@link BundleMeta}.</li>
 *   <li>Crossbows: {@code minecraft:charged_projectiles}.</li>
 * </ol>
 * The server's {@code ItemStack.validateComponents} recurses into {@code CONTAINER},
 * {@code BUNDLE_CONTENTS} and {@code CHARGED_PROJECTILES} but not
 * {@code SULFUR_CUBE_CONTENT}, so a bucket payload is never sanitised for us.
 */
public final class ContainerItemUtil {

    /**
     * Levels of nesting inspected by {@link #flattenPayload(ItemStack)}.
     * Vanilla caps the reachable depth well below this; the bound exists so a
     * datapack-deepened or malformed cyclic payload cannot loop.
     */
    public static final int MAX_NESTING_DEPTH = 3;

    private ContainerItemUtil() {}

    /**
     * Stacks held directly inside this one, across all four nesting classes.
     * Empty when the stack holds nothing or is not a container.
     */
    public static List<ItemStack> payloadOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return List.of();

        List<ItemStack> out = new ArrayList<>();

        // getItemMeta() clones, so the meta routes are gated on the type name first.
        // The component reads below are map lookups and cover what the gate misses,
        // including block-entity items that are not shulker boxes.
        String typeName = stack.getType().name();
        if (typeName.endsWith("SHULKER_BOX")) {
            if (stack.getItemMeta() instanceof BlockStateMeta bsm
                    && bsm.getBlockState() instanceof ShulkerBox box) {
                addNonEmpty(out, box.getInventory().getContents());
            }
        } else if (typeName.endsWith("BUNDLE")) {
            if (stack.getItemMeta() instanceof BundleMeta bundle) {
                addNonEmpty(out, bundle.getItems());
            }
        }

        if (out.isEmpty() && stack.hasData(DataComponentTypes.CONTAINER)) {
            ItemContainerContents container = stack.getData(DataComponentTypes.CONTAINER);
            if (container != null) addNonEmpty(out, container.contents());
        }
        if (out.isEmpty() && stack.hasData(DataComponentTypes.BUNDLE_CONTENTS)) {
            BundleContents bundle = stack.getData(DataComponentTypes.BUNDLE_CONTENTS);
            if (bundle != null) addNonEmpty(out, bundle.contents());
        }
        if (stack.hasData(DataComponentTypes.SULFUR_CUBE_CONTENT)) {
            SulfurCubeContent cube = stack.getData(DataComponentTypes.SULFUR_CUBE_CONTENT);
            if (cube != null) addNonEmpty(out, List.of(cube.absorbedItem()));
        }
        if (stack.hasData(DataComponentTypes.CHARGED_PROJECTILES)) {
            ChargedProjectiles charged = stack.getData(DataComponentTypes.CHARGED_PROJECTILES);
            if (charged != null) addNonEmpty(out, charged.projectiles());
        }

        return out;
    }

    /**
     * Every stack reachable from this one, breadth-first, down to
     * {@link #MAX_NESTING_DEPTH} levels. The stack itself is not included.
     */
    public static List<ItemStack> flattenPayload(ItemStack stack) {
        List<ItemStack> out = new ArrayList<>();
        Deque<ItemStack> frontier = new ArrayDeque<>(payloadOf(stack));
        int depth = 0;
        while (!frontier.isEmpty() && depth < MAX_NESTING_DEPTH) {
            int levelSize = frontier.size();
            for (int i = 0; i < levelSize; i++) {
                ItemStack current = frontier.poll();
                if (current == null) continue;
                out.add(current);
                if (depth + 1 < MAX_NESTING_DEPTH) frontier.addAll(payloadOf(current));
            }
            depth++;
        }
        return out;
    }

    private static void addNonEmpty(List<ItemStack> out, ItemStack[] items) {
        if (items == null) return;
        for (ItemStack i : items) {
            if (i != null && !i.getType().isAir()) out.add(i);
        }
    }

    private static void addNonEmpty(List<ItemStack> out, List<ItemStack> items) {
        if (items == null) return;
        for (ItemStack i : items) {
            if (i != null && !i.getType().isAir()) out.add(i);
        }
    }
}

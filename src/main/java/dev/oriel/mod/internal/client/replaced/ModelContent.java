package dev.oriel.mod.internal.client.replaced;


import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.oriel.engine.dom.Element;
import dev.oriel.mod.internal.client.render.McCanvas;
import dev.oriel.mod.internal.client.render.Scene;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * {@code <model block="minecraft:oak_stairs[facing=east]">} or {@code <model item="minecraft:diamond_sword">}: a
 * block (any block state, in {@code /setblock} syntax) or an item ({@code count} and {@code components} as on
 * {@code <item>}) drawn in 3D, centred in the content box (or where {@code object-position} puts it). At
 * {@code -mc-yaw}/{@code -mc-pitch} 0 it looks as in the inventory (blocks in the three-quarter view, flat items face
 * on, at the size an item fills its slot); {@code -mc-yaw} turns it, {@code -mc-pitch} views it from further above,
 * {@code -mc-model-scale} sizes it, and {@code rotatable} lets the pointer turn it. Blocks without a model (fluids,
 * air) draw nothing.
 *
 * <p>26.x resolved the block or item into a {@code BlockModelRenderState} / {@code TrackingItemStackRenderState} and
 * let the GUI renderer submit it later. 1.20.1 draws immediately, so the block state and the item stack go straight to
 * {@link Scene}, which asks the game's own renderers for this frame's model.
 */
public final class ModelContent extends TurnableContent {
    private @Nullable BlockState block;
    private ItemStack item = ItemStack.EMPTY;

    public ModelContent(Element element) {
        super(element);
        load();
    }

    @Override
    public float intrinsicWidth() {
        return 32;
    }

    @Override
    public float intrinsicHeight() {
        return 32;
    }

    @Override
    public void attributeChanged(String name) {
        if (name.equals("block") || name.equals("item") || name.equals("count") || name.equals("components")) load();
    }

    /**
     * The model takes a square as wide as the box's shorter side (times {@code -mc-model-scale}), placed by
     * {@code object-position}: the picture is the whole box, the model moved in it.
     */
    @Override
    protected void draw(McCanvas canvas, float x, float y, float width, float height) {
        int tint = tint();
        if (!canvas.sceneVisible(tint, x, y, width, height)) return;
        float[] square = element.computedStyle().objectSquare(x, y, width, height, modelScale());
        float size = square[2];
        if (size <= 0) return;
        // Its centre's offset from the box's, in blocks (a block is the square's side).
        float dx = (square[0] - x - (width - size) / 2) / size, dy = (square[1] - y - (height - size) / 2) / size;
        Scene scene = scene(dx, dy);
        if (scene != null) canvas.drawScene(scene, size, tint, x, y, width, height);
    }

    private void load() {
        String state = attr("block", "");
        block = null;
        if (!state.isEmpty()) {
            try {
                block = ItemStacks.blockState(state);
            } catch (CommandSyntaxException ignored) {
                // An unknown block or property: nothing to draw.
            }
        }
        item = block != null ? ItemStack.EMPTY : ItemStacks.of(element, "item");
    }

    /**
     * This frame's model (models change with resource packs), moved by {@code (dx, dy)} blocks, or null when there is
     * none.
     */
    private @Nullable Scene scene(float dx, float dy) {
        if (block != null) return Scene.block(block, yaw(), pitch(), dx, dy);
        if (item.isEmpty()) return null;
        return Scene.item(item, yaw(), pitch(), dx, dy);
    }
}

package dev.oriel.mod.internal.client.render;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.oriel.engine.scene.SceneCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.jspecify.annotations.Nullable;

/**
 * What a 3D picture shows, resolved for one frame: an entity, a block or an item, posed, and drawn around the middle
 * of the picture where a block is {@code scale} GUI px.
 *
 * <p>26.x resolved these into render states and let vanilla's {@code GuiRenderer} submit them later; 1.20.1's GUI is
 * drawn immediately, so the drawer here calls the game's renderers directly, into the same {@link MultiBufferSource}
 * the page is being drawn with. The picture is still axis-aligned on screen: the canvas passes its own box in GUI px
 * and the drawer poses inside it, so a rotated canvas only moves the box.
 */
public final class Scene {
    /**
     * How a picture is drawn: {@code pose} has the origin in the middle of the picture's bottom edge, y up, a block
     * per unit times {@code scale}.
     */
    @FunctionalInterface
    public interface Drawer {
        void submit(PoseStack pose, MultiBufferSource buffers, Picture picture);
    }

    /** The picture being drawn into, as its drawer needs it: its size in GUI px and how many of them a block is. */
    public record Picture(float scale, int y0, int y1) {}

    /**
     * A block's size in the inventory, 0.625 of a slot; it is seen as a scene's camera sees blocks
     * ({@link SceneCamera#BASE_TILT}, {@link SceneCamera#BASE_YAW}).
     */
    private static final float BLOCK_SCALE = 0.625F;

    /** Full bright: a GUI picture is lit as the inventory lights it, never by the world around it. */
    private static final int FULL_BRIGHT = 0xF000F0;

    private final Drawer drawer;

    private Scene(Drawer drawer) {
        this.drawer = drawer;
    }

    /**
     * An entity with its origin {@code dx} blocks right of the middle of the picture's bottom edge and {@code up}
     * blocks above it; {@code rotation} turns it, and the camera looks along {@code cameraTilt} (for billboards such
     * as name tags).
     */
    public static Scene entity(Entity entity, float dx, float up, Quaternionfc rotation, @Nullable Quaternionfc cameraTilt) {
        // 1.20.1's PoseStack takes a Quaternionf, not the read-only interface, so copy the caller's once, up front:
        // mutating it inside the frame would turn the entity a little more every time the picture is drawn.
        Quaternionf turn = rotation.get(new Quaternionf());
        // The camera orientation is dispatcher state rather than part of the pose, so the caller's is kept and put
        // back around the draw. JOML's no-argument conjugate() mutates, and Quaternionfc has no such method at all.
        // Vanilla's InventoryScreen.renderEntityInInventory conjugates the tilt the same way for 1.20.1.
        Quaternionf camera = cameraTilt == null ? null : cameraTilt.conjugate(new Quaternionf());
        return new Scene((pose, buffers, picture) -> {
            // The picture frame is the middle of the box, y down, a block per unit (see McGui.scene). Moving the origin
            // to the bottom edge and up in it, then flipping z, is vanilla's InventoryScreen.renderEntityInInventory:
            // it scales (size, size, -size) and its rotation carries the rotateZ(PI) that turns the model upright.
            pose.translate(dx, (picture.y1() - picture.y0()) / 2F / picture.scale() - up, 0);
            pose.scale(1, 1, -1);
            pose.mulPose(turn);
            EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            Quaternionf savedCamera = camera == null ? null : dispatcher.cameraOrientation().get(new Quaternionf());
            if (camera != null) dispatcher.overrideCameraOrientation(camera);
            // No getter for the flag, so this does as vanilla's InventoryScreen does: turn shadows off for the picture
            // and on again after it, which is the state every other draw of the frame expects anyway.
            dispatcher.setRenderShadow(false);
            try {
                dispatcher.render(entity, 0, 0, 0, 0, DeltaTracker.current().getGameTimeDeltaPartialTick(false),
                        pose, buffers, FULL_BRIGHT);
            } finally {
                if (savedCamera != null) dispatcher.overrideCameraOrientation(savedCamera);
                dispatcher.setRenderShadow(true);
            }
        });
    }

    /**
     * A block, as the inventory shows blocks (vanilla's {@code block/block} GUI transform: 30° from above, turned
     * 225°, 0.625 a box), turned by {@code yaw} about its upright axis and seen from {@code pitch} degrees further
     * above, its centre {@code dx} blocks right of the picture's middle and {@code dy} below.
     */
    public static Scene block(BlockState state, float yaw, float pitch, float dx, float dy) {
        return new Scene((pose, buffers, picture) -> {
            Lighting.setupFor3DItems();
            pose.translate(dx, dy, 0);
            turn(pose, SceneCamera.BASE_TILT, yaw, pitch);
            rotate(pose, Axis.YP, SceneCamera.BASE_YAW);
            pose.scale(BLOCK_SCALE, BLOCK_SCALE, BLOCK_SCALE);
            pose.translate(-0.5F, -0.5F, -0.5F);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, buffers, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        });
    }

    /**
     * An item, so at yaw and pitch 0 it is the inventory icon, turned by {@code yaw} about its upright axis and seen
     * from {@code pitch} degrees further above, its centre {@code dx} blocks right of the picture's middle and
     * {@code dy} below. Block-like items are tilted 30° toward the viewer in the inventory, flat ones not, so that is
     * the axis they turn about.
     */
    public static Scene item(ItemStack stack, float yaw, float pitch, float dx, float dy) {
        boolean blockLike = Minecraft.getInstance().getItemRenderer()
                .getModel(stack, null, null, 0).usesBlockLight();
        float tilt = blockLike ? SceneCamera.BASE_TILT : 0;
        return new Scene((pose, buffers, picture) -> {
            if (blockLike) Lighting.setupFor3DItems();
            else Lighting.setupForFlatItems();
            pose.translate(dx, dy, 0);
            turn(pose, tilt, yaw, pitch);
            rotate(pose, Axis.XP, -tilt); // the item's GUI transform tilts it again
            // 1.20.1's renderStatic is (stack, context, packedLight, packedOverlay, pose, buffers, level, seed): light
            // and overlay come second, not the boolean leftHand 26.x took.
            Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.GUI, FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, pose, buffers, null, 0);
        });
    }

    /** A picture drawn by {@code drawer}, lit as the inventory lights blocks. */
    public static Scene of(Drawer drawer) {
        return new Scene(drawer);
    }

    /** Faces a GUI model to the viewer, y up, tilted {@code tilt + pitch} degrees from above, turned by yaw. */
    private static void turn(PoseStack pose, float tilt, float yaw, float pitch) {
        pose.scale(1, -1, -1); // y up and facing the viewer, as vanilla draws GUI items
        rotate(pose, Axis.XP, tilt + pitch);
        rotate(pose, Axis.YP, -yaw);
    }

    /** Turns {@code pose} by {@code degrees} about {@code axis}. */
    private static void rotate(PoseStack pose, Axis axis, float degrees) {
        pose.mulPose(axis.rotationDegrees(degrees));
    }

    /** Draws this picture into {@code pose}, which is already at the picture's bottom-middle, y up, block per unit. */
    public void submit(PoseStack pose, MultiBufferSource buffers, float scale, int y0, int y1) {
        drawer.submit(pose, buffers, new Picture(scale, y0, y1));
    }

    // ---- Keys ----
    //
    // 26.x kept pictures of the same still model between frames, so the offscreen pool could hand a picture the
    // renderer that already held it. 1.20.1 draws every picture again each frame, so nothing is kept and nothing
    // needs a key.

    @Override
    public final boolean equals(Object o) {
        return this == o;
    }

    @Override
    public final int hashCode() {
        return System.identityHashCode(this);
    }
}

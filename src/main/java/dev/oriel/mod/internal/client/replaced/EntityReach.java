package dev.oriel.mod.internal.client.replaced;


import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * How far an entity's model reaches, in blocks from its feet: {@code radius} from its upright axis (what it sweeps as
 * it turns), {@code bottom} (negative when parts hang below its feet, like a ghast's tentacles) and {@code top}. Heads,
 * tails, arms and tentacles reach past bounding boxes.
 *
 * <p>1.20.1 port: the 26.x build measured by submitting the entity into a {@code SubmitNodeStorage} and reading the
 * posed model extents. 1.20.1 has no submit phase, so this draws the live model instead — into a
 * {@link VertexConsumer} that keeps nothing but where the vertices land. That covers every renderer that draws a
 * model, with the renderer's own transforms and scale; renderers that draw something else (an ender dragon's custom
 * geometry) keep their bounding box, exactly as the 26.x build did when nothing was submitted.</p>
 *
 * <p>The model is posed in a neutral stance first (no stride, no head turn, no idle swing, age 0), so the first pose a
 * type happens to be drawn in doesn't size every later portrait of it. Measured once per entity type, age and size
 * (slimes come in sizes).</p>
 */
public record EntityReach(float radius, float bottom, float top) {
    /** Where a model's own origin sits above an entity's feet, as {@code LivingEntityRenderer} places it. */
    private static final float MODEL_Y = -1.501F;

    private record Key(EntityType<?> type, boolean baby, float width, float height) {}

    private static final Map<Key, EntityReach> MEASURED = new HashMap<>();

    /** The reach of {@code entity}, never less than its bounding box. */
    public static EntityReach of(Entity entity) {
        Key key = new Key(entity.getType(), entity instanceof LivingEntity living && living.isBaby(),
                entity.getBbWidth(), entity.getBbHeight());
        EntityReach reach = MEASURED.get(key);
        if (reach == null) MEASURED.put(key, reach = measure(entity));
        return reach;
    }

    /** Drops the cache: entity models are rebuilt on a resource reload. */
    public static void clear() {
        MEASURED.clear();
    }

    private static EntityReach measure(Entity entity) {
        float boxRadius = entity.getBbWidth() / (float) Math.sqrt(2), height = entity.getBbHeight();
        Extents measured = drawModel(entity);
        // Turning takes twice the box: never smaller than the box it stands in.
        if (measured == null || measured.top == Float.NEGATIVE_INFINITY) return new EntityReach(entity.getBbWidth(), 0, height);
        return new EntityReach(Math.max(boxRadius, measured.radius), Math.min(0, measured.bottom), Math.max(height, measured.top));
    }

    /**
     * Draws {@code entity}'s model into a measuring consumer, in a neutral stance, and returns its extremes; null when
     * its renderer draws no model.
     */
    @SuppressWarnings("unchecked")
    private static <T extends LivingEntity> Extents drawModel(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return null;
        EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(living);
        if (!(renderer instanceof LivingEntityRenderer<?, ?> livingRenderer)) return null;
        EntityModel<T> model = (EntityModel<T>) livingRenderer.getModel();
        T target = (T) living;
        // A neutral stance: no stride, no head turn, no idle swing, so the reach is the same whichever pose came first.
        model.setupAnim(target, 0F, 0F, 0F, 0F, 0F);
        Extents extents = new Extents();
        PoseStack pose = new PoseStack();
        // The renderer's own placement, so the result is in blocks from the entity's feet.
        pose.scale(-1F, -1F, 1F);
        pose.translate(0F, MODEL_Y, 0F);
        model.renderToBuffer(pose, extents, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
        return extents;
    }

    /** A vertex consumer that keeps only the extremes of the positions it is given. */
    private static final class Extents implements VertexConsumer {
        private float radius, bottom = Float.POSITIVE_INFINITY, top = Float.NEGATIVE_INFINITY;

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            radius = Math.max(radius, (float) Math.sqrt(x * x + z * z));
            bottom = Math.min(bottom, (float) y);
            top = Math.max(top, (float) y);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {}

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {}

        @Override
        public void unsetDefaultColor() {}
    }
}

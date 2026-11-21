package dev.oriel.mod.internal.client.replaced;


import dev.oriel.engine.style.ComputedStyle;
import dev.oriel.engine.style.EntityFocus;
import dev.oriel.engine.style.EntityFraming;
import dev.oriel.mod.client.OrielEntities;
import dev.oriel.mod.internal.client.render.DeltaTracker;
import dev.oriel.mod.internal.client.render.McCanvas;
import dev.oriel.mod.internal.client.render.Scene;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Draws a live entity fitted into a box, as vanilla's inventory does for the player, as a {@link Scene} (so it can be
 * tinted and faded). Any number of entities, even of one type, can be drawn in a frame.
 *
 * <p>The body fit: the entity is drawn at the largest size at which its model ({@link EntityReach}: heads, tails, arms
 * and tentacles reach past bounding boxes) fits under a small margin at the top and sides, seen at the current pitch
 * and at any turn. So tall mobs fit the height and long or wide ones the width, and spinning one never changes its
 * size. The room it takes is placed by {@code object-position} ({@link EntityFraming#body}); unset, it stands on the
 * bottom edge, centred, and parts that hang below the feet (a ghast's tentacles) rest on the edge instead.
 *
 * <p>The eyes fit ({@link EntityFraming#eyes}): the box's shorter side spans 0.7 of the entity's eye height, which
 * frames a player's head and shoulders, and {@code object-position} places the point at its eye height on its upright
 * axis ({@code 50% 40%} unset). Turning, tilting and gazing keep that point where it is.
 *
 * <p>1.20.1 port: 26.x turned an {@code EntityRenderState} into a deferred picture, which let one entity be drawn
 * twice in a frame in two poses. 1.20.1 draws through {@code EntityRenderDispatcher.render}, which reads the live
 * entity, so the pose is written onto the entity itself and restored when the picture is done. Display entities (the
 * ones {@link #create} makes) are never added to a world and never tick, so nothing else sees the pose; an entity of
 * the world shown in a page keeps its pose between frames until the next one restores it.
 */
public final class EntityPortrait {
    /** Free space above the entity, as a fraction of the box height. */
    private static final float TOP_MARGIN = 0.08f;
    /** Free space on each side, as a fraction of the box width. */
    private static final float SIDE_MARGIN = 0.05f;

    /** Pose functions mods registered, by entity type. */
    private static final Map<EntityType<?>, OrielEntities.PortraitPose<?>> POSES = new ConcurrentHashMap<>();

    /**
     * How the entity is shown, in degrees. {@code yaw} turns it (0 faces the viewer, positive turns its front to the
     * right) and {@code pitch} views it from above (positive) or below. With {@code followMouse} it turns its head
     * toward the pointer ({@link #draw}); {@code scale} multiplies the size that fits the box and {@code walk} swings
     * its limbs at that speed (0 stands still).
     */
    public record Pose(float yaw, float pitch, boolean followMouse, float scale, float walk) {
        public static final Pose FRONT = new Pose(0, 0, false, 1, 0);
    }

    /**
     * Display entities get negative ids: renderers need one, and real entities' ids are positive. They never tick, so
     * their idle animations play on the clock.
     */
    private static int nextDisplayId = -1;

    private EntityPortrait() {}

    /** For {@code OrielEntities.registerPortraitState}: GUI renders of {@code type} are posed by {@code poser}. */
    public static <T extends Entity> void registerState(EntityType<T> type, OrielEntities.PortraitPose<? super T> poser) {
        POSES.put(type, poser);
    }

    /** An entity for display only, never added to the world: monsters show in peaceful worlds too. */
    public static @Nullable Entity create(EntityType<?> type, Level level) {
        Entity entity = type.create(level);
        if (entity != null) entity.setId(nextDisplayId--);
        return entity;
    }

    /**
     * Draws {@code entity} into the box {@code (x, y, width, height)} in the canvas's current transform, multiplied by
     * {@code tint} (ARGB; white for none), framed by {@code style}'s {@code -mc-entity-focus} and
     * {@code object-position}. Nothing is drawn for a box that is clipped away or transparent.
     *
     * <p>A GUI picture has no shadow, name tag, score, leash or passenger offset and is lit full bright; that is set
     * around the draw and restored after it. The body's turn, its limbs and the size ({@code scale} 1) are set from the
     * pose before the picture is drawn. With {@code followMouse} the head turns toward the canvas's pointer as
     * vanilla's inventory turns the player's, as far as {@code style}'s {@code -mc-gaze-reach} and
     * {@code -mc-gaze-limit} let it, and the body leans half of that turn. Otherwise the head looks ahead, except that
     * an entity a mod posed keeps its own head.
     */
    public static void draw(McCanvas canvas, Entity entity, Pose pose, ComputedStyle style, int tint, float x, float y, float width, float height) {
        if (!canvas.sceneVisible(tint, x, y, width, height)) return;

        // The point it looks from: with the eyes focus, also where its eyes go.
        float[] eyes = EntityFraming.gazeOrigin(style, x, y, width, height);
        boolean gazes = pose.followMouse() && canvas.mouseX() >= 0;
        float gazeYaw = gazes ? style.gazeYaw(canvas.mouseX() - eyes[0]) : 0;
        float gazePitch = gazes ? style.gazePitch(eyes[1] - canvas.mouseY()) : 0;
        // The body leans half of the gaze and the head turns the other half on top.
        float leanYaw = gazeYaw / 2, leanPitch = gazePitch / 2;
        // Leaning toward the gaze tilts the view the other way: looking up shows it from below.
        float tilt = leanPitch - pose.pitch();
        Quaternionf view = new Quaternionf().rotateX(tilt * Mth.DEG_TO_RAD);
        Quaternionf rotation = new Quaternionf().rotateZ(Mth.PI).mul(view);

        boolean posed = posed(entity);
        float eyeHeight = entity.getEyeHeight();
        boolean living = entity instanceof LivingEntity;
        // What the entity looked like, so an entity of the world is put back as it was after the picture.
        PoseStackSnapshot before = living ? PoseStackSnapshot.of((LivingEntity) entity) : null;
        if (living) {
            LivingEntity body = (LivingEntity) entity;
            body.yBodyRot = 180.0F - pose.yaw() - leanYaw;
            body.yBodyRotO = body.yBodyRot;
            if (pose.followMouse() || !posed) {
                // The head turns relative to the body, as vanilla's InventoryScreen does: it sets the body to
                // 180 + half the gaze and the head to 180 + the whole gaze, so the head is ahead of the body by the
                // other half. Setting the head to -leanYaw instead, near 0, left it 180 degrees about from the body
                // and the player was drawn looking backwards.
                float headYaw = body.yBodyRot - leanYaw;
                body.setYRot(headYaw);
                body.setXRot(-2 * leanPitch);
                body.yHeadRot = headYaw;
                body.yHeadRotO = headYaw;
            }
            // 1.20.1 renders through the live entity and LivingEntityRenderer interpolates the head with
            // Mth.lerp(partialTick, xRotO, xRot) and Mth.rotLerp(partialTick, yHeadRotO, yHeadRot). The pose above is
            // this frame's value, so the previous-frame fields have to be the same or the partial tick swings the head
            // between the two every frame - a nod, without the mouse moving. Vanilla's InventoryScreen only avoids it
            // by passing partialTick 1.0F; here it is the real partial tick, so the fields are levelled instead.
            body.xRotO = body.getXRot();
            body.yRotO = body.getYRot();
            body.yHeadRotO = body.yHeadRot;
            body.yBodyRotO = body.yBodyRot;
            // The limbs swing at the pose's speed; display entities never tick, so their clock is the wall clock.
            body.walkAnimation.setSpeed(pose.walk());
            body.walkAnimation.update(pose.walk(), 1F);
            if (entity.getId() < 0) body.tickCount = (int) (Util.getMillis() / 50);
        } else {
            rotation.mul(new Quaternionf().rotateY(pose.yaw() * Mth.DEG_TO_RAD));
        }

        // Where the entity's origin (its feet, on its upright axis) goes, in local px, and how big a block is.
        EntityFraming frame;
        if (style.entityFocus == EntityFocus.EYES) {
            frame = EntityFraming.eyes(eyes[0], eyes[1], width, height, eyeHeight, tilt, pose.scale());
        } else {
            EntityReach reach = EntityReach.of(entity);
            float sweep = 2 * reach.radius(), tall = Math.max(0.3F, reach.top() - reach.bottom());
            float sin = Math.abs(Mth.sin(pose.pitch() * Mth.DEG_TO_RAD)), cos = Math.abs(Mth.cos(pose.pitch() * Mth.DEG_TO_RAD));
            // The room it takes, seen from this pitch. Its origin is lifted off the room's bottom by what hangs below
            // the feet and, seen from above or below, by the near or far side of what it sweeps.
            float roomHeight = tall * cos + sweep * sin, lift = -reach.bottom() * cos + sweep / 2 * sin;
            float fitted = Math.min(height * (1 - TOP_MARGIN) / roomHeight, width * (1 - 2 * SIDE_MARGIN) / sweep) * pose.scale();
            frame = EntityFraming.fit(style, x, y, width, height, fitted, pose.scale(), sweep, roomHeight, lift);
        }
        float pixelsPerBlock = frame.pixelsPerBlock();
        float dx = (frame.originX() - x - width / 2) / pixelsPerBlock, up = (y + height - frame.originY()) / pixelsPerBlock;
        try {
            canvas.drawScene(Scene.entity(entity, dx, up, rotation, view), pixelsPerBlock, tint, x, y, width, height);
        } finally {
            if (before != null) before.restore((LivingEntity) entity);
        }
    }

    /**
     * {@code entity} posed for a {@code <scene>}: as {@link #draw} poses it (body facing {@code yaw}, head straight
     * ahead, limbs swinging at {@code walk}, scale 1). The pose is written onto the entity, which is returned for the
     * caller to pass to {@link Scene#entity}; it is not restored.
     */
    public static Entity sceneEntity(Entity entity, float yaw, float walk) {
        if (entity instanceof LivingEntity body) {
            body.yBodyRot = yaw;
            body.yBodyRotO = yaw;
            if (!posed(entity)) {
                body.setYRot(0F);
                body.setXRot(0F);
                body.yHeadRot = 0F;
                body.yHeadRotO = 0F;
            }
            // Same as draw: the pose is this frame's value, so the interpolation endpoints must agree.
            body.xRotO = body.getXRot();
            body.yRotO = body.getYRot();
            body.yHeadRotO = body.yHeadRot;
            body.yBodyRotO = body.yBodyRot;
            body.walkAnimation.setSpeed(walk);
            body.walkAnimation.update(walk, 1F);
            if (entity.getId() < 0) body.tickCount = (int) (Util.getMillis() / 50);
        }
        return entity;
    }

    /** Runs the pose function {@code entity}'s type has, if any. */
    private static boolean posed(Entity entity) {
        OrielEntities.PortraitPose<Entity> poser = poser(entity);
        if (poser == null) return false;
        poser.pose(entity, DeltaTracker.current().getGameTimeDeltaPartialTick(false));
        return true;
    }

    @SuppressWarnings("unchecked")
    private static OrielEntities.@Nullable PortraitPose<Entity> poser(Entity entity) {
        return (OrielEntities.PortraitPose<Entity>) POSES.get(entity.getType());
    }

    /** The fields of a living entity a picture writes, kept so an entity of the world is put back as it was. */
    private record PoseStackSnapshot(float yBodyRot, float yBodyRotO, float yRot, float yRotO, float xRot, float xRotO,
                                     float yHeadRot, float yHeadRotO, int tickCount, float walkSpeed, float walkPos) {
        static PoseStackSnapshot of(LivingEntity entity) {
            return new PoseStackSnapshot(entity.yBodyRot, entity.yBodyRotO, entity.getYRot(), entity.yRotO,
                    entity.getXRot(), entity.xRotO, entity.yHeadRot, entity.yHeadRotO, entity.tickCount,
                    entity.walkAnimation.speed(), entity.walkAnimation.position());
        }

        void restore(LivingEntity entity) {
            entity.yBodyRot = yBodyRot;
            entity.yBodyRotO = yBodyRotO;
            entity.setYRot(yRot);
            entity.yRotO = yRotO;
            entity.setXRot(xRot);
            entity.xRotO = xRotO;
            entity.yHeadRot = yHeadRot;
            entity.yHeadRotO = yHeadRotO;
            entity.tickCount = tickCount;
            entity.walkAnimation.setSpeed(walkSpeed);
            entity.walkAnimation.position(walkPos);
        }
    }
}

package dev.oriel.mod.client;


import dev.oriel.mod.internal.client.replaced.EntityPortrait;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Client API for how pages draw entities ({@code <entity>}).
 *
 * <pre>{@code
 * // In client setup: draw automatons in screens without the bubbles their renderer adds over their heads.
 * OrielEntities.registerPortraitState(MyEntities.AUTOMATON.get(), (automaton, partialTick) -> {
 *     automaton.setGlowingTag(false);
 * });
 * }</pre>
 */
public final class OrielEntities {
    private OrielEntities() {}

    /**
     * Poses an entity for a GUI picture ({@link #registerPortraitState}).
     *
     * <p>26.x had this build an {@code EntityRenderState}, which is what vanilla's renderers consume. 1.20.1 has no
     * such state — {@code EntityRenderDispatcher.render} reads the entity itself — so a poser changes the entity
     * instead. Oriel poses the body's turn, the walk animation and the size around it, and puts an entity of the world
     * back as it was once the picture is drawn.
     */
    @FunctionalInterface
    public interface PortraitPose<T extends Entity> {
        /** Poses {@code entity} for a picture drawn at {@code partialTick}. */
        void pose(T entity, float partialTick);
    }

    /**
     * Makes GUI renders of {@code type} pose their entity with {@code poser} before drawing it. Use it to turn off what
     * your renderer draws for the world only (speech bubbles, labels, glowing outlines, effects).
     *
     * <p>Oriel then poses the entity as it does any other: it sets the body's turn ({@code yBodyRot}), the walk
     * animation and the size, lights it full bright and turns off the shadow, name tag and leashes for the picture.
     * The head is only turned with {@code follow-mouse}: otherwise it stays as your poser left it. The body fit
     * measures the posed model too, so whatever a poser changes takes the room it really takes.
     *
     * <p>Registering a type again replaces its poser. Call during client setup; the poser runs on the render thread,
     * once per frame for every {@code <entity>} on screen showing that type.
     */
    public static <T extends Entity> void registerPortraitState(EntityType<T> type, PortraitPose<? super T> poser) {
        EntityPortrait.registerState(type, poser);
    }
}

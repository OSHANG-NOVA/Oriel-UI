package dev.oriel.mod.internal.client.replaced;


import dev.oriel.engine.dom.Element;
import dev.oriel.mod.internal.client.render.McCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * {@code <entity>}: a live entity render, fitted to the content box ({@link EntityPortrait}): standing on its bottom,
 * or with {@code -mc-entity-focus: eyes} its head and shoulders filling it, either placed by {@code object-position}.
 * <ul>
 *   <li>{@code <entity player>}: the local player;</li>
 *   <li>{@code <entity type="minecraft:pig">}: a client-side entity of that type, created once per world;</li>
 *   <li>{@code <entity id="123">}: an entity in the world by network id.</li>
 * </ul>
 * {@code -mc-yaw}, {@code -mc-pitch} and {@code -mc-model-scale} turn, view and size it; {@code rotatable} lets the
 * pointer turn it; {@code follow-mouse} turns its head toward the pointer (like the inventory's player), as far as
 * {@code -mc-gaze-reach} and {@code -mc-gaze-limit} let it; {@code walk} (optionally a speed, 0.7 by default) swings
 * its limbs. Created entities play their idle animations and take {@code baby}, {@code variant} and {@code color}
 * (the {@code <type>/variant} and {@code <type>/color} components, e.g. {@code variant="minecraft:black"} on a cat,
 * {@code color="pink"} on a sheep), {@code components} (SNBT of any entity components) and equipment by slot
 * ({@code mainhand="minecraft:iron_sword"}, {@code offhand}, {@code head}, {@code chest}, {@code legs}, {@code feet},
 * {@code body}, {@code saddle}). Attributes are read when they change.
 */
public final class EntityContent extends TurnableContent {
    /** Attributes that make up a created entity: when one changes, it is created again. */
    private static final Set<String> RECREATE = Set.of("type", "baby", "variant", "color", "components");

    private boolean player, followMouse;
    private float walk;
    /** The world entity's network id, or null for a created entity of {@link #type}. */
    private @Nullable Integer id;
    private @Nullable ResourceLocation type;
    private @Nullable Entity created;
    private @Nullable Level createdIn;

    public EntityContent(Element element) {
        super(element);
        load();
    }

    @Override
    public float intrinsicWidth() {
        return 48;
    }

    @Override
    public float intrinsicHeight() {
        return 48;
    }

    @Override
    public void attributeChanged(String name) {
        if (RECREATE.contains(name) || slot(name) != null) createdIn = null;
        load();
    }

    @Override
    public void dispose() {
        created = null;
        createdIn = null;
    }

    /** Posed by the element's style and attributes: the one place that decides how it stands. */
    @Override
    protected void draw(McCanvas canvas, float x, float y, float width, float height) {
        int tint = tint();
        if (!canvas.sceneVisible(tint, x, y, width, height)) return;
        Entity entity = entity();
        if (entity == null) return;
        EntityPortrait.Pose pose = new EntityPortrait.Pose(yaw(), pitch(), followMouse, modelScale(), walk);
        EntityPortrait.draw(canvas, entity, pose, element.computedStyle(), tint, x, y, width, height);
    }

    /**
     * Its entity in a {@code <scene>}: facing its {@code -mc-yaw} as Minecraft yaws go (0 south), limbs swinging
     * with {@code walk}. Null when there is no entity to show.
     */
    public net.minecraft.world.entity.@Nullable Entity sceneEntity() {
        Entity entity = entity();
        return entity == null ? null : EntityPortrait.sceneEntity(entity, element.computedStyle().modelYaw, walk);
    }

    private void load() {
        player = element.hasAttribute("player");
        followMouse = element.hasAttribute("follow-mouse");
        walk = element.hasAttribute("walk") ? element.numberAttribute("walk", 0.7F) : 0;
        float networkId = element.numberAttribute("id", Float.NaN);
        id = Float.isNaN(networkId) ? null : (int) networkId;
        type = ResourceLocation.tryParse(attr("type", "minecraft:pig"));
    }

    private @Nullable Entity entity() {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return null;
        if (player) return mc.player;
        if (id != null) return level.getEntity(id);
        if (createdIn != level) { // once per world (and after its attributes change), even when the type is unknown
            created = type == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(type).map(t -> create(t, level)).orElse(null);
            createdIn = level;
        }
        return created;
    }

    private @Nullable Entity create(EntityType<?> type, Level level) {
        Entity entity = EntityPortrait.create(type, level);
        if (entity == null) return null;
        if (entity instanceof Mob mob) mob.setBaby(element.hasAttribute("baby"));
        if (entity instanceof LivingEntity living) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                String item = element.getAttribute(slot.getName());
                if (item != null) living.setItemSlot(slot, ItemStacks.parse(item, 1, null));
            }
        }
        applyComponents(entity);
        return entity;
    }

    /**
     * Gives the entity the {@code variant} and {@code color} it was asked for, and then any {@code components} SNBT.
     *
     * <p>26.x carried both as data components (the {@code <type>/variant} and {@code <type>/color} keys, and the whole
     * {@code components} map) and pushed them into the entity through {@code setComponent}. 1.20.1 has no data
     * components on entities: the same values are ordinary NBT keys, so they go through {@link Entity#load}, which is
     * how vanilla saves and restores every mob's variant, colour and equipment. The keys are the ones the entity classes
     * read: {@code variant} (cat, fox, horse, parrot, rabbit…), {@code Color} (sheep, wolf collar, tropical fish) and
     * {@code CollarColor} (cat, wolf).
     */
    private void applyComponents(Entity entity) {
        CompoundTag tag = new CompoundTag();
        String variant = element.getAttribute("variant");
        if (variant != null) tag.putString("variant", variant.strip());
        String color = element.getAttribute("color");
        if (color != null) {
            tag.putString("Color", color.strip());
            tag.putString("CollarColor", color.strip());
        }
        String snbt = element.getAttribute("components");
        if (snbt != null) {
            CompoundTag parsed = ItemStacks.snbt(snbt, "<entity components>");
            if (parsed != null) tag.merge(parsed);
        }
        if (tag.isEmpty()) return;
        entity.load(tag);
    }

    /**
     * The equipment slot an attribute name means, or null. 26.x asked {@code EquipmentSlot.CODEC}; 1.20.1 has no codec
     * for it, so the names are compared by hand. {@code getName} is what 1.20.1 calls the slot in NBT
     * ({@code mainhand}, {@code offhand}, {@code chest}…), and a page may also write it hyphenated ({@code main-hand}),
     * so the hyphens are dropped before comparing.
     */
    private static @Nullable EquipmentSlot slot(String name) {
        String wanted = name.replace("-", "").replace("_", "");
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getName().equals(wanted)) return slot;
        }
        return null;
    }
}

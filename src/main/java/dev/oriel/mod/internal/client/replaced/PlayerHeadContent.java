package dev.oriel.mod.internal.client.replaced;


import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import dev.oriel.engine.dom.Element;
import dev.oriel.mod.internal.client.render.McCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * {@code <player-head name="..." uuid="...">}: a player's face (the 8×8 face plus the hat layer) from their skin,
 * filling the box ({@code object-fit: contain} makes it a square placed by {@code object-position}), multiplied by
 * {@code -mc-tint}. Without attributes it shows the local player. Unknown profiles resolve in the background and show
 * the default skin until then.
 *
 * <p>26.x asked {@code Minecraft.playerSkinRenderCache()} for a {@code PlayerSkin}; 1.20.1 has no such cache, so this
 * goes through {@link net.minecraft.client.resources.SkinManager} the way vanilla's skull renderer does: registered
 * skins answer immediately, unknown ones come back from the skin service later and {@link #skin()} falls back to
 * {@link DefaultPlayerSkin} until then.
 */
public final class PlayerHeadContent extends McReplaced {
    private static final float PX = 1 / 64f;
    /** Profiles the skin service was already asked about, so a page repainting or a new element asks once. */
    private static final Set<GameProfile> asked = new HashSet<>();

    private GameProfile profile;

    public PlayerHeadContent(Element element) {
        super(element);
        this.profile = profile();
    }

    @Override
    public float intrinsicWidth() {
        return 16;
    }

    @Override
    public float intrinsicHeight() {
        return 16;
    }

    /**
     * Whether the skin is still being looked up, so automation waits for the real face. 1.20.1 resolves skins
     * asynchronously inside the skin manager with no future to ask, so this reports "done" and the page settles on
     * whatever {@link #skin()} returns; a resource reload or the next frame picks up the resolved texture.
     */
    @Override
    public boolean loading() {
        return false;
    }

    @Override
    public void attributeChanged(String name) {
        if (name.equals("name") || name.equals("uuid")) profile = profile();
    }

    @Override
    protected void draw(McCanvas canvas, float x, float y, float width, float height) {
        ResourceLocation skin = skin();
        int tint = element.computedStyle().tint;
        canvas.blit(skin, x, y, width, height, 8 * PX, 8 * PX, 16 * PX, 16 * PX, tint, false);
        canvas.blit(skin, x, y, width, height, 40 * PX, 8 * PX, 48 * PX, 16 * PX, tint, false);
    }

    /** The skin texture for {@link #profile}: the one the skin manager knows, or the default skin until it does. */
    private ResourceLocation skin() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.getSkinManager().getInsecureSkinInformation(profile).containsKey(MinecraftProfileTexture.Type.SKIN)) {
            // Not in the profile's own textures (a bare name, or a uuid we made up): ask the session service. The
            // callback only fills the manager's cache, so the next frame sees the texture; until then the default
            // skin below shows.
            if (!asked.contains(profile)) {
                if (asked.size() > 256) asked.clear();
                asked.add(profile);
                mc.getSkinManager().registerSkins(profile, (type, location, texture) -> {}, false);
            }
        }
        return mc.getSkinManager().getInsecureSkinLocation(profile);
    }

    private GameProfile profile() {
        String uuid = element.getAttribute("uuid");
        if (uuid != null) {
            try {
                return new GameProfile(UUID.fromString(uuid.strip()), "");
            } catch (IllegalArgumentException ignored) {
                // Fall back to the name, then the local player.
            }
        }
        String name = element.getAttribute("name");
        if (name != null && !name.isBlank()) return new GameProfile(null, name.strip());
        return Minecraft.getInstance().getUser().getGameProfile();
    }
}

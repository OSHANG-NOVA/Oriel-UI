package dev.oriel.mod.internal.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.oriel.kit.OrielKit;
import dev.oriel.mod.client.OrielScreens;
import dev.oriel.mod.internal.Constants;
import dev.oriel.mod.internal.platform.Services;
import net.minecraft.client.KeyMapping;

/**
 * Oriel's key bindings, listed under Options > Controls > Oriel UI. The loaders register {@link #HOME}; {@link #tick()} acts on it.
 * The home key is bound to F8 in a development run and unbound for players (a library should not take a key in a modpack by
 * itself); either way it can be set in the controls screen.
 */
public final class OrielKeys {
    /** Opens Oriel's home page (the gallery; its Demos button leads to the finished showcase pages). */
    // 1.20.1 has no KeyMapping.Category (26.x only); the trailing string is the translation key of the
    // controls-screen heading instead, which is what vanilla 1.20.1 key bindings use.
    public static final KeyMapping HOME = new KeyMapping("key.oriel.home", InputConstants.Type.KEYSYM,
            Services.PLATFORM.isDevelopmentEnvironment() ? InputConstants.KEY_F8 : InputConstants.UNKNOWN.getValue(),
            "key.categories.oriel");

    private OrielKeys() {}

    /** Once a client tick: opens the home page for each press (the game only delivers the key while no screen is open). */
    public static void tick() {
        while (HOME.consumeClick()) {
            OrielScreens.open(dev.oriel.kit.ReferenceContent.home());
        }
    }
}

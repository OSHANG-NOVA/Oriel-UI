package dev.oriel.mod.internal.client;

import dev.oriel.mod.client.DocumentDriver;
import dev.oriel.mod.client.OrielHud;
import dev.oriel.mod.client.OrielScreens;

import dev.oriel.kit.GameRegistry;
import dev.oriel.kit.OrielKit;
import dev.oriel.mod.internal.Constants;
import dev.oriel.mod.internal.client.render.FolderTextures;
import dev.oriel.mod.internal.client.render.McFontMetrics;
import dev.oriel.mod.internal.client.render.McGui;
import dev.oriel.mod.internal.client.render.McImages;
import dev.oriel.mod.internal.client.showcase.Mobdex;
import dev.oriel.mod.internal.net.ClosePayload;
import dev.oriel.mod.internal.net.DataPayload;
import dev.oriel.mod.internal.net.OpenPayload;
import dev.oriel.mod.internal.net.PatchPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Client entry points. Called from the Forge client setup; handlers run on the client thread.
 *
 * <p>1.20.1 client-only port: there is no loader network channel, no container menu and no server-side demo, so the
 * payload plumbing of the 26.x build is gone. {@link #handle} stays, because a pack may still drive a page through it
 * on a single-player or LAN session, and {@link ServerPages} still enforces the client's own policy and rate limits
 * before anything is shown.</p>
 */
public final class OrielClient {
    public static final ResourceLocation DEMO_HUD = Constants.id("demo_hud");
    public static final ResourceLocation DEMO_TOAST = Constants.id("demo_toast");

    private OrielClient() {}

    /** Called once, from the Forge client setup, after {@code OrielCommon.init()}. */
    public static void init() {
        OrielResources.init();
        if (dev.oriel.kit.ReferenceContent.available()) {
            OrielHud.register(DEMO_HUD, "oriel:oriel/demo/hud.html");
            OrielHud.register(DEMO_TOAST, "oriel:oriel/demo/toast.html", OrielHud.Input.WHEN_CHAT_OPEN);
            OrielScreens.onPageLoad(Mobdex.URL, Mobdex::load);
        }
        OrielKit.register();
        if (DevFlags.RELOAD_AVAILABLE) DevReload.init();
        // Pickers list every registered item (vanilla and modded) by the name the game shows in its language.
        GameRegistry.provide(() -> {
            List<GameRegistry.Entry> all = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) continue;
                String name = new ItemStack(item).getHoverName().getString();
                all.add(new GameRegistry.Entry(BuiltInRegistries.ITEM.getKey(item).toString(), name, item instanceof BlockItem));
            }
            return all;
        });
        // And every entity type that can be summoned, for the editor's 3D entity.
        GameRegistry.provideEntities(() -> {
            List<GameRegistry.Entry> all = new ArrayList<>();
            for (net.minecraft.world.entity.EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                if (!type.canSummon() || key.toString().equals("minecraft:player")) continue;
                all.add(new GameRegistry.Entry(key.toString(), type.getDescription().getString(), false));
            }
            return all;
        });
        // The template centre's favourites, recently viewed and saved copies (config/oriel/).
        MyPages.init();
        if (dev.oriel.kit.ReferenceContent.available()) TemplateLibrary.init();
        ModSettings.init();
    }

    /**
     * Handles a page a server would have sent; {@link ServerPages} decides what to show.
     *
     * <p>Both mods must agree on the wire format for this to ever fire, and the 1.20.1 build ships no channel, so in
     * practice only in-process callers (the automation hooks and {@code /oriel} commands) reach it.</p>
     */
    public static void handle(Object payload) {
        switch (payload) {
            case OpenPayload p -> ServerPages.open(p);
            case DataPayload p -> ServerPages.data(p);
            case PatchPayload p -> ServerPages.patch(p);
            case ClosePayload p -> ServerPages.close(p);
            default -> Constants.LOG.warn("Oriel: unhandled payload {}", payload);
        }
    }

    /**
     * Resources reloaded (F3+T, resource packs): fonts, images and shaders may have changed, so reload every open page
     * and try the shape shader again.
     */
    public static void onResourceReload() {
        McFontMetrics.INSTANCE.clearCache();
        McImages.clearCaches();
        FolderTextures.clear();
        OrielPages.checkPacks();
        McGui.reloadShapes();
        DocumentDriver.reloadAll();
    }

    /** Called at the end of every client tick. */
    public static void tick() {
        OrielKeys.tick();
        ServerPages.tick();
        if (dev.oriel.kit.ReferenceContent.available()) Mobdex.tick();
    }
}

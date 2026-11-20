package dev.oriel.mod.forge;

import dev.oriel.mod.internal.Constants;
import dev.oriel.mod.internal.client.DevAutopilot;
import dev.oriel.mod.internal.client.DevFlags;
import dev.oriel.mod.internal.client.DevPointer;
import dev.oriel.mod.internal.client.McClient;
import dev.oriel.mod.internal.client.OrielClient;
import dev.oriel.mod.internal.client.OrielClientCommands;
import dev.oriel.mod.internal.client.OrielKeys;
import dev.oriel.mod.internal.client.TourCursor;
import dev.oriel.mod.internal.client.replaced.ItemTooltips;
import dev.oriel.mod.internal.client.render.GuiGraphicsExtractor;
import dev.oriel.mod.internal.input.MouseButtonEvent;
import dev.oriel.mod.internal.platform.Services;
import dev.oriel.mod.client.OrielHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * The client half of the Forge build.
 *
 * <p>1.20.1 Forge has no {@code dist} attribute on {@code @Mod}, so this class is only loaded from
 * {@link OrielForge} after it has checked {@code FMLEnvironment.dist}, and its constructors are never touched on a
 * dedicated server. Everything here maps a Forge event onto one of the shared entry points.</p>
 *
 * <p>The 26.x client took six NeoForge events that 1.20.1 splits differently: HUD layers are
 * {@code RegisterGuiOverlaysEvent} over {@link VanillaGuiOverlay} names, the scene renderer and the layer renderers are
 * gone (no deferred GUI pass — see {@code McGui}), and the menu screens have no container to bind, so
 * {@code RegisterMenuScreensEvent} has nothing to register.</p>
 */
@Mod.EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT)
public final class OrielForgeClient {
    private OrielForgeClient() {}

    /** Called from {@link OrielForge} once FML has set the mod bus up. */
    static void init(IEventBus modBus) {
        OrielClient.init();

        modBus.addListener(OrielForgeClient::clientSetup);
        modBus.addListener(OrielForgeClient::registerKeyMappings);
        modBus.addListener(OrielForgeClient::registerReloadListeners);
        modBus.addListener(OrielForgeClient::registerOverlays);

        // The HUD's own overlay above the screen the player has open, and its pointer input before the screen's.
        var bus = net.minecraftforge.common.MinecraftForge.EVENT_BUS;
        bus.addListener((RegisterClientCommandsEvent e) -> e.getDispatcher().register(OrielClientCommands.create()));
        bus.addListener((ScreenEvent.Render.Post e) -> {
            if (e.getScreen() == McClient.screen()) {
                OrielHud.extractAboveScreen(new GuiGraphicsExtractor(e.getGuiGraphics()), e.getPartialTick());
            }
        });
        bus.addListener((ScreenEvent.MouseButtonPressed.Pre e) -> {
            if (OrielHud.mouseClicked(MouseButtonEvent.of(e.getMouseX(), e.getMouseY(), e.getButton(), 0))) {
                e.setCanceled(true);
            }
        });
        bus.addListener((ScreenEvent.MouseButtonReleased.Pre e) -> {
            if (OrielHud.mouseReleased(MouseButtonEvent.of(e.getMouseX(), e.getMouseY(), e.getButton(), 0))) {
                e.setCanceled(true);
            }
        });
        bus.addListener((ScreenEvent.MouseDragged.Pre e) -> {
            if (OrielHud.mouseDragged(MouseButtonEvent.of(e.getMouseX(), e.getMouseY(), e.getMouseButton(), 0))) {
                e.setCanceled(true);
            }
        });
        bus.addListener((ScreenEvent.MouseScrolled.Pre e) -> {
            // 1.20.1 has one scroll axis; 26.x reported two (horizontal and vertical) for trackpads that send both.
            if (OrielHud.mouseScrolled(e.getMouseX(), e.getMouseY(), 0D, e.getScrollDelta())) e.setCanceled(true);
        });
        // ClientTickEvent has no Post in 1.20.1: it fires once per tick with a Phase, so the end is asked for by name.
        bus.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) OrielClient.tick();
        });
        if (DevFlags.AUTOPILOT) {
            bus.addListener((TickEvent.ClientTickEvent e) -> {
                if (e.phase == TickEvent.Phase.END) DevAutopilot.tick(Minecraft.getInstance());
            });
            bus.addListener((ScreenEvent.Init.Pre e) -> DevPointer.beforeScreenChange());
            bus.addListener((ScreenEvent.Closing e) -> DevPointer.beforeScreenChange());
        }
        if (DevFlags.TOUR) {
            bus.addListener(EventPriority.LOWEST, (ScreenEvent.Render.Post e) -> {
                if (e.getScreen() == McClient.screen()) TourCursor.extractAboveScreen(new GuiGraphicsExtractor(e.getGuiGraphics()));
            });
        }
    }

    /**
     * Runs once FML has finished the client setup, after the resource manager exists (the pages load through it).
     */
    private static void clientSetup(FMLClientSetupEvent event) {
        Constants.LOG.info("Oriel UI on Forge {} (client, {} build)",
                Services.PLATFORM.modVersion("forge"),
                dev.oriel.kit.ReferenceContent.available() ? "full" : "slim");
        // <item tooltip>: pass the stack on to Forge's tooltip events the way vanilla's item tooltips do.
        ItemTooltips.install((g, font, lines, stack, x, y) ->
                g.graphics().renderTooltip(font, lines, stack.getTooltipImage(), stack, x, y));
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OrielKeys.HOME);
    }

    private static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> OrielClient.onResourceReload());
    }

    private static void registerOverlays(RegisterGuiOverlaysEvent event) {
        // 1.20.1 names the vanilla layer to sit above; 26.x had an ordering by layer object instead. TITLE_TEXT is the
        // layer that draws the "title" and "subtitle" — the spot the 26.x build registered above too.
        event.registerAbove(VanillaGuiOverlay.TITLE_TEXT.id(), Constants.MOD_ID + "_hud", (gui, graphics, partialTick, width, height) -> {
            GuiGraphicsExtractor g = new GuiGraphicsExtractor(graphics);
            OrielHud.extract(g, dev.oriel.mod.internal.client.render.DeltaTracker.current());
        });
    }
}

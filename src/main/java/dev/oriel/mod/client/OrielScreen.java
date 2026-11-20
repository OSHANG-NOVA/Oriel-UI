package dev.oriel.mod.client;

import dev.oriel.mod.internal.client.PageNarrator;

import dev.oriel.mod.internal.client.render.GuiGraphicsExtractor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import dev.oriel.mod.internal.input.CharacterEvent;
import dev.oriel.mod.internal.input.KeyEvent;
import dev.oriel.mod.internal.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * A screen showing a Oriel page. The page draws its own background over the dimmed world (or the title panorama
 * when no world is loaded); Escape closes the screen unless the page handles it. Open one with {@link OrielScreens}.
 *
 * <p>Its title is the page's {@code <title>} (live, as scripts change {@code document.title}), else "Oriel". The
 * narrator reads it, then the page's focused or hovered element as vanilla reads its widgets ({@link PageNarrator}).
 *
 * <p>1.20.1 differences from the 26.x source this was ported from: the GUI pass is immediate, so painting happens in
 * {@link #render} rather than in a deferred {@code extractRenderState}; input arrives as loose primitives rather than
 * as event records; and there is no IME pre-edit callback ({@code preeditUpdated}) and no {@code isInGameUi()}. Oriel's
 * own text fields compose IME input in the engine, so nothing is lost.
 */
public class OrielScreen extends Screen implements DocumentDriver.Owner {
    private final DocumentDriver driver;
    private boolean pauses;

    /**
     * @param html    inline HTML, or null to load {@code url}
     * @param session the server session, or -1 for a client-side page
     */
    @org.jetbrains.annotations.ApiStatus.Internal
    // DocumentDriver retains the owner without invoking callbacks until init/resize after construction.
    @SuppressWarnings("this-escape")
    public OrielScreen(String url, @Nullable String html, int session) {
        super(Component.translatable("oriel.screen"));
        this.driver = new DocumentDriver(this, url, html, session);
    }

    /** The page's driver: push data, listen for messages ({@code driver().onMessage(channel, handler)}). */
    public DocumentDriver driver() {
        return driver;
    }

    @Override
    protected void init() {
        driver.resize(width, height);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No super.render: the page paints its own background, and vanilla's would draw over the world first.
        driver.extract(new GuiGraphicsExtractor(graphics), mouseX, mouseY);
    }

    /**
     * Whether this screen pauses a singleplayer world while it is open, like a vanilla book. Off by default, since
     * pages that talk to the server (shops, conversations) need it running. Minecraft asks every tick, so this can
     * change while the screen is open.
     */
    public OrielScreen pauses(boolean pauses) {
        this.pauses = pauses;
        return this;
    }

    /** The page's {@code <title>}, else the screen's own ("Oriel"). */
    @Override
    public Component getTitle() {
        return driver.narrator().title(super.getTitle());
    }

    /** The page's focused or hovered element, phrased as vanilla's widgets; with none, what vanilla says. */
    @Override
    protected void updateNarratedWidget(NarrationElementOutput output) {
        if (!driver.narrator().addNarratedElement(output)) super.updateNarratedWidget(output);
    }

    @Override
    public String narration() {
        return PageNarrator.collect(this::updateNarrationState);
    }

    @Override
    public boolean isPauseScreen() {
        return pauses;
    }

    @Override
    public void removed() {
        driver.close();
    }

    @Override
    public void closeDocument() {
        onClose();
    }

    @Override
    public Screen screen() {
        return this;
    }

    // ---- Input: the page first, then vanilla (Escape closes) ----

    @Override
    public void mouseMoved(double x, double y) {
        driver.mouseMoved(x, y);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        // 26.x delivered horizontal and vertical scroll separately; 1.20.1 has only the vertical wheel, which is the
        // axis every page actually scrolls on, so it takes the vertical slot and horizontal stays zero.
        return driver.mouseScrolled(x, y, 0D, delta);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return driver.mouseClicked(MouseButtonEvent.of(x, y, button, 0));
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return driver.mouseReleased(MouseButtonEvent.of(x, y, button, 0));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return driver.keyPressed(KeyEvent.of(keyCode, scanCode, modifiers)) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return driver.keyReleased(KeyEvent.of(keyCode, scanCode, modifiers));
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return driver.charTyped(CharacterEvent.of(codePoint, modifiers));
    }
}

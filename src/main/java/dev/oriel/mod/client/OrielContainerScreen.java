package dev.oriel.mod.client;

import dev.oriel.mod.internal.client.PageNarrator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.oriel.engine.dom.Document;
import dev.oriel.engine.dom.Element;
import dev.oriel.engine.paint.Coordinates;
import dev.oriel.mod.internal.Constants;
import dev.oriel.mod.internal.client.render.GuiGraphicsExtractor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import dev.oriel.mod.internal.input.CharacterEvent;
import dev.oriel.mod.internal.input.KeyEvent;
import dev.oriel.mod.internal.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;

/**
 * A container screen whose layout is a Oriel page. Each {@code <slot index="n">} element places menu slot
 * {@code n}: every frame the slot's 16×16 item area goes where the element's content box is painted, so slots follow
 * scrolling, transforms and animations. Slots that are not painted (no element, hidden, scrolled out of their clip)
 * move off-screen. Vanilla still draws slot items, highlights, tooltips and the carried stack, on top of the page,
 * and handles slot clicks, drags and shift-clicks; everything else goes to the page.
 *
 * <p>The screen's GUI area ({@code leftPos}, {@code topPos}, {@code imageWidth}, {@code imageHeight}, which JEI, REI
 * and other mods read to lay out beside it) is the page's content, updated every frame: the elements marked
 * {@code data-oriel-bounds}, or {@code body}'s in-flow children ({@link Coordinates#contentBounds}). Slot positions
 * are relative to it, as in vanilla.
 *
 * <p>Register one for a menu type with {@link OrielScreens#registerContainer}. The page's {@code oriel.data} is
 * {@code {title, inventory, slots: [{id, count, name}, ...]}}, plus the fields of the registration's data function
 * (the mod's own: an entity id, a tier), updated when the menu's contents or those fields change, so pages can show
 * totals, filter slots by name, or show the mod's state.
 *
 * <p>The narrator reads it as a {@link OrielScreen}: the page's {@code <title>} if it has one (else the menu's title),
 * then its focused or hovered element; a hovered {@code <slot>} reads as its item ("Item: Coal").
 *
 * <p>1.20.1 differences from the 26.x source this was ported from: painting and input are immediate-mode, so
 * {@code extractRenderState} becomes {@link #render} with its {@link #renderBg} abstract partner, {@code extractLabels}
 * becomes {@link #renderLabels}, {@code extractTooltip} becomes {@link #renderTooltip}, and {@code hasClickedOutside}
 * grew a fifth argument (the menu's slot count). Slot.x / Slot.y are {@code public final} in 1.20.1 and are widened by
 * {@code META-INF/accesstransformer.cfg}.
 */
public class OrielContainerScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> implements DocumentDriver.Owner {
    private static final int OFF_SCREEN = -10_000;

    private final DocumentDriver driver;
    private final DocumentDriver.SlotSink slotSink = this::placeSlot;
    /** The mod's fields for {@code oriel.data}, or null. */
    private final @Nullable Function<? super M, ? extends JsonObject> extra;
    /** Copies of the stacks the page was last sent, so data goes out only when the menu's contents change. */
    private @Nullable ItemStack[] sent = new ItemStack[0];
    /** A copy of the extra fields the page was last sent. */
    private @Nullable JsonObject sentExtra;
    private boolean extraFailed;
    /** Whether the current press went to the page (its release goes there too) rather than to vanilla. */
    private boolean pagePress;

    public OrielContainerScreen(M menu, Inventory inventory, Component title, String url) {
        this(menu, inventory, title, url, null);
    }

    /**
     * @param extra fields to add to {@code oriel.data}, from the menu (called on open and every client tick; the
     *              page gets new data when its result changes), or null
     */
    // DocumentDriver only retains the owner; callbacks start at init/resize after construction, never in its constructor.
    @SuppressWarnings("this-escape")
    public OrielContainerScreen(M menu, Inventory inventory, Component title, String url,
                                 @Nullable Function<? super M, ? extends JsonObject> extra) {
        super(menu, inventory, title);
        this.driver = new DocumentDriver(this, url, null, -1);
        this.extra = extra;
        pushSlotData();
        for (Slot slot : menu.slots) hide(slot); // until the page paints them
    }

    public DocumentDriver driver() {
        return driver;
    }

    @Override
    protected void init() {
        super.init();
        setGuiArea(0, 0, width, height); // the whole screen until the page has laid out
        driver.resize(width, height);
    }

    /** The GUI area is where the page's content is, as laid out for this frame (slots are placed relative to it). */
    @Override
    public void beforePaint(Document document) {
        float[] r = Coordinates.contentBounds(document);
        if (r == null) {
            setGuiArea(0, 0, width, height);
            return;
        }
        int x0 = (int) Math.floor(r[0]), y0 = (int) Math.floor(r[1]);
        setGuiArea(x0, y0, (int) Math.ceil(r[0] + r[2]) - x0, (int) Math.ceil(r[1] + r[3]) - y0);
    }

    private void setGuiArea(int left, int top, int width, int height) {
        leftPos = left;
        topPos = top;
        imageWidth = width;
        imageHeight = height;
    }

    /**
     * The page paints as the container's background, and vanilla draws slots, labels, the carried stack and tooltips
     * on top of it, as it always does. {@code renderBg} is where the page goes because it is the only hook vanilla
     * calls before widgets and slots, so the page ends up underneath them.
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (Slot slot : menu.slots) hide(slot); // painting puts back the slots it draws
        super.render(graphics, mouseX, mouseY, partialTick); // -> renderBg (the page) then slots, labels, tooltips
    }

    /** The page, painted where vanilla draws a container's background. */
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        driver.extractPage(new GuiGraphicsExtractor(graphics), mouseX, mouseY);
    }

    /** A hovered slot's item tooltip first; the page's tooltip (an {@code <item tooltip>}'s or a title) where there is none. */
    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        if (hoveredSlot == null || !hoveredSlot.hasItem()) driver.extractTooltip(new GuiGraphicsExtractor(graphics), mouseX, mouseY);
    }

    @Override
    public DocumentDriver.SlotSink slots() {
        return slotSink;
    }

    /** Vanilla's slot positions are relative to the GUI area. */
    private void placeSlot(int index, int x, int y) {
        if (index >= menu.slots.size()) return;
        Slot slot = menu.slots.get(index);
        slot.x = x - leftPos;
        slot.y = y - topPos;
    }

    @Override
    protected void containerTick() {
        pushSlotData();
    }

    /** Sends the title, slot contents and extra fields to the page when they changed. */
    private void pushSlotData() {
        JsonObject fields = extraFields();
        boolean changed = !Objects.equals(fields, sentExtra);
        sentExtra = fields;
        if (sent.length != menu.slots.size()) {
            sent = new ItemStack[menu.slots.size()];
            changed = true;
        }
        for (int i = 0; i < sent.length; i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (sent[i] == null || !ItemStack.matches(sent[i], stack)) {
                sent[i] = stack.copy();
                changed = true;
            }
        }
        if (!changed) return;
        JsonArray slots = new JsonArray();
        for (ItemStack stack : sent) {
            JsonObject o = new JsonObject();
            o.addProperty("id", stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            o.addProperty("count", stack.getCount());
            o.addProperty("name", stack.isEmpty() ? "" : stack.getHoverName().getString());
            slots.add(o);
        }
        JsonObject data = new JsonObject();
        data.addProperty("title", title.getString());
        data.addProperty("inventory", playerInventoryTitle.getString());
        data.add("slots", slots);
        if (fields != null) fields.entrySet().forEach(e -> data.add(e.getKey(), e.getValue()));
        driver.pushData(data.toString());
    }

    /** A copy of the mod's fields now (it may reuse and change its object), or null; a failing function is logged once. */
    private @Nullable JsonObject extraFields() {
        if (extra == null) return null;
        try {
            JsonObject fields = extra.apply(menu);
            return fields == null ? null : fields.deepCopy();
        } catch (RuntimeException e) {
            if (!extraFailed) Constants.LOG.error("Oriel: the data function of {} failed", title.getString(), e);
            extraFailed = true;
            return null;
        }
    }

    /** The page's {@code <title>}, else the menu's title. */
    @Override
    public Component getTitle() {
        return driver.narrator().title(super.getTitle());
    }

    /** The page's focused or hovered element (a slot reads as its item); with none, what vanilla says. */
    @Override
    protected void updateNarratedWidget(NarrationElementOutput output) {
        if (!driver.narrator().addNarratedElement(output)) super.updateNarratedWidget(output);
    }

    @Override
    public String narration() {
        return PageNarrator.collect(this::updateNarrationState);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // The page draws its own titles.
    }

    @Override
    public void removed() {
        super.removed();
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

    /** Dropping the carried stack happens outside the page's content: where only html or body is under the pointer. */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int slotCount) {
        Element hit = driver.elementAt(mouseX, mouseY);
        return hit != null && DocumentDriver.isBackground(hit);
    }

    private static void hide(Slot slot) {
        slot.x = OFF_SCREEN;
        slot.y = OFF_SCREEN;
    }

    private @Nullable Slot slotAt(double x, double y) {
        for (Slot slot : menu.slots) {
            if (slot.isActive() && isHovering(slot.x, slot.y, 16, 16, x, y)) return slot;
        }
        return null;
    }

    // ---- Input: slots go to vanilla, everything else to the page ----

    @Override
    public void mouseMoved(double x, double y) {
        driver.mouseMoved(x, y);
    }

    /**
     * Presses on slots go to vanilla, and so do presses on the page's background while a stack is carried: vanilla drops
     * it ({@link #hasClickedOutside}), as outside a vanilla container.
     */
    @Override
    public boolean mouseClicked(double x, double y, int button) {
        MouseButtonEvent event = MouseButtonEvent.of(x, y, button, 0);
        boolean vanilla = slotAt(x, y) != null
                || !menu.getCarried().isEmpty() && driver.contentAt(x, y) == null;
        pagePress = !vanilla && driver.mouseClicked(event);
        return pagePress || super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (pagePress) {
            pagePress = false;
            return driver.mouseReleased(MouseButtonEvent.of(x, y, button, 0));
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        return (slotAt(x, y) == null && driver.mouseScrolled(x, y, 0D, delta)) || super.mouseScrolled(x, y, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return driver.keyPressed(KeyEvent.of(keyCode, scanCode, modifiers)) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return driver.keyReleased(KeyEvent.of(keyCode, scanCode, modifiers)) || super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return driver.charTyped(CharacterEvent.of(codePoint, modifiers));
    }
}

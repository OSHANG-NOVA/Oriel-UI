package dev.oriel.mod.internal.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Minecraft's GUI drawing entry point, in the shape Oriel's engine expects.
 *
 * <p>26.x's {@code GuiGraphicsExtractor} recorded render states that the GUI renderer drew at the end of the frame;
 * it had a pose stack of {@code Matrix3x2f}, a {@code scissorStack}, a {@code guiRenderState}, {@code nextStratum()}
 * for ordering and {@code extractDeferredElements} for tooltips. 1.20.1's {@link GuiGraphics} draws immediately, with
 * a {@code Matrix4f} pose stack and its own scissor stack, and its tooltips are flushed at once.
 *
 * <p>This wraps a {@link GuiGraphics} and offers the 26.x names the shared sources use, so those sources stay as they
 * were and only this file knows the difference. Everything here is a thin forward, except where 1.20.1 simply has no
 * equivalent: the ordering calls flush, and the deferred-element call is a no-op because nothing is deferred.
 */
public final class GuiGraphicsExtractor {
    private final GuiGraphics g;

    public GuiGraphicsExtractor(GuiGraphics g) {
        this.g = g;
    }

    /** The wrapped 1.20.1 graphics, for the few places that need one (a real vanilla screen, say). */
    public GuiGraphics graphics() {
        return g;
    }

    public PoseStack pose() {
        return g.pose();
    }

    public int guiWidth() {
        return g.guiWidth();
    }

    public int guiHeight() {
        return g.guiHeight();
    }

    /**
     * Starts a new drawing order. 26.x pushed a new stratum so later elements drew above earlier ones; 1.20.1 draws in
     * submission order, so this flushes what is pending, which puts it below whatever is drawn next.
     */
    public void nextStratum() {
        g.flush();
    }

    public void fill(int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y1, color);
    }

    public void enableScissor(int x0, int y0, int x1, int y1) {
        g.enableScissor(x0, y0, x1, y1);
    }

    public void disableScissor() {
        g.disableScissor();
    }

    public void text(Font font, net.minecraft.util.FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        g.drawString(font, text, x, y, color, shadow);
    }

    public void text(Font font, Component text, int x, int y, int color, boolean shadow) {
        g.drawString(font, text, x, y, color, shadow);
    }

    public void text(Font font, String text, int x, int y, int color, boolean shadow) {
        g.drawString(font, text, x, y, color, shadow);
    }

    /**
     * A GUI sprite stretched to ({@code width} × {@code height}). 1.20.1 has no {@code blitSprite}: a sprite is looked
     * up in the block atlas and its atlas UVs are handed to {@link GuiGraphics#blit(int, int, int, int, int, TextureAtlasSprite)}.
     */
    public void blitSprite(net.minecraft.resources.ResourceLocation sprite, int x, int y, int width, int height) {
        var atlas = Minecraft.getInstance().getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);
        if (atlas == null) return;
        g.blit(x, y, 0, width, height, atlas.apply(sprite));
    }

    public void item(ItemStack stack, int x, int y) {
        g.renderItem(stack, x, y);
    }

    public void itemDecorations(Font font, ItemStack stack, int x, int y) {
        g.renderItemDecorations(font, stack, x, y);
    }

    /** 1.20.1 has no {@code outline}: it draws the four edges with {@link #fill}, one pixel each. */
    public void outline(int x, int y, int width, int height, int color) {
        fill(x, y, x + width, y + 1, color);
        fill(x, y + height - 1, x + width, y + height, color);
        fill(x, y + 1, x + 1, y + height - 1, color);
        fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    public void setTooltipForNextFrame(Font font, List<net.minecraft.util.FormattedCharSequence> lines, int x, int y) {
        g.renderTooltip(font, lines, x, y);
    }

    public void setComponentTooltipForNextFrame(Font font, List<Component> lines, int x, int y) {
        g.renderComponentTooltip(font, lines, x, y);
    }

    public void setTooltipForNextFrame(Font font, List<Component> lines, ItemStack stack, int x, int y) {
        g.renderTooltip(font, lines, stack.getTooltipImage(), stack, x, y);
    }

    /**
     * Draws what was deferred to the end of the frame. Nothing is deferred on 1.20.1 — tooltips are drawn where they
     * are asked for — so this only makes sure what is pending has gone out.
     */
    public void extractDeferredElements(int mouseX, int mouseY, float partialTick) {
        g.flush();
    }

    /**
     * Asks for the pointer to be drawn as {@code cursor}. 1.20.1's cursor is resolved per screen type by vanilla each
     * frame, with no per-frame override, so this does nothing.
     */
    public void requestCursor(@Nullable Object cursor) {}

    /** The frame's graphics, for the static helpers that need one. */
    static GuiGraphics of(GuiGraphicsExtractor extractor) {
        return extractor.g;
    }

    /** A wrapper for a graphics object, or null when there is none. */
    static @Nullable GuiGraphicsExtractor of(@Nullable GuiGraphics graphics) {
        return graphics == null ? null : new GuiGraphicsExtractor(graphics);
    }

    /** The client, for the helpers that ask it something. */
    static Minecraft minecraft() {
        return Minecraft.getInstance();
    }
}

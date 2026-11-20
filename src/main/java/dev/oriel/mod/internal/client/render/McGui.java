package dev.oriel.mod.internal.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.oriel.engine.paint.ScissorStack;
import dev.oriel.engine.paint.SmoothShape;
import dev.oriel.engine.style.Cursor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Minecraft's GUI drawing, the part of it that differs between Minecraft versions; this is the one for 1.20.1, where a
 * {@link GuiGraphics} is a drawing pass rather than a recorder: every primitive is emitted at once, in submission
 * order, into the GUI shader.
 *
 * <p>26.x could hand a {@code GuiGraphicsExtractor} a render state and let the GUI renderer sort it out at the end of
 * the frame; 1.20.1 has no equivalent, so the ordering the canvas asks for is the ordering the client draws in.
 * Geometries go out immediately, and {@link #nextLayer} flushes so what comes after sits above what came before.
 *
 * <p>An instance is {@link McCanvas}'s backend for one frame: McCanvas keeps the transform, opacity and clip stacks and
 * computes bounds, and hands each primitive here already transformed and clipped. The static methods draw the few
 * things Oriel puts on screen without the engine (the error panel, the typing notice) and tooltips.
 */
public final class McGui {
    private final GuiGraphics g;
    /**
     * Where this GUI's (0, 0) is on screen, in GUI px: 0 for the frame's, a layer's origin for one of
     * {@link McCanvas}'s groups (its offscreen target covers only the group). The canvas works in screen coordinates
     * throughout; everything handed to vanilla is moved by this, and the scissor handed back is moved back.
     */
    private final int ox, oy;
    private final Matrix3x2f shifted = new Matrix3x2f();
    /** The scissor currently pushed, kept so {@link #scissor()} can answer without asking vanilla. */
    private @Nullable ScreenRectangle clip;

    public McGui(GuiGraphics g) {
        this(g, 0, 0);
    }

    public McGui(GuiGraphics g, int ox, int oy) {
        this.g = g;
        this.ox = ox;
        this.oy = oy;
    }

    /** {@code pose} in this GUI's coordinates (a copy when moved: nothing keeps the pose afterwards). */
    private Matrix3x2fc at(Matrix3x2fc pose) {
        return ox == 0 && oy == 0 ? pose : new Matrix3x2f().translation(-ox, -oy).mul(pose);
    }

    /** {@code pose} in this GUI's coordinates, for vanilla's pose stack (which copies it). */
    private Matrix3x2fc atStack(Matrix3x2fc pose) {
        return ox == 0 && oy == 0 ? pose : shifted.translation(-ox, -oy).mul(pose);
    }

    private @Nullable ScreenRectangle at(@Nullable ScreenRectangle r) {
        return r == null || ox == 0 && oy == 0 ? r : new ScreenRectangle(r.left() - ox, r.top() - oy, r.width(), r.height());
    }

    // ---- For McCanvas ----

    /**
     * What the GUI renderer can draw this frame: the framebuffer at the GUI scale it renders with (its scissors are
     * clamped to the framebuffer, and one clamped to nothing is a crash), within the screen and any scissor already
     * pushed. The framebuffer is not always the GUI size: {@code Window.setWindowed} resizes it at once, while the GUI
     * scale and size change only when the resize event arrives, so for a frame the GUI can reach past the framebuffer.
     */
    public ScissorStack drawableArea() {
        com.mojang.blaze3d.platform.Window window = Minecraft.getInstance().getWindow();
        int scale = (int) Math.max(1D, window.getGuiScale()), frameWidth = window.getWidth(),
                frameHeight = window.getHeight();
        int width = Math.min(g.guiWidth(), Math.ceilDiv(frameWidth, scale));
        int height = Math.min(g.guiHeight(), Math.ceilDiv(frameHeight, scale));
        ScreenRectangle outer = clip;
        return outer == null ? new ScissorStack(0, 0, width, height) : new ScissorStack(Math.max(0, outer.left()),
                Math.max(0, outer.top()), Math.min(width, outer.right()), Math.min(height, outer.bottom()));
    }

    /** Saves vanilla's pose for the canvas, which sets it before each call; returns the transform it had. */
    public Matrix3x2f begin() {
        g.pose().pushPose();
        Matrix4f pose = g.pose().last().pose();
        return new Matrix3x2f(pose.m00(), pose.m01(), pose.m10(), pose.m11(), pose.m30(), pose.m31());
    }

    /** Puts vanilla's pose back. */
    public void end() {
        g.pose().popPose();
    }

    /** Pushes a scissor, in GUI px on screen (the transform is not applied). */
    public void pushScissor(int left, int top, int right, int bottom) {
        clip = new ScreenRectangle(left - ox, top - oy, right - left, bottom - top);
        g.enableScissor(left - ox, top - oy, right - ox, bottom - oy);
    }

    public void popScissor() {
        clip = null;
        g.disableScissor();
    }

    /** The scissor elements are clipped to now, or null. */
    public @Nullable ScreenRectangle scissor() {
        ScreenRectangle r = clip;
        return r == null || ox == 0 && oy == 0 ? r : new ScreenRectangle(r.left() + ox, r.top() + oy, r.width(), r.height());
    }

    /** Whether text in this colour shows (vanilla skips text with alpha 0). */
    public static boolean textVisible(int argb) {
        return argb >>> 24 != 0;
    }

    /**
     * One rectangle in one colour: the vertices go (x0,y0) (x0,y1) (x1,y1) (x1,y0), wound as vanilla's when x0 &lt; x1
     * and y0 &lt; y1 (the caller swaps x0 and x1 under a mirroring transform; the GUI shader culls back faces).
     *
     * @param bounds the transformed bounding box clipped to the scissor
     */
    public void fill(Matrix3x2fc pose, float x0, float y0, float x1, float y1, int color, ScreenRectangle bounds,
                     @Nullable ScreenRectangle scissor) {
        Matrix3x2fc m = at(pose);
        BufferBuilder b = beginColored();
        addColor(b, m, x0, y0, color);
        addColor(b, m, x0, y1, color);
        addColor(b, m, x1, y1, color);
        addColor(b, m, x1, y0, color);
        BufferUploader.drawWithShader(b.end());
    }

    /** A texture region (normalised UVs) multiplied by {@code color}, wound as {@link #fill}. */
    public void blit(Matrix3x2fc pose, ResourceLocation texture, boolean smooth, float x0, float y0, float x1, float y1,
                     float u0, float v0, float u1, float v1, int color, ScreenRectangle bounds,
                     @Nullable ScreenRectangle scissor) {
        Matrix3x2fc m = at(pose);
        RenderSystem.setShaderTexture(0, texture);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        addTex(b, m, x0, y1, u0, v1, color);
        addTex(b, m, x0, y0, u0, v0, color);
        addTex(b, m, x1, y0, u1, v0, color);
        addTex(b, m, x1, y1, u1, v1, color);
        BufferUploader.drawWithShader(b.end());
    }

    /** Quads with a colour per vertex, 4 vertices each, already wound as vanilla's. */
    public void quads(Matrix3x2fc pose, float[] xy, int[] colors, ScreenRectangle bounds, @Nullable ScreenRectangle scissor) {
        Matrix3x2fc m = at(pose);
        BufferBuilder b = beginColored();
        for (int v = 0; v < colors.length; v++) {
            addColor(b, m, xy[v * 2], xy[v * 2 + 1], colors[v]);
        }
        BufferUploader.drawWithShader(b.end());
    }

    /** Whether {@link #shape} can draw: the shape shader is compiled. */
    public boolean shapesAvailable() {
        return false;
    }

    /** Resources reloaded: if the shape shader failed to compile, {@link #shapesAvailable} would try it again. */
    public static void reloadShapes() {
        Layers.reload();
    }

    /**
     * A rounded box, border or box-shadow drawn by the shape shader as one quad. The SDF pipeline a shape needs is
     * 26.x-only, so 1.20.1 draws the same quad as a plain rectangle in the same colour: rounded corners are lost, but
     * the content sits where it should. {@link #shapesAvailable} answers false, so the painter prefers its own
     * subdivided path wherever one exists.
     */
    public void shape(Matrix3x2fc pose, SmoothShape shape, float edge, short[] radii, int color, ScreenRectangle bounds,
                      @Nullable ScreenRectangle scissor) {
        fill(pose, shape.x0() - edge, shape.y0() - edge, shape.x1() + edge, shape.y1() + edge, color, bounds, scissor);
    }

    /** Text at (x, y) in {@code m}, scaled by {@code scale}. */
    public void text(Matrix3x2fc m, float x, float y, float scale, FormattedCharSequence text, int color, boolean shadow) {
        setPose(m, x, y, scale, scale);
        g.drawString(Minecraft.getInstance().font, text, 0, 0, color, shadow);
        g.pose().popPose();
    }

    /** A GUI sprite (nine-slice and tiling at its integer size {@code w × h}), at (x, y) in {@code m} scaled by (sx, sy). */
    public void sprite(Matrix3x2fc m, float x, float y, float sx, float sy, ResourceLocation sprite, int w, int h, int color) {
        setPose(m, x, y, sx, sy);
        // 1.20.1 has no blitSprite: look the sprite up in the block atlas and let blit take its UVs.
        var atlas = Minecraft.getInstance().getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);
        if (atlas != null) g.blit(0, 0, 0, w, h, atlas.apply(sprite));
        g.pose().popPose();
    }

    /** An item at (x, y) in {@code m}, scaled from its 16 px, with count and durability when asked. */
    public void item(Matrix3x2fc m, float x, float y, float scale, ItemStack stack, boolean decorations) {
        setPose(m, x, y, scale, scale);
        g.renderItem(stack, 0, 0);
        if (decorations) g.renderItemDecorations(Minecraft.getInstance().font, stack, 0, 0);
        g.pose().popPose();
    }

    /**
     * One of McCanvas's groups. 26.x blitted the group's offscreen target here; 1.20.1 has no offscreen GUI pass, so
     * groups never open and this is never called (see {@link Layers}).
     */
    public void layer(Object layer) {
        throw new UnsupportedOperationException("Oriel: offscreen layers are not available on 1.20.1");
    }

    /**
     * A 3D scene (an entity, a block or an item) drawn into the GUI box {@code (x0, y0)–(x1, y1)}, the picture's
     * origin in the middle of its bottom edge. 26.x handed a picture-in-picture state to the GUI renderer; 1.20.1
     * draws it now, into the same buffer source the rest of the page is going into, flushing first so the page's
     * quads stay behind it.
     */
    public void scene(Scene scene, int color, int x0, int y0, int x1, int y1, float scale, @Nullable ScreenRectangle scissor) {
        if (scale <= 0) return;
        int left = x0 - ox, top = y0 - oy, right = x1 - ox, bottom = y1 - oy;
        if (right <= left || bottom <= top) return;
        if (clip != null && (right <= clip.left() || bottom <= clip.top() || left >= clip.right() || top >= clip.bottom())) return;
        g.flush();
        PoseStack pose = g.pose();
        pose.pushPose();
        // The picture's frame: its bottom-middle at the box's bottom-middle, y up, a block per `scale` GUI px.
        pose.translate((left + right) / 2F, bottom, 0);
        pose.scale(scale, -scale, -scale);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        try {
            scene.submit(pose, g.bufferSource(), scale, top, bottom);
        } finally {
            g.flush();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            pose.popPose();
            com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
        }
    }

    /** {@code argb} with its colour channels multiplied by its alpha. */
    private static int premultiplied(int argb) {
        float a = (argb >>> 24) / 255f;
        return argb & 0xFF000000 | (int) ((argb >> 16 & 0xFF) * a) << 16 | (int) ((argb >> 8 & 0xFF) * a) << 8
                | (int) ((argb & 0xFF) * a);
    }

    // ---- Pose and vertex helpers ----

    /** Pushes the canvas pose onto vanilla's stack, moved into this GUI's frame and scaled. */
    private void setPose(Matrix3x2fc m, float x, float y, float sx, float sy) {
        PoseStack pose = g.pose();
        pose.pushPose();
        Matrix3x2fc at = atStack(m);
        pose.last().pose().set(at.m00(), at.m01(), 0, 0, at.m10(), at.m11(), 0, 0, 0, 0, 1, 0, at.m20() + x, at.m21() + y,
                0, 1);
        pose.scale(sx, sy, 1);
    }

    private static BufferBuilder beginColored() {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        return b;
    }

    private static void addColor(BufferBuilder b, Matrix3x2fc m, float x, float y, int c) {
        b.vertex(m.m00() * x + m.m10() * y + m.m20(), m.m01() * x + m.m11() * y + m.m21(), 0)
                .color((c >> 16 & 0xFF) / 255f, (c >> 8 & 0xFF) / 255f, (c & 0xFF) / 255f, (c >>> 24) / 255f).endVertex();
    }

    private static void addTex(BufferBuilder b, Matrix3x2fc m, float x, float y, float u, float v, int c) {
        b.vertex(m.m00() * x + m.m10() * y + m.m20(), m.m01() * x + m.m11() * y + m.m21(), 0)
                .color((c >> 16 & 0xFF) / 255f, (c >> 8 & 0xFF) / 255f, (c & 0xFF) / 255f, (c >>> 24) / 255f).uv(u, v)
                .endVertex();
    }

    // ---- Panels, tooltips, the cursor ----

    /** Vanilla tooltip layout in page pixels, submitted to the same frame with the page's paint transform. */
    public static GuiGraphicsExtractor tooltipContext(GuiGraphicsExtractor parent, float ratio) {
        // 1.20.1 has no way to re-scale an existing GuiGraphics: hand back one on the same buffer source whose pose is
        // the parent's; the caller's own transform already carries the page scale.
        GuiGraphics scaled = new GuiGraphics(Minecraft.getInstance(), parent.graphics().bufferSource());
        scaled.pose().last().pose().set(parent.graphics().pose().last().pose());
        GuiGraphicsExtractor wrapped = new GuiGraphicsExtractor(scaled);
        nextLayer(wrapped);
        return wrapped;
    }

    /** From here on, everything is drawn above what was drawn before. */
    public static void nextLayer(GuiGraphicsExtractor g) {
        g.graphics().flush();
        RenderSystem.enableBlend();
    }

    /** Moves what is drawn next by ({@code x}, {@code y}) GUI px, until {@link #popTranslation}. */
    public static void pushTranslation(GuiGraphicsExtractor g, float x, float y) {
        g.graphics().pose().pushPose();
        g.graphics().pose().translate(x, y, 0);
    }

    public static void popTranslation(GuiGraphicsExtractor g) {
        g.graphics().pose().popPose();
    }

    public static void fill(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
        g.graphics().fill(x0, y0, x1, y1, color);
    }

    public static void outline(GuiGraphicsExtractor g, int x, int y, int width, int height, int color) {
        fill(g, x, y, x + width, y + 1, color);
        fill(g, x, y + height - 1, x + width, y + height, color);
        fill(g, x, y + 1, x + 1, y + height - 1, color);
        fill(g, x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    public static void text(GuiGraphicsExtractor g, Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        g.graphics().drawString(font, text, x, y, color, shadow);
    }

    public static void text(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int color, boolean shadow) {
        g.graphics().drawString(font, text, x, y, color, shadow);
    }

    public static void text(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color, boolean shadow) {
        g.graphics().drawString(font, text, x, y, color, shadow);
    }

    /** A tooltip of already wrapped lines, drawn at once. */
    public static void tooltip(GuiGraphicsExtractor g, Font font, List<FormattedCharSequence> lines, int x, int y) {
        g.graphics().renderTooltip(font, lines, x, y);
    }

    /** A tooltip of lines that are not wrapped. */
    public static void componentTooltip(GuiGraphicsExtractor g, Font font, List<Component> lines, int x, int y) {
        g.graphics().renderComponentTooltip(font, lines, x, y);
    }

    /** An item tooltip of {@code lines} (the item's own and any after them), with the stack's tooltip image. */
    public static void itemTooltip(GuiGraphicsExtractor g, Font font, List<Component> lines, ItemStack stack, int x, int y) {
        g.graphics().renderTooltip(font, lines, stack.getTooltipImage(), stack, x, y);
    }

    /**
     * Draws the tooltips set for this frame now, for HUD overlays drawn after the screen's own pass has drawn its
     * tooltips. 1.20.1 draws tooltips at once, so there is nothing deferred: flush what is queued.
     */
    public static void drawTooltipsNow(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.graphics().flush();
    }

    /**
     * Shows the CSS cursor a page asks for while the pointer is over it; vanilla resets it every frame. Screens' pages
     * and HUD overlays both ask ({@code screen} tells them apart).
     *
     * <p>1.20.1 resolves the cursor from the screen type each frame and offers no per-frame override, so a page cannot
     * ask for a shape of its own.
     */
    public static void cursor(GuiGraphicsExtractor g, Cursor cursor, boolean screen) {}

    /** A page's cursor is no longer asked for (its screen closed). Nothing to do: vanilla resets it every frame. */
    public static void resetCursor() {}
}
package dev.oriel.mod.internal.client.render;

import dev.oriel.engine.style.BlendMode;
import dev.oriel.engine.style.Filter;
import dev.oriel.engine.style.MaskComposite;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The offscreen layers behind {@link McCanvas}'s groups (filters, masks, clip-path, blend modes, rounded overflow
 * clips, {@code background-clip: text}).
 *
 * <p>On 26.x a group's content was recorded into a {@link Layer}: a GUI render state with a GUI renderer of its own,
 * drawn into a window-sized offscreen target by {@link LayerRenderer} and blitted back. That machinery is built on
 * {@code GuiRenderState}, {@code GuiRenderer}, {@code RenderPipeline} and {@code PictureInPictureRenderer}, none of
 * which exist in 1.20.1 — the GUI there is an immediate-mode pass, not a deferred one.
 *
 * <p>So on 1.20.1 this class reports {@link #available()} false and every blend mode as unsupported. That is the
 * documented degradation path, not a failure: {@link McCanvas#beginGroup()} then answers false, and the painter draws
 * the element directly, without the effects, and says so through {@link McCanvas#groupLimits}. Everything a page
 * shows is still drawn in the right place, in the right colour and in the right order; only the compositing effects
 * a group would have applied are skipped, and the page's own {@code opacity} is carried by the colours instead.
 */
public final class Layers {
    private Layers() {}

    /** A group's coordinate space: where its (0, 0) is on screen and its size, in GUI px. */
    public record Space(int x, int y, int width, int height) {
        public Space {
            width = Math.max(1, width);
            height = Math.max(1, height);
        }
    }

    /**
     * A GUI pass and its renderer. 26.x kept one per group so a group could be recorded and replayed; 1.20.1 has no
     * recording pass, so this stands only as the type {@link McCanvas} names while an unsupported group is open.
     */
    public static final class Layer {
        Layer() {}
    }

    /** Each loader sets the GUI renderer factory on 26.x; 1.20.1 has no GUI renderer to make. */
    public static void renderers(Object factory) {}

    /** A page is about to be painted. 1.20.1 keeps no pools between frames, so there is nothing to hand out again. */
    public static void beginFrame() {}

    /** Whether groups can be drawn at all: never on 1.20.1 (see the class comment). */
    public static boolean available() {
        return false;
    }

    /** The pipeline for a blend mode, or null when it is not supported (every mode, on 1.20.1). */
    public static @Nullable Object blend(BlendMode blend) {
        return null;
    }

    /** Whether the blend mode asks for anything other than normal drawing. */
    public static boolean supported(BlendMode blend) {
        return false;
    }

    /** Resources reloaded: there are no pipelines to rebuild. */
    public static void reload() {}

    /** A description of what is pooled, for diagnostics. */
    public static String describe() {
        return "layers: unavailable on 1.20.1 (no deferred GUI pass; groups draw directly)";
    }

    // ---- The API the 26.x layer machinery used ----
    // Kept so the shared sources still compile; nothing on 1.20.1 reaches them, because available() is false and
    // LayerRenderer/GuiSceneRenderer are gone.

    /** A fresh layer. Unreachable on 1.20.1: only {@link #available()} groups make them. */
    static Layer layer() {
        throw new UnsupportedOperationException("Oriel: offscreen layers are not available on 1.20.1");
    }

    static void finish(Layer layer) {}

    static List<Filter> filters() {
        return List.of();
    }

    static void mask(List<MaskComposite> ops) {}
}

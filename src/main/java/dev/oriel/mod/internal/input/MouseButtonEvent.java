package dev.oriel.mod.internal.input;

/**
 * A mouse button press, in the shape Oriel's engine expects.
 *
 * <p>26.x had a {@code dev.oriel.mod.internal.input.MouseButtonEvent} record with {@code x()}, {@code y()},
 * {@code button()} (a DOM button: 0 left, 1 middle, 2 right) and {@code modifiers()}, and vanilla's screens passed one
 * to every handler. 1.20.1's screens pass {@code (double mouseX, double mouseY, int button)} instead, where the button
 * is GLFW's (0 left, 1 right, 2 middle), so this package supplies the record and the screens build one from those
 * three numbers, converting the button with {@link #domButton}.
 */
public record MouseButtonEvent(double x, double y, int button, int modifiers) {
    /** The button as DOM numbers it (0 left, 1 middle, 2 right); others pass through. */
    public static int domButton(int glfwButton) {
        return switch (glfwButton) {
            case 1 -> 2; // GLFW right is DOM 2
            case 2 -> 1; // GLFW middle is DOM 1
            default -> glfwButton;
        };
    }

    /** DOM button numbers back to GLFW's (the same swap, it is its own inverse). */
    public static int mcButton(int domButton) {
        return domButton(domButton);
    }

    public boolean hasShiftDown() {
        return (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0;
    }

    public boolean hasControlDown() {
        return (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0;
    }

    public boolean hasAltDown() {
        return (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_ALT) != 0;
    }

    /** For a screen that was given a position and a GLFW button. */
    public static MouseButtonEvent of(double x, double y, int glfwButton, int modifiers) {
        return new MouseButtonEvent(x, y, domButton(glfwButton), modifiers);
    }
}

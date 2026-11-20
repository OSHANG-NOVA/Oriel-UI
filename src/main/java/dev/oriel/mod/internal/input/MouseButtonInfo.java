package dev.oriel.mod.internal.input;

/**
 * What a mouse button press carries besides its position, in the shape Oriel's engine expects.
 *
 * <p>26.x's {@code MouseHandler} reported presses as {@code (long handle, MouseButtonInfo info, int action)}, where the
 * info held the button and the modifiers. 1.20.1's {@code MouseHandler} takes {@code (long handle, int button, int
 * action, int modifiers)} apart, and its screens take {@code (double x, double y, int button)}, so this record only
 * exists so the shared sources keep compiling: the button numbers are GLFW's, as 1.20.1 reports them.
 */
public record MouseButtonInfo(int button, int modifiers) {
    public static MouseButtonInfo of(int glfwButton, int modifiers) {
        return new MouseButtonInfo(glfwButton, modifiers);
    }

    /** The button as DOM numbers it (0 left, 1 middle, 2 right). */
    public int domButton() {
        return MouseButtonEvent.domButton(button);
    }
}

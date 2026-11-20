package dev.oriel.mod.internal.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * A key press, in the shape Oriel's engine expects.
 *
 * <p>26.x had a {@code dev.oriel.mod.internal.input.KeyEvent} record with {@code key()}, {@code keycode()},
 * {@code scancode()}, {@code modifiers()}, {@code hasShiftDown()} and {@code isEscape()}, and vanilla's screens passed
 * one to every handler. 1.20.1's screens pass {@code (int keyCode, int scanCode, int modifiers)} instead, so this
 * package supplies the record and the screens build one from those three numbers.
 *
 * <p>{@code key()} is GLFW's key code, as {@link InputConstants} and {@code KeyMapping} use it, because that is what
 * 1.20.1 reports and what {@code KeyMapping.getKey().getValue()} compares against. {@code keycode()} is the same
 * number: 26.3 split it into an SDL physical key and a layout key, 1.20.1 has only the one.
 */
public record KeyEvent(int key, int scanCode, int modifiers) {
    /** The name of the key in the current layout, as GLFW gives it; null when it is not a printable key. */
    public @Nullable String typed() {
        return GLFW.glfwGetKeyName(key, scanCode);
    }

    /** A GLFW key code, as 26.3's {@code keycode()} was. 1.20.1 has one code per key, so it is {@link #key()}. */
    public int keycode() {
        return key;
    }

    public int scancode() {
        return scanCode;
    }

    public boolean hasShiftDown() {
        return (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
    }

    public boolean hasControlDown() {
        return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
    }

    public boolean hasAltDown() {
        return (modifiers & GLFW.GLFW_MOD_ALT) != 0;
    }

    public boolean hasMetaDown() {
        return (modifiers & GLFW.GLFW_MOD_SUPER) != 0;
    }

    public boolean isEscape() {
        return key == InputConstants.KEY_ESCAPE;
    }

    /** For a screen that was given a key code and nothing else. */
    public static KeyEvent of(int key, int scanCode, int modifiers) {
        return new KeyEvent(key, scanCode, modifiers);
    }

    /** The modifiers of a press vanilla handed a screen, as GLFW's flags (they are the same numbers). */
    public static int modifiers(int vanillaModifiers) {
        return vanillaModifiers;
    }

    /** Whether {@code key} is held down now, by either of its two codes (shift, control, alt, meta). */
    public static boolean isDown(int left, int right) {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), left)
                || InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), right);
    }
}

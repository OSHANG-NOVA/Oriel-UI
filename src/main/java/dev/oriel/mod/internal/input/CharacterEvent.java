package dev.oriel.mod.internal.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * A typed character, in the shape Oriel's engine expects.
 *
 * <p>26.x had a {@code dev.oriel.mod.internal.input.CharacterEvent} record with {@code codepoint()},
 * {@code codepointAsString()} and {@code modifiers()}. 1.20.1's screens pass {@code (char codePoint, int modifiers)},
 * so this package supplies the record and the screens build one from those two values.
 */
public record CharacterEvent(int codepoint, int modifiers) {
    /** The character as a string, empty when the code point is not a valid one. */
    public String codepointAsString() {
        return Character.isValidCodePoint(codepoint) ? new String(Character.toChars(codepoint)) : "";
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

    /** For a screen that was given a typed character. */
    public static CharacterEvent of(char codePoint, int modifiers) {
        return new CharacterEvent(codePoint, modifiers);
    }

    /**
     * The chat/sign keyboard state 1.20.1 tracks while a text field has focus, as 26.x's pre-edit event.
     *
     * <p>26.x reported an IME composition as a {@code PreeditEvent} with the whole text, the caret, and which block of
     * it is being composed. 1.20.1 has no such callback — vanilla text fields take IME input through {@code Screen}'s
     * character events and its own {@code EditBox} cursor handling — so this package reports an empty composition
     * unless the platform gave one, and Oriel's own text boxes keep their text themselves.
     */
    public static @Nullable PreeditEvent preedit() {
        return null;
    }

    /** 1.20.1's screens, for the parts of the input path that need one. */
    public static @Nullable Screen screen() {
        return Minecraft.getInstance().screen;
    }
}

package dev.oriel.mod.internal.input;

import java.util.List;

/**
 * An IME composition, in the shape Oriel's engine expects.
 *
 * <p>26.x reported one through {@code Screen.preeditUpdated} with the full text, the caret's position in it, and which
 * block of it the input method is still composing. 1.20.1 has no equivalent callback: vanilla text fields take IME
 * input as ordinary character events, and there is nothing to report. Oriel's text boxes therefore compose in the
 * engine ({@code InputHandler.preedit}), driven by what the platform does send, and this record only exists so the
 * shared sources keep compiling.
 */
public record PreeditEvent(String fullText, int caretPosition, int focusedBlock, List<String> blocks) {
    /** Nothing is being composed. */
    public static final PreeditEvent EMPTY = new PreeditEvent("", 0, 0, List.of(""));
}

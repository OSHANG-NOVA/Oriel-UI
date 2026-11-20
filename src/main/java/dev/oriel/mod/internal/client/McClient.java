package dev.oriel.mod.internal.client;

import dev.oriel.mod.client.OrielAutomation;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import dev.oriel.mod.internal.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The client state and input that Oriel reaches differently on each Minecraft version; this is the one for 1.20.1.
 */
public final class McClient {
    private McClient() {}

    /** The open screen, or null. */
    public static @Nullable Screen screen() {
        return Minecraft.getInstance().screen;
    }

    /** Opens {@code screen}, or closes the open one with null. */
    public static void setScreen(@Nullable Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    /** Whether a loading overlay (resource reload) covers the screen and takes its input. */
    public static boolean loadingOverlay() {
        return Minecraft.getInstance().getOverlay() != null;
    }

    /** Whether the player hid the HUD (F1). */
    public static boolean hudHidden() {
        return Minecraft.getInstance().options.hideGui;
    }

    public static ChatComponent chat() {
        return Minecraft.getInstance().gui.getChat();
    }

    /** Shows a message from the client in chat. */
    public static void systemMessage(Component message) {
        chat().addMessage(message);
    }

    /** Where the pointer is, in GUI px. */
    public static double mouseX() {
        // 1.20.1's mouse handler already holds the pointer in GUI px while a screen is open (it scales by
        // guiScaledWidth/screenWidth as it stores the move), which is what 26.x's getScaledXPos did on the way out.
        return Minecraft.getInstance().mouseHandler.xpos();
    }

    public static double mouseY() {
        return Minecraft.getInstance().mouseHandler.ypos();
    }

    /** The local player's skin texture. */
    public static ResourceLocation localSkin() {
        Minecraft mc = Minecraft.getInstance();
        // 26.x asked playerSkinRenderCache() for a PlayerSkin; 1.20.1's skin manager answers the same question, and
        // falls back to the default skin for a profile it has not resolved yet.
        return mc.getSkinManager().getInsecureSkinLocation(mc.getUser().getGameProfile());
    }

    /**
     * Turns text input (and with it the input method) on or off for {@code owner}. 1.20.1 has no such toggle: the
     * game always has the input method on, which is what Oriel's own text fields want.
     */
    public static void textInput(Screen owner, boolean on) {}

    /**
     * Where the input method's candidate window goes: under a caret at GUI px (x, y), {@code height} tall. 1.20.1
     * does not track a candidate window, and nothing here can move it, so the call is kept for the shared code path.
     */
    public static void textInputArea(int x, int y, int height) {}

    // ---- Input through Minecraft's own handlers, as a real mouse and keyboard send it (OrielAutomation) ----

    /** A GUI px x coordinate in screen px (window pixels, as the mouse handler has the pointer). */
    public static double screenX(double guiX) {
        Window w = Minecraft.getInstance().getWindow();
        return guiX * w.getScreenWidth() / w.getGuiScaledWidth();
    }

    public static double screenY(double guiY) {
        Window w = Minecraft.getInstance().getWindow();
        return guiY * w.getScreenHeight() / w.getGuiScaledHeight();
    }

    /** Moves the pointer to a window point in screen px, as the mouse handler takes a move. */
    public static void moveMouse(double x, double y) {
        Minecraft mc = Minecraft.getInstance();
        mc.mouseHandler.setIgnoreFirstMove(); // takes the position without queueing a second move event
        mc.mouseHandler.onMove(window(), x, y);
    }

    /**
     * Shuts the real mouse out: its moves, buttons and wheel, and the window's enter and leave, are dropped where the
     * platform delivers them (SDL's event queue, GLFW's callbacks before 26.3), so the pointer moves only through these
     * methods. For the dev autopilot, which owns the pointer for its whole run ({@link DevPointer}); there is no
     * undoing it.
     */
    public static void ignoreRealMouse() {
        GLFW.glfwSetCursorPosCallback(window(), null);
        GLFW.glfwSetMouseButtonCallback(window(), null);
        GLFW.glfwSetScrollCallback(window(), null);
        GLFW.glfwSetCursorEnterCallback(window(), null);
    }

    /** Presses or releases DOM button {@code button} (0 left, 1 middle, 2 right) where the pointer is. */
    public static void mouseButton(int button, boolean press) {
        Minecraft.getInstance().mouseHandler.onPress(window(), KeyNames.mcButton(button),
                press ? InputConstants.PRESS : InputConstants.RELEASE, 0);
    }

    /** Turns the wheel where the pointer is; positive {@code notches} scroll down. */
    public static void scroll(double notches) {
        Minecraft.getInstance().mouseHandler.onScroll(window(), 0, -notches);
    }

    public static void key(KeyEvent event, boolean press) {
        // 1.20.1 takes the key, scan code, action and modifiers apart, where 26.x passed a KeyEvent.
        Minecraft.getInstance().keyboardHandler.keyPress(window(), event.key(), event.scancode(),
                press ? InputConstants.PRESS : InputConstants.RELEASE, event.modifiers());
    }

    public static void typeChar(int codepoint) {
        Minecraft.getInstance().keyboardHandler.charTyped(window(), codepoint, 0);
    }

    private static long window() {
        // 1.20.1 calls the GLFW handle getWindow(); 26.x renamed it handle().
        return Minecraft.getInstance().getWindow().getWindow();
    }
}

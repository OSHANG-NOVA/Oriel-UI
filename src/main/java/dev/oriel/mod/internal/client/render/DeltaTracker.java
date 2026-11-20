package dev.oriel.mod.internal.client.render;

import net.minecraft.client.Minecraft;

/**
 * How far through a tick the frame is, in the shape Oriel's engine expects.
 *
 * <p>26.x passed a {@code dev.oriel.mod.internal.client.render.DeltaTracker} to every HUD and screen render call, and it carried the
 * partial tick for both the game and the real time. 1.20.1 has no such object — a HUD callback gets a {@code float
 * partialTick} and a screen gets one too — but the shared sources already pass a tracker around, so this holds the
 * client's partial tick and answers the two questions those sources ask.
 *
 * <p>There is one per client; {@link #current()} gives it, and it reads the clock fresh every call, so it is never
 * stale.
 */
public final class DeltaTracker {
    private static final DeltaTracker INSTANCE = new DeltaTracker();

    private DeltaTracker() {}

    /** The client's tracker. */
    public static DeltaTracker current() {
        return INSTANCE;
    }

    /** How far into the current tick this frame is, in [0,1). 1.20.1 keeps one number for game and real time alike. */
    public float getGameTimeDeltaPartialTick(boolean ignoreFreeze) {
        return Minecraft.getInstance().getPartialTick();
    }

    /** The same, for the other name 26.x used. */
    public float getRealtimeDeltaTicks() {
        return 1F;
    }

    /** One tick's worth of game time, for the callers that advance a clock by it. */
    public float getGameTimeDeltaTicks() {
        return 1F;
    }
}

package dev.oriel.mod.internal.client;

import net.minecraft.client.Minecraft;

import java.util.Deque;

/* The scripted showcase tour for demo videos ({@code -Ptour}) is recorded on 26.3 only; on 26.1/26.2 it is off. */
final class DevTour {
    public static final boolean ENABLED = false;

    private DevTour() {}

    public static void plan(Minecraft mc, Deque<Runnable> steps) {}
}

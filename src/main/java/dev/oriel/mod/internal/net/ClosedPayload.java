package dev.oriel.mod.internal.net;

/** Client to server: the player closed a session's screen (or it was replaced by another). */
public record ClosedPayload(int session) {}
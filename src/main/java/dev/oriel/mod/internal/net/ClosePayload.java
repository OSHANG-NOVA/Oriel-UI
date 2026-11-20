package dev.oriel.mod.internal.net;

/** Server to client: the server closed a session; the client closes its screen if it still shows it. */
public record ClosePayload(int session) {}
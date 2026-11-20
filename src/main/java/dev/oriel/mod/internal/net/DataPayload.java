package dev.oriel.mod.internal.net;

/** Server to client: new {@code oriel.data} (a JSON value) for an open session. */
public record DataPayload(int session, String data) {}
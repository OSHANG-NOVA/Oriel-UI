package dev.oriel.mod.internal.net;

/**
 * Server to client: sets one top-level field of an open session's {@code oriel.data} to {@code value} (a JSON
 * value), or removes it when {@code value} is JSON null.
 */
public record PatchPayload(int session, String key, String value) {
    public static final int MAX_KEY = 256;
}
package dev.oriel.mod.internal.net;

/** Client to server: {@code oriel.send(channel, value)} from a session's page; {@code json} is the value as JSON. */
public record MessagePayload(int session, String channel, String json) {
    public static final int MAX_CHANNEL = 64;
    /** Serverbound payloads are capped at 32 KiB; messages are small by design. */
    public static final int MAX_JSON = 8192;
}
package dev.oriel.mod.internal.net;

/**
 * Server → client: show a UI for a server session. Exactly one of {@code url} (a page the client has, e.g. bundled in
 * a mod or a resource pack) and {@code html} (an inline page sent by the server) is non-empty.
 *
 * <p>1.20.1 client-only port: this is a plain value type. The 26.x build made it a {@code CustomPacketPayload} with a
 * {@code StreamCodec}; this build has no network layer, so the record keeps only the shape and the size limits that
 * {@code OrielConfig} and {@code ServerPages} read.</p>
 *
 * @param session the server's id for this UI; messages and data refer to it
 * @param data    the initial {@code oriel.data}, a JSON value
 */
public record OpenPayload(int session, String url, String html, String data) {
    public static final int MAX_URL = 256;
    /** Inline pages and data stay well inside the 1 MiB clientbound payload limit, even at 3 bytes per char. */
    public static final int MAX_HTML = 200_000;
    public static final int MAX_DATA = 100_000;
}

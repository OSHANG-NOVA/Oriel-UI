package dev.oriel.mod.internal;


/**
 * Loader-independent entry point.
 *
 * <p>1.20.1 client-only port: the 26.x build called this from each loader's mod constructor and then hung server
 * hooks off it ({@code RegisterCommandsEvent}, {@code PlayerLoggedInEvent}, {@code ServerStoppingEvent}). This build
 * has no server side, no game tests and no nested-jar scanning, so only the settings load and the log banner remain.
 * The Forge entry point calls {@link #init()} from its client setup, before anything renders.</p>
 */
public final class OrielCommon {
    private OrielCommon() {}

    /** Reads the settings. Call before anything reads {@link OrielConfig}. */
    public static void init() {
        Constants.LOG.info("Oriel UI runtime version: {}", dev.oriel.mod.internal.platform.Services.PLATFORM.modVersion(Constants.MOD_ID));
        Constants.LOG.info("Oriel UI distribution: {} version={} {}",
                dev.oriel.kit.ReferenceContent.available() ? "full" : "slim",
                dev.oriel.mod.internal.platform.Services.PLATFORM.modVersion(Constants.MOD_ID),
                dev.oriel.kit.ReferenceContent.available() ? "reference content included" : "参考内容未包含（官方模板/showcase/demo）");
        OrielConfig.load();
    }
}

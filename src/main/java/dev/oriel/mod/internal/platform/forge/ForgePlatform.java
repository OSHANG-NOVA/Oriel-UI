package dev.oriel.mod.internal.platform.forge;

import dev.oriel.mod.internal.platform.Platform;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

/**
 * The Forge answer to {@link Platform}.
 *
 * <p>1.20.1 Forge spells these differently from NeoForge for the same decade of loaders: the production flag is a
 * public field ({@code FMLEnvironment.production}) rather than {@code isProduction()}, and a mod's version is an
 * {@code ArtifactVersion} that has to be turned into a string by hand.</p>
 */
public final class ForgePlatform implements Platform {
    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLEnvironment.production;
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public String modVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .orElseThrow(() -> new IllegalStateException("no mod " + modId))
                .getModInfo()
                .getVersion()
                .toString();
    }
}

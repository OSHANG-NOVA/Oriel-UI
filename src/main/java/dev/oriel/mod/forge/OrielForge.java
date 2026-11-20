package dev.oriel.mod.forge;

import dev.oriel.mod.internal.Constants;
import dev.oriel.mod.internal.OrielCommon;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Oriel UI on Minecraft 1.20.1 Forge, client only.
 *
 * <p>The 26.x build had one mod class per loader and hung a server half off it (commands, network, a container menu,
 * game tests, nested-JAR scanning). This port is the client only: there is no server side, no network channel and no
 * game tests, so this class reads the settings and then hands everything else to {@link OrielForgeClient}, which FML
 * must not load on a dedicated server.</p>
 *
 * <p>1.20.1 Forge's {@code @Mod} has no {@code dist} attribute (that arrived with NeoForge), so the client class is
 * reached through a {@code Dist} check instead of an annotation.</p>
 */
@Mod(Constants.MOD_ID)
public final class OrielForge {
    public OrielForge() {
        OrielCommon.init();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            OrielForgeClient.init(FMLJavaModLoadingContext.get().getModEventBus());
        }
    }
}

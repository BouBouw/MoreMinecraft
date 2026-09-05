package com.dynamicmusic;

import com.dynamicmusic.config.DynamicMusicConfig;
import com.dynamicmusic.sound.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * Point d'entree du mod DynamicMusic.
 *
 * <p>Mod <b>100% client</b> : aucune logique serveur, aucun paquet reseau.
 * Les {@code SoundEvent} sont malgre tout enregistres des deux cotes afin que
 * les registres restent coherents si le mod est installe sur un serveur.</p>
 */
@Mod(DynamicMusic.MOD_ID)
public final class DynamicMusic {

    public static final String MOD_ID = "dynamicmusic";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DynamicMusic() {
        final IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Enregistrement des SoundEvent (DeferredRegister).
        ModSounds.init(modBus);

        // Configuration client (fondus, volumes, activation des declencheurs).
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, DynamicMusicConfig.SPEC, "dynamicmusic-client.toml");

        if (FMLEnvironment.dist == Dist.CLIENT) {
            LOGGER.info("[DynamicMusic] Initialisation cote client.");
        }
    }
}

package com.dynamicmusic.client;

import com.dynamicmusic.DynamicMusic;
import com.dynamicmusic.config.DynamicMusicConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Inscription des evenements cote client.
 *
 * <p>Deux bus distincts sont utilises :</p>
 * <ul>
 *   <li>le <b>bus du jeu</b> (classe englobante) pour les evenements recurrents :
 *       tick client, lecture d'un son, deconnexion, dechargement de monde ;</li>
 *   <li>le <b>bus du mod</b> (classe imbriquee {@link ModBus}) pour le cycle de
 *       vie : initialisation, configuration, rechargement des ressources.</li>
 * </ul>
 *
 * <p>Toutes les inscriptions sont marquees {@code Dist.CLIENT} : les classes ne
 * sont jamais chargees sur un serveur dedie.</p>
 */
@Mod.EventBusSubscriber(modid = DynamicMusic.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientModEvents {

    private ClientModEvents() {
    }

    /**
     * Tick client : une seule fois par tick, en phase END, pour travailler sur
     * un etat de monde deja mis a jour.
     *
     * <p>La vignette est mise a jour avant le selecteur de musique et sans
     * condition sur la presence d'un monde : la musique du menu principal doit
     * elle aussi etre annoncee.</p>
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MusicNotifier.INSTANCE.tick(Minecraft.getInstance());
        ClientMusicHandler.INSTANCE.tick();
    }

    /**
     * Intercepte les sons juste avant leur envoi au moteur audio.
     *
     * <p>Deux roles :</p>
     * <ul>
     *   <li>annuler la musique d'ambiance vanilla tant qu'une piste du mod est
     *       en cours, ce qui evite toute superposition ;</li>
     *   <li>signaler a la vignette le morceau qui demarre, du mod comme du jeu
     *       de base.</li>
     * </ul>
     *
     * <p>Aucun effet sur les autres categories sonores : blocs, mobs, meteo et
     * disques passent sans etre touches.</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlaySound(PlaySoundEvent event) {
        final SoundInstance sound = event.getSound();
        if (sound == null || sound.getSource() != SoundSource.MUSIC) {
            return;
        }

        // Une piste vanilla annulee ne doit evidemment pas etre annoncee.
        if (!(sound instanceof DynamicSoundInstance)
                && ClientMusicHandler.INSTANCE.isSuppressingVanillaMusic()) {
            event.setSound(null);
            return;
        }

        MusicNotifier.INSTANCE.onMusicStarted(sound);
    }

    /**
     * Dessine le panneau musical dans les ecrans d'options, apres le rendu de
     * l'ecran lui-meme.
     *
     * <p>Le panneau ne passe pas par le systeme de notifications : celui-ci met
     * les vignettes en file, leur alloue des emplacements et les retire au bout
     * d'un temps. Pour un affichage qui doit simplement rester la tant que
     * l'ecran est ouvert, dessiner ici est deterministe et sans condition.</p>
     */
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!DynamicMusicConfig.showToast || !DynamicMusicConfig.pinToastInOptions) {
            return;
        }
        if (!MusicNotifier.isOptionsScreen(event.getScreen())) {
            return;
        }
        MusicToast.renderPinned(event.getGuiGraphics());
    }

    /** Deconnexion du serveur : on coupe tout et on repart d'un etat neutre. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientMusicHandler.INSTANCE.reset();
        MusicNotifier.INSTANCE.reset();
    }

    /** Changement de dimension ou fermeture du monde : les caches deviennent invalides. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ClientMusicHandler.INSTANCE.reset();
        }
    }

    // ==================================================================
    //  Bus du mod : cycle de vie
    // ==================================================================

    @Mod.EventBusSubscriber(modid = DynamicMusic.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {

        private ModBus() {
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                DynamicMusicConfig.bake();
                DynamicMusic.LOGGER.info("[DynamicMusic] Systeme de musique dynamique pret.");
            });
        }

        /** Table des metadonnees : rechargee avec les packs de ressources. */
        @SubscribeEvent
        public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener(MusicMetadata.INSTANCE);
        }

        /** Premiere lecture du fichier de configuration. */
        @SubscribeEvent
        public static void onConfigLoading(ModConfigEvent.Loading event) {
            if (event.getConfig().getSpec() == DynamicMusicConfig.SPEC) {
                DynamicMusicConfig.bake();
            }
        }

        /** Modification a chaud du fichier de configuration. */
        @SubscribeEvent
        public static void onConfigReloading(ModConfigEvent.Reloading event) {
            if (event.getConfig().getSpec() == DynamicMusicConfig.SPEC) {
                DynamicMusicConfig.bake();
                ClientMusicHandler.INSTANCE.reset();
            }
        }
    }
}

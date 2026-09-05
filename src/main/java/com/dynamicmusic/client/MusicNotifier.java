package com.dynamicmusic.client;

import com.dynamicmusic.config.DynamicMusicConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

/**
 * Suit la musique en cours, quelle que soit son origine, et pilote l'affichage
 * de la vignette.
 *
 * <p>Une piste est captee au moment ou le moteur audio la lance, via
 * {@code PlaySoundEvent}. Les musiques du jeu de base sont donc traitees
 * exactement comme celles du mod.</p>
 *
 * <p>L'identification est volontairement <b>differee d'un tick</b> : au moment ou
 * l'evenement est emis, l'instance n'a pas toujours choisi son fichier parmi les
 * variantes declarees dans {@code sounds.json}. Attendre le tick suivant garantit
 * un identifiant exact, et sort au passage tout travail du chemin audio.</p>
 */
public final class MusicNotifier {

    public static final MusicNotifier INSTANCE = new MusicNotifier();

    /** Instance captee par PlaySoundEvent, en attente d'identification. */
    private SoundInstance pending;

    private SoundInstance currentSound;
    private MusicInfo currentInfo;

    /**
     * Derniere piste connue, conservee apres la fin du morceau. Elle alimente le
     * panneau des options pendant les silences, pour que l'ecran de reglage du
     * volume ne soit jamais vide.
     */
    private MusicInfo lastInfo;

    /** Temps ecoule sur la piste, en ticks client, fige quand le jeu est en pause. */
    private int elapsedTicks;

    /** Incremente a chaque nouveau morceau : sert a la vignette pour relancer son minuteur. */
    private long generation;

    private MusicNotifier() {
    }

    // ==================================================================
    //  Capture
    // ==================================================================

    /** Appele depuis {@code PlaySoundEvent} pour toute musique acceptee. */
    public void onMusicStarted(SoundInstance instance) {
        this.pending = instance;
    }

    // ==================================================================
    //  Boucle client
    // ==================================================================

    /**
     * Appele a chaque tick client, y compris hors partie : la musique du menu
     * principal doit elle aussi afficher sa vignette.
     */
    public void tick(Minecraft mc) {
        if (!DynamicMusicConfig.showToast) {
            clear();
            return;
        }

        // 1) Identification differee de la piste captee au tick precedent.
        if (this.pending != null) {
            final SoundInstance instance = this.pending;
            this.pending = null;
            adopt(instance);
        }

        // 2) Fin de la piste : la vignette n'a plus rien a montrer.
        if (this.currentSound != null && !mc.getSoundManager().isActive(this.currentSound)) {
            clear();
            return;
        }

        if (this.currentInfo == null) {
            return;
        }

        // 3) Avancement. En pause, le moteur audio est suspendu : le compteur l'est aussi.
        if (!mc.isPaused()) {
            this.elapsedTicks++;
        }
    }

    private void adopt(SoundInstance instance) {
        final Identifier trackId = trackIdOf(instance);
        if (trackId == null) {
            return;
        }
        this.currentSound = instance;
        this.currentInfo = MusicMetadata.INSTANCE.get(trackId);
        this.elapsedTicks = 0;
        this.generation++;
        if (!isMusicMuted()) {
            MusicToast.ensureVisible(Minecraft.getInstance());
        }
    }

    /**
     * Emplacement du fichier audio reellement joue. On preferera toujours
     * {@code getSound()}, qui designe la variante tiree au sort, a l'emplacement
     * de l'evenement sonore, qui peut en regrouper plusieurs.
     */
    private static Identifier trackIdOf(SoundInstance instance) {
        final Sound sound = instance.getSound();
        if (sound != null && sound.getLocation() != null) {
            return sound.getLocation();
        }
        return instance.getIdentifier();
    }

    private void clear() {
        if (this.currentInfo != null) {
            this.lastInfo = this.currentInfo;
        }
        this.currentSound = null;
        this.currentInfo = null;
        this.elapsedTicks = 0;
    }

    /** Remise a zero complete (deconnexion, changement de monde). */
    public void reset() {
        this.pending = null;
        clear();
        this.lastInfo = null;
    }

    // ==================================================================
    //  Etat lu par la vignette
    // ==================================================================

    /** Piste en cours, ou {@code null} si aucune musique ne joue. */
    public MusicInfo current() {
        return this.currentInfo;
    }

    /**
     * Ce que le panneau des options doit montrer : la piste en cours, sinon la
     * derniere jouee. Evite un ecran vide pendant les silences entre morceaux.
     */
    public MusicInfo displayInfo() {
        return this.currentInfo != null ? this.currentInfo : this.lastInfo;
    }

    /** Vrai si la piste affichee est reellement en train de jouer. */
    public boolean isLive() {
        return this.currentInfo != null;
    }

    /** Change a chaque nouveau morceau. */
    public long generation() {
        return this.generation;
    }

    public int elapsedSeconds() {
        return this.elapsedTicks / 20;
    }

    /** Avancement dans la piste, entre 0 et 1, ou -1 si la duree est inconnue. */
    public float progress() {
        final MusicInfo info = this.currentInfo;
        if (info == null || !info.hasDuration()) {
            return -1.0F;
        }
        return Math.min(1.0F, (float) elapsedSeconds() / (float) info.duration());
    }

    /**
     * Vrai quand la vignette doit rester affichee sans limite de temps, c'est a
     * dire dans l'ecran des options et tous ses sous-ecrans, celui du volume
     * compris.
     */
    public boolean isPinned() {
        return DynamicMusicConfig.pinToastInOptions
                && !isMusicMuted()
                && displayInfo() != null
                && isOptionsScreen(Minecraft.getInstance().gui.screen());
    }

    /**
     * Vrai quand aucune musique ne peut etre entendue : curseur Musique a zero,
     * ou volume general a zero, ce qui rend le premier sans effet.
     *
     * <p>La vignette n'a alors rien a annoncer et disparait, en jeu comme dans
     * les ecrans d'options. Le test est refait a chaque image : deplacer le
     * curseur a zero la fait disparaitre immediatement, et la remonter la fait
     * revenir.</p>
     */
    public static boolean isMusicMuted() {
        final Options options = Minecraft.getInstance().options;
        return options.getSoundSourceVolume(SoundSource.MUSIC) <= 0.0F
                || options.getSoundSourceVolume(SoundSource.MASTER) <= 0.0F;
    }

    /**
     * Vrai pour l'ecran des options et tous ses sous-ecrans, celui du volume
     * compris. {@code OptionsSubScreen} est la classe mere commune a tous les
     * sous-ecrans de reglages.
     */
    public static boolean isOptionsScreen(Screen screen) {
        return screen instanceof OptionsScreen || screen instanceof OptionsSubScreen;
    }
}

package com.dynamicmusic.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * Instance sonore "vivante" utilisee pour toutes les pistes du mod.
 *
 * <p>Fonctionnement du fondu : le moteur audio ({@code SoundEngine#tick})
 * appelle {@link #tick()} une fois par tick client sur chaque
 * {@code TickableSoundInstance}, puis relit {@code getVolume()} pour pousser la
 * nouvelle valeur dans le canal OpenAL. Il suffit donc de faire evoluer le
 * champ {@code volume} ici pour obtenir un fade-in / fade-out reel, sans
 * toucher au volume global de la categorie MUSIC.</p>
 *
 * <p>Deux details indispensables :</p>
 * <ul>
 *   <li>{@link #canStartSilent()} doit renvoyer {@code true}, sinon le moteur
 *       refuse de demarrer une instance dont le volume initial vaut 0 et le
 *       fade-in ne se produit jamais.</li>
 *   <li>Le son est declare relatif avec {@code Attenuation.NONE} : il est joue
 *       "dans la tete" du joueur, comme la musique vanilla, et non a une
 *       position du monde.</li>
 * </ul>
 */
public class DynamicSoundInstance extends AbstractTickableSoundInstance {

    /** Volume vise une fois le fade-in termine (deja multiplie par le volume du mod). */
    private final float targetVolume;

    /** Duree du fondu en cours, en ticks. */
    private int fadeDuration;
    /** Ticks ecoules dans le fondu en cours. */
    private int fadeElapsed;
    /** Volume au moment ou le fondu courant a demarre (permet d'interrompre un fade-in par un fade-out). */
    private float fadeStartVolume;

    private boolean fadingOut;

    public DynamicSoundInstance(SoundEvent event, float targetVolume, int fadeInTicks) {
        super(event, SoundSource.MUSIC, SoundInstance.createUnseededRandom());

        this.targetVolume = Mth.clamp(targetVolume, 0.0F, 1.0F);
        this.fadeDuration = Math.max(1, fadeInTicks);
        this.fadeElapsed = 0;
        this.fadeStartVolume = 0.0F;

        // Comportement "musique" : non repetitif, sans delai, non spatialise.
        this.looping = false;
        this.delay = 0;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.pitch = 1.0F;
        this.volume = 0.0F; // demarre silencieux : le fade-in fait le reste
        this.x = 0.0D;
        this.y = 0.0D;
        this.z = 0.0D;
    }

    /**
     * Autorise le demarrage a volume nul. Sans cela, {@code SoundEngine#play}
     * abandonne la lecture avec le message "Skipped playing sound, volume was zero".
     */
    @Override
    public boolean canStartSilent() {
        return true;
    }

    /** Declenche le fondu de sortie ; l'instance s'arretera d'elle-meme a la fin. */
    public void fadeOut(int ticks) {
        if (this.fadingOut) {
            return;
        }
        this.fadingOut = true;
        this.fadeStartVolume = this.volume;
        this.fadeDuration = Math.max(1, ticks);
        this.fadeElapsed = 0;
    }

    public boolean isFadingOut() {
        return this.fadingOut;
    }

    /**
     * Arret immediat, demande depuis l'exterieur de la classe.
     *
     * <p>{@code AbstractTickableSoundInstance#stop()} est {@code protected final} :
     * seule une sous-classe peut l'appeler, et uniquement sur elle-meme. Ce
     * relais public permet au gestionnaire de couper la piste sans passer par
     * un fondu, par exemple quand le mod est desactive en cours de partie.</p>
     */
    public void requestStop() {
        this.stop();
    }

    public float getTargetVolume() {
        return this.targetVolume;
    }

    @Override
    public void tick() {
        // Le drapeau interne d'arret est prive dans la classe mere : on passe
        // par l'accesseur public isStopped().
        if (this.isStopped()) {
            return;
        }

        // Fondu termine : plus rien a calculer, on laisse la piste jouer a plein volume.
        if (!this.fadingOut && this.fadeElapsed >= this.fadeDuration) {
            return;
        }

        this.fadeElapsed++;
        final float progress = Mth.clamp((float) this.fadeElapsed / (float) this.fadeDuration, 0.0F, 1.0F);
        // Courbe en S : evite l'attaque brutale d'une interpolation lineaire.
        final float eased = progress * progress * (3.0F - 2.0F * progress);

        if (this.fadingOut) {
            this.volume = Mth.lerp(eased, this.fadeStartVolume, 0.0F);
            if (progress >= 1.0F) {
                this.volume = 0.0F;
                this.stop(); // libere le canal audio au prochain tick du SoundEngine
            }
        } else {
            this.volume = Mth.lerp(eased, this.fadeStartVolume, this.targetVolume);
        }
    }
}

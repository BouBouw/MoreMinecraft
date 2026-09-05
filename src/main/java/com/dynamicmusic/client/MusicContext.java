package com.dynamicmusic.client;

import com.dynamicmusic.config.DynamicMusicConfig;
import com.dynamicmusic.sound.ModSounds;
import net.minecraft.sounds.SoundEvent;

import java.util.function.Supplier;

/**
 * Contextes musicaux possibles, classes par priorite decroissante.
 *
 * <p>La cascade est encodee dans le champ {@link #priority} : plus la valeur est
 * haute, plus le contexte est prioritaire. {@link ClientMusicHandler} evalue les
 * declencheurs dans cet ordre et retient le premier qui est a la fois vrai
 * <b>et</b> pourvu d'une piste, ce qui garantit qu'une musique de biome ne peut
 * jamais recouvrir un combat.</p>
 *
 * <p>Les contextes de priorite inferieure ou egale a {@link #AMBIENT_MAX_PRIORITY}
 * forment le palier "ambiance" : ce sont les seuls ou une piste classique peut
 * se substituer a la piste attendue, afin que les morceaux sans declencheur
 * entrent naturellement dans la rotation.</p>
 */
public enum MusicContext {

    /** Aucun contexte : on laisse la main a la musique d'ambiance vanilla. */
    NONE(0, Speed.NORMAL, 0.0F, () -> null),

    // --- 1. Danger vital ------------------------------------------------
    DANGER(100, Speed.FAST, 1.0F, ModSounds.MUSIC_DANGER),

    // --- 2. Combat et boss ----------------------------------------------
    BOSS(90, Speed.FAST, 1.0F, ModSounds.MUSIC_BOSS),
    COMBAT(80, Speed.FAST, 1.0F, ModSounds.MUSIC_COMBAT),

    // --- 3. Sous l'eau ---------------------------------------------------
    // Volontairement branche sur l'evenement de l'ocean, faute de piste
    // sous-marine dediee. Partager l'evenement, plutot que dupliquer la meme
    // liste de fichiers dans deux evenements distincts, rend le test
    // anti-redemarrage exact : plonger dans l'ocean ne coupe jamais le morceau
    // en cours, quelle que soit la variante tiree.
    // Pour donner sa propre musique au sous-marin : rebrancher sur
    // ModSounds.MUSIC_UNDERWATER et declarer music.underwater dans sounds.json.
    UNDERWATER(70, Speed.NORMAL, 0.9F, ModSounds.MUSIC_BIOME_OCEAN),

    // --- 4. Meteo --------------------------------------------------------
    THUNDER(62, Speed.NORMAL, 1.0F, ModSounds.MUSIC_THUNDER),
    RAIN(60, Speed.NORMAL, 0.85F, ModSounds.MUSIC_RAIN),

    // --- 5. Souterrain ---------------------------------------------------
    UNDERGROUND(50, Speed.SLOW, 0.85F, ModSounds.MUSIC_UNDERGROUND),

    // --- 6. Nuit a ciel ouvert : une ambiance, pas une action, donc une
    //        piste classique peut s'y substituer comme sur les biomes.
    NIGHT(45, Speed.SLOW, 0.85F, ModSounds.MUSIC_NIGHT),

    // --- 7. Biomes -------------------------------------------------------
    BIOME_DEEP_DARK(40, Speed.SLOW, 0.85F, ModSounds.MUSIC_BIOME_DEEP_DARK),
    BIOME_LUSH_CAVE(40, Speed.SLOW, 0.8F, ModSounds.MUSIC_BIOME_LUSH_CAVE),
    BIOME_MUSHROOM(40, Speed.SLOW, 0.8F, ModSounds.MUSIC_BIOME_MUSHROOM),
    BIOME_CHERRY(40, Speed.SLOW, 0.8F, ModSounds.MUSIC_BIOME_CHERRY),
    BIOME_SNOWY(40, Speed.SLOW, 0.8F, ModSounds.MUSIC_BIOME_SNOWY),
    BIOME_DESERT(40, Speed.SLOW, 0.8F, ModSounds.MUSIC_BIOME_DESERT),
    BIOME_BADLANDS(40, Speed.SLOW, 0.8F, ModSounds.MUSIC_BIOME_BADLANDS),
    BIOME_SWAMP(40, Speed.SLOW, 0.8F, ModSounds.MUSIC_BIOME_SWAMP),
    BIOME_JUNGLE(40, Speed.SLOW, 0.8F, ModSounds.MUSIC_BIOME_JUNGLE),
    BIOME_SAVANNA(40, Speed.SLOW, 0.8F, ModSounds.MUSIC_BIOME_SAVANNA),
    BIOME_BEACH(40, Speed.SLOW, 0.8F, ModSounds.MUSIC_BIOME_BEACH),
    BIOME_RIVER(40, Speed.SLOW, 0.8F, ModSounds.MUSIC_BIOME_RIVER),
    BIOME_OCEAN(40, Speed.SLOW, 0.8F, ModSounds.MUSIC_BIOME_OCEAN),
    BIOME_TAIGA(40, Speed.SLOW, 0.8F, ModSounds.MUSIC_BIOME_TAIGA),
    BIOME_FOREST(40, Speed.SLOW, 0.8F, ModSounds.MUSIC_BIOME_FOREST),
    BIOME_MOUNTAIN(40, Speed.SLOW, 0.8F, ModSounds.MUSIC_BIOME_MOUNTAIN),
    BIOME_PLAINS(40, Speed.SLOW, 0.8F, ModSounds.MUSIC_BIOME_PLAINS),

    // --- 8. Dimension (repli global) --------------------------------------
    DIM_OVERWORLD(20, Speed.SLOW, 0.75F, ModSounds.MUSIC_DIM_OVERWORLD),
    DIM_NETHER(20, Speed.SLOW, 0.8F, ModSounds.MUSIC_DIM_NETHER),
    DIM_END(20, Speed.SLOW, 0.8F, ModSounds.MUSIC_DIM_END),

    // --- 9. Classique : aucun declencheur, dernier repli -------------------
    CLASSIC(10, Speed.SLOW, 0.8F, ModSounds.MUSIC_CLASSIC);

    /** Priorite maximale du palier "ambiance", ou les pistes classiques peuvent s'inviter. */
    public static final int AMBIENT_MAX_PRIORITY = 45;

    /** Facteur applique a la duree de fondu de reference definie dans la config. */
    public enum Speed {
        FAST(0.4F),
        NORMAL(1.0F),
        SLOW(1.8F);

        private final float factor;

        Speed(float factor) {
            this.factor = factor;
        }
    }

    private final int priority;
    private final Speed speed;
    private final float volume;
    private final Supplier<SoundEvent> sound;

    MusicContext(int priority, Speed speed, float volume, Supplier<SoundEvent> sound) {
        this.priority = priority;
        this.speed = speed;
        this.volume = volume;
        this.sound = sound;
    }

    public int priority() {
        return this.priority;
    }

    public float volume() {
        return this.volume;
    }

    /**
     * Vrai pour les contextes d'ambiance (biome, dimension, classique), c'est a
     * dire ceux qui ne traduisent aucune action ni aucun danger. Une piste
     * classique ne remplace jamais une musique de combat ou de danger.
     */
    public boolean isAmbient() {
        return this != NONE && this.priority <= AMBIENT_MAX_PRIORITY;
    }

    /**
     * Resout paresseusement le {@link SoundEvent}. L'appel n'a lieu qu'au moment
     * de jouer la piste, donc bien apres le remplissage des registres.
     */
    public SoundEvent soundEvent() {
        return this.sound.get();
    }

    /** Duree du fondu d'entree, en ticks. */
    public int fadeInTicks() {
        return Math.max(1, Math.round(DynamicMusicConfig.fadeTicks * this.speed.factor));
    }

    /**
     * Duree du fondu de sortie, en ticks.
     *
     * @param urgent vrai quand le contexte suivant est plus prioritaire : la
     *               coupure doit alors etre nettement plus rapide pour que le
     *               combat ou le danger prenne la main immediatement.
     */
    public int fadeOutTicks(boolean urgent) {
        final int base = Math.max(1, Math.round(DynamicMusicConfig.fadeTicks * this.speed.factor));
        return urgent ? Math.max(1, base / 3) : base;
    }
}

package com.dynamicmusic.client;

import com.dynamicmusic.DynamicMusic;
import com.dynamicmusic.config.DynamicMusicConfig;
import com.dynamicmusic.sound.ModBiomeTags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Predicate;

/**
 * Cerveau du mod : detecte l'etat du joueur, choisit le contexte musical
 * prioritaire et pilote les fondus entre les pistes.
 *
 * <h2>Degradation progressive</h2>
 * Un contexte n'est retenu que s'il dispose reellement d'une piste chargee
 * (voir {@link #hasTrack(MusicContext)}). Sans piste, la cascade continue vers
 * le niveau suivant, jusqu'au repli "classique" puis, en dernier ressort, a la
 * musique vanilla. Ajouter une musique ne demande donc aucune modification du
 * code : il suffit de deposer le {@code .ogg} et de declarer l'entree
 * correspondante dans {@code sounds.json}.
 *
 * <h2>Budget CPU</h2>
 * Le handler tourne sur le thread client (20 Hz). Tout travail lourd fait ici
 * bloque le rendu, d'ou plusieurs garde-fous :
 * <ul>
 *   <li>La detection complete ne tourne que toutes les {@value #SCAN_INTERVAL_TICKS}
 *       ticks, pas a chaque tick.</li>
 *   <li>La cascade court-circuite : si le joueur est en danger vital, aucun scan
 *       d'entites ni aucune lecture de biome n'a lieu.</li>
 *   <li>Le scan d'entites, seule operation reellement couteuse, est limite a
 *       1 Hz et son resultat est mis en cache entre deux scans.</li>
 *   <li>Le biome n'est relu que lorsque le joueur change de section de chunk
 *       (16x16x16), pas a chaque deplacement.</li>
 *   <li>Aucune lecture de configuration ni allocation dans le chemin chaud en
 *       dehors du scan d'entites.</li>
 * </ul>
 */
public final class ClientMusicHandler {

    public static final ClientMusicHandler INSTANCE = new ClientMusicHandler();

    // --- Cadences ------------------------------------------------------
    /** Periode d'evaluation de la cascade de priorites (0,5 s). */
    private static final int SCAN_INTERVAL_TICKS = 10;
    /** Periode du scan d'entites hostiles (1 s). */
    private static final int THREAT_SCAN_INTERVAL_TICKS = 20;
    /**
     * Le scan de menaces n'est pas cadence en ticks mais en nombre de passes de
     * detection, puisqu'il n'est atteint que depuis {@link #detectContext}.
     */
    private static final int THREAT_SCAN_PASSES =
            Math.max(1, THREAT_SCAN_INTERVAL_TICKS / SCAN_INTERVAL_TICKS);
    /** Delai avant de tester la fin d'une piste fraichement lancee (evite un faux positif au demarrage). */
    private static final int START_GRACE_TICKS = 10;

    // --- Seuils de detection -------------------------------------------
    private static final double COMBAT_RADIUS = 16.0D;
    private static final double COMBAT_RADIUS_SQR = COMBAT_RADIUS * COMBAT_RADIUS;
    private static final double BOSS_RADIUS = 48.0D;
    /** Portee max de l'heuristique "le mob me regarde" : les raycasts coutent cher. */
    private static final double HEURISTIC_RADIUS_SQR = 12.0D * 12.0D;
    /** Nombre maximal de raycasts de ligne de vue par scan. */
    private static final int MAX_LINE_OF_SIGHT_CHECKS = 4;
    private static final int UNDERGROUND_MAX_Y = 50;

    /** Ne conserve que les entites hostiles vivantes et dotees d'une IA. */
    private static final Predicate<Mob> HOSTILE_FILTER =
            mob -> mob instanceof Enemy && mob.isAlive() && !mob.isNoAi();

    /** Association tag de biome vers contexte musical. */
    private record BiomeRule(TagKey<Biome> tag, MusicContext context) {
    }

    /**
     * Table de correspondance des biomes, evaluee dans l'ordre : du plus
     * specifique au plus general. Un biome enneige sonne "enneige" avant de
     * sonner "taiga", une grotte luxuriante avant une montagne, etc.
     */
    private static final BiomeRule[] BIOME_RULES = {
            new BiomeRule(ModBiomeTags.IS_DEEP_DARK, MusicContext.BIOME_DEEP_DARK),
            new BiomeRule(ModBiomeTags.IS_LUSH_CAVE, MusicContext.BIOME_LUSH_CAVE),
            new BiomeRule(ModBiomeTags.IS_MUSHROOM, MusicContext.BIOME_MUSHROOM),
            new BiomeRule(ModBiomeTags.IS_CHERRY, MusicContext.BIOME_CHERRY),
            new BiomeRule(ModBiomeTags.IS_SNOWY, MusicContext.BIOME_SNOWY),
            new BiomeRule(ModBiomeTags.IS_DESERT, MusicContext.BIOME_DESERT),
            new BiomeRule(ModBiomeTags.IS_BADLANDS, MusicContext.BIOME_BADLANDS),
            new BiomeRule(ModBiomeTags.IS_SWAMP, MusicContext.BIOME_SWAMP),
            new BiomeRule(ModBiomeTags.IS_JUNGLE, MusicContext.BIOME_JUNGLE),
            new BiomeRule(ModBiomeTags.IS_SAVANNA, MusicContext.BIOME_SAVANNA),
            new BiomeRule(ModBiomeTags.IS_BEACH, MusicContext.BIOME_BEACH),
            new BiomeRule(ModBiomeTags.IS_RIVER, MusicContext.BIOME_RIVER),
            new BiomeRule(ModBiomeTags.IS_OCEAN, MusicContext.BIOME_OCEAN),
            new BiomeRule(ModBiomeTags.IS_TAIGA, MusicContext.BIOME_TAIGA),
            new BiomeRule(ModBiomeTags.IS_FOREST, MusicContext.BIOME_FOREST),
            new BiomeRule(ModBiomeTags.IS_MOUNTAIN, MusicContext.BIOME_MOUNTAIN),
            new BiomeRule(ModBiomeTags.IS_PLAINS, MusicContext.BIOME_PLAINS),
    };

    private final RandomSource random = RandomSource.create();

    // --- Etat de lecture ------------------------------------------------
    private MusicContext playingContext = MusicContext.NONE;
    private DynamicSoundInstance playingSound;
    /**
     * Piste effectivement en cours. Plusieurs contextes peuvent partager le meme
     * fichier : c'est ce champ, et non le contexte, qui dit s'il faut relancer
     * quelque chose.
     */
    private SoundEvent playingEvent;
    private int graceTicks;
    /** Ticks de silence restants avant d'autoriser une nouvelle piste (anti-repetition). */
    private int silenceTicks;

    // --- Caches de detection -------------------------------------------
    private MusicContext desiredContext = MusicContext.NONE;
    private int scanCooldown;
    private int threatCooldown;
    private MusicContext cachedThreat = MusicContext.NONE;
    private long cachedSection = Long.MIN_VALUE;
    private ResourceKey<Level> cachedDimension;
    private MusicContext cachedAmbient = MusicContext.NONE;

    private ClientMusicHandler() {
    }

    // ==================================================================
    //  Boucle principale
    // ==================================================================

    /** Appele une fois par tick client (phase END). */
    public void tick() {
        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer player = mc.player;
        final ClientLevel level = mc.level;

        // Hors monde : on repart d'un etat propre.
        if (player == null || level == null) {
            if (this.playingSound != null) {
                reset();
            }
            return;
        }

        if (!DynamicMusicConfig.enabled) {
            if (this.playingSound != null) {
                stopImmediately(mc);
            }
            return;
        }

        // En pause, le moteur audio est deja suspendu : rien a recalculer.
        if (mc.isPaused()) {
            return;
        }

        updatePlaybackState(mc);

        // 1) Detection, throttlee.
        if (--this.scanCooldown <= 0) {
            this.scanCooldown = SCAN_INTERVAL_TICKS;
            this.desiredContext = detectContext(player, level);
        }

        // 2) Application de la decision.
        if (this.playingSound != null) {
            if (this.desiredContext != this.playingContext) {
                crossfadeTo(mc, this.desiredContext);
            }
        } else if (this.silenceTicks > 0) {
            this.silenceTicks--;
        } else if (this.desiredContext != MusicContext.NONE) {
            start(mc, this.desiredContext);
        }
    }

    /** Detecte la fin naturelle de la piste en cours. */
    private void updatePlaybackState(Minecraft mc) {
        if (this.playingSound == null) {
            return;
        }
        if (this.graceTicks > 0) {
            this.graceTicks--;
            return;
        }
        if (!mc.getSoundManager().isActive(this.playingSound)) {
            // La piste s'est terminee toute seule : on impose un silence avant
            // d'en relancer une, meme si le contexte n'a pas change.
            this.playingSound = null;
            this.playingContext = MusicContext.NONE;
            this.playingEvent = null;
            this.silenceTicks = rollSilenceTicks();
        }
    }

    // ==================================================================
    //  Disponibilite des pistes
    // ==================================================================

    /**
     * Vrai si le contexte dispose d'une piste effectivement chargee.
     *
     * <p>Un {@code SoundEvent} enregistre mais absent de {@code sounds.json}
     * renvoie {@code null} ici : le contexte est alors ignore et la cascade
     * poursuit vers le niveau suivant. C'est ce mecanisme qui permet de livrer
     * le mod avec une partie seulement des musiques.</p>
     *
     * <p>Le cout est une lecture de table de hachage, negligeable a la cadence
     * ou la cascade est evaluee (2 fois par seconde). Ne pas mettre en cache
     * evite toute invalidation manquee lors d'un rechargement des ressources.</p>
     */
    private static boolean hasTrack(MusicContext context) {
        if (context == MusicContext.NONE) {
            return false;
        }
        final SoundEvent event = context.soundEvent();
        if (event == null) {
            return false;
        }
        return Minecraft.getInstance().getSoundManager().getSoundEvent(event.location()) != null;
    }

    // ==================================================================
    //  Cascade de priorites
    // ==================================================================

    /**
     * Evalue les declencheurs du plus prioritaire au moins prioritaire et
     * renvoie le premier qui correspond et possede une piste. L'ordre des tests
     * est significatif : il implemente la hierarchie Danger, Combat, Sous l'eau,
     * Meteo, Souterrain, Nuit, Biome, Dimension, Classique.
     */
    private MusicContext detectContext(LocalPlayer player, ClientLevel level) {
        if (player.isSpectator() || !player.isAlive()) {
            return MusicContext.NONE;
        }

        // --- 1. Danger vital -------------------------------------------
        if (DynamicMusicConfig.triggerDanger
                && !player.isCreative()
                && player.getHealth() < DynamicMusicConfig.dangerHealth
                && hasTrack(MusicContext.DANGER)) {
            return MusicContext.DANGER;
        }

        // --- 2. Combat et boss ------------------------------------------
        if (DynamicMusicConfig.triggerCombat && !player.isCreative()) {
            MusicContext threat = detectThreat(player, level);
            // Sans piste de boss dediee, un boss reste un combat.
            if (threat == MusicContext.BOSS && !hasTrack(MusicContext.BOSS)) {
                threat = MusicContext.COMBAT;
            }
            if (threat != MusicContext.NONE && hasTrack(threat)) {
                return threat;
            }
        } else {
            this.cachedThreat = MusicContext.NONE;
        }

        // --- 3. Sous l'eau ---------------------------------------------
        // isUnderWater() vaut "les yeux sous la surface". On evite ainsi
        // isEyeInFluid(TagKey), deprecie depuis l'arrivee des types de fluides.
        if (DynamicMusicConfig.triggerUnderwater
                && player.isUnderWater()
                && hasTrack(MusicContext.UNDERWATER)) {
            return MusicContext.UNDERWATER;
        }

        final BlockPos pos = player.blockPosition();
        // canSeeSky s'appuie sur la couche de lumiere celeste : simple lecture
        // de chunk, independante de l'heure du jour, et tres bon marche.
        final boolean seesSky = level.canSeeSky(pos);

        // --- 4. Meteo ---------------------------------------------------
        if (DynamicMusicConfig.triggerWeather && seesSky) {
            if (level.isThundering() && hasTrack(MusicContext.THUNDER)) {
                return MusicContext.THUNDER;
            }
            if (level.isRaining() && hasTrack(MusicContext.RAIN) && hasPrecipitation(level, pos)) {
                return MusicContext.RAIN;
            }
        }

        // --- 5. Souterrain ---------------------------------------------
        if (DynamicMusicConfig.triggerUnderground
                && pos.getY() < UNDERGROUND_MAX_Y
                && !seesSky
                && hasTrack(MusicContext.UNDERGROUND)) {
            return MusicContext.UNDERGROUND;
        }

        // --- 6. Nuit a ciel ouvert ---------------------------------------
        // Exclusif du souterrain, qui exige au contraire un ciel bouche.
        // isNight() renvoie faux dans les dimensions a heure figee : le Nether
        // et l'End sont donc ecartes sans test supplementaire.
        if (DynamicMusicConfig.triggerNight
                && seesSky
                && level.isNight()
                && hasTrack(MusicContext.NIGHT)) {
            return MusicContext.NIGHT;
        }

        // --- 7, 8 et 9. Biome, dimension puis classique ------------------
        return resolveAmbient(level, pos);
    }

    /** Vrai si le biome courant connait des precipitations (pluie ou neige). */
    private static boolean hasPrecipitation(ClientLevel level, BlockPos pos) {
        final Holder<Biome> biome = level.getBiome(pos);
        return biome.value().hasPrecipitation();
    }

    // ==================================================================
    //  Detection des menaces
    // ==================================================================

    /**
     * Scanne les entites hostiles autour du joueur (1 Hz maximum) et renvoie
     * {@link MusicContext#BOSS}, {@link MusicContext#COMBAT} ou
     * {@link MusicContext#NONE}.
     */
    private MusicContext detectThreat(LocalPlayer player, ClientLevel level) {
        if (--this.threatCooldown > 0) {
            return this.cachedThreat;
        }
        this.threatCooldown = THREAT_SCAN_PASSES;

        // Une seule requete spatiale, dimensionnee sur le rayon "boss" ; le
        // rayon de combat classique est ensuite filtre par distance au carre.
        final AABB box = player.getBoundingBox().inflate(BOSS_RADIUS);
        final List<Mob> mobs = level.getEntitiesOfClass(Mob.class, box, HOSTILE_FILTER);

        MusicContext result = MusicContext.NONE;
        int lineOfSightBudget = MAX_LINE_OF_SIGHT_CHECKS;

        for (int i = 0; i < mobs.size(); i++) {
            final Mob mob = mobs.get(i);
            final double distSqr = mob.distanceToSqr(player);

            if (isBoss(mob)) {
                // Un boss dans le rayon etendu l'emporte immediatement.
                result = MusicContext.BOSS;
                break;
            }

            // Deja en combat, ou hors de portee : on poursuit uniquement la
            // recherche d'un boss.
            if (result == MusicContext.COMBAT || distSqr > COMBAT_RADIUS_SQR) {
                continue;
            }

            final boolean useRaycast = lineOfSightBudget > 0;
            if (isThreateningPlayer(mob, player, distSqr, useRaycast)) {
                result = MusicContext.COMBAT;
            } else if (useRaycast) {
                lineOfSightBudget--;
            }
        }

        this.cachedThreat = result;
        return result;
    }

    private static boolean isBoss(Mob mob) {
        return mob instanceof EnderDragon
                || mob instanceof WitherBoss
                || mob instanceof Warden
                || mob instanceof ElderGuardian;
    }

    /**
     * Determine si un mob s'en prend au joueur.
     *
     * <p>Le champ {@code target} d'un {@link Mob} n'est pas synchronise vers le
     * client pour la plupart des entites : {@code getTarget()} y renvoie
     * generalement {@code null}. On l'utilise quand il est disponible, puis on
     * retombe sur deux signaux qui, eux, transitent par les
     * {@code SynchedEntityData} ou sont calculables localement :</p>
     * <ul>
     *   <li>le drapeau "agressif" (bras leves d'un zombie, creeper amorce) ;</li>
     *   <li>le mob oriente vers le joueur, avec ligne de vue degagee.</li>
     * </ul>
     */
    private static boolean isThreateningPlayer(Mob mob, LocalPlayer player, double distSqr, boolean allowRaycast) {
        final LivingEntity target = mob.getTarget();
        if (target != null) {
            return target == player;
        }
        if (mob.isAggressive()) {
            return true;
        }
        if (!allowRaycast || distSqr > HEURISTIC_RADIUS_SQR) {
            return false;
        }
        return isFacing(mob, player) && mob.hasLineOfSight(player);
    }

    /** Vrai si le regard du mob pointe vers le joueur, dans un cone d'environ 60 degres. */
    private static boolean isFacing(Mob mob, Entity target) {
        final Vec3 look = mob.getViewVector(1.0F).normalize();
        Vec3 toTarget = target.getEyePosition().subtract(mob.getEyePosition());
        final double length = toTarget.length();
        if (length < 1.0E-4D) {
            return true;
        }
        toTarget = toTarget.scale(1.0D / length);
        return look.dot(toTarget) > 0.5D;
    }

    // ==================================================================
    //  Ambiance : biome, dimension, classique
    // ==================================================================

    private MusicContext resolveAmbient(ClientLevel level, BlockPos pos) {
        final ResourceKey<Level> dimension = level.dimension();
        final long section = SectionPos.asLong(pos);

        // Cache valide tant que le joueur reste dans la meme section 16x16x16
        // et dans la meme dimension.
        if (section == this.cachedSection && dimension.equals(this.cachedDimension)) {
            return this.cachedAmbient;
        }
        this.cachedSection = section;
        this.cachedDimension = dimension;
        this.cachedAmbient = computeAmbient(level, pos, dimension);
        return this.cachedAmbient;
    }

    private static MusicContext computeAmbient(ClientLevel level, BlockPos pos, ResourceKey<Level> dimension) {
        // --- 7. Biome ---------------------------------------------------
        if (DynamicMusicConfig.triggerBiome) {
            final Holder<Biome> biome = level.getBiome(pos);
            for (int i = 0; i < BIOME_RULES.length; i++) {
                final BiomeRule rule = BIOME_RULES[i];
                // hasTrack est teste en premier : c'est le filtre le moins cher
                // et il elimine d'emblee les categories sans musique.
                if (hasTrack(rule.context()) && biome.is(rule.tag())) {
                    return rule.context();
                }
            }
        }

        // --- 8. Dimension ------------------------------------------------
        if (DynamicMusicConfig.triggerDimension) {
            final MusicContext dim = dimensionContext(dimension);
            if (hasTrack(dim)) {
                return dim;
            }
        }

        // --- 9. Classique : aucun declencheur, dernier repli --------------
        if (DynamicMusicConfig.triggerClassic && hasTrack(MusicContext.CLASSIC)) {
            return MusicContext.CLASSIC;
        }

        // Plus rien a proposer : la musique vanilla reprend la main.
        return MusicContext.NONE;
    }

    private static MusicContext dimensionContext(ResourceKey<Level> dimension) {
        if (Level.NETHER.equals(dimension)) {
            return MusicContext.DIM_NETHER;
        }
        if (Level.END.equals(dimension)) {
            return MusicContext.DIM_END;
        }
        if (Level.OVERWORLD.equals(dimension)) {
            return MusicContext.DIM_OVERWORLD;
        }
        // Dimension moddee inconnue.
        return MusicContext.NONE;
    }

    // ==================================================================
    //  Lecture et fondus
    // ==================================================================

    private void start(Minecraft mc, MusicContext context) {
        startWith(mc, context, resolveTrack(context));
    }

    private void startWith(Minecraft mc, MusicContext context, SoundEvent event) {
        if (event == null) {
            // Contexte sans piste associee : on patiente avant de retenter.
            this.silenceTicks = rollSilenceTicks();
            return;
        }

        final float volume = context.volume() * DynamicMusicConfig.masterVolume;
        final DynamicSoundInstance instance = new DynamicSoundInstance(event, volume, context.fadeInTicks());

        mc.getSoundManager().play(instance);
        this.playingSound = instance;
        this.playingContext = context;
        this.playingEvent = event;
        this.graceTicks = START_GRACE_TICKS;
        this.silenceTicks = 0;

        // Coupe une eventuelle piste vanilla deja lancee.
        if (DynamicMusicConfig.suppressVanillaMusic) {
            mc.getMusicManager().stopPlaying();
        }
    }

    /**
     * Choisit la piste reellement jouee pour un contexte.
     *
     * <p>Sur le palier ambiance uniquement, une musique classique peut se
     * substituer a la piste attendue selon {@code classicChance}. Les morceaux
     * sans declencheur entrent ainsi dans la rotation sans jamais recouvrir une
     * musique de combat ou de danger. Le tirage a lieu au demarrage de la piste,
     * pas a chaque detection : le choix reste donc stable pendant toute la
     * duree du morceau et ne provoque aucun fondu parasite.</p>
     */
    private SoundEvent resolveTrack(MusicContext context) {
        if (context.isAmbient()
                && context != MusicContext.CLASSIC
                && DynamicMusicConfig.triggerClassic
                && DynamicMusicConfig.classicChance > 0.0F
                && this.random.nextFloat() < DynamicMusicConfig.classicChance
                && hasTrack(MusicContext.CLASSIC)) {
            return MusicContext.CLASSIC.soundEvent();
        }
        return context.soundEvent();
    }

    /**
     * Vrai crossfade : la piste sortante continue de jouer en s'attenuant
     * pendant que la nouvelle monte en volume. L'instance sortante n'est plus
     * referencee, elle s'arrete d'elle-meme a la fin de son fondu.
     */
    private void crossfadeTo(Minecraft mc, MusicContext next) {
        final SoundEvent nextEvent = next == MusicContext.NONE ? null : resolveTrack(next);

        // Le nouveau contexte reclame la piste deja en cours : on ne coupe rien
        // et on ne relance rien, on se contente d'adopter le contexte. Sans ce
        // garde-fou, passer d'un biome a un autre qui retombe sur le meme
        // morceau le redemarrerait a zero par dessus lui-meme.
        if (isAlreadyPlaying(nextEvent)) {
            this.playingContext = next;
            return;
        }

        final boolean urgent = next.priority() > this.playingContext.priority();
        this.playingSound.fadeOut(this.playingContext.fadeOutTicks(urgent));

        this.playingSound = null;
        this.playingContext = MusicContext.NONE;
        this.playingEvent = null;
        this.silenceTicks = 0;

        if (nextEvent != null) {
            startWith(mc, next, nextEvent);
        }
    }

    /**
     * Vrai si le morceau vise est deja celui qui joue.
     *
     * <p>La comparaison porte sur le <b>fichier audio</b>, pas sur l'evenement
     * sonore : deux evenements distincts peuvent designer le meme fichier, comme
     * {@code music.underwater} et {@code music.biome.ocean} qui partagent la
     * meme piste. Comparer les evenements laisserait la musique se relancer a
     * zero par dessus elle-meme en plongeant dans l'ocean.</p>
     */
    private boolean isAlreadyPlaying(SoundEvent candidate) {
        if (candidate == null || this.playingSound == null) {
            return false;
        }
        if (candidate == this.playingEvent) {
            return true;
        }
        final Sound playing = this.playingSound.getSound();
        if (playing == null) {
            return false;
        }
        final WeighedSoundEvents events =
                Minecraft.getInstance().getSoundManager().getSoundEvent(candidate.location());
        if (events == null) {
            return false;
        }
        // Pour un evenement a piste unique le tirage est deterministe, donc
        // exact. Pour un evenement a plusieurs variantes, une egalite signifie
        // simplement qu'on garde la piste en cours : c'est le comportement
        // souhaite dans les deux cas.
        final Sound next = events.getSound(this.random);
        return next != null && playing.getLocation().equals(next.getLocation());
    }

    private void stopImmediately(Minecraft mc) {
        if (this.playingSound != null) {
            this.playingSound.requestStop();
            mc.getSoundManager().stop(this.playingSound);
        }
        reset();
    }

    /** Duree de silence aleatoire imposee apres la fin naturelle d'une piste. */
    private int rollSilenceTicks() {
        final int min = DynamicMusicConfig.minSilenceSeconds * 20;
        final int max = DynamicMusicConfig.maxSilenceSeconds * 20;
        return max <= min ? min : min + this.random.nextInt(max - min);
    }

    // ==================================================================
    //  Cycle de vie
    // ==================================================================

    /** Remet le gestionnaire a zero (changement de monde, deconnexion). */
    public void reset() {
        this.playingSound = null;
        this.playingContext = MusicContext.NONE;
        this.playingEvent = null;
        this.desiredContext = MusicContext.NONE;
        this.cachedThreat = MusicContext.NONE;
        this.cachedAmbient = MusicContext.NONE;
        this.cachedSection = Long.MIN_VALUE;
        this.cachedDimension = null;
        this.graceTicks = 0;
        this.silenceTicks = 0;
        this.scanCooldown = 0;
        this.threatCooldown = 0;
    }

    /**
     * Vrai tant qu'une piste du mod occupe le canal musical : sert a annuler la
     * musique d'ambiance vanilla dans {@code PlaySoundEvent}.
     */
    public boolean isSuppressingVanillaMusic() {
        return DynamicMusicConfig.enabled
                && DynamicMusicConfig.suppressVanillaMusic
                && this.playingSound != null;
    }

    /** Contexte actuellement joue (utile pour du debug ou un overlay). */
    public MusicContext currentContext() {
        return this.playingContext;
    }

    /** Trace l'etat interne, uniquement en niveau debug. */
    public void logState() {
        DynamicMusic.LOGGER.debug("[DynamicMusic] contexte joue={} souhaite={} silence={}t",
                this.playingContext, this.desiredContext, this.silenceTicks);
    }
}

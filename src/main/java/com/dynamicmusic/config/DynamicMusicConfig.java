package com.dynamicmusic.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Configuration client (fichier {@code config/dynamicmusic-client.toml}).
 *
 * <p>Les valeurs sont "cuites" (mises en cache dans des champs primitifs) au
 * chargement / rechargement du fichier. La boucle de tick ne touche donc jamais
 * a l'API de configuration, qui passe par une couche NightConfig relativement
 * couteuse pour un acces 20 fois par seconde.</p>
 */
public final class DynamicMusicConfig {

    private DynamicMusicConfig() {
    }

    public static final ForgeConfigSpec SPEC;

    // --- Definitions ---------------------------------------------------
    private static final ForgeConfigSpec.BooleanValue CFG_ENABLED;
    private static final ForgeConfigSpec.DoubleValue CFG_MASTER_VOLUME;
    private static final ForgeConfigSpec.IntValue CFG_FADE_TICKS;
    private static final ForgeConfigSpec.IntValue CFG_MIN_SILENCE_SECONDS;
    private static final ForgeConfigSpec.IntValue CFG_MAX_SILENCE_SECONDS;
    private static final ForgeConfigSpec.BooleanValue CFG_SUPPRESS_VANILLA;

    private static final ForgeConfigSpec.BooleanValue CFG_SHOW_TOAST;
    private static final ForgeConfigSpec.IntValue CFG_TOAST_SECONDS;
    private static final ForgeConfigSpec.BooleanValue CFG_PIN_TOAST_IN_OPTIONS;

    private static final ForgeConfigSpec.DoubleValue CFG_DANGER_HEALTH;
    private static final ForgeConfigSpec.BooleanValue CFG_TRIGGER_DANGER;
    private static final ForgeConfigSpec.BooleanValue CFG_TRIGGER_COMBAT;
    private static final ForgeConfigSpec.BooleanValue CFG_TRIGGER_UNDERWATER;
    private static final ForgeConfigSpec.BooleanValue CFG_TRIGGER_WEATHER;
    private static final ForgeConfigSpec.BooleanValue CFG_TRIGGER_NIGHT;
    private static final ForgeConfigSpec.BooleanValue CFG_TRIGGER_UNDERGROUND;
    private static final ForgeConfigSpec.BooleanValue CFG_TRIGGER_BIOME;
    private static final ForgeConfigSpec.BooleanValue CFG_TRIGGER_DIMENSION;
    private static final ForgeConfigSpec.BooleanValue CFG_TRIGGER_CLASSIC;
    private static final ForgeConfigSpec.DoubleValue CFG_CLASSIC_CHANCE;

    // --- Valeurs mises en cache (lues par le tick client) --------------
    public static boolean enabled = true;
    public static float masterVolume = 1.0F;
    public static int fadeTicks = 40;
    public static int minSilenceSeconds = 20;
    public static int maxSilenceSeconds = 90;
    public static boolean suppressVanillaMusic = true;

    public static boolean showToast = true;
    public static int toastSeconds = 7;
    public static boolean pinToastInOptions = true;

    public static float dangerHealth = 6.0F;
    public static boolean triggerDanger = true;
    public static boolean triggerCombat = true;
    public static boolean triggerUnderwater = true;
    public static boolean triggerWeather = true;
    public static boolean triggerNight = true;
    public static boolean triggerUnderground = true;
    public static boolean triggerBiome = true;
    public static boolean triggerDimension = true;
    public static boolean triggerClassic = true;
    public static float classicChance = 0.35F;

    static {
        final ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Reglages generaux de DynamicMusic").push("general");
        CFG_ENABLED = b.comment("Active ou desactive completement le systeme de musique dynamique.")
                .define("enabled", true);
        CFG_MASTER_VOLUME = b.comment("Volume maximal des pistes du mod (multiplie par le volume 'Musique' du jeu).")
                .defineInRange("masterVolume", 1.0D, 0.0D, 1.0D);
        CFG_FADE_TICKS = b.comment("Duree de reference d'un fondu, en ticks (20 ticks = 1 seconde).",
                        "Chaque contexte applique un facteur rapide / normal / lent sur cette valeur.")
                .defineInRange("fadeTicks", 40, 1, 400);
        CFG_MIN_SILENCE_SECONDS = b.comment("Silence minimal apres la fin naturelle d'une piste (anti-repetition).")
                .defineInRange("minSilenceSeconds", 20, 0, 3600);
        CFG_MAX_SILENCE_SECONDS = b.comment("Silence maximal apres la fin naturelle d'une piste (anti-repetition).")
                .defineInRange("maxSilenceSeconds", 90, 0, 3600);
        CFG_SUPPRESS_VANILLA = b.comment("Coupe la musique vanilla tant qu'une piste du mod est en cours de lecture.")
                .define("suppressVanillaMusic", true);
        b.pop();

        b.comment("Vignette affichee en haut a droite au demarrage d'un morceau").push("toast");
        CFG_SHOW_TOAST = b.comment("Affiche la vignette (pochette, titre, artiste, duree).",
                        "Vaut aussi pour les musiques du jeu de base, pas seulement celles du mod.")
                .define("show", true);
        CFG_TOAST_SECONDS = b.comment("Duree d'affichage en secondes.")
                .defineInRange("displaySeconds", 7, 1, 60);
        CFG_PIN_TOAST_IN_OPTIONS = b.comment("Maintient la vignette affichee en permanence dans l'ecran des",
                        "options et ses sous-ecrans, pour garder les informations sous les yeux",
                        "pendant le reglage du volume.")
                .define("pinInOptionsScreen", true);
        b.pop();

        b.comment("Activation des declencheurs contextuels").push("triggers");
        CFG_DANGER_HEALTH = b.comment("Seuil de points de vie en dessous duquel la piste 'danger' se declenche (6.0 = 3 coeurs).")
                .defineInRange("dangerHealth", 6.0D, 0.5D, 20.0D);
        CFG_TRIGGER_DANGER = b.define("danger", true);
        CFG_TRIGGER_COMBAT = b.comment("Detection des monstres agressifs et des boss (seul declencheur qui scanne les entites).")
                .define("combat", true);
        CFG_TRIGGER_UNDERWATER = b.define("underwater", true);
        CFG_TRIGGER_WEATHER = b.define("weather", true);
        CFG_TRIGGER_NIGHT = b.comment("Musique de nuit, uniquement a ciel ouvert et dans l'Overworld.")
                .define("night", true);
        CFG_TRIGGER_UNDERGROUND = b.define("underground", true);
        CFG_TRIGGER_BIOME = b.define("biome", true);
        CFG_TRIGGER_DIMENSION = b.define("dimension", true);
        CFG_TRIGGER_CLASSIC = b.comment("Pistes classiques : morceaux sans declencheur, joues en repli general.")
                .define("classic", true);
        CFG_CLASSIC_CHANCE = b.comment("Probabilite qu'une piste classique remplace une musique d'ambiance",
                        "(biome ou dimension). 0 = jamais, 1 = toujours. Sans effet sur le combat ou le danger.")
                .defineInRange("classicChance", 0.35D, 0.0D, 1.0D);
        b.pop();

        SPEC = b.build();
    }

    /** Recopie les valeurs du fichier dans les champs primitifs. */
    public static void bake() {
        enabled = CFG_ENABLED.get();
        masterVolume = CFG_MASTER_VOLUME.get().floatValue();
        fadeTicks = CFG_FADE_TICKS.get();
        minSilenceSeconds = CFG_MIN_SILENCE_SECONDS.get();
        maxSilenceSeconds = Math.max(minSilenceSeconds, CFG_MAX_SILENCE_SECONDS.get());
        suppressVanillaMusic = CFG_SUPPRESS_VANILLA.get();

        showToast = CFG_SHOW_TOAST.get();
        toastSeconds = CFG_TOAST_SECONDS.get();
        pinToastInOptions = CFG_PIN_TOAST_IN_OPTIONS.get();

        dangerHealth = CFG_DANGER_HEALTH.get().floatValue();
        triggerDanger = CFG_TRIGGER_DANGER.get();
        triggerCombat = CFG_TRIGGER_COMBAT.get();
        triggerUnderwater = CFG_TRIGGER_UNDERWATER.get();
        triggerWeather = CFG_TRIGGER_WEATHER.get();
        triggerNight = CFG_TRIGGER_NIGHT.get();
        triggerUnderground = CFG_TRIGGER_UNDERGROUND.get();
        triggerBiome = CFG_TRIGGER_BIOME.get();
        triggerDimension = CFG_TRIGGER_DIMENSION.get();
        triggerClassic = CFG_TRIGGER_CLASSIC.get();
        classicChance = CFG_CLASSIC_CHANCE.get().floatValue();
    }
}

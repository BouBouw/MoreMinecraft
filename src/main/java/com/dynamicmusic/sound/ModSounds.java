package com.dynamicmusic.sound;

import com.dynamicmusic.DynamicMusic;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Declaration de tous les {@link SoundEvent} du mod via un {@link DeferredRegister}.
 *
 * <p>Le nom d'enregistrement correspond exactement a la cle utilisee dans
 * {@code assets/dynamicmusic/sounds.json}. Exemple : {@code music.biome.ocean}
 * donne le ResourceLocation {@code dynamicmusic:music.biome.ocean}.</p>
 *
 * <p><b>Tous les evenements ci-dessous sont enregistres, mais seuls ceux
 * declares dans {@code sounds.json} disposent reellement d'une piste.</b>
 * Un contexte sans piste est simplement ignore par la cascade de priorites, qui
 * passe au niveau suivant. Ajouter une musique se resume donc a deposer un
 * fichier {@code .ogg} et a ajouter une entree dans {@code sounds.json} : aucun
 * code Java a modifier.</p>
 *
 * <p>Toutes les pistes sont declarees avec {@code "stream": true} : les fichiers
 * OGG longs sont decodes a la volee au lieu d'etre charges integralement en
 * memoire, ce qui evite les pics d'allocation et les micro-freezes.</p>
 */
public final class ModSounds {

    private ModSounds() {
    }

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, DynamicMusic.MOD_ID);

    // ------------------------------------------------------------------
    // Pistes "classiques" : aucun declencheur, elles s'ajoutent a la
    // rotation d'ambiance et servent de repli general.
    // ------------------------------------------------------------------
    public static final RegistryObject<SoundEvent> MUSIC_CLASSIC = register("music.classic");

    // ------------------------------------------------------------------
    // Priorite 1 - Danger vital (sante basse)
    // ------------------------------------------------------------------
    public static final RegistryObject<SoundEvent> MUSIC_DANGER = register("music.danger");

    // ------------------------------------------------------------------
    // Priorite 2 - Combat / Boss
    // ------------------------------------------------------------------
    public static final RegistryObject<SoundEvent> MUSIC_BOSS = register("music.boss");
    public static final RegistryObject<SoundEvent> MUSIC_COMBAT = register("music.combat");

    // ------------------------------------------------------------------
    // Priorite 3 - Sous l'eau
    // ------------------------------------------------------------------
    public static final RegistryObject<SoundEvent> MUSIC_UNDERWATER = register("music.underwater");

    // ------------------------------------------------------------------
    // Priorite 4 - Meteo
    // ------------------------------------------------------------------
    public static final RegistryObject<SoundEvent> MUSIC_THUNDER = register("music.thunder");
    public static final RegistryObject<SoundEvent> MUSIC_RAIN = register("music.rain");

    // ------------------------------------------------------------------
    // Priorite 6 - Nuit, a ciel ouvert
    // ------------------------------------------------------------------
    public static final RegistryObject<SoundEvent> MUSIC_NIGHT = register("music.night");

    // ------------------------------------------------------------------
    // Priorite 5 - Souterrain / minage
    // ------------------------------------------------------------------
    public static final RegistryObject<SoundEvent> MUSIC_UNDERGROUND = register("music.underground");

    // ------------------------------------------------------------------
    // Priorite 7 - Biomes (tags du mod, voir ModBiomeTags)
    // ------------------------------------------------------------------
    public static final RegistryObject<SoundEvent> MUSIC_BIOME_DEEP_DARK = register("music.biome.deep_dark");
    public static final RegistryObject<SoundEvent> MUSIC_BIOME_LUSH_CAVE = register("music.biome.lush_cave");
    public static final RegistryObject<SoundEvent> MUSIC_BIOME_MUSHROOM = register("music.biome.mushroom");
    public static final RegistryObject<SoundEvent> MUSIC_BIOME_CHERRY = register("music.biome.cherry");
    public static final RegistryObject<SoundEvent> MUSIC_BIOME_SNOWY = register("music.biome.snowy");
    public static final RegistryObject<SoundEvent> MUSIC_BIOME_DESERT = register("music.biome.desert");
    public static final RegistryObject<SoundEvent> MUSIC_BIOME_BADLANDS = register("music.biome.badlands");
    public static final RegistryObject<SoundEvent> MUSIC_BIOME_SWAMP = register("music.biome.swamp");
    public static final RegistryObject<SoundEvent> MUSIC_BIOME_JUNGLE = register("music.biome.jungle");
    public static final RegistryObject<SoundEvent> MUSIC_BIOME_SAVANNA = register("music.biome.savanna");
    public static final RegistryObject<SoundEvent> MUSIC_BIOME_BEACH = register("music.biome.beach");
    public static final RegistryObject<SoundEvent> MUSIC_BIOME_RIVER = register("music.biome.river");
    public static final RegistryObject<SoundEvent> MUSIC_BIOME_OCEAN = register("music.biome.ocean");
    public static final RegistryObject<SoundEvent> MUSIC_BIOME_TAIGA = register("music.biome.taiga");
    public static final RegistryObject<SoundEvent> MUSIC_BIOME_FOREST = register("music.biome.forest");
    public static final RegistryObject<SoundEvent> MUSIC_BIOME_MOUNTAIN = register("music.biome.mountain");
    public static final RegistryObject<SoundEvent> MUSIC_BIOME_PLAINS = register("music.biome.plains");

    // ------------------------------------------------------------------
    // Priorite 8 - Dimensions
    // ------------------------------------------------------------------
    public static final RegistryObject<SoundEvent> MUSIC_DIM_OVERWORLD = register("music.dimension.overworld");
    public static final RegistryObject<SoundEvent> MUSIC_DIM_NETHER = register("music.dimension.nether");
    public static final RegistryObject<SoundEvent> MUSIC_DIM_END = register("music.dimension.end");

    /**
     * Cree un SoundEvent a portee variable : la portee reelle est definie par
     * l'instance sonore. Nos musiques utilisent {@code Attenuation.NONE}, la
     * portee n'a donc aucun impact sur elles.
     */
    private static RegistryObject<SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name,
                () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(DynamicMusic.MOD_ID, name)));
    }

    /** Branche le DeferredRegister sur le bus d'evenements du mod. */
    public static void init(BusGroup modBus) {
        SOUND_EVENTS.register(modBus);
    }
}

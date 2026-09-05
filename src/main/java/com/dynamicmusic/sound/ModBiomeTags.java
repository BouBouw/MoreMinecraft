package com.dynamicmusic.sound;

import com.dynamicmusic.DynamicMusic;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/**
 * Tags de biomes appartenant au mod, definis dans
 * {@code data/dynamicmusic/tags/worldgen/biome/}.
 *
 * <p>Le mod n'utilise volontairement pas les tags vanilla directement : passer
 * par ses propres tags permet</p>
 * <ul>
 *   <li>de couvrir des regroupements qui n'existent pas en vanilla (plaines,
 *       biomes enneiges, marais, desert) ;</li>
 *   <li>de laisser un datapack ajouter un biome moddé a une categorie musicale
 *       sans recompiler quoi que ce soit.</li>
 * </ul>
 *
 * <p>La plupart des fichiers se contentent de referencer le tag vanilla
 * equivalent, par exemple {@code "#minecraft:is_forest"}.</p>
 */
public final class ModBiomeTags {

    private ModBiomeTags() {
    }

    // Categories souterraines et rares : testees en premier car elles
    // recoupent souvent des categories plus larges.
    public static final TagKey<Biome> IS_DEEP_DARK = create("is_deep_dark");
    public static final TagKey<Biome> IS_LUSH_CAVE = create("is_lush_cave");
    public static final TagKey<Biome> IS_MUSHROOM = create("is_mushroom");
    public static final TagKey<Biome> IS_CHERRY = create("is_cherry");

    // Climat : prioritaire sur la vegetation (une taiga enneigee sonne
    // "enneigee" avant de sonner "taiga").
    public static final TagKey<Biome> IS_SNOWY = create("is_snowy");
    public static final TagKey<Biome> IS_DESERT = create("is_desert");
    public static final TagKey<Biome> IS_BADLANDS = create("is_badlands");
    public static final TagKey<Biome> IS_SWAMP = create("is_swamp");

    // Vegetation et relief.
    public static final TagKey<Biome> IS_JUNGLE = create("is_jungle");
    public static final TagKey<Biome> IS_SAVANNA = create("is_savanna");
    public static final TagKey<Biome> IS_BEACH = create("is_beach");
    public static final TagKey<Biome> IS_RIVER = create("is_river");
    public static final TagKey<Biome> IS_OCEAN = create("is_ocean");
    public static final TagKey<Biome> IS_TAIGA = create("is_taiga");
    public static final TagKey<Biome> IS_FOREST = create("is_forest");
    public static final TagKey<Biome> IS_MOUNTAIN = create("is_mountain");
    public static final TagKey<Biome> IS_PLAINS = create("is_plains");

    private static TagKey<Biome> create(String name) {
        return TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(DynamicMusic.MOD_ID, name));
    }
}

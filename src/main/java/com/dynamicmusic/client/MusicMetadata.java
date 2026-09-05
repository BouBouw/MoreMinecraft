package com.dynamicmusic.client;

import com.dynamicmusic.DynamicMusic;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.GsonHelper;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Charge {@code assets/<namespace>/music_metadata.json} et fournit les
 * proprietes d'une piste a la vignette.
 *
 * <p>La cle d'une entree est l'emplacement du <b>fichier audio</b>, pas celui de
 * l'evenement sonore. Un evenement vanilla comme {@code minecraft:music.game}
 * tire au sort parmi plusieurs morceaux : indexer par fichier est le seul moyen
 * d'afficher le bon titre.</p>
 *
 * <p>Le fichier est lu via {@code getResourceStack}, donc un pack de ressources
 * peut completer ou remplacer la table sans toucher au mod. Une piste absente
 * reste affichee : le titre est alors deduit du nom de fichier et la pochette
 * par defaut est utilisee.</p>
 */
public final class MusicMetadata implements ResourceManagerReloadListener {

    public static final MusicMetadata INSTANCE = new MusicMetadata();

    private static final Identifier FILE =
            Identifier.fromNamespaceAndPath(DynamicMusic.MOD_ID, "music_metadata.json");

    public static final Identifier DEFAULT_COVER =
            Identifier.fromNamespaceAndPath(DynamicMusic.MOD_ID, "textures/gui/music/default.png");

    /** Table declaree, indexee par emplacement de fichier audio. */
    private final Map<Identifier, MusicInfo> tracks = new HashMap<>();
    /** Valeurs par defaut par namespace, pour les pistes non declarees. */
    private final Map<String, NamespaceDefaults> namespaces = new HashMap<>();
    /** Cache des fiches deduites, pour ne pas les reconstruire a chaque morceau. */
    private final Map<Identifier, MusicInfo> derived = new HashMap<>();

    private record NamespaceDefaults(String artist, Identifier cover) {
    }

    private MusicMetadata() {
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        this.tracks.clear();
        this.namespaces.clear();
        this.derived.clear();

        // getResourceStack renvoie la pile complete : le pack le plus prioritaire
        // est lu en dernier et ecrase les entrees de meme cle.
        for (Resource resource : manager.getResourceStack(FILE)) {
            try (BufferedReader reader = resource.openAsReader()) {
                read(GsonHelper.parse(reader));
            } catch (Exception e) {
                DynamicMusic.LOGGER.error("[DynamicMusic] Lecture de {} impossible depuis {}",
                        FILE, resource.sourcePackId(), e);
            }
        }

        DynamicMusic.LOGGER.info("[DynamicMusic] {} piste(s) decrite(s) dans music_metadata.json",
                this.tracks.size());
    }

    private void read(JsonObject root) {
        if (root.has("namespaces")) {
            final JsonObject section = GsonHelper.getAsJsonObject(root, "namespaces");
            for (String namespace : section.keySet()) {
                final JsonObject entry = GsonHelper.getAsJsonObject(section, namespace);
                this.namespaces.put(namespace, new NamespaceDefaults(
                        GsonHelper.getAsString(entry, "artist", ""),
                        readCover(entry)));
            }
        }

        if (!root.has("tracks")) {
            return;
        }
        final JsonObject section = GsonHelper.getAsJsonObject(root, "tracks");
        for (String key : section.keySet()) {
            final Identifier id = Identifier.tryParse(key);
            if (id == null) {
                DynamicMusic.LOGGER.warn("[DynamicMusic] Cle de piste invalide : {}", key);
                continue;
            }
            final JsonObject entry = GsonHelper.getAsJsonObject(section, key);
            final Identifier cover = readCover(entry);
            this.tracks.put(id, new MusicInfo(
                    id,
                    GsonHelper.getAsString(entry, "title", fallbackTitle(id)),
                    GsonHelper.getAsString(entry, "artist", ""),
                    GsonHelper.getAsString(entry, "album", ""),
                    Math.max(0, GsonHelper.getAsInt(entry, "duration", 0)),
                    cover != null ? cover : DEFAULT_COVER));
        }
    }

    private static Identifier readCover(JsonObject entry) {
        final String raw = GsonHelper.getAsString(entry, "cover", "");
        return raw.isEmpty() ? null : Identifier.tryParse(raw);
    }

    /**
     * Fiche d'une piste. Ne renvoie jamais {@code null} : une piste inconnue
     * recoit un titre deduit de son nom de fichier.
     */
    public MusicInfo get(Identifier trackId) {
        final MusicInfo declared = this.tracks.get(trackId);
        if (declared != null) {
            return declared;
        }
        return this.derived.computeIfAbsent(trackId, MusicMetadata::derive);
    }

    private static MusicInfo derive(Identifier id) {
        final NamespaceDefaults defaults = INSTANCE.namespaces.get(id.getNamespace());
        final String artist = defaults != null ? defaults.artist() : prettify(id.getNamespace());
        final Identifier cover = defaults != null && defaults.cover() != null
                ? defaults.cover()
                : DEFAULT_COVER;
        return new MusicInfo(id, fallbackTitle(id), artist, "", 0, cover);
    }

    /** {@code minecraft:music/game/calm1} devient {@code Calm 1}. */
    private static String fallbackTitle(Identifier id) {
        final String path = id.getPath();
        final int slash = path.lastIndexOf('/');
        return prettify(slash >= 0 ? path.substring(slash + 1) : path);
    }

    /** Met en forme un identifiant technique : separateurs enleves, mots capitalises, chiffres detaches. */
    private static String prettify(String raw) {
        final StringBuilder out = new StringBuilder(raw.length() + 4);
        boolean startOfWord = true;
        char previous = 0;

        for (int i = 0; i < raw.length(); i++) {
            final char c = raw.charAt(i);
            if (c == '_' || c == '-' || c == '.') {
                out.append(' ');
                startOfWord = true;
                previous = ' ';
                continue;
            }
            // "calm1" devient "calm 1"
            if (Character.isDigit(c) && Character.isLetter(previous)) {
                out.append(' ');
                startOfWord = true;
            }
            out.append(startOfWord ? Character.toUpperCase(c) : c);
            startOfWord = false;
            previous = c;
        }
        return out.length() == 0 ? raw.toUpperCase(Locale.ROOT) : out.toString();
    }
}

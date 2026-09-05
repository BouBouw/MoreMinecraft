package com.dynamicmusic.client;

import net.minecraft.resources.Identifier;

/**
 * Proprietes affichees par la vignette pour une piste donnee.
 *
 * @param id       emplacement du fichier audio, tel que Minecraft le voit
 *                 (par exemple {@code dynamicmusic:music/classic})
 * @param title    titre du morceau
 * @param artist   interprete, chaine vide si inconnu
 * @param album    album, chaine vide si inconnu
 * @param duration duree en secondes, 0 si inconnue (la barre de progression est
 *                 alors masquee)
 * @param cover    texture de la pochette, 64x64
 */
public record MusicInfo(Identifier id,
                        String title,
                        String artist,
                        String album,
                        int duration,
                        Identifier cover) {

    public boolean hasArtist() {
        return !this.artist.isEmpty();
    }

    public boolean hasDuration() {
        return this.duration > 0;
    }

    /** Formate une duree en secondes sous la forme {@code m:ss}. */
    public static String formatTime(int seconds) {
        final int safe = Math.max(0, seconds);
        return (safe / 60) + ":" + (safe % 60 < 10 ? "0" : "") + (safe % 60);
    }
}

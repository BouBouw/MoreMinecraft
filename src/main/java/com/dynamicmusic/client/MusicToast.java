package com.dynamicmusic.client;

import com.dynamicmusic.config.DynamicMusicConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Vignette affichee en haut a droite de l'ecran.
 *
 * <p>Elle existe sous deux formes qui partagent exactement le meme dessin :</p>
 * <ul>
 *   <li>une <b>notification</b>, sur le systeme du jeu, qui glisse a l'entree et
 *       a la sortie et s'efface au bout de quelques secondes ;</li>
 *   <li>un <b>panneau epingle</b>, dessine directement dans les ecrans
 *       d'options, tant que l'ecran reste ouvert.</li>
 * </ul>
 *
 * <p>Le panneau epingle ne passe volontairement pas par le systeme de
 * notifications. Celui-ci met les vignettes en file, leur alloue des
 * emplacements et les retire au bout d'un temps : autant de conditions a reunir
 * pour un affichage qui doit simplement rester la. Dessiner dans l'evenement de
 * rendu de l'ecran est deterministe et ne depend de rien.</p>
 *
 * <p>Les deux formes ne coexistent jamais : la notification s'efface des qu'un
 * ecran d'options est ouvert.</p>
 *
 * <p>Depuis 1.21.2, le systeme de notifications est scinde en deux : {@code update}
 * decide de la visibilite, {@code render} ne fait plus que dessiner. Le
 * gestionnaire s'appelle desormais {@code ToastManager} et le dessin passe par
 * une fonction de type de rendu.</p>
 */
public final class MusicToast implements Toast {

    /** Jeton d'unicite : garantit qu'une seule notification musicale coexiste. */
    public static final Object TOKEN = new Object();

    /** Fond des notifications du jeu, decoupe en neuf tranches donc etirable. */
    private static final Identifier BACKGROUND_SPRITE = Identifier.withDefaultNamespace("toast/advancement");

    private static final int WIDTH = 200;
    private static final int HEIGHT = 48;

    private static final int COVER_SIZE = 32;
    private static final int COVER_TEXTURE_SIZE = 64;
    private static final int PADDING = 8;
    private static final int TEXT_X = PADDING + COVER_SIZE + 6;
    /** Marge du panneau epingle par rapport au coin de l'ecran. */
    private static final int PINNED_MARGIN = 4;

    private static final int COLOR_TITLE = 0xFFFFFFFF;
    private static final int COLOR_ARTIST = 0xFFB4A8E0;
    private static final int COLOR_TIME = 0xFF808080;
    private static final int COLOR_BAR_BG = 0xFF3A3A3A;
    private static final int COLOR_BAR_FG = 0xFFB4A8E0;

    /** Visibilite demandee au gestionnaire, calculee dans update. */
    private Toast.Visibility wanted = Toast.Visibility.SHOW;
    /** Derniere generation vue : un changement relance le minuteur d'affichage. */
    private long seenGeneration = -1L;
    /** Instant, en millisecondes de visibilite, ou le minuteur a ete relance. */
    private long lastChanged;

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return HEIGHT;
    }

    @Override
    public Object getToken() {
        return TOKEN;
    }

    // ==================================================================
    //  Forme notification
    // ==================================================================

    /** Place la notification dans la file si elle n'y est pas deja. */
    public static void ensureVisible(Minecraft mc) {
        final ToastManager toasts = mc.getToastManager();
        if (toasts.getToast(MusicToast.class, TOKEN) == null) {
            toasts.addToast(new MusicToast());
        }
    }

    @Override
    public Toast.Visibility getWantedVisibility() {
        return this.wanted;
    }

    /** Toute la decision d'affichage vit ici depuis 1.21.2, le rendu ne fait plus que dessiner. */
    @Override
    public void update(ToastManager manager, long visibleMillis) {
        // Volume musique a zero : rien a annoncer.
        // Ecran d'options ouvert : le panneau epingle prend le relais, la
        // notification s'efface pour ne pas s'afficher en double.
        if (MusicNotifier.isMusicMuted()
                || MusicNotifier.INSTANCE.isPinned()
                || MusicNotifier.INSTANCE.current() == null) {
            this.wanted = Toast.Visibility.HIDE;
            return;
        }

        // Nouveau morceau : le minuteur repart, la vignette reste a l'ecran.
        final long generation = MusicNotifier.INSTANCE.generation();
        if (generation != this.seenGeneration) {
            this.seenGeneration = generation;
            this.lastChanged = visibleMillis;
        }

        final long displayMillis = DynamicMusicConfig.toastSeconds * 1000L;
        this.wanted = visibleMillis - this.lastChanged >= displayMillis
                ? Toast.Visibility.HIDE
                : Toast.Visibility.SHOW;
    }

    /**
     * Depuis 26.1 le dessin ne s'appelle plus render mais extractRenderState :
     * l'interface ne peint plus directement, elle alimente un extracteur d'etat
     * de rendu.
     */
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long visibleMillis) {
        final MusicInfo info = MusicNotifier.INSTANCE.current();
        if (info != null) {
            drawPanel(graphics, font, info, true);
        }
    }

    // ==================================================================
    //  Forme panneau epingle
    // ==================================================================

    /**
     * Dessine le panneau dans le coin haut droit d'un ecran d'options. Appele
     * depuis l'evenement de rendu de l'ecran, donc a chaque image tant que
     * l'ecran est affiche.
     */
    public static void renderPinned(GuiGraphicsExtractor graphics) {
        if (MusicNotifier.isMusicMuted()) {
            return;
        }
        final MusicInfo info = MusicNotifier.INSTANCE.displayInfo();
        if (info == null) {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        final int x = graphics.guiWidth() - WIDTH - PINNED_MARGIN;

        // Depuis 1.21.6 la pile de transformations de l'interface est en deux
        // dimensions : plus de composante de profondeur, et pushMatrix remplace
        // pushPose. Le panneau est de toute facon dessine apres l'ecran.
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) x, (float) PINNED_MARGIN);
        drawPanel(graphics, mc.font, info, MusicNotifier.INSTANCE.isLive());
        graphics.pose().popMatrix();
    }

    // ==================================================================
    //  Dessin commun
    // ==================================================================

    /**
     * Dessine le panneau a l'origine courante du repere.
     *
     * @param live vrai si la piste joue reellement. Sinon la barre de
     *             progression laisse place a l'album : pendant les silences
     *             entre deux morceaux, l'ecran des options montre la derniere
     *             piste jouee plutot que rien du tout.
     */
    private static void drawPanel(GuiGraphicsExtractor graphics, Font font, MusicInfo info, boolean live) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND_SPRITE, 0, 0, WIDTH, HEIGHT);

        // Pochette : texture 64x64 reduite a 32x32.
        graphics.blit(RenderPipelines.GUI_TEXTURED, info.cover(), PADDING, PADDING,
                0.0F, 0.0F, COVER_SIZE, COVER_SIZE,
                COVER_TEXTURE_SIZE, COVER_TEXTURE_SIZE,
                COVER_TEXTURE_SIZE, COVER_TEXTURE_SIZE);

        final int textWidth = WIDTH - TEXT_X - PADDING;

        graphics.text(font, ellipsize(font, info.title(), textWidth), TEXT_X, 9, COLOR_TITLE, false);

        if (info.hasArtist()) {
            graphics.text(font, ellipsize(font, info.artist(), textWidth), TEXT_X, 20, COLOR_ARTIST, false);
        }

        if (live && info.hasDuration()) {
            final String time = MusicInfo.formatTime(MusicNotifier.INSTANCE.elapsedSeconds())
                    + " / " + MusicInfo.formatTime(info.duration());
            graphics.text(font, time, TEXT_X, 31, COLOR_TIME, false);
            drawProgressBar(graphics, TEXT_X, 42, textWidth, MusicNotifier.INSTANCE.progress());
        } else if (!info.album().isEmpty()) {
            graphics.text(font, ellipsize(font, info.album(), textWidth), TEXT_X, 31, COLOR_TIME, false);
        } else if (info.hasDuration()) {
            graphics.text(font, MusicInfo.formatTime(info.duration()), TEXT_X, 31, COLOR_TIME, false);
        }
    }

    private static void drawProgressBar(GuiGraphicsExtractor graphics, int x, int y, int width, float progress) {
        if (progress < 0.0F) {
            return;
        }
        graphics.fill(x, y, x + width, y + 2, COLOR_BAR_BG);
        final int filled = Math.round(width * Math.min(1.0F, Math.max(0.0F, progress)));
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + 2, COLOR_BAR_FG);
        }
    }

    /** Tronque le texte a la largeur disponible et ajoute des points de suspension. */
    private static String ellipsize(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        final String dots = "...";
        return font.plainSubstrByWidth(text, maxWidth - font.width(dots)) + dots;
    }
}

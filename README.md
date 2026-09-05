# DynamicMusic

Cette branche cible **Forge 1.20.4**.

Le mod existe sur **19 branches**, une par version de Minecraft, de 1.20.2
jusqu'a 26.2. Toutes compilent et 18 sur 19 ont vu leur chargement verifie en
lancant le jeu. Voir [PORTING.md](PORTING.md) pour le tableau complet, les
quatre ruptures d'API traversees et la marche a suivre pour ajouter une cible.

Mod **client uniquement** pour Minecraft Forge 1.20.4. Il remplace la musique
d'ambiance vanilla par un systeme contextuel avec fondu croise, et annonce
chaque morceau par une vignette en haut a droite de l'ecran.

## Vignette musicale

Des qu'une musique demarre, une vignette glisse depuis le bord droit, comme une
notification de progres. Elle affiche la pochette, le titre, l'artiste, le temps
ecoule sur la duree totale et une barre de progression.

Elle apparait pour **toute** musique, celle du mod comme celle du jeu de base,
menu principal compris. Un morceau vanilla non decrit dans la table de
metadonnees reste affiche : le titre est alors deduit du nom de fichier et la
pochette par defaut est utilisee.

Duree d'affichage par defaut : 7 secondes, reglable de 1 a 60.

**Dans les ecrans d'options, un panneau reste affiche en permanence**, y compris
dans le sous-ecran du volume, pour garder les informations du morceau sous les
yeux pendant le reglage du son.

Ce panneau ne passe pas par le systeme de notifications. Celui-ci met les
vignettes en file, leur alloue des emplacements et les retire au bout d'un
temps : autant de conditions a reunir pour un affichage qui doit simplement
rester la. Le panneau est donc dessine directement dans l'evenement de rendu de
l'ecran, ce qui est deterministe. La notification s'efface pendant ce temps,
les deux ne se superposent jamais.

Pendant les silences entre deux morceaux, le panneau montre la derniere piste
jouee au lieu de rester vide. La barre de progression laisse alors place a
l'album.

Le volume, lui, n'est pas touche par le mod. Les pistes sont jouees dans la
categorie Musique et suivent donc les reglages du jeu.

**Curseur Musique a zero, pas de vignette.** Ni en jeu, ni dans les ecrans
d'options. Le volume general a zero compte aussi, puisqu'il rend le curseur
Musique sans effet. Le test est refait a chaque image : descendre le curseur a
zero fait disparaitre le panneau immediatement, le remonter le fait revenir.

## Metadonnees des pistes

Elles vivent dans
[music_metadata.json](src/main/resources/assets/dynamicmusic/music_metadata.json).
La cle est l'emplacement du **fichier audio**, pas celui de l'evenement sonore.
Un evenement vanilla comme `minecraft:music.game` tire au sort parmi plusieurs
morceaux, donc indexer par fichier est le seul moyen d'afficher le bon titre.

Le fichier est lu sur toute la pile des packs de ressources : un pack peut
completer ou remplacer la table sans toucher au mod.

| Champ | Role |
| --- | --- |
| `title` | Titre affiche en blanc |
| `artist` | Ligne secondaire, masquee si vide |
| `album` | Affiche a la place du temps quand la duree est inconnue |
| `duration` | Secondes. A zero, la barre de progression disparait |
| `cover` | Texture 64x64, reduite a 32x32 dans la vignette |

Les titres, artistes et durees des six pistes installees ont ete lus directement
dans les tags Vorbis des fichiers OGG, ils sont donc exacts. Les pochettes sont
des visuels generes, a remplacer par les vraies quand tu les auras.

## Cascade de priorites

L'etat du joueur est evalue toutes les 10 ticks (0,5 s) du plus prioritaire au
moins prioritaire. Le premier niveau qui est a la fois vrai **et** pourvu d'une
piste gagne. Un niveau sans musique est simplement saute.

| Rang | Contexte | Condition | Piste actuelle |
| --- | --- | --- | --- |
| 1 | Danger | Sante sous le seuil configure (6 HP = 3 coeurs) | a fournir |
| 2 | Boss | Ender Dragon, Wither, Warden ou Elder Guardian a moins de 48 blocs | a fournir |
| 3 | Combat | Mob hostile visant le joueur a moins de 16 blocs | a fournir |
| 4 | Sous l'eau | Tete immergee dans l'eau | water |
| 5 | Orage puis pluie | Meteo active, ciel visible, biome avec precipitations | a fournir |
| 6 | Souterrain | Y sous 50 et ciel bouche | a fournir |
| 7 | Nuit | Nuit dans l'Overworld, a ciel ouvert | night |
| 8 | Biome | 17 categories de biomes | plaines, enneige, ocean |
| 9 | Dimension | Overworld, Nether, End | nether, end |
| 10 | Classique | Aucun declencheur, repli general | classic |
| 11 | Vanilla | Plus rien a proposer : le jeu reprend la main | |

La nuit et le souterrain s'excluent : l'un exige un ciel visible, l'autre un
ciel bouche. La detection de la nuit ecarte d'elle-meme le Nether et l'End,
dont l'heure est figee.

## Musiques classiques

Les morceaux sans declencheur ne sont pas cantonnes au repli final. Sur le
palier ambiance uniquement (biome, dimension), une piste classique peut se
substituer a la musique attendue selon `classicChance`, par defaut 35 %. Elles
entrent ainsi naturellement dans la rotation sans jamais recouvrir un combat ni
un danger.

Le tirage a lieu au demarrage du morceau, pas a chaque detection. Le choix reste
donc stable pendant toute sa duree et ne provoque aucun fondu parasite.

La nuit fait partie du palier ambiance : un morceau classique peut donc s'y
inviter. Sans cela l'unique piste de nuit tournerait en boucle du crepuscule a
l'aube.

## Pistes installees

| Fichier | Morceau | Artiste | Duree | Se declenche sur |
| --- | --- | --- | --- | --- |
| `music/classic.ogg` | Take You With Me | Jellyraymen | 4:44 | Partout, sans declencheur |
| `music/classic_2.ogg` | Under The Aurora | Cheryltje | 3:40 | Partout, sans declencheur |
| `music/night.ogg` | Grove | Cheryltje | 1:34 | Nuit, a ciel ouvert |
| `music/biome/plains.ogg` | Busy Bees | Jellyraymen | 2:09 | Plaines, plaines de tournesols, prairie |
| `music/biome/snowy.ogg` | Solace | Jellyraymen | 2:43 | Onze biomes enneiges ou geles |
| `music/biome/water.ogg` | Lucidity | Jellyraymen | 3:13 | Oceans, rivieres, et tete sous l'eau |
| `music/biome/water_2.ogg` | Into the Ocean | hysenn | 5:26 | Oceans, rivieres, et tete sous l'eau |
| `music/dimension/nether.ogg` | Ballad of the Mirrors | Jellyraymen | 3:11 | Nether |
| `music/dimension/nether_2.ogg` | After Nostalgia, Melancholia | Cheryltje | 2:13 | Nether |
| `music/dimension/nether_3.ogg` | A Place With No Hope | Cheryltje | 2:35 | Nether |
| `music/dimension/end.ogg` | Undefined | Jellyraymen | 3:48 | End |
| `music/dimension/end_2.ogg` | secrets beneath | Cheryltje | 3:04 | End |

Quand plusieurs pistes partagent un contexte, le jeu en tire une au hasard a
chaque lecture. Le Nether en propose trois, l'ocean, l'End et le repli classique
deux chacun. Chaque piste garde sa propre fiche pour la vignette, puisque la
table des metadonnees est indexee par fichier et non par contexte.

Faute de piste sous-marine dediee, le contexte "sous l'eau" est branche sur
l'evenement de l'ocean plutot que de dupliquer la meme liste de fichiers dans
deux evenements. Le test anti-redemarrage reste ainsi exact : plonger ne coupe
jamais le morceau en cours, quelle que soit la variante tiree. Pour donner sa
propre musique au sous-marin, rebrancher `UNDERWATER` sur
`ModSounds.MUSIC_UNDERWATER` dans
[MusicContext.java](src/main/java/com/dynamicmusic/client/MusicContext.java) et
declarer `music.underwater` dans `sounds.json`.

## Categories de biomes

Le mod n'utilise pas les tags vanilla directement. Il definit les siens dans
[data/dynamicmusic/tags/worldgen/biome/](src/main/resources/data/dynamicmusic/tags/worldgen/biome/),
la plupart se contentant de pointer vers le tag vanilla equivalent. Cela permet
de couvrir des regroupements absents du jeu de base (plaines, enneige, marais,
desert) et laisse un datapack ranger un biome moddé dans une categorie musicale
sans recompiler.

Les 17 categories sont testees dans cet ordre, du plus specifique au plus
general : deep dark, grotte luxuriante, champignon, cerisier, enneige, desert,
badlands, marais, jungle, savane, plage, riviere, ocean, taiga, foret, montagne,
plaines.

Un biome enneige sonne donc "enneige" avant de sonner "taiga".

## Ajouter une musique

Aucune ligne de Java a ecrire. Les 28 evenements sonores sont deja enregistres.

1. Depose le `.ogg` dans `src/main/resources/assets/dynamicmusic/sounds/music/`.
2. Ajoute l'entree correspondante dans
   [sounds.json](src/main/resources/assets/dynamicmusic/sounds.json), avec
   `"stream": true`.
3. Ajoute sa fiche dans `music_metadata.json` pour la vignette, et une pochette
   64x64 dans `textures/gui/music/`. Sans fiche, le morceau s'affiche quand meme
   avec un titre deduit du nom de fichier.
4. Recompile.

Les noms d'evenements disponibles sont listes dans
[ModSounds.java](src/main/java/com/dynamicmusic/sound/ModSounds.java) :
`music.danger`, `music.boss`, `music.combat`, `music.underwater`,
`music.thunder`, `music.rain`, `music.underground`, les 17 `music.biome.*`, les
trois `music.dimension.*` et `music.classic`.

Pour proposer plusieurs morceaux sur un meme contexte, ajoute simplement
plusieurs objets dans le tableau `sounds`, avec un `weight` optionnel. Minecraft
en tire un au hasard a chaque lecture.

## Fichiers principaux

| Fichier | Role |
| --- | --- |
| [ModSounds.java](src/main/java/com/dynamicmusic/sound/ModSounds.java) | Enregistrement des `SoundEvent` via `DeferredRegister` |
| [ModBiomeTags.java](src/main/java/com/dynamicmusic/sound/ModBiomeTags.java) | Cles des 17 tags de biomes du mod |
| [ClientMusicHandler.java](src/main/java/com/dynamicmusic/client/ClientMusicHandler.java) | Detection, cascade, pilotage des fondus |
| [DynamicSoundInstance.java](src/main/java/com/dynamicmusic/client/DynamicSoundInstance.java) | `AbstractTickableSoundInstance` a volume variable |
| [MusicContext.java](src/main/java/com/dynamicmusic/client/MusicContext.java) | Enum des contextes, priorites et vitesses de fondu |
| [MusicToast.java](src/main/java/com/dynamicmusic/client/MusicToast.java) | Rendu de la vignette |
| [MusicNotifier.java](src/main/java/com/dynamicmusic/client/MusicNotifier.java) | Suivi du morceau en cours et de son avancement |
| [MusicMetadata.java](src/main/java/com/dynamicmusic/client/MusicMetadata.java) | Chargement des fiches, rechargeable avec les packs |
| [MusicInfo.java](src/main/java/com/dynamicmusic/client/MusicInfo.java) | Fiche d'une piste |
| [ClientModEvents.java](src/main/java/com/dynamicmusic/client/ClientModEvents.java) | Inscription bus jeu et bus mod |
| [DynamicMusicConfig.java](src/main/java/com/dynamicmusic/config/DynamicMusicConfig.java) | Config client `dynamicmusic-client.toml` |

## Notes techniques

- **Crossfade reel.** La piste sortante continue de jouer en s'attenuant pendant
  que la nouvelle monte. `DynamicSoundInstance` surcharge `canStartSilent()`,
  sans quoi le moteur refuserait de demarrer une instance a volume nul.
- **Identification differee.** Le morceau est identifie au tick suivant son
  demarrage, pas dans l'evenement sonore : a cet instant l'instance n'a pas
  toujours choisi son fichier parmi les variantes declarees. Cela sort au
  passage tout travail du chemin audio.
- **Anti-repetition.** Une piste qui se termine naturellement impose un silence
  aleatoire de 20 a 90 secondes avant la suivante, meme si le contexte n'a pas
  change.
- **Jamais de redemarrage a zero.** Un changement de contexte ne relance rien si
  la piste visee est deja celle qui joue. La comparaison porte sur le fichier
  audio, pas sur l'evenement sonore : deux evenements distincts peuvent designer
  le meme fichier, comme la piste sous-marine et celle de l'ocean. Sans cela,
  plonger dans l'ocean, ou passer d'un biome a un autre qui retombe sur le meme
  morceau, le redemarrerait par dessus lui-meme.
- **Musique vanilla.** Elle est annulee via `PlaySoundEvent` tant qu'une piste du
  mod est active, sans toucher aux autres categories sonores. Une piste annulee
  n'est evidemment pas annoncee par la vignette.
- **Cout CPU.** Une seule requete d'entites par seconde au maximum, biome relu
  seulement au changement de section de chunk, au plus 4 raycasts de ligne de vue
  par scan, aucune lecture de configuration dans la boucle de tick.

## Compilation et installation

Un wrapper Gradle est fourni, rien d'autre a installer que le JDK.

```bash
./gradlew build
```

Le mod sort dans `build/libs/dynamicmusic-1.0.0.jar`. Copie-le dans le dossier
`mods` de ton instance Minecraft, sur un profil **Forge 1.20.4**.

Sous Windows, le dossier par defaut est `%appdata%\.minecraft\mods`.

Le mod est purement client. Aucune installation cote serveur, et il ne bloque
pas la connexion a un serveur qui ne l'a pas.

### Environnement de developpement

```bash
./gradlew runClient    # client Minecraft de developpement
./gradlew runData      # generateur de donnees, sert aussi de test de chargement
```

Depuis 1.20.2, Forge charge les mods de developpement par le chemin de modules,
et un mod doit tenir dans un seul repertoire. Gradle separant les classes des
ressources, le build force les deux au meme endroit :

```groovy
sourceSets.main.output.resourcesDir = sourceSets.main.java.destinationDirectory
```

Sans cette ligne, Forge ne voit que le repertoire contenant `mods.toml` et
s'arrete sur `The following classes are missing, but are reported in the
mods.toml`.

### Versions

| Element | Version |
| --- | --- |
| Minecraft | 1.20.4 |
| Forge | 49.0.38 |
| Gradle | 8.9 (wrapper fourni) |
| ForgeGradle | 6.0.54 |
| JDK de compilation | 17 (chaine d'outils Gradle) |

La compilation fonctionne avec Java 17 comme avec Java 21 : Gradle selectionne
tout seul le JDK 17 pour compiler.

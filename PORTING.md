# Portage : versions et chargeurs

## Pourquoi plusieurs branches et non un seul jar

Un jar de mod ne peut couvrir **ni plusieurs chargeurs, ni plusieurs lignes de
version**. Ce n'est pas une limite de ce projet, c'est ainsi que fonctionne le
modding Minecraft.

**Les chargeurs sont mutuellement incompatibles.** Forge, NeoForge, Fabric et
Quilt ont des points d'entree, des manifestes et des systemes d'evenements
differents. Aucun jar ne se charge sur plus d'un. Les mods multi-chargeurs
partagent un code source et publient un jar par chargeur.

**Chaque ligne majeure de Forge vise un binaire Minecraft precis.** La ligne 49
est 1.20.4, la 52 est 1.21.1, la 65 est 26.2. Le jar est lie a un jeu de
mappings et a une version de Java.

D'ou le choix d'**une branche Git par couple version + chargeur**, nommee
`<version>-<chargeur>`, par exemple `1.20.4-forge` ou `1.21.1-forge`.

## Etat des branches

| Branche | Minecraft | Chargeur | Version du chargeur | Java | Etat |
| --- | --- | --- | --- | --- | --- |
| `main` | 1.20.4 | Forge | 49.2.8 | 17 | Compile, chargement verifie |
| `1.20.4-forge` | 1.20.3 et 1.20.4 | Forge | 49.2.8 | 17 | Compile, chargement verifie |
| `1.21.1-forge` | 1.21.1 | Forge | 52.1.16 | 21 | Compile, chargement verifie |

Le plancher de fonctionnalites est **1.20.2** : le fond des vignettes utilise
l'atlas de sprites d'interface, apparu a cette version. En dessous, il faudrait
un autre chemin de rendu.

## Delta mesure entre 1.20.4 et 1.21.1

Constate au compilateur, pas suppose. C'est la reference pour les portages
suivants dans la meme famille.

| Ce qui change | Detail |
| --- | --- |
| `ResourceLocation` | Constructeur public supprime en 1.21. Utiliser `fromNamespaceAndPath` et `withDefaultNamespace`. |
| Ecrans d'options | Deplaces dans `net.minecraft.client.gui.screens.options`. |
| `reobfJar` | N'existe plus depuis Forge 1.20.6, le jeu tournant sur les mappings officiels. La tache n'est branchee que si elle existe. |
| Java | 17 jusqu'a 1.20.4, 21 a partir de 1.20.5. |
| Format de pack | 22 en 1.20.3 et 1.20.4, 32 en 1.20.5 et 1.20.6, 34 en 1.21 et 1.21.1. Au dela, a verifier version par version. |

Ce qui n'a **pas** bouge et compile tel quel : `TickEvent`, `PlaySoundEvent`,
`ForgeConfigSpec`, l'API des vignettes, `blitSprite`, les tags de biomes,
`ScreenEvent`, `RegisterClientReloadListenersEvent`, `AbstractTickableSoundInstance`.

## Ruptures connues plus loin

A anticiper avant d'attaquer ces cibles.

| A partir de | Rupture |
| --- | --- |
| 1.21.2 | Le systeme de vignettes est refondu. `Toast.render` change de signature et `ToastComponent` devient `ToastManager`. C'est le plus gros morceau pour ce mod. |
| 1.21.2 | `blitSprite` prend un parametre de type de rendu. |
| 1.21.11 | Mojang renomme `ResourceLocation` en `Identifier`. Derniere version obfusquee. |
| 26.x | Java 25 requis. |
| NeoForge | `ForgeConfigSpec` devient `ModConfigSpec`, `RegistryObject` devient `DeferredHolder`, le manifeste devient `META-INF/neoforge.mods.toml`. |
| Fabric | Aucun equivalent de `PlaySoundEvent`. La capture des musiques demande un Mixin dans `SoundEngine`. Registres, config et evenements sont a reecrire. |

## Cibles Forge disponibles

Numeros releves sur `promotions_slim.json` de Forge.

| Minecraft | Forge | Minecraft | Forge |
| --- | --- | --- | --- |
| 1.20 | 46.0.14 | 1.21.4 | 54.1.18 |
| 1.20.1 | 47.4.23 | 1.21.5 | 55.1.13 |
| 1.20.2 | 48.1.0 | 1.21.6 | 56.0.9 |
| 1.20.3 | 49.0.2 | 1.21.7 | 57.0.3 |
| 1.20.4 | 49.2.8 | 1.21.8 | 58.1.22 |
| 1.20.6 | 50.2.10 | 1.21.9 | 59.0.5 |
| 1.21 | 51.0.33 | 1.21.10 | 60.1.15 |
| 1.21.1 | 52.1.16 | 1.21.11 | 61.2.1 |
| 1.21.3 | 53.1.12 | 26.1 | 62.0.9 |
| | | 26.2 | 65.1.3 |

## Creer une nouvelle branche de cible

```bash
tools/new-target.sh <version-minecraft> <version-forge> <java> <format-de-pack>
```

Exemple pour 1.21 :

```bash
tools/new-target.sh 1.21 51.0.33 21 34
```

Le script cree la branche depuis la base la plus proche, applique les
changements mecaniques (versions, Java, format de pack, plage dans
`mods.toml`) et lance la compilation. Le reste se fait en suivant les erreurs
du compilateur, qui est la seule source de verite fiable sur ce qui a change.

Verifier ensuite le chargement reel :

```bash
./gradlew runData
```

La ligne `[DynamicMusic] Initialisation cote client.` suivie de
`BUILD SUCCESSFUL` signifie que le mod se charge : manifeste valide, classe
principale trouvee, registres passes.

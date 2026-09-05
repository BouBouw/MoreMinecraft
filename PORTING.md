# Portage : versions et chargeurs

## Pourquoi une branche par cible

Un jar de mod ne peut couvrir **ni plusieurs chargeurs, ni plusieurs lignes de
version**. Ce n'est pas une limite de ce projet, c'est ainsi que fonctionne le
modding Minecraft.

Les chargeurs Forge, NeoForge, Fabric et Quilt ont des points d'entree, des
manifestes et des systemes d'evenements differents et mutuellement
incompatibles. Chaque ligne majeure de Forge vise par ailleurs un binaire
Minecraft precis, avec ses mappings et sa version de Java.

D'ou une branche Git par couple version et chargeur, nommee
`<version>-<chargeur>`.

## Branches Forge livrees

Toutes compilent. La derniere colonne indique si le chargement reel du mod a
ete verifie en lancant le jeu en mode generateur de donnees, ce qui exerce le
manifeste, la classe principale et les registres.

| Branche | Forge | Java | Pack | Chargement |
| --- | --- | --- | --- | --- |
| `1.20.2-forge` | 48.1.0 | 17 | 18 | verifie |
| `1.20.3-forge` | 49.0.2 | 17 | 22 | verifie |
| `1.20.4-forge` et `main` | 49.0.38 | 17 | 22 | verifie |
| `1.20.6-forge` | 50.2.10 | 21 | 32 | verifie |
| `1.21-forge` | 51.0.33 | 21 | 34 | **impossible**, voir ci-dessous |
| `1.21.1-forge` | 52.1.16 | 21 | 34 | verifie |
| `1.21.3-forge` | 53.1.12 | 21 | 42 | verifie |
| `1.21.4-forge` | 54.1.18 | 21 | 46 | verifie |
| `1.21.5-forge` | 55.1.13 | 21 | 55 | verifie |
| `1.21.6-forge` | 56.0.9 | 21 | 63 | verifie |
| `1.21.7-forge` | 57.0.3 | 21 | 64 | verifie |
| `1.21.8-forge` | 58.1.22 | 21 | 64 | verifie |
| `1.21.9-forge` | 59.0.5 | 21 | 69 | verifie |
| `1.21.10-forge` | 60.1.15 | 21 | 69 | verifie |
| `1.21.11-forge` | 61.2.1 | 21 | 75 | verifie |
| `26.1-forge` | 62.0.9 | 25 | 84 | verifie |
| `26.1.1-forge` | 63.0.2 | 25 | 84 | verifie |
| `26.1.2-forge` | 64.1.3 | 25 | 84 | verifie |
| `26.2-forge` | 65.1.3 | 25 | 88 | verifie |

**1.21 fait exception.** Le build Forge 51.0.33 oublie `jopt-simple` sur le
chemin de modules des runs de developpement, et modlauncher s'arrete dessus. Le
defaut est en amont et ne touche pas le jar publie, qui compile normalement. La
verification a ete faite sur 1.21.1, qui partage la meme API cliente et n'a
demande aucune modification de source. En cas de doute, preferer `1.21.1-forge`.

## Plancher et plafond

Le plancher est **1.20.2** : le fond des vignettes utilise l'atlas de sprites
d'interface, apparu a cette version. En 1.20 et 1.20.1 il faudrait un autre
chemin de rendu, donc le mod y perdrait une fonctionnalite.

Le plafond est **26.2**, la derniere version sortie au moment de ce portage.

## Les quatre ruptures d'API traversees

Mesurees au compilateur, pas supposees. Entre deux ruptures, un portage se
resume a changer trois numeros.

### 1.21 : identifiants et ecrans

- Le constructeur public de `ResourceLocation` disparait, remplace par
  `fromNamespaceAndPath` et `withDefaultNamespace`.
- Les ecrans d'options passent dans `net.minecraft.client.gui.screens.options`.
- `reobfJar` disparait depuis Forge 1.20.6, le jeu tournant sur les mappings
  officiels.

### 1.21.2 : refonte des vignettes

- `ToastComponent` devient `ToastManager`, `Minecraft.getToasts` devient
  `getToastManager`.
- `Toast` se scinde : `getWantedVisibility` decide, `update` calcule, `render`
  ne fait plus que dessiner et ne renvoie plus rien.
- `blitSprite` et `blit` prennent une fonction de type de rendu.
- `SoundEvent.getLocation` devient `location`.

### 1.21.6 : bus d'evenements et moteur de rendu

- EventBus 7 : `SubscribeEvent` et `Priority` passent dans `api.listener`,
  `EventPriority` disparait au profit de constantes `byte`. `IEventBus` devient
  `BusGroup`, `getModEventBus` devient `getModBusGroup`.
- `blitSprite` et `blit` prennent un `RenderPipeline`.
- La pile de transformations de l'interface devient bidimensionnelle,
  `Matrix3x2fStack` : `pushMatrix` remplace `pushPose`, la profondeur disparait.

### 1.21.11 : renommage et changement d'outil

- `ResourceLocation` devient `Identifier`, dans `net.minecraft.resources`.
- `SoundInstance.getLocation` devient `getIdentifier`.
- ForgeGradle 6 echoue sur l'userdev de Forge 61. Forge exige ForgeGradle 7,
  qui exige lui-meme Gradle 9.3. Les branches a partir de 1.21.11 ont donc un
  wrapper Gradle 9.3 la ou les precedentes restent en 8.9.
- Le reglage `merge-source-sets` remplace l'ajustement manuel de `resourcesDir`.

### 26.1 puis 26.2 : extraction d'etat de rendu

- `GuiGraphics` devient `GuiGraphicsExtractor` : l'objet ne peint plus
  directement, il alimente un extracteur d'etat de rendu.
- `Toast.render` devient `extractRenderState`, `drawString` devient `text`.
- Java 25.
- En 26.2, l'ecran courant et le gestionnaire de notifications passent sur
  l'objet `Gui` : `mc.gui.screen()` et `mc.gui.toastManager()`.

## Changements plus discrets

| Version | Changement |
| --- | --- |
| 1.20.5 | Java passe de 17 a 21 |
| 1.21.5 | `Level.isNight` disparait, reconstruit avec la lumiere celeste et l'heure figee de la dimension |
| 1.21.9 | Le tick client se scinde en `ClientTickEvent.Pre` et `ClientTickEvent.Post`, la notion de phase disparait |
| 1.21.9 | Le manifeste du jeu expose `resource_major` au lieu de `resource` |

## Ce qui n'a jamais bouge

D'un bout a l'autre, de 1.20.2 a 26.2 : `PlaySoundEvent`, `ForgeConfigSpec`,
`DeferredRegister`, `AbstractTickableSoundInstance`, `ScreenEvent`,
`RegisterClientReloadListenersEvent`, les tags de biomes, `Biome.hasPrecipitation`,
`Sound.getLocation`, `Minecraft.getSoundManager`.

## Reste a faire

NeoForge, Fabric et Quilt. Chacun demande un jar par version, donc autant de
branches supplementaires, et un travail different du portage de version.

| Chargeur | Nature du travail |
| --- | --- |
| NeoForge | Proche de Forge. `ForgeConfigSpec` devient `ModConfigSpec`, `RegistryObject` devient `DeferredHolder`, le manifeste devient `META-INF/neoforge.mods.toml`. |
| Fabric | Reecriture. Aucun equivalent de `PlaySoundEvent` : la capture des musiques demande un Mixin dans `SoundEngine`. Registres, configuration et evenements sont a refaire. |
| Quilt | Fork de Fabric, compatible avec ses mods. Le jar Fabric suffit dans la plupart des cas. |

## Ajouter une cible

```bash
tools/new-target.sh <version-minecraft> <version-forge> <java> <format-de-pack>
```

Partir de la branche la plus proche : meme version de Java et meme cote de la
derniere rupture. Le script applique les changements mecaniques, puis c'est le
compilateur qui dit ce qui reste, et il est la seule source de verite fiable.

Verifier ensuite le chargement reel :

```bash
./gradlew runData
```

La ligne `[DynamicMusic] Initialisation cote client.` suivie de
`BUILD SUCCESSFUL` signifie que le mod se charge.

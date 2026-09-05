#!/usr/bin/env bash
# Cree une branche de cible et applique les changements mecaniques du portage.
#
#   tools/new-target.sh <version-minecraft> <version-forge> <java> <format-de-pack> [branche-de-base]
#
# Exemple :
#   tools/new-target.sh 1.21 51.0.33 21 34
#
# Le script ne devine rien sur l'API : il prepare le terrain, puis c'est le
# compilateur qui dit ce qui reste a corriger.
set -euo pipefail

if [ "$#" -lt 4 ]; then
    sed -n '2,12p' "$0"
    exit 1
fi

MC="$1"; FORGE="$2"; JAVA="$3"; PACK="$4"
BASE="${5:-}"
MAJOR="${FORGE%%.*}"
BRANCH="${MC}-forge"

# Base par defaut : la branche existante dont la version de Java correspond,
# ce qui limite le nombre de corrections a faire.
if [ -z "$BASE" ]; then
    if [ "$JAVA" -ge 21 ] && git show-ref --verify --quiet refs/heads/1.21.1-forge; then
        BASE="1.21.1-forge"
    else
        BASE="main"
    fi
fi

echo "Cible   : Minecraft $MC, Forge $FORGE (ligne $MAJOR), Java $JAVA, pack $PACK"
echo "Base    : $BASE"
echo "Branche : $BRANCH"
echo

git checkout -q "$BASE"
git checkout -q -b "$BRANCH"

python - "$MC" "$FORGE" "$JAVA" "$PACK" "$MAJOR" <<'PYEOF'
import json, pathlib, re, sys
mc, forge, java, pack, major = sys.argv[1:6]

p = pathlib.Path('gradle.properties'); s = p.read_text(encoding='utf-8')
s = re.sub(r'^minecraft_version=.*$', f'minecraft_version={mc}', s, flags=re.M)
s = re.sub(r'^forge_version=.*$',     f'forge_version={forge}',   s, flags=re.M)
s = re.sub(r'^mapping_version=.*$',   f'mapping_version={mc}',    s, flags=re.M)
p.write_text(s, encoding='utf-8')

p = pathlib.Path('build.gradle'); s = p.read_text(encoding='utf-8')
s = re.sub(r'JavaLanguageVersion\.of\(\d+\)', f'JavaLanguageVersion.of({java})', s)
s = re.sub(r'// Minecraft \S+ tourne sur Java \d+\.', f'// Minecraft {mc} tourne sur Java {java}.', s)
p.write_text(s, encoding='utf-8')

pathlib.Path('src/main/resources/pack.mcmeta').write_text(json.dumps(
    {"pack": {"description": "DynamicMusic resources",
              "pack_format": int(pack),
              "supported_formats": [int(pack), int(pack)]}}, indent=2) + "\n", encoding='utf-8')

p = pathlib.Path('src/main/resources/META-INF/mods.toml'); s = p.read_text(encoding='utf-8')
s = re.sub(r'loaderVersion = "\[\d+,\)"', f'loaderVersion = "[{major},)"', s)
s = re.sub(r'versionRange = "\[\d+,\)"',  f'versionRange = "[{major},)"',  s)
s = re.sub(r'versionRange = "\[1\.[^"]*"', f'versionRange = "[{mc}]"', s, count=1) \
    if False else s
s = re.sub(r'(modId = "minecraft"\s*\n\s*mandatory = true\s*\n\s*versionRange = )"[^"]*"',
           rf'\1"[{mc}]"', s)
p.write_text(s, encoding='utf-8')
print("versions appliquees")
PYEOF

echo
echo "Compilation, les erreurs restantes sont les vraies differences d'API :"
./gradlew build --console=plain 2>&1 | grep -E "error:|BUILD|symbol:|location:" | head -40

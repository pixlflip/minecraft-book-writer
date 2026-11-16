#!/usr/bin/env bash
# Builds Book Writer for every Minecraft version in versions/ and copies the jars to dist/.
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p dist
for file in $(ls versions/*.properties | sort -V); do
    mc=$(basename "$file" .properties)
    echo "== Minecraft $mc"
    ./gradlew build -Pmc="$mc" --no-daemon -q
    cp build/libs/*+mc"$mc".jar dist/
done
(cd dist && sha256sum *.jar > SHA256SUMS.txt)
echo "Jars are in dist/"

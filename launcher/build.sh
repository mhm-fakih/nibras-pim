#!/usr/bin/env bash
# Build nibras-launcher.jar (Java 17+, no dependencies): ./build.sh  ->  build/nibras-launcher.jar
set -euo pipefail
cd "$(dirname "$0")"
rm -rf build/classes && mkdir -p build/classes
javac --release 17 -d build/classes src/NibrasLauncher.java
printf 'Main-Class: NibrasLauncher\n' > build/manifest.txt
jar cfm build/nibras-launcher.jar build/manifest.txt -C build/classes .
echo "built $(pwd)/build/nibras-launcher.jar"

#!/bin/sh
# Builds TheWello's Infdev Server Project. Requires JDK 8.
#
# Inputs (not included in this repository):
#   CLIENT_JAR  original Infdev jar, default: original/inf-20100630-1340.jar
#   LWJGL_JAR   LWJGL 2 jar (compile-time only), default: lib/lwjgl.jar
#   GAME227_JAR original inf-20100227-1433 jar, default: original/inf-20100227-1433.jar (skipped if missing)
#   GAME313_JAR original inf-20100313 jar, default: original/inf-20100313.jar (skipped if missing)
#   GAME316_JAR original inf-20100316 jar, default: original/inf-20100316.jar (skipped if missing)
#   GAME320_JAR original inf-20100320 jar, default: original/inf-20100320.jar (skipped if missing)
#   GAME321_JAR original inf-20100321 jar, default: original/inf-20100321.jar (skipped if missing)
#   GAME325_JAR original inf-20100325-1640 jar, default: original/inf-20100325-1640.jar (skipped if missing)
#   GAME327_JAR original inf-20100327 jar, default: original/inf-20100327.jar (skipped if missing)
#   GAME330_JAR original inf-20100330-1611 jar, default: original/inf-20100330-1611.jar (skipped if missing)
#   GAME413_JAR original inf-20100413-1953 jar, default: original/inf-20100413-1953.jar (skipped if missing)
#   GAME414_JAR original inf-20100414 jar, default: original/inf-20100414.jar (skipped if missing)
#   GAME415_JAR original inf-20100415 jar, default: original/inf-20100415.jar (skipped if missing)
#   BETACRAFT_DIR       optional: your .betacraft folder; the patched clients are installed there
#   PATCHED_CLIENTS_DIR optional: a folder to keep the patched client jars in (they hold Minecraft code: private use)
# Output:
#   dist/       jars built from the source
#   $RELEASE_DIR  the release bundle and its zip archive (no Minecraft code inside)
set -e
cd "$(dirname "$0")"

JBIN="${JAVA_HOME:+$JAVA_HOME/bin/}"
JAVAC="${JBIN}javac"
JAR="${JBIN}jar"
JAVA="${JBIN}java"
CLIENT_JAR="${CLIENT_JAR:-original/inf-20100630-1340.jar}"
LWJGL_JAR="${LWJGL_JAR:-lib/lwjgl.jar}"
GAME227_JAR="${GAME227_JAR:-original/inf-20100227-1433.jar}"
GAME313_JAR="${GAME313_JAR:-original/inf-20100313.jar}"
GAME316_JAR="${GAME316_JAR:-original/inf-20100316.jar}"
GAME320_JAR="${GAME320_JAR:-original/inf-20100320.jar}"
GAME321_JAR="${GAME321_JAR:-original/inf-20100321.jar}"
GAME325_JAR="${GAME325_JAR:-original/inf-20100325-1640.jar}"
GAME327_JAR="${GAME327_JAR:-original/inf-20100327.jar}"
GAME330_JAR="${GAME330_JAR:-original/inf-20100330-1611.jar}"
GAME413_JAR="${GAME413_JAR:-original/inf-20100413-1953.jar}"
GAME414_JAR="${GAME414_JAR:-original/inf-20100414.jar}"
GAME415_JAR="${GAME415_JAR:-original/inf-20100415.jar}"
BETACRAFT_DIR="${BETACRAFT_DIR:-}"
PATCHED_CLIENTS_DIR="${PATCHED_CLIENTS_DIR:-}"
RELEASE_DIR="${RELEASE_DIR:-../TheWellosInfdevServer-Release}"
S="${SEP:-;}"
JOPT="-nowarn -encoding UTF-8 -source 1.8 -target 1.8"
ASM="lib/asm-9.8.jar${S}lib/asm-tree-9.8.jar"

if [ ! -f "$CLIENT_JAR" ]; then
  echo "Missing $CLIENT_JAR. Put the original inf-20100630-1340.jar there or set CLIENT_JAR."
  exit 1
fi
if [ ! -f "$LWJGL_JAR" ]; then
  echo "Missing $LWJGL_JAR. Put an LWJGL 2 jar there or set LWJGL_JAR."
  exit 1
fi

rm -rf build
mkdir -p build/common build/patcher build/mod build/api build/server build/bridge/bridge build/plugins dist

echo "[1/8] common (protocol and ASM hook tool)"
"$JAVAC" $JOPT -cp "$ASM" -d build/common $(find common/src -name '*.java')

echo "[2/8] client patcher"
"$JAVAC" $JOPT -cp "$ASM${S}build/common" -d build/patcher $(find client/patcher/src -name '*.java')

echo "[3/8] public client jar (for compiling)"
"$JAVA" -cp "$ASM${S}build/common${S}build/patcher" net.thewellodev.patcher.ClientPatcher "$CLIENT_JAR" build/client-public.jar --publicize-only

echo "[4/8] client mod, patcher jar and patched client"
"$JAVAC" $JOPT -cp "$LWJGL_JAR${S}build/client-public.jar${S}build/common" -d build/mod $(find client/mod -name '*.java')
mkdir -p build/patcher/mod
cp -r build/mod/* build/patcher/mod/
(cd build/common && find net/thewellodev/protocol -name '*.class') | while read f; do
  mkdir -p "build/patcher/mod/$(dirname "$f")"; cp "build/common/$f" "build/patcher/mod/$f"; done
(cd build/patcher/mod && find . -name '*.class' | sed 's|^\./||' | sort) > build/patcher/mod/index.txt
if [ -n "$GAME227_JAR" ] && [ -f "$GAME227_JAR" ]; then
  echo "  client mod for inf-20100227-1433 (compiled against $GAME227_JAR)"
  mkdir -p build/mod227 build/patcher/mod227
  "$JAVAC" $JOPT -cp "$GAME227_JAR${S}$LWJGL_JAR${S}build/common" -d build/mod227 $(find client/mod227 -name '*.java') client/mod/TWConnection.java client/mod/TWAuth.java client/mod/TWMouse.java
  cp -r build/mod227/* build/patcher/mod227/
  (cd build/common && find net/thewellodev/protocol -name '*.class') | while read f; do
    mkdir -p "build/patcher/mod227/$(dirname "$f")"; cp "build/common/$f" "build/patcher/mod227/$f"; done
  (cd build/patcher/mod227 && find . -name '*.class' | sed 's|^\./||' | sort) > build/patcher/mod227/index.txt
fi
if [ -n "$GAME313_JAR" ] && [ -f "$GAME313_JAR" ]; then
  echo "  client mod for inf-20100313 (compiled against $GAME313_JAR)"
  mkdir -p build/mod313 build/patcher/mod313
  "$JAVAC" $JOPT -cp "$GAME313_JAR${S}$LWJGL_JAR${S}build/common" -d build/mod313 $(find client/mod313 -name '*.java') client/mod/TWConnection.java client/mod/TWAuth.java client/mod/TWMouse.java
  cp -r build/mod313/* build/patcher/mod313/
  (cd build/common && find net/thewellodev/protocol -name '*.class') | while read f; do
    mkdir -p "build/patcher/mod313/$(dirname "$f")"; cp "build/common/$f" "build/patcher/mod313/$f"; done
  (cd build/patcher/mod313 && find . -name '*.class' | sed 's|^\./||' | sort) > build/patcher/mod313/index.txt
fi
if [ -n "$GAME316_JAR" ] && [ -f "$GAME316_JAR" ]; then
  echo "  client mod for inf-20100316 (compiled against $GAME316_JAR)"
  mkdir -p build/mod316 build/patcher/mod316
  "$JAVAC" $JOPT -cp "$GAME316_JAR${S}$LWJGL_JAR${S}build/common" -d build/mod316 $(find client/mod316 -name '*.java') client/mod/TWConnection.java client/mod/TWAuth.java client/mod/TWMouse.java
  cp -r build/mod316/* build/patcher/mod316/
  (cd build/common && find net/thewellodev/protocol -name '*.class') | while read f; do
    mkdir -p "build/patcher/mod316/$(dirname "$f")"; cp "build/common/$f" "build/patcher/mod316/$f"; done
  (cd build/patcher/mod316 && find . -name '*.class' | sed 's|^\./||' | sort) > build/patcher/mod316/index.txt
fi
if [ -n "$GAME320_JAR" ] && [ -f "$GAME320_JAR" ]; then
  echo "  client mod for inf-20100320 (compiled against $GAME320_JAR)"
  mkdir -p build/mod320 build/patcher/mod320
  "$JAVAC" $JOPT -cp "$GAME320_JAR${S}$LWJGL_JAR${S}build/common" -d build/mod320 $(find client/mod320 -name '*.java') client/mod/TWConnection.java client/mod/TWAuth.java client/mod/TWMouse.java
  cp -r build/mod320/* build/patcher/mod320/
  (cd build/common && find net/thewellodev/protocol -name '*.class') | while read f; do
    mkdir -p "build/patcher/mod320/$(dirname "$f")"; cp "build/common/$f" "build/patcher/mod320/$f"; done
  (cd build/patcher/mod320 && find . -name '*.class' | sed 's|^\./||' | sort) > build/patcher/mod320/index.txt
fi
if [ -n "$GAME321_JAR" ] && [ -f "$GAME321_JAR" ]; then
  echo "  client mod for inf-20100321 (compiled against $GAME321_JAR)"
  mkdir -p build/mod321 build/patcher/mod321
  "$JAVAC" $JOPT -cp "$GAME321_JAR${S}$LWJGL_JAR${S}build/common" -d build/mod321 $(find client/mod321 -name '*.java') client/mod/TWConnection.java client/mod/TWAuth.java client/mod/TWMouse.java
  cp -r build/mod321/* build/patcher/mod321/
  (cd build/common && find net/thewellodev/protocol -name '*.class') | while read f; do
    mkdir -p "build/patcher/mod321/$(dirname "$f")"; cp "build/common/$f" "build/patcher/mod321/$f"; done
  (cd build/patcher/mod321 && find . -name '*.class' | sed 's|^\./||' | sort) > build/patcher/mod321/index.txt
fi
if [ -n "$GAME325_JAR" ] && [ -f "$GAME325_JAR" ]; then
  echo "  client mod for inf-20100325-1640 (compiled against $GAME325_JAR)"
  mkdir -p build/mod325 build/patcher/mod325
  "$JAVAC" $JOPT -cp "$GAME325_JAR${S}$LWJGL_JAR${S}build/common" -d build/mod325 $(find client/mod325 -name '*.java') client/mod/TWConnection.java client/mod/TWAuth.java client/mod/TWMouse.java
  cp -r build/mod325/* build/patcher/mod325/
  (cd build/common && find net/thewellodev/protocol -name '*.class') | while read f; do
    mkdir -p "build/patcher/mod325/$(dirname "$f")"; cp "build/common/$f" "build/patcher/mod325/$f"; done
  (cd build/patcher/mod325 && find . -name '*.class' | sed 's|^\./||' | sort) > build/patcher/mod325/index.txt
fi
if [ -n "$GAME327_JAR" ] && [ -f "$GAME327_JAR" ]; then
  echo "  client mod for inf-20100327 (compiled against $GAME327_JAR)"
  mkdir -p build/mod327 build/patcher/mod327
  "$JAVAC" $JOPT -cp "$GAME327_JAR${S}$LWJGL_JAR${S}build/common" -d build/mod327 $(find client/mod327 -name '*.java') client/mod/TWConnection.java client/mod/TWAuth.java client/mod/TWMouse.java
  cp -r build/mod327/* build/patcher/mod327/
  (cd build/common && find net/thewellodev/protocol -name '*.class') | while read f; do
    mkdir -p "build/patcher/mod327/$(dirname "$f")"; cp "build/common/$f" "build/patcher/mod327/$f"; done
  (cd build/patcher/mod327 && find . -name '*.class' | sed 's|^\./||' | sort) > build/patcher/mod327/index.txt
fi
if [ -n "$GAME330_JAR" ] && [ -f "$GAME330_JAR" ]; then
  echo "  client mod for inf-20100330-1611 (compiled against $GAME330_JAR)"
  mkdir -p build/mod330 build/patcher/mod330
  "$JAVAC" $JOPT -cp "$GAME330_JAR${S}$LWJGL_JAR${S}build/common" -d build/mod330 $(find client/mod330 -name '*.java') client/mod/TWConnection.java client/mod/TWAuth.java client/mod/TWMouse.java
  cp -r build/mod330/* build/patcher/mod330/
  (cd build/common && find net/thewellodev/protocol -name '*.class') | while read f; do
    mkdir -p "build/patcher/mod330/$(dirname "$f")"; cp "build/common/$f" "build/patcher/mod330/$f"; done
  (cd build/patcher/mod330 && find . -name '*.class' | sed 's|^\./||' | sort) > build/patcher/mod330/index.txt
fi
if [ -n "$GAME413_JAR" ] && [ -f "$GAME413_JAR" ]; then
  echo "  client mod for inf-20100413-1953 (compiled against $GAME413_JAR)"
  mkdir -p build/mod413 build/patcher/mod413
  "$JAVAC" $JOPT -cp "$GAME413_JAR${S}$LWJGL_JAR${S}build/common" -d build/mod413 $(find client/mod413 -name '*.java') client/mod/TWConnection.java client/mod/TWAuth.java client/mod/TWMouse.java
  cp -r build/mod413/* build/patcher/mod413/
  (cd build/common && find net/thewellodev/protocol -name '*.class') | while read f; do
    mkdir -p "build/patcher/mod413/$(dirname "$f")"; cp "build/common/$f" "build/patcher/mod413/$f"; done
  (cd build/patcher/mod413 && find . -name '*.class' | sed 's|^\./||' | sort) > build/patcher/mod413/index.txt
fi
if [ -n "$GAME414_JAR" ] && [ -f "$GAME414_JAR" ]; then
  echo "  client mod for inf-20100414 (compiled against $GAME414_JAR)"
  mkdir -p build/mod414 build/patcher/mod414
  "$JAVAC" $JOPT -cp "$GAME414_JAR${S}$LWJGL_JAR${S}build/common" -d build/mod414 $(find client/mod414 -name '*.java') client/mod/TWConnection.java client/mod/TWAuth.java client/mod/TWMouse.java
  cp -r build/mod414/* build/patcher/mod414/
  (cd build/common && find net/thewellodev/protocol -name '*.class') | while read f; do
    mkdir -p "build/patcher/mod414/$(dirname "$f")"; cp "build/common/$f" "build/patcher/mod414/$f"; done
  (cd build/patcher/mod414 && find . -name '*.class' | sed 's|^\./||' | sort) > build/patcher/mod414/index.txt
fi
if [ -n "$GAME415_JAR" ] && [ -f "$GAME415_JAR" ]; then
  echo "  client mod for inf-20100415 (compiled against $GAME415_JAR)"
  mkdir -p build/mod415 build/patcher/mod415
  "$JAVAC" $JOPT -cp "$GAME415_JAR${S}$LWJGL_JAR${S}build/common" -d build/mod415 $(find client/mod415 -name '*.java') client/mod/TWConnection.java client/mod/TWAuth.java client/mod/TWMouse.java
  cp -r build/mod415/* build/patcher/mod415/
  (cd build/common && find net/thewellodev/protocol -name '*.class') | while read f; do
    mkdir -p "build/patcher/mod415/$(dirname "$f")"; cp "build/common/$f" "build/patcher/mod415/$f"; done
  (cd build/patcher/mod415 && find . -name '*.class' | sed 's|^\./||' | sort) > build/patcher/mod415/index.txt
fi
mkdir -p build/patcher-all && cp -r build/common/* build/patcher/* build/patcher-all/
(cd build/patcher-all && "$JAR" xf ../../lib/asm-9.8.jar && "$JAR" xf ../../lib/asm-tree-9.8.jar && rm -rf META-INF module-info.class)
printf 'Main-Class: net.thewellodev.patcher.ClientPatcher\n' > build/patcher.mf
"$JAR" cfm dist/TheWellosInfdevClientPatcher.jar build/patcher.mf -C build/patcher-all .
"$JAVA" -jar dist/TheWellosInfdevClientPatcher.jar "$CLIENT_JAR" dist/inf-20100630-1340-mp.jar
if [ -n "$GAME227_JAR" ] && [ -f "$GAME227_JAR" ]; then
  "$JAVA" -jar dist/TheWellosInfdevClientPatcher.jar "$GAME227_JAR" dist/inf-20100227-1433-mp.jar
fi
if [ -n "$GAME313_JAR" ] && [ -f "$GAME313_JAR" ]; then
  "$JAVA" -jar dist/TheWellosInfdevClientPatcher.jar "$GAME313_JAR" dist/inf-20100313-mp.jar
fi

if [ -n "$GAME316_JAR" ] && [ -f "$GAME316_JAR" ]; then
  "$JAVA" -jar dist/TheWellosInfdevClientPatcher.jar "$GAME316_JAR" dist/inf-20100316-mp.jar
fi

if [ -n "$GAME320_JAR" ] && [ -f "$GAME320_JAR" ]; then
  "$JAVA" -jar dist/TheWellosInfdevClientPatcher.jar "$GAME320_JAR" dist/inf-20100320-mp.jar
fi

if [ -n "$GAME321_JAR" ] && [ -f "$GAME321_JAR" ]; then
  "$JAVA" -jar dist/TheWellosInfdevClientPatcher.jar "$GAME321_JAR" dist/inf-20100321-mp.jar
fi

if [ -n "$GAME325_JAR" ] && [ -f "$GAME325_JAR" ]; then
  "$JAVA" -jar dist/TheWellosInfdevClientPatcher.jar "$GAME325_JAR" dist/inf-20100325-1640-mp.jar
fi

if [ -n "$GAME327_JAR" ] && [ -f "$GAME327_JAR" ]; then
  "$JAVA" -jar dist/TheWellosInfdevClientPatcher.jar "$GAME327_JAR" dist/inf-20100327-mp.jar
fi

if [ -n "$GAME330_JAR" ] && [ -f "$GAME330_JAR" ]; then
  "$JAVA" -jar dist/TheWellosInfdevClientPatcher.jar "$GAME330_JAR" dist/inf-20100330-1611-mp.jar
fi

if [ -n "$GAME413_JAR" ] && [ -f "$GAME413_JAR" ]; then
  "$JAVA" -jar dist/TheWellosInfdevClientPatcher.jar "$GAME413_JAR" dist/inf-20100413-1953-mp.jar
fi
if [ -n "$GAME414_JAR" ] && [ -f "$GAME414_JAR" ]; then
  "$JAVA" -jar dist/TheWellosInfdevClientPatcher.jar "$GAME414_JAR" dist/inf-20100414-mp.jar
fi
if [ -n "$GAME415_JAR" ] && [ -f "$GAME415_JAR" ]; then
  "$JAVA" -jar dist/TheWellosInfdevClientPatcher.jar "$GAME415_JAR" dist/inf-20100415-mp.jar
fi

echo "[5/8] plugin APIs (api/<build>/)"
# inf-20100630-1340: the main API
"$JAVAC" $JOPT -d build/api $(find api/inf-20100630-1340/src -name '*.java')
mkdir -p build/api-src && cp -r api/inf-20100630-1340/src/* build/api-src/
"$JAR" cf dist/TheWellosInfdevServer-API-inf-20100630-1340.jar -C build/api .
"$JAR" cf dist/TheWellosInfdevServer-API-inf-20100630-1340-sources.jar -C build/api-src .
# inf-20100227-1433: its own API (net.thewellodev.api227) on top of the classes every build shares. The jar for
# plugin authors holds both, the server gets the api227 classes next to the main API.
mkdir -p build/api227 build/api227-all build/api227-src
"$JAVAC" $JOPT -cp build/api -d build/api227 $(find api/inf-20100227-1433/src -name '*.java')
cp -r build/api/* build/api227/* build/api227-all/
cp -r build/api-src/* api/inf-20100227-1433/src/* build/api227-src/
"$JAR" cf dist/TheWellosInfdevServer-API-inf-20100227-1433.jar -C build/api227-all .
"$JAR" cf dist/TheWellosInfdevServer-API-inf-20100227-1433-sources.jar -C build/api227-src .
# inf-20100316: its own API (net.thewellodev.api316), made the same way
mkdir -p build/api316 build/api316-all build/api316-src
"$JAVAC" $JOPT -cp build/api -d build/api316 $(find api/inf-20100316/src -name '*.java')
cp -r build/api/* build/api316/* build/api316-all/
cp -r build/api-src/* api/inf-20100316/src/* build/api316-src/
"$JAR" cf dist/TheWellosInfdevServer-API-inf-20100316.jar -C build/api316-all .
"$JAR" cf dist/TheWellosInfdevServer-API-inf-20100316-sources.jar -C build/api316-src .
# inf-20100320: its own API (net.thewellodev.api320), made the same way
mkdir -p build/api320 build/api320-all build/api320-src
"$JAVAC" $JOPT -cp build/api -d build/api320 $(find api/inf-20100320/src -name '*.java')
cp -r build/api/* build/api320/* build/api320-all/
cp -r build/api-src/* api/inf-20100320/src/* build/api320-src/
"$JAR" cf dist/TheWellosInfdevServer-API-inf-20100320.jar -C build/api320-all .
"$JAR" cf dist/TheWellosInfdevServer-API-inf-20100320-sources.jar -C build/api320-src .
# inf-20100321: its own API (net.thewellodev.api321), made the same way
mkdir -p build/api321 build/api321-all build/api321-src
"$JAVAC" $JOPT -cp build/api -d build/api321 $(find api/inf-20100321/src -name '*.java')
cp -r build/api/* build/api321/* build/api321-all/
cp -r build/api-src/* api/inf-20100321/src/* build/api321-src/
"$JAR" cf dist/TheWellosInfdevServer-API-inf-20100321.jar -C build/api321-all .
"$JAR" cf dist/TheWellosInfdevServer-API-inf-20100321-sources.jar -C build/api321-src .
# inf-20100325-1640: its own API (net.thewellodev.api325), made the same way
mkdir -p build/api325 build/api325-all build/api325-src
"$JAVAC" $JOPT -cp build/api -d build/api325 $(find api/inf-20100325-1640/src -name '*.java')
cp -r build/api/* build/api325/* build/api325-all/
cp -r build/api-src/* api/inf-20100325-1640/src/* build/api325-src/
"$JAR" cf dist/TheWellosInfdevServer-API-inf-20100325-1640.jar -C build/api325-all .
"$JAR" cf dist/TheWellosInfdevServer-API-inf-20100325-1640-sources.jar -C build/api325-src .
# inf-20100327: its own API (net.thewellodev.api327), made the same way
mkdir -p build/api327 build/api327-all build/api327-src
"$JAVAC" $JOPT -cp build/api -d build/api327 $(find api/inf-20100327/src -name '*.java')
cp -r build/api/* build/api327/* build/api327-all/
cp -r build/api-src/* api/inf-20100327/src/* build/api327-src/
"$JAR" cf dist/TheWellosInfdevServer-API-inf-20100327.jar -C build/api327-all .
"$JAR" cf dist/TheWellosInfdevServer-API-inf-20100327-sources.jar -C build/api327-src .
# inf-20100330-1611: its own API (net.thewellodev.api330), made the same way
mkdir -p build/api330 build/api330-all build/api330-src
"$JAVAC" $JOPT -cp build/api -d build/api330 $(find api/inf-20100330-1611/src -name '*.java')
cp -r build/api/* build/api330/* build/api330-all/
cp -r build/api-src/* api/inf-20100330-1611/src/* build/api330-src/
"$JAR" cf dist/TheWellosInfdevServer-API-inf-20100330-1611.jar -C build/api330-all .
"$JAR" cf dist/TheWellosInfdevServer-API-inf-20100330-1611-sources.jar -C build/api330-src .
# inf-20100413-1953: its own API (net.thewellodev.api413), made the same way
mkdir -p build/api413 build/api413-all build/api413-src
"$JAVAC" $JOPT -cp build/api -d build/api413 $(find api/inf-20100413-1953/src -name '*.java')
cp -r build/api/* build/api413/* build/api413-all/
cp -r build/api-src/* api/inf-20100413-1953/src/* build/api413-src/
"$JAR" cf dist/TheWellosInfdevServer-API-inf-20100413-1953.jar -C build/api413-all .
"$JAR" cf dist/TheWellosInfdevServer-API-inf-20100413-1953-sources.jar -C build/api413-src .
# inf-20100414: its own API (net.thewellodev.api414), made the same way
mkdir -p build/api414 build/api414-all build/api414-src
"$JAVAC" $JOPT -cp build/api -d build/api414 $(find api/inf-20100414/src -name '*.java')
cp -r build/api/* build/api414/* build/api414-all/
cp -r build/api-src/* api/inf-20100414/src/* build/api414-src/
"$JAR" cf dist/TheWellosInfdevServer-API-inf-20100414.jar -C build/api414-all .
"$JAR" cf dist/TheWellosInfdevServer-API-inf-20100414-sources.jar -C build/api414-src .
# inf-20100415: its own API (net.thewellodev.api415), made the same way
mkdir -p build/api415 build/api415-all build/api415-src
"$JAVAC" $JOPT -cp build/api -d build/api415 $(find api/inf-20100415/src -name '*.java')
cp -r build/api/* build/api415/* build/api415-all/
cp -r build/api-src/* api/inf-20100415/src/* build/api415-src/
"$JAR" cf dist/TheWellosInfdevServer-API-inf-20100415.jar -C build/api415-all .
"$JAR" cf dist/TheWellosInfdevServer-API-inf-20100415-sources.jar -C build/api415-src .
# inf-20100313: its own API (net.thewellodev.api313), made the same way
mkdir -p build/api313 build/api313-all build/api313-src
"$JAVAC" $JOPT -cp build/api -d build/api313 $(find api/inf-20100313/src -name '*.java')
cp -r build/api/* build/api313/* build/api313-all/
cp -r build/api-src/* api/inf-20100313/src/* build/api313-src/
"$JAR" cf dist/TheWellosInfdevServer-API-inf-20100313.jar -C build/api313-all .
"$JAR" cf dist/TheWellosInfdevServer-API-inf-20100313-sources.jar -C build/api313-src .

echo "[6/8] server"
"$JAVAC" $JOPT -cp "$ASM${S}build/common${S}build/api${S}build/api227${S}build/api313${S}build/api316${S}build/api320${S}build/api321${S}build/api325${S}build/api327${S}build/api330${S}build/api413${S}build/api414${S}build/api415" -d build/server $(find server/src -name '*.java')
"$JAVAC" $JOPT -cp "build/client-public.jar${S}build/common${S}build/server" -d build/bridge/bridge $(find server/bridge -name '*.java')
if [ -n "$GAME227_JAR" ] && [ -f "$GAME227_JAR" ]; then
  echo "  bridge for inf-20100227-1433 (compiled against $GAME227_JAR)"
  "$JAVAC" $JOPT -cp "$GAME227_JAR${S}build/common${S}build/server" -d build/bridge/bridge $(find server/bridge227 -name '*.java') server/bridgeshared/TWSeed.java
else
  echo "  inf-20100227-1433 bridge skipped (set GAME227_JAR to the original 20100227 jar to include it)"
fi
if [ -n "$GAME313_JAR" ] && [ -f "$GAME313_JAR" ]; then
  echo "  bridge for inf-20100313 (compiled against $GAME313_JAR)"
  "$JAVAC" $JOPT -cp "$GAME313_JAR${S}build/common${S}build/server${S}build/bridge/bridge" -d build/bridge/bridge $(find server/bridge313 -name '*.java') server/bridge227/TW227Nbt.java server/bridgeshared/TWSeed.java
else
  echo "  inf-20100313 bridge skipped (set GAME313_JAR to the original 20100313 jar to include it)"
fi
if [ -n "$GAME316_JAR" ] && [ -f "$GAME316_JAR" ]; then
  echo "  bridge for inf-20100316 (compiled against $GAME316_JAR)"
  "$JAVAC" $JOPT -cp "$GAME316_JAR${S}build/common${S}build/server${S}build/bridge/bridge" -d build/bridge/bridge $(find server/bridge316 -name '*.java') server/bridge227/TW227Nbt.java server/bridgeshared/TWSeed.java
else
  echo "  inf-20100316 bridge skipped (set GAME316_JAR to the original 20100316 jar to include it)"
fi
if [ -n "$GAME320_JAR" ] && [ -f "$GAME320_JAR" ]; then
  echo "  bridge for inf-20100320 (compiled against $GAME320_JAR)"
  "$JAVAC" $JOPT -cp "$GAME320_JAR${S}build/common${S}build/server${S}build/bridge/bridge" -d build/bridge/bridge $(find server/bridge320 -name '*.java') server/bridge227/TW227Nbt.java server/bridgeshared/TWSeed.java
else
  echo "  inf-20100320 bridge skipped (set GAME320_JAR to the original 20100320 jar to include it)"
fi
if [ -n "$GAME321_JAR" ] && [ -f "$GAME321_JAR" ]; then
  echo "  bridge for inf-20100321 (compiled against $GAME321_JAR)"
  "$JAVAC" $JOPT -cp "$GAME321_JAR${S}build/common${S}build/server${S}build/bridge/bridge" -d build/bridge/bridge $(find server/bridge321 -name '*.java') server/bridge227/TW227Nbt.java server/bridgeshared/TWSeed.java
else
  echo "  inf-20100321 bridge skipped (set GAME321_JAR to the original 20100321 jar to include it)"
fi
if [ -n "$GAME325_JAR" ] && [ -f "$GAME325_JAR" ]; then
  echo "  bridge for inf-20100325-1640 (compiled against $GAME325_JAR)"
  "$JAVAC" $JOPT -cp "$GAME325_JAR${S}build/common${S}build/server${S}build/bridge/bridge" -d build/bridge/bridge $(find server/bridge325 -name "*.java") server/bridge227/TW227Nbt.java server/bridgeshared/TWSeed.java server/bridgeshared/TWTick.java
else
  echo "  inf-20100325-1640 bridge skipped (set GAME325_JAR to the original 20100325-1640 jar to include it)"
fi
if [ -n "$GAME327_JAR" ] && [ -f "$GAME327_JAR" ]; then
  echo "  bridge for inf-20100327 (compiled against $GAME327_JAR)"
  "$JAVAC" $JOPT -cp "$GAME327_JAR${S}build/common${S}build/server${S}build/bridge/bridge" -d build/bridge/bridge $(find server/bridge327 -name "*.java") server/bridge227/TW227Nbt.java server/bridgeshared/TWSeed.java server/bridgeshared/TWTick.java
else
  echo "  inf-20100327 bridge skipped (set GAME327_JAR to the original 20100327 jar to include it)"
fi
if [ -n "$GAME330_JAR" ] && [ -f "$GAME330_JAR" ]; then
  echo "  bridge for inf-20100330-1611 (compiled against $GAME330_JAR)"
  "$JAVAC" $JOPT -cp "$GAME330_JAR${S}build/common${S}build/server${S}build/bridge/bridge" -d build/bridge/bridge $(find server/bridge330 -name "*.java") server/bridge227/TW227Nbt.java server/bridgeshared/TWSeed.java server/bridgeshared/TWTick.java
else
  echo "  inf-20100330-1611 bridge skipped (set GAME330_JAR to the original 20100330-1611 jar to include it)"
fi
if [ -n "$GAME413_JAR" ] && [ -f "$GAME413_JAR" ]; then
  echo "  bridge for inf-20100413-1953 (compiled against $GAME413_JAR)"
  "$JAVAC" $JOPT -cp "$GAME413_JAR${S}build/common${S}build/server${S}build/bridge/bridge" -d build/bridge/bridge $(find server/bridge413 -name "*.java") server/bridge227/TW227Nbt.java server/bridgeshared/TWSeed.java server/bridgeshared/TWTick.java
else
  echo "  inf-20100413-1953 bridge skipped (set GAME413_JAR to the original 20100413-1953 jar to include it)"
fi
if [ -n "$GAME414_JAR" ] && [ -f "$GAME414_JAR" ]; then
  echo "  bridge for inf-20100414 (compiled against $GAME414_JAR)"
  "$JAVAC" $JOPT -cp "$GAME414_JAR${S}build/common${S}build/server${S}build/bridge/bridge" -d build/bridge/bridge $(find server/bridge414 -name "*.java") server/bridge227/TW227Nbt.java server/bridgeshared/TWSeed.java server/bridgeshared/TWTick.java
else
  echo "  inf-20100414 bridge skipped (set GAME414_JAR to the original 20100414 jar to include it)"
fi
if [ -n "$GAME415_JAR" ] && [ -f "$GAME415_JAR" ]; then
  echo "  bridge for inf-20100415 (compiled against $GAME415_JAR)"
  "$JAVAC" $JOPT -cp "$GAME415_JAR${S}build/common${S}build/server${S}build/bridge/bridge" -d build/bridge/bridge $(find server/bridge415 -name "*.java") server/bridge227/TW227Nbt.java server/bridgeshared/TWSeed.java server/bridgeshared/TWTick.java
else
  echo "  inf-20100415 bridge skipped (set GAME415_JAR to the original 20100415 jar to include it)"
fi
mkdir -p build/server-all && cp -r build/common/* build/api/* build/api227/* build/api313/* build/api316/* build/api320/* build/api321/* build/api325/* build/api327/* build/api330/* build/api413/* build/api414/* build/api415/* build/server/* build/bridge/* build/server-all/
(cd build/server-all && "$JAR" xf ../../lib/asm-9.8.jar && "$JAR" xf ../../lib/asm-tree-9.8.jar && rm -rf META-INF module-info.class)
printf 'Main-Class: net.thewellodev.server.Main\n' > build/server.mf
"$JAR" cfm dist/TheWellosInfdevServer.jar build/server.mf -C build/server-all .

echo "[7/8] plugins (plugins/<build>/<plugin>/)"
for v in inf-20100630-1340 inf-20100227-1433 inf-20100313 inf-20100316 inf-20100320 inf-20100321 inf-20100325-1640 inf-20100327 inf-20100330-1611 inf-20100413-1953 inf-20100414 inf-20100415; do
  mkdir -p "dist/plugins/$v"
  for dir in plugins/$v/*/; do
    name=$(basename "$dir")
    out="build/plugins/$v/$name"
    mkdir -p "$out"
    "$JAVAC" $JOPT -cp "dist/TheWellosInfdevServer-API-$v.jar" -d "$out" $(find "$dir/src" -name '*.java')
    cp "$dir/src/plugin.yml" "$out/"
    jarname=$(grep '^name:' "$dir/src/plugin.yml" | sed 's/^name: *//' | tr -d '\r')
    "$JAR" cf "dist/plugins/$v/$jarname.jar" -C "$out" .
  done
done

echo "[8/8] release bundle -> $RELEASE_DIR"
VERSION=$(sed -n 's/.*String VERSION = "\([^"]*\)".*/\1/p' server/src/net/thewellodev/server/TWServer.java | head -1)
BUNDLE="TheWellosInfdevServer-$VERSION"
rm -rf "$RELEASE_DIR"
mkdir -p "$RELEASE_DIR/$BUNDLE/client/betacraft" "$RELEASE_DIR/$BUNDLE/server"
OUT="$RELEASE_DIR/$BUNDLE"
cp release/README.txt release/THIRD-PARTY-LICENSES.txt lib/ASM-LICENSE.txt LICENSE "$OUT/"
# Server
cp dist/TheWellosInfdevServer.jar release/server/start-server.bat release/server/start-server.sh release/server/server.properties "$OUT/server/"
# Client patcher and the BetaCraft files (the bundle holds no Minecraft code: each user patches his own jar)
cp dist/TheWellosInfdevClientPatcher.jar release/client/patch-client.bat release/client/patch-client.sh "$OUT/client/"
for v in inf-20100630-1340 inf-20100227-1433 inf-20100313 inf-20100316 inf-20100320 inf-20100321 inf-20100325-1640 inf-20100327 inf-20100330-1611 inf-20100413-1953 inf-20100414 inf-20100415; do
  cp "release/client/$v-mp.json" "release/client/$v-mp.info" "$OUT/client/betacraft/"
done
# Plugins and APIs, one folder for each build
for v in inf-20100630-1340 inf-20100227-1433 inf-20100313 inf-20100316 inf-20100320 inf-20100321 inf-20100325-1640 inf-20100327 inf-20100330-1611 inf-20100413-1953 inf-20100414 inf-20100415; do
  mkdir -p "$OUT/server/optional-plugins/$v" "$OUT/plugin-api/$v/example-plugin"
  cp dist/plugins/$v/*.jar "$OUT/server/optional-plugins/$v/"
  rm -f "$OUT/server/optional-plugins/$v/HelloPlugin.jar"
  cp "dist/TheWellosInfdevServer-API-$v.jar" "dist/TheWellosInfdevServer-API-$v-sources.jar" "$OUT/plugin-api/$v/"
  cp "docs/plugin-api/$v.md" "$OUT/plugin-api/$v/PLUGIN-API.md"
  cp -r plugins/$v/example/src "$OUT/plugin-api/$v/example-plugin/source"
  cp dist/plugins/$v/HelloPlugin.jar "$OUT/plugin-api/$v/example-plugin/" 2>/dev/null || true
done
# The archive for the GitHub release page
(cd "$RELEASE_DIR" && "$JAR" cfM "$BUNDLE.zip" "$BUNDLE")

# The patched game jars contain Minecraft code: they are never put into the bundle. They go to these folders if you ask.
if [ -n "$PATCHED_CLIENTS_DIR" ]; then
  mkdir -p "$PATCHED_CLIENTS_DIR"
  for v in inf-20100630-1340 inf-20100227-1433 inf-20100313 inf-20100316 inf-20100320 inf-20100321 inf-20100325-1640 inf-20100327 inf-20100330-1611 inf-20100413-1953 inf-20100414 inf-20100415; do
    [ -f "dist/$v-mp.jar" ] && cp "dist/$v-mp.jar" "$PATCHED_CLIENTS_DIR/"
  done
fi
if [ -n "$BETACRAFT_DIR" ] && [ -d "$BETACRAFT_DIR/versions/jsons" ]; then
  echo "[9/9] install into BetaCraft: $BETACRAFT_DIR"
  for v in inf-20100630-1340 inf-20100227-1433 inf-20100313 inf-20100316 inf-20100320 inf-20100321 inf-20100325-1640 inf-20100327 inf-20100330-1611 inf-20100413-1953 inf-20100414 inf-20100415; do
    if [ -f "dist/$v-mp.jar" ]; then
      cp "dist/$v-mp.jar" "$BETACRAFT_DIR/versions/"
      cp "release/client/$v-mp.json" "release/client/$v-mp.info" "$BETACRAFT_DIR/versions/jsons/"
    fi
  done
fi

# Only the release bundle is kept; the working folders are removed
rm -rf build dist
echo "Done. The release bundle and $BUNDLE.zip are in $RELEASE_DIR"

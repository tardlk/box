#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
TASK_JAVA="${IKANBOT_JAVA_HOME:-$HOME/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home}"
TASK_SDK="${IKANBOT_ANDROID_SDK:-$HOME/Library/Android/sdk}"
JAVAC="$TASK_JAVA/bin/javac"
JAR="$TASK_JAVA/bin/jar"
D8="$TASK_SDK/build-tools/35.0.0/d8"
ANDROID="$TASK_SDK/platforms/android-30/android.jar"
[[ -x "$JAVAC" && -x "$D8" && -f "$ANDROID" ]] || { echo '需要 JDK 17、Android SDK 30 和 build-tools 35.0.0' >&2; exit 1; }
export JAVA_HOME="$TASK_JAVA"
export PATH="$TASK_JAVA/bin:$PATH"
cd "$ROOT"
rm -rf build/stubs build/classes build/dex
mkdir -p build/stubs build/classes build/dex lib dist
python3 scripts/dependencies.py
find src/stubs/java -name '*.java' | sort > build/stubs.list
find src/main/java -name '*.java' | sort > build/sources.list
"$JAVAC" --release 8 -encoding UTF-8 -cp "$ANDROID" -d build/stubs @build/stubs.list
"$JAVAC" --release 8 -encoding UTF-8 -cp "$ANDROID:build/stubs:lib/jsoup.jar" -d build/classes @build/sources.list
"$JAR" cf build/plugin-classes.jar -C build/classes .
"$D8" --release --min-api 24 --lib "$ANDROID" --classpath build/stubs \
    --output build/dex build/plugin-classes.jar lib/jsoup.jar
python3 scripts/package.py

#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
TASK_JAVA="${IKANBOT_JAVA_HOME:-$HOME/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home}"
TASK_SDK="${IKANBOT_ANDROID_SDK:-$HOME/Library/Android/sdk}"
cd "$ROOT"
python3 scripts/dependencies.py
mkdir -p build/tests
CP="lib/json-test.jar:lib/jsoup.jar:$TASK_SDK/platforms/android-30/android.jar:build/classes:build/stubs"
"$TASK_JAVA/bin/javac" -encoding UTF-8 -cp "$CP" -d build/tests test/*.java
"$TASK_JAVA/bin/java" -cp "build/tests:$CP" com.github.catvod.spider.ContractTest
if [[ "${1:-}" == "--live" ]]; then
    "$TASK_JAVA/bin/java" -cp "build/tests:$CP" com.github.catvod.spider.LiveTest
fi

#!/bin/bash
export ANDROID_HOME="C:/Users/youlinaixu/AppData/Local/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export JAVA_HOME="$ANDROID_HOME/../jbr" 2>/dev/null || export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"

# Find Java
if [ ! -d "$JAVA_HOME" ]; then
    for jdk in "$ANDROID_HOME/../jbr" "C:/Program Files/Android/Android Studio/jbr" "C:/Users/youlinaixu/.gradle/jdks/"*; do
        if [ -d "$jdk" ]; then JAVA_HOME="$jdk"; break; fi
    done
fi

# Find gradle distribution
GRADLE_DIST="$GRADLE_USER_HOME/wrapper/dists/gradle-8.4-bin"
GRADLE_DIR=$(find "$GRADLE_DIST" -maxdepth 2 -name "gradle-8.4" -type d 2>/dev/null | head -1)

if [ -z "$GRADLE_DIR" ]; then
    echo "Downloading gradle..."
    mkdir -p "$GRADLE_DIST"
    cd "$GRADLE_DIST"
    curl -L -o gradle-8.4-bin.zip "https://services.gradle.org/distributions/gradle-8.4-bin.zip" 2>/dev/null
    unzip -q gradle-8.4-bin.zip 2>/dev/null
    GRADLE_DIR="$GRADLE_DIST/gradle-8.4"
fi

if [ -n "$GRADLE_DIR" ] && [ -f "$GRADLE_DIR/bin/gradle" ]; then
    "$GRADLE_DIR/bin/gradle" "$@"
else
    echo "Cannot find or download Gradle"
    echo "Please build from Android Studio: Build > Build Bundle(s) / APK(s) > Build APK(s)"
    exit 1
fi

#!/usr/bin/env sh
set -eu
APP_HOME="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
GRADLE_VERSION="9.2.1"
GRADLE_SHA256="72f44c9f8ebcb1af43838f45ee5c4aa9c5444898b3468ab3f4af7b6076c5bc3f"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
INSTALL_ROOT="$GRADLE_USER_HOME/yppowers-bootstrap"
GRADLE_HOME="$INSTALL_ROOT/gradle-$GRADLE_VERSION"
if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
    if [ -z "$GRADLE_SHA256" ]; then
        echo "Gradle $GRADLE_VERSION is required. Install Gradle $GRADLE_VERSION or run: gradle build" >&2
        exit 1
    fi
    mkdir -p "$INSTALL_ROOT"
    TMP_ZIP="$INSTALL_ROOT/gradle-$GRADLE_VERSION-bin.zip.part"
    ZIP_PATH="$INSTALL_ROOT/gradle-$GRADLE_VERSION-bin.zip"
    echo "Gradle $GRADLE_VERSION is not cached; downloading the official distribution..." >&2
    if command -v curl >/dev/null 2>&1; then
        curl --fail --location --retry 2 --output "$TMP_ZIP" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$TMP_ZIP" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
    else
        echo "Error: install curl or wget to bootstrap Gradle." >&2
        exit 1
    fi
    if command -v sha256sum >/dev/null 2>&1; then
        ACTUAL_SHA="$(sha256sum "$TMP_ZIP" | awk '{print $1}')"
    elif command -v shasum >/dev/null 2>&1; then
        ACTUAL_SHA="$(shasum -a 256 "$TMP_ZIP" | awk '{print $1}')"
    else
        echo "Error: sha256sum or shasum is required to verify the Gradle distribution." >&2
        rm -f "$TMP_ZIP"
        exit 1
    fi
    if [ "$ACTUAL_SHA" != "$GRADLE_SHA256" ]; then
        echo "Error: Gradle distribution SHA-256 did not match the pinned checksum." >&2
        rm -f "$TMP_ZIP"
        exit 1
    fi
    mv "$TMP_ZIP" "$ZIP_PATH"
    if ! command -v unzip >/dev/null 2>&1; then
        echo "Error: unzip is required to extract Gradle." >&2
        exit 1
    fi
    unzip -q -o "$ZIP_PATH" -d "$INSTALL_ROOT"
    rm -f "$ZIP_PATH"
fi
exec "$GRADLE_HOME/bin/gradle" "$@"

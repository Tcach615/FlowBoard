# Reproducible Android build environment: same JDK and SDK pins as
# .github/workflows/build-apk.yml, so local / Codespaces / CI behave the same.
FROM eclipse-temurin:17.0.13_11-jdk-jammy

ARG ANDROID_CMDLINE_TOOLS_VERSION=11076708
ARG ANDROID_CMDLINE_TOOLS_SHA256=2d2d50857e4eb553af5a6dc3ad507a17adf43d115264b1afc116f95c92e5e258
ARG ANDROID_PLATFORM=android-35
ARG ANDROID_BUILD_TOOLS=35.0.0

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk

RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl git unzip \
 && rm -rf /var/lib/apt/lists/*

RUN mkdir -p "$ANDROID_HOME/cmdline-tools" \
 && curl -fsSL -o /tmp/cmdline-tools.zip \
      "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS_VERSION}_latest.zip" \
 && echo "${ANDROID_CMDLINE_TOOLS_SHA256}  /tmp/cmdline-tools.zip" | sha256sum -c - \
 && unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools-unpack \
 && mv /tmp/cmdline-tools-unpack/cmdline-tools "$ANDROID_HOME/cmdline-tools/16.0" \
 && rm -rf /tmp/cmdline-tools-unpack /tmp/cmdline-tools.zip

ENV PATH="${PATH}:${ANDROID_HOME}/cmdline-tools/16.0/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/build-tools/${ANDROID_BUILD_TOOLS}"

# Accept licences non-interactively, then install only the pinned packages.
RUN (yes | sdkmanager --licenses > /dev/null || true) \
 && sdkmanager --install "platform-tools" "platforms;${ANDROID_PLATFORM}" "build-tools;${ANDROID_BUILD_TOOLS}" \
 && rm -rf "${ANDROID_HOME}/.temp"

WORKDIR /workspace

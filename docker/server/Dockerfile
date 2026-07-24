# syntax=docker/dockerfile:1
# Multi-arch: linux/amd64 (x64) + linux/arm64 (Apple Silicon, Raspberry Pi 4 / ARMv8)
#
# Local (build JAR inside Docker):
#   docker build -f docker/server/Dockerfile -t zula-server .
#
# CI uses --target runtime-prebuilt with a host-built server-all.jar in the context
# so arm64 images do not compile Gradle under QEMU.

ARG JRE_IMAGE=eclipse-temurin:21-jre-jammy
ARG JDK_IMAGE=eclipse-temurin:21-jdk-jammy

FROM ${JDK_IMAGE} AS build
WORKDIR /workspace

COPY gradlew ./
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/libs.versions.toml ./gradle/
COPY gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.properties ./gradle/wrapper/
COPY server/build.gradle.kts ./server/
COPY server/src/main ./server/src/main

RUN chmod +x ./gradlew \
  && ./gradlew :server:shadowJar --no-daemon -x test

FROM ${JRE_IMAGE} AS runtime-base
WORKDIR /app

RUN groupadd --system --gid 10001 zula \
  && useradd --system --uid 10001 --gid zula --home-dir /app --shell /usr/sbin/nologin zula

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# CI target: host-built fat JAR (avoids Gradle under QEMU on arm64).
# Build context must include server-all.jar at the context root.
FROM runtime-base AS runtime-prebuilt
COPY --chown=zula:zula server-all.jar /app/server.jar
USER zula
EXPOSE 8000
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/server.jar"]

# Default target: compile inside the image (local / full builds).
FROM runtime-base AS runtime
COPY --from=build --chown=zula:zula /workspace/server/build/libs/server-all.jar /app/server.jar
USER zula
EXPOSE 8000
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/server.jar"]

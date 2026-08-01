# syntax=docker/dockerfile:1
# Multi-arch: linux/amd64 (x64) + linux/arm64 (Apple Silicon, Raspberry Pi 4 / ARMv8)
#
# Fat JAR is arch-independent — compile once on the builder CPU ($BUILDPLATFORM),
# then copy into per-arch JRE images ($TARGETPLATFORM).
#
# Layer order (cache-friendly):
#   1) Gradle wrapper distribution
#   2) Dependency resolution (build scripts only)
#   3) Compile sources
#
#   docker build -f docker/server/Dockerfile -t zula-server .

ARG JDK_IMAGE=azul/zulu-openjdk-alpine:25
ARG JRE_IMAGE=azul/zulu-openjdk-alpine:25-jre

FROM --platform=$BUILDPLATFORM ${JDK_IMAGE} AS build
WORKDIR /workspace

# --- 1. Install Gradle via wrapper (invalidates only when wrapper changes) ---
COPY gradlew ./
COPY gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.properties ./gradle/wrapper/
RUN chmod +x ./gradlew \
  && ./gradlew --version --no-daemon

# --- 2. Resolve deps (invalidates when build scripts / catalog change) ---
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/libs.versions.toml ./gradle/
COPY server/build.gradle.kts ./server/
RUN ./gradlew :server:dependencies --no-daemon -x test

# --- 3. Compile (invalidates when sources change) ---
COPY server/src/main ./server/src/main
RUN ./gradlew :server:shadowJar --no-daemon -x test

FROM ${JRE_IMAGE} AS runtime
WORKDIR /app

RUN groupadd --system --gid 10001 zula \
  && useradd --system --uid 10001 --gid zula --home-dir /app --shell /usr/sbin/nologin zula

COPY --from=build --chown=zula:zula /workspace/server/build/libs/server-all.jar /app/server.jar

USER zula
EXPOSE 8000

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/server.jar"]

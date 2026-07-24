# syntax=docker/dockerfile:1
# Multi-arch: linux/amd64 (x64) + linux/arm64 (Apple Silicon, Raspberry Pi 4 / ARMv8)
#
#   docker build -f docker/server/Dockerfile -t zula-server .

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

FROM ${JRE_IMAGE} AS runtime
WORKDIR /app

RUN groupadd --system --gid 10001 zula \
  && useradd --system --uid 10001 --gid zula --home-dir /app --shell /usr/sbin/nologin zula

COPY --from=build --chown=zula:zula /workspace/server/build/libs/server-all.jar /app/server.jar

USER zula
EXPOSE 8000

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/server.jar"]

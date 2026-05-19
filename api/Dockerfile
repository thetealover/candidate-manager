# syntax=docker/dockerfile:1.7

# --- Stage 1: build the application distribution ---
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# Copy build scripts first to leverage Docker layer cache for dependencies.
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle

# Warm the Gradle cache (best effort; harmless if it fails on stripped build files).
RUN ./gradlew --version --no-daemon

# Now copy the modules and build.
COPY domain         ./domain
COPY application    ./application
COPY infrastructure ./infrastructure
COPY api            ./api

# assembleDist produces a tar/zip of the app + all runtime JARs + launch script.
RUN ./gradlew :api:assembleDist -x test --no-daemon

# --- Stage 2: minimal JRE runtime ---
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# Unpack the distribution as root, then hand ownership to the non-root user.
COPY --from=build /workspace/api/build/distributions/api-*.tar /tmp/app.tar
RUN tar xf /tmp/app.tar -C /app --strip-components=1 && rm /tmp/app.tar

# Non-root user for the running process.
RUN useradd -r -u 1001 -g root appuser && chown -R 1001:root /app
USER 1001

EXPOSE 8080

# The distribution's bin/api script launches the service.
ENTRYPOINT ["/app/bin/api"]

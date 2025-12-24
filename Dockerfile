# ---- Build stage ----
FROM gradle:8.8-jdk17 AS builder
WORKDIR /workspace

# Copy Gradle wrapper & config first for caching
COPY build.gradle.kts settings.gradle.kts gradlew ./
COPY gradle ./gradle

# Make gradlew executable
RUN chmod +x gradlew

# Download dependencies (cached unless build files change)
RUN ./gradlew dependencies --no-daemon || return 0

# Copy source files explicitly (avoids recursive copy of entire directory)
COPY config ./config
COPY extensions ./extensions

# Build the fat jar (skip tests for faster builds, remove -x test if you want them)
RUN ./gradlew shadowJar --no-daemon -x test


# ---- Runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# Set default log level (can be overridden at runtime)
ENV LOG_LEVEL=info

# Install helper tools and create user
RUN apt-get update && apt-get install -y --no-install-recommends \
        curl jq \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r appuser \
    && useradd -r -g appuser -u 10001 appuser

# Copy only the fat jar and configs from builder
COPY --from=builder /workspace/build/libs/*.jar /app/identity-hub.jar

# Ensure application files are owned by non-root user
RUN chown -R appuser:appuser /app

# Drop privileges: run as non-root
USER appuser

# Run the jar with config + logging level
CMD ["sh", "-c", "exec java -jar /app/identity-hub.jar --log-level=$LOG_LEVEL"]
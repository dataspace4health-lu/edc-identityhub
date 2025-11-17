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

# Copy the rest of the source
COPY . .

# Build the fat jar (skip tests for faster builds, remove -x test if you want them)
RUN ./gradlew shadowJar --no-daemon -x test


# ---- Runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# Install helper tools if needed
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl jq \
 && rm -rf /var/lib/apt/lists/*

# Copy only the fat jar and configs from builder
COPY --from=builder /workspace/build/libs/*.jar /app/identity-hub.jar
# Copy default java.util.logging config (can be overridden in image or mounted)
COPY logging.properties /app/logging.properties

# Default log level environment variable (can be overridden at runtime)
ENV LOG_LEVEL=DEBUG

# Run the jar with config + logging
CMD ["sh", "-c", "exec java -jar /app/identity-hub.jar --log-level=debug"]
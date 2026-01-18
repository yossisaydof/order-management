# ============================================
# Yotpo Order Management System - Dockerfile
# ============================================
# Multi-stage build for optimal image size and security
#
# Stage 1: Build - Compiles the application using Maven
# Stage 2: Runtime - Runs the application with minimal footprint
#
# Usage:
#   docker build -t yotpo-orders -f docker/Dockerfile .
#   docker run -p 8080:8080 yotpo-orders
#
# ============================================

# --------------------------------------------
# Stage 1: Build
# --------------------------------------------
# Using Eclipse Temurin (formerly AdoptOpenJDK) - production-grade JDK
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /app

# Copy Maven wrapper and pom.xml first (for better layer caching)
# This layer is cached unless pom.xml changes
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Download dependencies (cached unless pom.xml changes)
# This speeds up subsequent builds significantly
RUN ./mvnw dependency:go-offline -B

# Copy source code (this layer changes frequently)
COPY src/ src/

# Build the application
# -DskipTests: Skip tests in Docker build (run separately in CI/CD)
# -B: Batch mode (non-interactive, less output)
RUN ./mvnw clean package -DskipTests -B

# --------------------------------------------
# Stage 2: Runtime
# --------------------------------------------
# Using JRE-only image for smaller footprint and better security
FROM eclipse-temurin:17-jre-jammy AS runtime

WORKDIR /app

# Create non-root user for security best practice
# Application should never run as root in production
RUN groupadd -g 1001 appgroup && \
    useradd -u 1001 -g appgroup -m -s /usr/sbin/nologin appuser

# Copy the built JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Change ownership to non-root user
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose application port
EXPOSE 8080

# Health check for container orchestration (Docker Compose, Kubernetes)
# Checks the Spring Boot Actuator health endpoint
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# JVM tuning for containerized environments
# -XX:+UseContainerSupport: Enables JVM to detect container limits
# -XX:MaxRAMPercentage=75: Use up to 75% of container memory
# -Djava.security.egd: Faster random number generation
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Run the application
# Using shell form to allow environment variable expansion
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

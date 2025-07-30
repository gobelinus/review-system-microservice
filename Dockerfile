# Multi-stage Dockerfile for Review System Microservice

# Stage 1: Build stage
FROM openjdk:17-jdk-slim AS builder

# Install required packages
RUN apt-get update && apt-get install -y \
    maven \
    curl \
    git \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy Maven files first for better layer caching
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn

# For dev plugin com.rudikershaw.gitbuildhook:git-build-hook-maven-plugin:3.4.1:install
# a local git repository is required
RUN git init

# Download dependencies (this layer will be cached if pom.xml doesn't change)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Development stage
FROM openjdk:17-jdk-slim AS development

# Install required packages for development
RUN apt-get update && apt-get install -y \
    curl \
    wget \
    vim \
    net-tools \
    telnet \
    && rm -rf /var/lib/apt/lists/*

# Create app user for security
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

# Set working directory
WORKDIR /app

# Copy built JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Copy Maven wrapper for development
COPY --from=builder /app/mvnw ./
COPY --from=builder /app/.mvn ./.mvn
COPY --from=builder /app/pom.xml ./

# Create directories for logs and data
RUN mkdir -p /app/logs /app/data && \
    chown -R appuser:appgroup /app

# Expose application port
EXPOSE 8080

# Expose LiveReload port for development
EXPOSE 35729

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Switch to app user
USER appuser

# Default command for development
CMD ["java", \
     "-Djava.security.egd=file:/dev/./urandom", \
     "-Dspring.profiles.active=local", \
     "-Dspring.devtools.restart.enabled=true", \
     "-Dspring.devtools.livereload.enabled=true", \
     "-jar", \
     "app.jar"]

# Stage 3: Production stage
FROM openjdk:17-jre-slim AS production

# Install required packages for production
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Create app user for security
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

# Set working directory
WORKDIR /app

# Copy built JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Create directories for logs and data
RUN mkdir -p /app/logs /app/data && \
    chown -R appuser:appgroup /app

# Expose application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Switch to app user
USER appuser

# JVM optimization for production
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC -XX:+UseStringDeduplication"

# Default command for production
CMD java $JAVA_OPTS \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.profiles.active=prod \
    -jar app.jar

# Labels for better container management
LABEL maintainer="Review System Team" \
      version="1.0.0" \
      description="Review System Microservice for processing hotel reviews" \
      org.opencontainers.image.source="https://github.com/gobelinus/review-system-microservice"

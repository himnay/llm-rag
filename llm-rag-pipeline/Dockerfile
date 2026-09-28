# syntax=docker/dockerfile:1

# ---- build stage ----
# Java 27 images: eclipse-temurin:27 wasn't on Docker Hub yet (Sept 2026), so these stages use
# SapMachine 27, an OpenJDK build that is also a Docker Official Image.
FROM sapmachine:27-jdk-ubuntu AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src ./src
RUN ./mvnw -q -B -DskipTests clean package

# ---- layer extraction stage ----
FROM sapmachine:27-jre-ubuntu AS extract
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Boot 3.3+/4 replaced -Djarmode=layertools with the 'tools' jarmode
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ---- runtime stage ----
FROM sapmachine:27-jre-ubuntu
WORKDIR /app
# Install Tesseract OCR native libraries required by Tess4J, and curl for the HEALTHCHECK
# (the SapMachine image has no curl)
RUN apt-get update && apt-get install -y --no-install-recommends \
    tesseract-ocr \
    tesseract-ocr-eng \
    curl \
    && rm -rf /var/lib/apt/lists/*
# Run as a non-root user
# The logback config writes logs/llm-rag-pipeline.json under /app, which the non-root user must be able to create
RUN useradd --system --uid 10001 appuser \
    && mkdir -p /app/logs && chown appuser /app/logs
COPY --from=extract /app/extracted/dependencies/ ./
COPY --from=extract /app/extracted/spring-boot-loader/ ./
COPY --from=extract /app/extracted/snapshot-dependencies/ ./
COPY --from=extract /app/extracted/application/ ./
USER appuser
EXPOSE 8081
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8081/actuator/health || exit 1

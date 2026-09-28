# Java 27 images: eclipse-temurin:27 wasn't on Docker Hub yet (Sept 2026), so these stages use
# SapMachine 27, an OpenJDK build that is also a Docker Official Image.
FROM sapmachine:27-jdk-ubuntu AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline -q
COPY src src
RUN ./mvnw package -DskipTests -q

FROM sapmachine:27-jre-ubuntu AS extract
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
# Boot 3.3+/4 replaced -Djarmode=layertools with the 'tools' jarmode
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM sapmachine:27-jre-ubuntu
WORKDIR /app
# The SapMachine image has no curl; the HEALTHCHECK below needs it
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd --system spring && useradd --system --gid spring spring
COPY --from=extract /app/extracted/dependencies/ ./
COPY --from=extract /app/extracted/spring-boot-loader/ ./
COPY --from=extract /app/extracted/snapshot-dependencies/ ./
COPY --from=extract /app/extracted/application/ ./
USER spring:spring
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

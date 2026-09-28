# Java 27 images: eclipse-temurin:27 wasn't on Docker Hub yet (Sept 2026), so these stages use
# SapMachine 27, an OpenJDK build that is also a Docker Official Image.
FROM sapmachine:27-jre-alpine AS extract
WORKDIR /app
COPY target/*.jar app.jar
# Boot 3.3+/4 replaced -Djarmode=layertools with the 'tools' jarmode
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM sapmachine:27-jre-alpine
WORKDIR /app
COPY --from=extract /app/extracted/dependencies/ ./
COPY --from=extract /app/extracted/spring-boot-loader/ ./
COPY --from=extract /app/extracted/snapshot-dependencies/ ./
COPY --from=extract /app/extracted/application/ ./
EXPOSE 8080
# ZGC is generational-only since JDK 24 (JEP 490); JDK 27 rejects the old -XX:+ZGenerational flag
ENTRYPOINT ["java", "-XX:+UseZGC", "org.springframework.boot.loader.launch.JarLauncher"]
# The alpine image has BusyBox wget but no curl
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

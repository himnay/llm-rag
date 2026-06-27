FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app
COPY target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=runtime /app/dependencies/ ./
COPY --from=runtime /app/spring-boot-loader/ ./
COPY --from=runtime /app/snapshot-dependencies/ ./
COPY --from=runtime /app/application/ ./
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:+ZGenerational", "org.springframework.boot.loader.launch.JarLauncher"]
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

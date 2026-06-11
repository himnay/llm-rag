FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline -q
COPY src src
RUN ./mvnw package -DskipTests -q

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

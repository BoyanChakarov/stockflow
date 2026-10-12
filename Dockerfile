FROM eclipse-temurin:21-jdk-jammy@sha256:e0c60c487345d1dc9d0fc7b6f0496f3cc941e5132e09296cc17a6decc71b902b AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw -B -ntp dependency:go-offline
COPY src src
RUN ./mvnw -B -ntp package -DskipTests
COPY docker/Healthcheck.java ./
RUN javac --release 21 Healthcheck.java

FROM eclipse-temurin:21-jre-jammy@sha256:f04fb34e053148344e83317976114ec3f37e4b830ec8bdab5a2fe3cecd7d010b
RUN groupadd --system stockflow && useradd --system --gid stockflow --home-dir /app stockflow
WORKDIR /app
COPY --from=build --chown=stockflow:stockflow /build/target/stockflow.jar /app/stockflow.jar
COPY --from=build --chown=stockflow:stockflow /build/Healthcheck.class /app/Healthcheck.class
USER stockflow
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 CMD ["java", "-cp", "/app", "Healthcheck"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/stockflow.jar"]

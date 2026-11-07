FROM maven:3.9.12-eclipse-temurin-21 AS test
WORKDIR /build
COPY pom.xml .
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp verify

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && groupadd --gid 10001 structiq && useradd --system --uid 10001 --gid 10001 --create-home structiq
WORKDIR /app
RUN mkdir -p /var/lib/structiq/documents && chown 10001:10001 /var/lib/structiq/documents && chmod 0750 /var/lib/structiq/documents
COPY --from=test /build/target/structiq-api-0.1.0.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

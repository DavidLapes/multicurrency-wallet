# Build stage: compile and package the jar with the Maven wrapper
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
# Download dependencies in a separate layer so code changes don't re-download them
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B package -DskipTests

# Runtime stage: JRE only, non-root user
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system wallet
USER wallet
COPY --from=build /src/target/wallet.jar wallet.jar
# JSON logs (Elastic Common Schema) in the container; plain text when run locally
ENV LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "wallet.jar"]

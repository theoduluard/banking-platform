# ─── Stage 1: Build ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copy Maven wrapper and pom first — lets Docker cache the dependency layer
COPY .mvn/  .mvn/
COPY mvnw   pom.xml ./
RUN ./mvnw dependency:go-offline -B -q

# Copy source and build (tests already ran in CI)
COPY src/ src/
RUN ./mvnw package -DskipTests -q

# ─── Stage 2: Runtime ────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S solaris && adduser -S solaris -G solaris
USER solaris

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]

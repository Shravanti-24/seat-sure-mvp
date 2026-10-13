# Build stage: compiled with the full JDK and Maven, dependencies cached in their own layer.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# Runtime stage: slim JRE, non-root user, nothing from the build toolchain.
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S seatsure && adduser -S seatsure -G seatsure
WORKDIR /app
COPY --from=build /build/target/seatsure-*.jar app.jar
RUN chown -R seatsure:seatsure /app
USER seatsure
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75" \
    PORT=8080
# Follows PORT, because hosts such as Render inject their own.
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
    CMD wget -qO- "http://localhost:${PORT}/actuator/health" | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

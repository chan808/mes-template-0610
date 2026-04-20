FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY gradlew .
COPY gradle/ gradle/
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies --configuration runtimeClasspath || true
COPY . .
RUN chmod +x gradlew && ./gradlew --no-daemon -x test bootJar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=builder /app/build/libs/*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-26-alpine@sha256:4c85d516dd8c7e50d49a403d604e565e0dbb09a790186fe3245725d313ae7cb9 AS builder
WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw --batch-mode dependency:go-offline

COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw --batch-mode package -DskipTests

FROM eclipse-temurin:25-jre-alpine@sha256:e1898fd6a11fc965a994d8ace186fd3585aeb636cf8f05f47a723fcfee0b0918
WORKDIR /app

# Match the existing Vietnam business-date policy; persisted Instants remain UTC.
ENV TZ=Asia/Ho_Chi_Minh

RUN addgroup -S moodly && adduser -S moodly -G moodly
COPY --from=builder /workspace/target/moodly-0.0.1-SNAPSHOT.jar app.jar

USER moodly
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
	CMD wget --quiet --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]

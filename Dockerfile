FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN chmod +x gradlew

COPY src src

RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN useradd --system --uid 10001 --create-home appuser

COPY --from=builder /workspace/build/libs/*.jar /app/app.jar

ENV SERVER_PORT=8081
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS=""

EXPOSE 8081

USER appuser

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]

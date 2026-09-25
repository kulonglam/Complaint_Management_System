FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline
COPY src ./src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget \
    && addgroup -S cms && adduser -S cms -G cms
WORKDIR /app
COPY --from=build /app/target/complaint-management-backend-1.0.0.jar app.jar
USER cms
ENV PORT=8000
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=3s --start-period=25s --retries=3 \
    CMD ["sh", "-c", "wget -qO- http://127.0.0.1:${PORT:-8000}/health >/dev/null || exit 1"]
ENTRYPOINT ["java", "-jar", "app.jar"]

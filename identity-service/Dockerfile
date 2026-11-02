# The jar is platform-independent, so it is built once on the builder's own platform; only the runtime stage is per
# target platform (linux/amd64, linux/arm64), which needs no emulation.
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -Dmaven.wagon.http.retryHandler.count=3 \
    dependency:resolve dependency:resolve-plugins

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER 10001:10001
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]

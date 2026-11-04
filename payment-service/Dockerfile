FROM maven:3.9-eclipse-temurin-21
WORKDIR /app
COPY pom.xml .
RUN mvn -B -Dmaven.wagon.http.retryHandler.count=3 dependency:go-offline
COPY src ./src
EXPOSE 8086
CMD ["mvn", "-B", "spring-boot:run"]

FROM maven:3.9-eclipse-temurin-21
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package
EXPOSE 8089
CMD ["java", "-jar", "target/payment-simulator.jar"]

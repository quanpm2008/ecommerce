FROM maven:3.9-eclipse-temurin-21
WORKDIR /app

COPY payment-simulator/pom.xml ./simulator/pom.xml
COPY payment-simulator/src ./simulator/src
RUN mvn -B -f simulator/pom.xml -DskipTests package

COPY payment-service/pom.xml .
RUN mvn -B -Dmaven.wagon.http.retryHandler.count=3 dependency:go-offline
COPY payment-service/src ./src
COPY payment-service/entrypoint.sh ./entrypoint.sh
RUN sed -i 's/\r$//' ./entrypoint.sh && chmod +x ./entrypoint.sh

EXPOSE 8086 8089
CMD ["./entrypoint.sh"]

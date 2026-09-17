FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/

RUN chmod +x mvnw && ./mvnw -B -DskipTests clean package

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN apk add --no-cache bash aws-cli postgresql-client

COPY --from=build /app/target/*.jar app.jar
COPY scripts/ scripts/

RUN chmod +x scripts/backup.sh scripts/restore.sh

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java -Dserver.port=${PORT:-8080} -jar app.jar"]
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY backend/ ./
ARG SERVICE
RUN mvn -B -ntp -pl "$SERVICE" -am -DskipTests package

FROM eclipse-temurin:17-jre
ARG SERVICE
WORKDIR /app
COPY --from=build /build/${SERVICE}/target/${SERVICE}-1.0-SNAPSHOT.jar /app/app.jar
RUN mkdir /app/data && chown -R 10001:10001 /app
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

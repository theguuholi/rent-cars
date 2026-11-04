# syntax=docker/dockerfile:1
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src/main src/main
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -Dmaven.test.skip=true package && \
    cp target/*.jar application.jar && java -Djarmode=tools -jar application.jar extract --layers --destination /layers

FROM eclipse-temurin:25-jre-noble
WORKDIR /app
COPY --from=build /layers/dependencies/ ./
COPY --from=build /layers/spring-boot-loader/ ./
COPY --from=build /layers/snapshot-dependencies/ ./
COPY --from=build /layers/application/ ./
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "application.jar"]

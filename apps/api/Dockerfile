# syntax=docker/dockerfile:1
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src/main src/main
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -Dmaven.test.skip=true package && \
    cp target/*.jar application.jar && java -Djarmode=tools -jar application.jar extract --layers --destination /layers

FROM gcr.io/distroless/java25-debian13:nonroot
WORKDIR /app
COPY --from=build /layers/dependencies/ ./
COPY --from=build /layers/spring-boot-loader/ ./
COPY --from=build /layers/snapshot-dependencies/ ./
COPY --from=build /layers/application/ ./
USER 65532:65532
EXPOSE 8080
ENTRYPOINT ["/usr/bin/java", "-jar", "application.jar"]

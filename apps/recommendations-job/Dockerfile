# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src/main src/main
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-noble
RUN groupadd --gid 1001 spark && useradd --uid 1001 --gid 1001 --create-home spark
WORKDIR /tmp
COPY --from=build /build/target/dependency/ /app/lib/
COPY --from=build /build/target/rent-cars-recommendations-job-0.0.1-SNAPSHOT.jar /app/job.jar
USER 1001:1001
ENV HADOOP_USER_NAME=hadoop
ENTRYPOINT ["java", "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED", "-Xmx1g", "-Dspark.master=local[2]", "-Dspark.ui.enabled=false", "-Dspark.sql.shuffle.partitions=4", "-Dspark.hadoop.dfs.replication=1", "-cp", "/app/job.jar:/app/lib/*", "com.github.theguuholi.recommendationsjob.RecommendationJob"]

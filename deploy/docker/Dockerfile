# 在项目根目录构建：docker build -f deploy/docker/Dockerfile -t ota-platform .
# 前端打包结果（src/main/resources/static/vue）随代码提交，这里只打 jar
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/target/ota-platform.jar /app/ota-platform.jar
VOLUME /data
EXPOSE 8812
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/ota-platform.jar", "--ota.data-dir=/data"]

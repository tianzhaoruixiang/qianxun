# ───────── Build：Maven 3.9 + JDK 21 ─────────
# 注：未使用 `# syntax=docker/dockerfile:1.x` 与 `--mount=type=cache`，
# 是为了避免某些镜像 mirror 拉不到 dockerfile frontend 镜像；
# 依靠 Docker 自带 layer 缓存：pom.xml 不变时 dependency:go-offline 那一层会复用。
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY backend/pom.xml ./pom.xml
RUN mvn -B -q -DskipTests dependency:go-offline || true

COPY backend/src ./src
RUN mvn -B -q -DskipTests package \
 && cp target/qianxun-server-*.jar /tmp/app.jar

# ───────── Runtime：Temurin JRE 21 ─────────
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /tmp/app.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]

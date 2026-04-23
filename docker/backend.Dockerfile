# ───────── Build：Maven 3.9 + JDK 21 ─────────
# 注：未使用 `# syntax=...` 与 `--mount=type=cache`，避免部分环境拉不到 Dockerfile 语法镜像。
# pom.xml 不变时，下面 RUN 整层可复用，仅改源码时不必重新下依赖。
#
# 不用 dependency:go-offline：会为“离线全量构建”拉插件生态，国内/弱网下极慢且 -q 无输出像卡死。
# resolve + resolve-plugins 已覆盖 package 所需依赖与插件，首构建仍可能需数分钟，属正常。
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

ENV MAVEN_OPTS="-Dmaven.wagon.http.retryHandler.count=5"

COPY docker/maven-settings.xml /root/.m2/settings.xml

COPY backend/pom.xml ./pom.xml
RUN mvn -s /root/.m2/settings.xml -B -ntp -DskipTests dependency:resolve-plugins dependency:resolve

COPY backend/src ./src
RUN mvn -s /root/.m2/settings.xml -B -ntp -DskipTests package \
 && cp target/qianxun-server-*.jar /tmp/app.jar

# ───────── Runtime：Temurin JRE 21 ─────────
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /tmp/app.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]

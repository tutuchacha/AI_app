# 构建阶段：容器内用 Maven 编译，服务器不需要装 JDK / Maven
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 先只拷 pom 和 wrapper，依赖命中缓存；改业务代码不会重新下载依赖
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -Pprod -DskipTests clean package

# 运行阶段：只带 JRE，镜像比构建阶段小很多
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd -r app && useradd -r -g app -d /app app
COPY --from=build /build/target/*.jar app.jar
RUN mkdir -p /app/uploads && chown -R app:app /app
USER app

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Shanghai"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

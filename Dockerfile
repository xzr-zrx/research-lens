# ---- 构建阶段 ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
# 先下载依赖（利用 Docker 缓存层）
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# ---- 运行阶段 ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# 数据、上传目录
RUN mkdir -p /app/data /app/uploads

COPY --from=build /app/target/*.jar app.jar

EXPOSE 9900

ENTRYPOINT ["java", "-jar", "app.jar"]

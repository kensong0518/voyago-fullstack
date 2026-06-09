# syntax=docker/dockerfile:1.7

# ============ build stage ============
# 把 pom.xml 先 COPY，讓 Maven 依賴成獨立層；
# 之後只改 src 不會重抓所有套件 → 鏡像 rebuild 從 ~2 min 縮到 ~20s
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml ./
# 先把 deps 拉進 local repo（src 不變時這層會被 cache 命中）
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -ntp dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -ntp clean package -DskipTests \
    && cp target/voyago-backend.jar /app/app.jar

# ============ run stage ============
# Alpine JRE 比 -jre 小約 60%；以 non-root user 跑，符合容器最佳實務
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 非 root 使用者（UID 10001）
RUN addgroup -S app && adduser -S -G app -u 10001 app

COPY --from=build --chown=app:app /app/app.jar /app/app.jar

USER app
EXPOSE 8080

# JVM 在容器中自動感知記憶體限制（Java 17 預設啟用，這裡明示）
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

# 健康檢查：用 Spring Boot Actuator 的 /actuator/health
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -q --spider http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java","-jar","/app/app.jar"]

# syntax=docker/dockerfile:1.7
#
# 整個網站的映像：一個 Spring Boot 服務同時提供前端網頁與 API（單一網址部署，見 SpaWebConfig）。
# build context 是 repo 根目錄、檔案用 ASCII 名稱放在根目錄：Render 的 Blueprint 不接受非 ASCII 路徑
# （./後端/Dockerfile 會被判定無效）。render.yaml、docker-compose.yml 與 CI 都用這一份。

# ============ 前端 build stage ============
# 正式環境的前端設定在 前端/.env.production（全是公開值），vite build 會自動讀取
FROM node:20-alpine AS web
WORKDIR /web

COPY 前端/package.json 前端/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

COPY 前端/ ./
RUN npm run build

# ============ 後端 build stage ============
# 把 pom.xml 先 COPY，讓 Maven 依賴成獨立層；
# 之後只改 src 不會重抓所有套件 → 鏡像 rebuild 從 ~2 min 縮到 ~20s
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY 後端/pom.xml ./
# 先把 deps 拉進 local repo（src 不變時這層會被 cache 命中）
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -ntp dependency:go-offline

COPY 後端/src ./src
# 前端產物放進 classpath:/static/，跟後端打包成同一個 jar
COPY --from=web /web/dist ./src/main/resources/static
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -ntp clean package -DskipTests \
    && cp target/voyago-backend.jar /app/app.jar
# 把 fat jar 拆開成「一般 classpath」（classes + lib/*.jar）：
# JVM 的類別資料共享（CDS）只對一般 jar 有效，對 Spring Boot 巢狀 jar 裡的類別無效
RUN mkdir -p /app/exploded && cd /app/exploded && jar -xf /app/app.jar \
    && mkdir -p /app/out && mv BOOT-INF/lib /app/out/lib && mv BOOT-INF/classes /app/out/classes

# ============ run stage ============
# Alpine JRE 比 -jre 小約 60%；以 non-root user 跑，符合容器最佳實務
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 非 root 使用者（UID 10001）；建 CDS 存檔要寫 /app，所以試跑在切換使用者之前做
RUN addgroup -S app && adduser -S -G app -u 10001 app

COPY --from=build --chown=app:app /app/out/lib /app/lib
COPY --from=build --chown=app:app /app/out/classes /app/classes

# ---- 啟動加速：CDS（Class Data Sharing）----
# 免費方案只有 0.1 顆 CPU，冷啟動有一大半時間花在「載入、驗證幾千個類別」。
# 建置時先試跑一次（spring.context.exit=onRefresh：初始化完就結束，不跑資料初始化），
# 把用到的類別存成 app.jsa；正式啟動時直接映射這個檔，省掉重新解析類別的時間。
# 試跑用的密鑰、連線字串都是假的，只存在建置過程，不會進正式環境。
# 試跑的 JVM 參數要跟 render.yaml 的 JAVA_TOOL_OPTIONS 一致（GC 等設定不同，存檔會被忽略）。
# Alpine 版的 JRE 沒有附 JDK 類別的基礎存檔，動態存檔要以它為基礎，先產生（-Xshare:dump）
RUN JAVA_TOOL_OPTIONS="" java -Xshare:dump -XX:+UseSerialGC > /dev/null
# 試跑不連資料庫：關掉 Hibernate 改表（ddl-auto=none）與讀取資料庫中繼資料、關掉啟動時的資料庫升級。
# 萬一還是有地方去連資料庫而失敗也沒關係：JVM 結束時照樣把已載入的類別存檔，只是少存一些
RUN JAVA_TOOL_OPTIONS="" java -XX:ArchiveClassesAtExit=/app/app.jsa -Xlog:cds=warning \
      -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:MaxRAMPercentage=70.0 \
      -Dspring.context.exit=onRefresh \
      -Dapp.db.migrate=false \
      -DJWT_SECRET=cds-training-only-not-a-real-secret-0123456789 \
      -DDB_PASSWORD=x \
      -Dspring.datasource.url=jdbc:mysql://127.0.0.1:1/cds \
      -Dspring.jpa.hibernate.ddl-auto=none \
      -Dspring.jpa.properties.hibernate.temp.use_jdbc_metadata_defaults=false \
      -Dspring.jpa.database-platform=org.hibernate.dialect.MySQLDialect \
      -cp "/app/classes:/app/lib/*" com.voyago.VoyagoApplication > /tmp/cds-training.log 2>&1 \
    || true; \
    grep -E "Root WebApplicationContext|CDS|cds" /tmp/cds-training.log | cut -c1-200; \
    (chmod a+r /app/app.jsa && ls -la /app/app.jsa) 2>/dev/null \
    || echo "CDS 存檔沒有產生，啟動時會自動略過（只是比較慢，不影響功能）"

USER app
EXPOSE 8080

# 跟 render.yaml 一致（免費方案 512MB、0.1 CPU：SerialGC 最省、C1 編譯啟動最快）；CDS 存檔也是用這組參數建的
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Djava.security.egd=file:/dev/./urandom"

# 健康檢查：用 Spring Boot Actuator 的 /actuator/health
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -q --spider http://127.0.0.1:8080/actuator/health || exit 1

# 有 CDS 存檔就用（-Xshare:auto 預設：存檔不相容時 JVM 自己略過，照常啟動）
ENTRYPOINT ["sh", "-c", "exec java $( [ -f /app/app.jsa ] && echo -XX:SharedArchiveFile=/app/app.jsa ) -cp '/app/classes:/app/lib/*' com.voyago.VoyagoApplication"]

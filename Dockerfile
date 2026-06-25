# Multi-stage build for OpenClaw Wargame
# Stage 1: Build fat jar
FROM maven:3.8-openjdk-17 AS builder
WORKDIR /build

# Cache deps
COPY pom.xml ./
COPY openclaw-wargame-core/pom.xml openclaw-wargame-core/
COPY openclaw-wargame-realtime/pom.xml openclaw-wargame-realtime/
COPY openclaw-wargame-analysis/pom.xml openclaw-wargame-analysis/
COPY openclaw-wargame-ai/pom.xml openclaw-wargame-ai/
COPY openclaw-wargame-weapon/pom.xml openclaw-wargame-weapon/
COPY openclaw-wargame-simulation/pom.xml openclaw-wargame-simulation/
COPY openclaw-wargame-rl/pom.xml openclaw-wargame-rl/
COPY openclaw-wargame-autonomy/pom.xml openclaw-wargame-autonomy/
COPY openclaw-wargame-web/pom.xml openclaw-wargame-web/
COPY openclaw-wargame-demo/pom.xml openclaw-wargame-demo/
RUN mvn dependency:go-offline -B -q || true

# Build
COPY . .
RUN mvn clean package -DskipTests -B -q

# Stage 2: Runtime image
FROM eclipse-temurin:17-jre-alpine
LABEL maintainer="liugl951127@gmail.com"
LABEL org.opencontainers.image.source="https://github.com/liugl951127/aiWar"
LABEL org.opencontainers.image.description="AI-powered autonomous wargame simulation with real-time analysis"
LABEL org.opencontainers.image.licenses="Apache-2.0"

# 非 root 运行
RUN addgroup -S wargame && adduser -S wargame -G wargame
WORKDIR /app

# 复制 fat jar
COPY --from=builder /build/openclaw-wargame-demo/target/openclaw-wargame-demo-1.0.0.jar /app/wargame.jar
RUN chown -R wargame:wargame /app
USER wargame

EXPOSE 18080 18081

# 默认启动 Web 可视化
ENV WG_SEED=42
ENV WG_MAX_TICKS=300
ENV WG_PORT=18080

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost:18080/api/snapshot || exit 1

ENTRYPOINT ["java", "-jar", "/app/wargame.jar"]
CMD ["42", "300", "--web", "--port", "18080"]

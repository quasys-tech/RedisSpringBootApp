# ==============================================================================
# Aşama 1: Derleme (Builder Stage)
# Multi-stage build ile kaynak kodlar Maven ile derlenir ve Spring Boot katmanlarına ayrılır.
# ==============================================================================
FROM maven:3.9.9-eclipse-temurin-17-alpine AS builder

WORKDIR /workspace

# Bağımlılıkları önbelleğe almak (Docker layer cache) için önce pom.xml kopyalanır
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Kaynak kodları kopyala ve derle
COPY src ./src
RUN mvn clean package -DskipTests -B

# Spring Boot layered JAR'ı katmanlara ayır (Tools extract mekanizması)
RUN java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination /workspace/extracted

# ==============================================================================
# Aşama 2: Çalışma Zamanı (Runtime Stage)
# Hafif ve güvenli JRE imajı. OpenShift Security Context Constraints (SCC) uyumlu.
# ==============================================================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# OpenShift için Non-Root Kullanıcı ve Grup İzinleri
# OpenShift rastgele bir UID (örn. 1000670000) ile root grubunda (GID 0) pod çalıştırır.
# Bu nedenle sahiplik ve grup izinleri g=u olarak ayarlanır.
RUN addgroup -S appgroup -g 1001 && \
    adduser -S appuser -u 1001 -G appgroup && \
    chown -R 1001:0 /app && \
    chmod -R g=u /app

# Katmanları sırasıyla kopyala (En az değişenden en sık değişene doğru)
# 1. Dış kütüphaneler / Bağımlılıklar
COPY --from=builder --chown=1001:0 /workspace/extracted/dependencies/ ./
# 2. Spring Boot loader sınıfları
COPY --from=builder --chown=1001:0 /workspace/extracted/spring-boot-loader/ ./
# 3. Snapshot bağımlılıklar
COPY --from=builder --chown=1001:0 /workspace/extracted/snapshot-dependencies/ ./
# 4. Uygulama sınıfları ve konfigürasyon
COPY --from=builder --chown=1001:0 /workspace/extracted/application/ ./

# OpenShift uyumlu non-root kullanıcıya geçiş
USER 1001

# Uygulama dinleme portu
EXPOSE 8080

# JVM Container bellek ve optimizasyon parametreleri
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Spring Boot 3.3+ JarLauncher ile başlatma
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

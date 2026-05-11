# ── Stage 1 : Builder ─────────────────────────────────────────────────────────
# Tag patch exact — évite les régressions silencieuses sur rebuild
FROM maven:3.9.14-eclipse-temurin-25 AS builder
WORKDIR /build

# Télécharger les dépendances en cache (layer stable — invalide uniquement si pom.xml change)
COPY pom.xml .

# Patcher les CVE du builder (Ubuntu Jammy)
RUN apt-get update \
 && apt-get upgrade -y --no-install-recommends \
 && rm -rf /var/lib/apt/lists/*

RUN mvn dependency:go-offline -q

# Compiler et packager
COPY src ./src
RUN mvn package -DskipTests -q

# Extraire les layers Spring Boot pour le cache Docker
RUN java -Djarmode=layertools \
         -jar target/isp-zoho-notifier-*.jar extract \
         --destination extracted

# ── Stage 2 : Runtime — Golden Java 25 Runtime Base ────────────────────────────
FROM harbor.internal.korlu.com/korlu/java-runtime-base@sha256:d6386efab96304a018271c71261358d84f7099dfacea742a549dbf9e6239b6ae

LABEL org.opencontainers.image.title="isp-zoho-notifier"
LABEL org.opencontainers.image.description="isp-zoho-notifier — ISP Platform AMQP worker Zoho CRM"
LABEL org.opencontainers.image.vendor="Korlu ISP Platform"
LABEL org.opencontainers.image.licenses="Apache-2.0"
LABEL org.opencontainers.image.base.name="harbor.internal.korlu.com/korlu/java-runtime-base@sha256:d6386efab96304a018271c71261358d84f7099dfacea742a549dbf9e6239b6ae"

WORKDIR /app

# Copier les layers dans l'ordre (du plus stable au plus volatil)
COPY --from=builder --chown=65532:65532 /build/extracted/dependencies/          ./
COPY --from=builder --chown=65532:65532 /build/extracted/spring-boot-loader/    ./
COPY --from=builder --chown=65532:65532 /build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=65532:65532 /build/extracted/application/           ./

EXPOSE 8080
EXPOSE 8081

USER nonroot:nonroot

# ENTRYPOINT hérité de la base golden

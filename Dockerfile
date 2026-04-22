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

# ── Stage 2 : Runtime ─────────────────────────────────────────────────────────
# Tag patch exact — Ubuntu 22.04 LTS, dpkg metadata complète → Harbor SBOM OK
FROM eclipse-temurin:25.0.2_7-jre-jammy

# Métadonnées OCI (obligatoires pour Harbor SBOM + Argo CD)
LABEL org.opencontainers.image.title="isp-zoho-notifier"
LABEL org.opencontainers.image.description="isp-zoho-notifier — ISP Platform AMQP worker Zoho CRM"
LABEL org.opencontainers.image.vendor="Korlu ISP Platform"
LABEL org.opencontainers.image.licenses="Apache-2.0"
LABEL org.opencontainers.image.base.name="eclipse-temurin:25.0.2_7-jre-jammy"

# Patcher les CVE Ubuntu — apt laisse les metadata dpkg intactes
RUN apt-get update \
 && apt-get upgrade -y --no-install-recommends \
 && rm -rf /var/lib/apt/lists/*

# Utilisateur non-root (convention distroless UID 65532)
RUN groupadd -g 65532 nonroot \
 && useradd  -u 65532 -g nonroot -s /bin/false -M nonroot

WORKDIR /app

# Copier les layers dans l'ordre (du plus stable au plus volatil)
COPY --from=builder --chown=65532:65532 /build/extracted/dependencies/          ./
COPY --from=builder --chown=65532:65532 /build/extracted/spring-boot-loader/    ./
COPY --from=builder --chown=65532:65532 /build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=65532:65532 /build/extracted/application/           ./

EXPOSE 8080
EXPOSE 8081

USER nonroot:nonroot

# JVM exec form (pas sh -c) : SIGTERM géré directement par la JVM → shutdown propre K8s
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseZGC", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "org.springframework.boot.loader.launch.JarLauncher"]

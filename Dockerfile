# Obraz backendu Gradus iuris. Zawiera tylko środowisko uruchomieniowe: gotowy plik app.jar budujesz wcześniej
# (./mvnw clean package, patrz deploy/deploy-backend.sh) i kładziesz obok tego pliku.
# Lokalny test obrazu:
#   ./mvnw -q clean package && cp target/backend-*.jar app.jar && docker build -t gradusiuris-backend .
FROM eclipse-temurin:17-jre

# curl jest potrzebny do sprawdzania stanu (healthcheck) w docker compose.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --no-create-home --shell /usr/sbin/nologin app

WORKDIR /app
COPY app.jar /app/app.jar

# Nie działamy jako root.
USER app
EXPOSE 8080

# Pamięć: JVM bierze 70% limitu kontenera; przy braku pamięci proces kończy się (i kontener startuje od nowa), zamiast wisieć.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]

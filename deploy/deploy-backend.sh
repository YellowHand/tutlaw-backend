#!/usr/bin/env bash
# Wdrożenie backendu na serwer produkcyjny jednym poleceniem (uruchamiasz na swoim komputerze, w katalogu backendu).
# Windows: uruchamiaj w Git Bash albo WSL.
#
#   SSH_TARGET=ubuntu@api.gradusiuris.pl SSH_KEY=~/.ssh/gradusiuris.pem ./deploy/deploy-backend.sh
#
# Zmienne:
#   SSH_TARGET  (wymagane)  użytkownik@serwer, np. ubuntu@api.gradusiuris.pl
#   SSH_KEY     (opcjonalne) plik klucza prywatnego (.pem); bez niego używany jest domyślny klucz SSH
#   SKIP_TESTS  (opcjonalne) 1 = zbuduj bez uruchamiania testów (domyślnie testy jednostkowe się uruchamiają; pomijany jest
#               tylko BackendApplicationTests, który wymaga lokalnej bazy)
#
# Co robi: buduje JAR, wysyła go jako nowe „wydanie” w /opt/gradusiuris/releases/<data>, buduje obraz, uruchamia, czeka aż
# aplikacja będzie zdrowa. Jeśli nie będzie, wraca do poprzedniego wydania. Zostawia 5 ostatnich wydań (do szybkiego cofnięcia).
# Migracje bazy (Flyway) wykonują się same przy starcie aplikacji. Sekretów NIE wysyła: leżą tylko na serwerze (.env.stary.prod).
set -euo pipefail

: "${SSH_TARGET:?Ustaw SSH_TARGET, np. SSH_TARGET=ubuntu@api.gradusiuris.pl}"
SSH_OPTS=(-o StrictHostKeyChecking=accept-new)
[ -n "${SSH_KEY:-}" ] && SSH_OPTS+=(-i "$SSH_KEY")

cd "$(dirname "$0")/.."

echo "== 1/5 Budowanie"
MVN_ARGS=(-q clean package "-Dtest=!BackendApplicationTests" -Dsurefire.failIfNoSpecifiedTests=false)
[ "${SKIP_TESTS:-0}" = "1" ] && MVN_ARGS+=(-DskipTests)
./mvnw "${MVN_ARGS[@]}"
JAR=$(ls target/*.jar | grep -v '\.original$' | head -n 1)
[ -f "$JAR" ] || { echo "Nie znaleziono pliku JAR w target/" >&2; exit 1; }

RELEASE=$(date +%Y%m%d-%H%M%S)
REMOTE=/opt/gradusiuris
echo "== 2/5 Wysyłanie wydania $RELEASE ($JAR)"
ssh "${SSH_OPTS[@]}" "$SSH_TARGET" "mkdir -p $REMOTE/releases/$RELEASE"
scp "${SSH_OPTS[@]}" "$JAR" "$SSH_TARGET:$REMOTE/releases/$RELEASE/app.jar"
scp "${SSH_OPTS[@]}" Dockerfile "$SSH_TARGET:$REMOTE/releases/$RELEASE/Dockerfile"
scp "${SSH_OPTS[@]}" deploy/docker-compose.prod.yml "$SSH_TARGET:$REMOTE/docker-compose.yml"
scp "${SSH_OPTS[@]}" deploy/Caddyfile "$SSH_TARGET:$REMOTE/Caddyfile"

echo "== 3/5 Uruchamianie na serwerze"
ssh "${SSH_OPTS[@]}" "$SSH_TARGET" bash -s -- "$RELEASE" <<'REMOTE_SCRIPT'
set -euo pipefail
RELEASE="$1"
cd /opt/gradusiuris
[ -f .env.prod ] || { echo "Brak /opt/gradusiuris/.env.prod. Utwórz go najpierw (wzór: deploy/.env.prod.example)." >&2; exit 1; }

PREVIOUS=""
[ -L current ] && PREVIOUS=$(basename "$(readlink current)")

ln -sfn "releases/$RELEASE" current
# --pull: świeża podstawa obrazu (poprawki bezpieczeństwa Javy); caddy:2 też odświeżamy (błąd pobrania nie przerywa wdrożenia).
docker compose pull caddy || true
docker compose build --pull backend
docker compose up -d

echo "== 4/5 Czekam na zdrowy backend (do 3 minut)"
STATUS=none
for i in $(seq 1 36); do
  STATUS=$(docker inspect -f '{{.State.Health.Status}}' gradusiuris-backend 2>/dev/null || echo none)
  [ "$STATUS" = "healthy" ] && break
  sleep 5
done

if [ "$STATUS" != "healthy" ]; then
  echo "!! Backend nie jest zdrowy (stan: $STATUS). Ostatnie logi:" >&2
  docker logs --tail 60 gradusiuris-backend >&2 || true
  if [ -n "$PREVIOUS" ] && [ "$PREVIOUS" != "$RELEASE" ]; then
    echo "!! Wracam do poprzedniego wydania $PREVIOUS" >&2
    ln -sfn "releases/$PREVIOUS" current
    docker compose build backend
    docker compose up -d
    echo "!! Przywrócono $PREVIOUS. Uwaga: jeśli nowe wydanie zdążyło wykonać migrację bazy, zostaje ona w bazie (migracje są tylko dokładane)." >&2
  fi
  exit 1
fi

echo "== 5/5 Sprzątanie starych wydań (zostaje 5)"
ls -1dt releases/* | tail -n +6 | xargs -r rm -rf
docker image prune -f >/dev/null
echo "OK: wydanie $RELEASE działa."
REMOTE_SCRIPT

echo "Gotowe. Sprawdź: curl -fsS https://api.gradusiuris.pl/actuator/health"

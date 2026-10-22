#!/usr/bin/env bash
# Cofnięcie backendu do poprzedniego wydania (albo wskazanego). Uruchamiasz na swoim komputerze.
#   SSH_TARGET=ubuntu@ADRES_IP_SERWERA SSH_KEY=~/.ssh/gradusiuris.pem ./deploy/rollback.sh            (poprzednie wydanie)
#   SSH_TARGET=... ./deploy/rollback.sh 20261021-153000                                                 (konkretne wydanie)
# Lista wydań: ssh ... "ls -1t /opt/gradusiuris/releases"
# Uwaga: cofnięcie kodu NIE cofa migracji bazy. Dlatego migracje muszą być wstecznie zgodne (tylko dokładanie kolumn i tabel).
set -euo pipefail
: "${SSH_TARGET:?Ustaw SSH_TARGET na adres IP serwera, np. SSH_TARGET=ubuntu@ADRES_IP_SERWERA}"
SSH_OPTS=(-o StrictHostKeyChecking=accept-new)
[ -n "${SSH_KEY:-}" ] && SSH_OPTS+=(-i "$SSH_KEY")

ssh "${SSH_OPTS[@]}" "$SSH_TARGET" bash -s -- "${1:-}" <<'REMOTE_SCRIPT'
set -euo pipefail
cd /opt/gradusiuris
CURRENT=$(basename "$(readlink current)")
TARGET="${1:-}"
if [ -z "$TARGET" ]; then
  TARGET=$(ls -1t releases | grep -vx "$CURRENT" | head -n 1 || true)
fi
[ -n "$TARGET" ] && [ -d "releases/$TARGET" ] || { echo "Nie ma wydania do przywrócenia." >&2; ls -1t releases >&2; exit 1; }
echo "Wydanie aktualne: $CURRENT. Wracam do: $TARGET"
ln -sfn "releases/$TARGET" current
docker compose build backend
docker compose up -d
for i in $(seq 1 36); do
  [ "$(docker inspect -f '{{.State.Health.Status}}' gradusiuris-backend 2>/dev/null || echo none)" = "healthy" ] && { echo "OK: działa wydanie $TARGET."; exit 0; }
  sleep 5
done
echo "Backend nie jest zdrowy po cofnięciu. Logi:" >&2
docker logs --tail 60 gradusiuris-backend >&2
exit 1
REMOTE_SCRIPT

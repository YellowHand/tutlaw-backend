#!/usr/bin/env bash
# Jednorazowe przygotowanie serwera (Ubuntu 24.04 na EC2): Docker, katalog aplikacji, automatyczne poprawki bezpieczeństwa, swap.
# Uruchamiasz NA SERWERZE (po zalogowaniu przez SSH):
#   curl -fsSL <adres tego pliku> -o server-setup.sh   albo wklej plik ręcznie, potem:   bash server-setup.sh
set -euo pipefail

if [ "$(id -u)" -eq 0 ]; then
  echo "Uruchom jako zwykły użytkownik (ubuntu), nie jako root. Skrypt sam użyje sudo." >&2
  exit 1
fi

echo "== Aktualizacja systemu"
sudo apt-get update -y
sudo DEBIAN_FRONTEND=noninteractive apt-get upgrade -y

echo "== Automatyczne poprawki bezpieczeństwa"
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y unattended-upgrades

echo "== Docker (oficjalny skrypt instalacyjny Dockera)"
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sudo sh
fi
sudo usermod -aG docker "$USER"

echo "== Swap 1 GB (zapas pamięci dla małej maszyny)"
if ! swapon --show | grep -q /swapfile; then
  sudo fallocate -l 1G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
fi

echo "== Katalog aplikacji /opt/gradusiuris"
sudo mkdir -p /opt/gradusiuris/releases
sudo chown -R "$USER":"$USER" /opt/gradusiuris

echo
echo "Gotowe. WYLOGUJ SIĘ i zaloguj ponownie (żeby grupa docker zaczęła działać), a potem sprawdź:  docker ps"
echo "Następny krok: utwórz /opt/gradusiuris/.env.prod (wzór: deploy/.env.prod.example) i wdróż aplikację skryptem deploy-backend.sh."

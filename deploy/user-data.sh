#!/bin/bash
# Liso test server: first-boot script for an Ubuntu 24.04 EC2 instance in eu-west-1.
# Paste this whole file into EC2 "Advanced details > User data" when launching the instance.
#
# No AWS keys go in here: the instance gets video permissions from its IAM role (liso-server).
# It runs Postgres, the Liso API and Caddy (automatic HTTPS) with Docker, and serves the API at
#   https://<public-ip-with-dashes>.sslip.io     e.g. https://52-18-1-2.sslip.io
# Progress is logged to /var/log/liso-setup.log.
set -euo pipefail
exec > >(tee -a /var/log/liso-setup.log) 2>&1

IMAGE="ghcr.io/briankilembeka-alt/liso-api:main"
# Sign-in code for testers while there's no SMS gateway. Change it, and never use on a real server.
TEST_OTP_CODE="246810"

# Small instances (t3.micro, 1 GB) need swap to run Postgres, the API and Caddy together.
if [ ! -f /swapfile ]; then
  fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
  echo "/swapfile none swap sw 0 0" >> /etc/fstab
fi

apt-get update -y
apt-get install -y docker.io docker-compose-v2 curl openssl
systemctl enable --now docker

# Public address -> sslip.io name, so Caddy can get a real HTTPS certificate without a domain.
TOKEN=$(curl -sX PUT http://169.254.169.254/latest/api/token -H "X-aws-ec2-metadata-token-ttl-seconds: 300")
IP=$(curl -s -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/public-ipv4)
HOST="${IP//./-}.sslip.io"

mkdir -p /opt/liso && cd /opt/liso
if [ ! -f .env ]; then
  cat > .env <<ENV
POSTGRES_PASSWORD=$(openssl rand -hex 16)
TEST_OTP_CODE=$TEST_OTP_CODE
ENV
  chmod 600 .env
fi

cat > Caddyfile <<CADDY
$HOST {
  reverse_proxy 127.0.0.1:8080
}
CADDY

# Host networking so the API can reach the instance role's credentials (EC2 metadata service).
cat > compose.yml <<'COMPOSE'
services:
  postgres:
    image: postgres:16
    restart: unless-stopped
    network_mode: host
    command: ["postgres", "-c", "listen_addresses=127.0.0.1"]
    environment:
      POSTGRES_USER: liso
      POSTGRES_DB: liso
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes: ["pgdata:/var/lib/postgresql/data"]
  api:
    image: ghcr.io/briankilembeka-alt/liso-api:main
    restart: unless-stopped
    network_mode: host
    depends_on: [postgres]
    environment:
      DATABASE_URL: postgres://liso:${POSTGRES_PASSWORD}@127.0.0.1:5432/liso
      PORT: "8080"
      VIDEO_PROVIDER: ivs
      AWS_REGION: eu-west-1
      TEST_OTP_CODE: ${TEST_OTP_CODE}
  caddy:
    image: caddy:2
    restart: unless-stopped
    network_mode: host
    volumes: ["./Caddyfile:/etc/caddy/Caddyfile:ro", "caddy_data:/data"]
volumes:
  pgdata:
  caddy_data:
COMPOSE
sed -i "s|ghcr.io/briankilembeka-alt/liso-api:main|$IMAGE|" compose.yml

docker compose pull
# Postgres needs a few seconds before the API's first migration; retry the API until it's up.
docker compose up -d postgres caddy
sleep 10
docker compose up -d api

# Update to the newest API image every night at 03:00.
echo "0 3 * * * root cd /opt/liso && docker compose pull api && docker compose up -d api" > /etc/cron.d/liso-update

echo "Liso test server is starting at https://$HOST/health"

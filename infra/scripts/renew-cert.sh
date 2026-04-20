#!/bin/bash
# Let's Encrypt 인증서 갱신 (cron: 0 3 * * * /opt/agolive/infra/scripts/renew-cert.sh)
set -e

APP_DIR="/opt/agolive"

docker run --rm \
  -v "${APP_DIR}/certbot/conf:/etc/letsencrypt" \
  -v "${APP_DIR}/certbot/www:/var/www/certbot" \
  certbot/certbot renew \
  --webroot -w /var/www/certbot \
  --quiet

docker exec agolive-nginx nginx -s reload
echo "$(date): 인증서 갱신 완료" >> /var/log/certbot-renew.log

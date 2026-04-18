#!/bin/bash
# SSL 인증서 최초 발급 + nginx 초기화 스크립트 (최초 1회 실행)
# 전제: .env에 DOMAIN, GRAFANA_ADMIN_USER/PASSWORD 가 설정되어 있어야 함
set -e

APP_DIR="/opt/agolive"
CONF_D="${APP_DIR}/infra/docker/nginx/conf.d"
CERTBOT_CONF="${APP_DIR}/certbot/conf"
CERTBOT_WWW="${APP_DIR}/certbot/www"
COMPOSE="docker compose --env-file ${APP_DIR}/.env -f ${APP_DIR}/infra/docker/docker-compose.prod.yml"

# .env에서 DOMAIN, EMAIL 로드
set -a; source "${APP_DIR}/.env"; set +a

if [ -z "$DOMAIN" ] || [ -z "$CERTBOT_EMAIL" ]; then
  echo "ERROR: .env에 DOMAIN과 CERTBOT_EMAIL 이 설정되어야 합니다."
  exit 1
fi

echo "=== 1. ACME 챌린지용 HTTP nginx 설정 ==="
mkdir -p "${CONF_D}"
cat > "${CONF_D}/default.conf" <<EOF
server {
    listen 80;
    server_name ${DOMAIN};

    location ^~ /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    location / {
        return 200 "HTTP_OK\n";
        add_header Content-Type text/plain;
    }
}
EOF

echo "=== 2. nginx 기동 ==="
$COMPOSE up -d nginx
sleep 3

echo "=== 3. Let's Encrypt 인증서 발급 ==="
docker run --rm \
  -v "${CERTBOT_CONF}:/etc/letsencrypt" \
  -v "${CERTBOT_WWW}:/var/www/certbot" \
  certbot/certbot certonly \
  --webroot -w /var/www/certbot \
  -d "${DOMAIN}" \
  --email "${CERTBOT_EMAIL}" \
  --agree-tos --no-eff-email

echo "=== 4. HTTPS nginx 설정으로 교체 ==="
cat > "${CONF_D}/00-api-upstream.conf" <<EOF
upstream api_active {
    server agolive-api-blue:8080;
}
EOF

cat > "${CONF_D}/00-realtime-upstream.conf" <<EOF
upstream realtime_active {
    server agolive-realtime-blue:8081;
}
EOF

cat > "${CONF_D}/default.conf" <<EOF
server {
    listen 80;
    server_name ${DOMAIN};

    location ^~ /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }
    location / {
        return 301 https://\$host\$request_uri;
    }
}

server {
    listen 443 ssl;
    http2 on;
    server_name ${DOMAIN};

    ssl_certificate     /etc/letsencrypt/live/${DOMAIN}/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/${DOMAIN}/privkey.pem;
    ssl_protocols       TLSv1.2 TLSv1.3;
    ssl_ciphers         HIGH:!aNULL:!MD5;
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;

    # 내부 엔드포인트 외부 차단
    location /internal/  { return 403; }
    location /actuator/  { return 403; }

    # Grafana
    location /grafana/ {
        proxy_pass http://agolive-grafana:3000;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }

    # WebSocket (Go Realtime)
    location /ws/ {
        proxy_pass http://realtime_active;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection \$connection_upgrade;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }

    # REST API (Spring Boot)
    location /api/ {
        proxy_pass http://api_active;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }

    # OAuth2 콜백 (Spring Boot)
    location /login/ {
        proxy_pass http://api_active;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }

    # Frontend (Next.js)
    location / {
        proxy_pass http://agolive-frontend:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection \$connection_upgrade;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
}
EOF

echo "=== 5. nginx 설정 반영 ==="
docker exec agolive-nginx nginx -t
docker exec agolive-nginx nginx -s reload

echo "=== 6. Blue/Green 초기 상태 파일 생성 ==="
echo "api-blue"      > "${APP_DIR}/.active-api"
echo "realtime-blue" > "${APP_DIR}/.active-realtime"

echo "=== 7. ECR 로그인 및 초기 컨테이너 기동 ==="
set -a; source "${APP_DIR}/.env"; set +a
aws ecr get-login-password --region "${AWS_REGION}" \
  | docker login --username AWS --password-stdin "${ECR_REGISTRY}"

$COMPOSE pull api-blue realtime-blue frontend
$COMPOSE up -d api-blue realtime-blue frontend

echo ""
echo "=== SSL 초기화 완료 ==="
echo "  https://${DOMAIN} 으로 접속 확인하세요."
echo "  Grafana: https://${DOMAIN}/grafana"

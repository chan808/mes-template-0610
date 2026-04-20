#!/bin/bash
# Blue/Green 배포 스크립트
# 사용법: deploy.sh <api|realtime|frontend> <image-tag>
set -e

APP_DIR="/opt/agolive"
COMPOSE="docker compose --env-file ${APP_DIR}/.env -f ${APP_DIR}/infra/docker/docker-compose.prod.yml"
CONF_D="${APP_DIR}/infra/docker/nginx/conf.d"
NGINX="agolive-nginx"

SERVICE="${1:?사용법: deploy.sh <api|realtime|frontend> <image-tag>}"
TAG="${2:?사용법: deploy.sh <api|realtime|frontend> <image-tag>}"

# .env 로드
set -a; source "${APP_DIR}/.env"; set +a

ecr_login() {
    aws ecr get-login-password --region "${AWS_REGION}" \
        | docker login --username AWS --password-stdin "${ECR_REGISTRY}"
}

# 컨테이너 헬스체크 (nginx 컨테이너에서 wget으로 수행)
wait_healthy() {
    local container="$1" port="$2" path="$3"
    echo "헬스체크 대기: ${container}..."
    for i in $(seq 1 30); do
        if docker exec "${NGINX}" wget -qO- "http://${container}:${port}${path}" >/dev/null 2>&1; then
            echo "${container} 헬스체크 통과"
            return 0
        fi
        sleep 3
    done
    echo "ERROR: ${container} 헬스체크 실패 (90초 초과)"
    return 1
}

# nginx upstream 파일 교체 후 reload
swap_upstream() {
    local svc="$1" inactive="$2" port="$3"
    cat > "${CONF_D}/00-${svc}-upstream.conf" <<EOF
upstream ${svc}_active {
    server ${inactive}:${port};
}
EOF
    docker exec "${NGINX}" nginx -t
    docker exec "${NGINX}" nginx -s reload
    echo "nginx upstream → ${inactive}"
}

# Blue/Green 배포
deploy_blue_green() {
    local svc="$1" tag="$2" port health_path
    case "$svc" in
        api)      port=8080; health_path="/actuator/health" ;;
        realtime) port=8081; health_path="/health" ;;
    esac

    local state_file="${APP_DIR}/.active-${svc}"
    local active; active=$(cat "${state_file}" 2>/dev/null || echo "${svc}-blue")

    if [ "${active}" = "${svc}-blue" ]; then
        inactive="${svc}-green"
        tag_key="$(echo ${svc} | tr '[:lower:]' '[:upper:]')_GREEN_TAG"
    else
        inactive="${svc}-blue"
        tag_key="$(echo ${svc} | tr '[:lower:]' '[:upper:]')_BLUE_TAG"
    fi

    echo "배포 시작: ${active} → ${inactive} (tag=${tag})"

    # .env에서 이미지 태그 업데이트
    sed -i "s|^${tag_key}=.*|${tag_key}=${tag}|" "${APP_DIR}/.env"
    set -a; source "${APP_DIR}/.env"; set +a

    # 새 이미지 pull
    $COMPOSE pull "${inactive}"

    # inactive 슬롯 기동
    $COMPOSE up -d "${inactive}"

    # 헬스체크 통과 확인
    wait_healthy "${inactive}" "${port}" "${health_path}"

    # nginx upstream 교체
    swap_upstream "${svc}" "${inactive}" "${port}"

    # 이전 슬롯 중지
    $COMPOSE stop "${active}"
    echo "중지: ${active}"

    # 상태 파일 갱신
    echo "${inactive}" > "${state_file}"
    echo "배포 완료: ${active} → ${inactive}"
}

# Frontend 단순 재배포 (B/G 불필요)
deploy_frontend() {
    local tag="$1"
    echo "Frontend 배포: tag=${tag}"

    sed -i "s|^FRONTEND_IMAGE_TAG=.*|FRONTEND_IMAGE_TAG=${tag}|" "${APP_DIR}/.env"
    set -a; source "${APP_DIR}/.env"; set +a

    $COMPOSE pull frontend
    $COMPOSE up -d --no-deps frontend
    echo "Frontend 배포 완료"
}

# 실행
ecr_login

case "$SERVICE" in
    api|realtime) deploy_blue_green "$SERVICE" "$TAG" ;;
    frontend)     deploy_frontend "$TAG" ;;
    *)
        echo "ERROR: 알 수 없는 서비스: ${SERVICE}"
        exit 1
        ;;
esac

echo ""
docker ps --format "table {{.Names}}\t{{.Status}}"

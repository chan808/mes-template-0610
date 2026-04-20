#!/bin/bash
# EC2 초기 환경 세팅 스크립트 (최초 1회 실행)
set -e

echo "=== 1. 시스템 업데이트 ==="
sudo apt update && sudo apt -y upgrade
sudo apt -y install ca-certificates curl gnupg lsb-release unzip jq vim
sudo timedatectl set-timezone Asia/Seoul

echo "=== 2. 스왑 설정 (2GB) ==="
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

echo "=== 3. Docker 설치 ==="
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt update
sudo apt -y install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker ubuntu

echo "=== 4. Docker 로그 로테이션 ==="
sudo mkdir -p /etc/docker
cat <<'EOF' | sudo tee /etc/docker/daemon.json
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "10m", "max-file": "5" }
}
EOF
sudo systemctl restart docker

echo "=== 5. AWS CLI 설치 ==="
curl -s "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "/tmp/awscliv2.zip"
unzip -q /tmp/awscliv2.zip -d /tmp
sudo /tmp/aws/install
rm -rf /tmp/aws /tmp/awscliv2.zip

echo "=== 6. 앱 디렉토리 생성 ==="
sudo mkdir -p /opt/agolive/{certbot/conf,certbot/www}
sudo chown -R ubuntu:ubuntu /opt/agolive

echo "=== 7. 레포 클론 ==="
# Deploy Key 등록 후 실행
# git clone git@github.com:chan808/agolive.git /opt/agolive
echo "INFO: 'git clone git@github.com:chan808/agolive.git /opt/agolive' 를 직접 실행하세요."

echo ""
echo "=== 완료 ==="
echo "다음 단계:"
echo "  1. git clone 실행"
echo "  2. /opt/agolive/.env 작성 (.env.example 참고)"
echo "  3. infra/scripts/init-ssl.sh 실행"

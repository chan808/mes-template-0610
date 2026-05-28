#!/bin/bash
# EC2 자동 부트스트랩 (Terraform user_data로 실행, 재실행 불필요)
set -euo pipefail
exec > >(tee /var/log/user-data.log | logger -t user-data) 2>&1

AWS_REGION="${aws_region}"
REPO="git@github.com:${github_org}/${github_repo}.git"
APP_DIR=/opt/agolive

echo "=== 1. 시스템 업데이트 ==="
export DEBIAN_FRONTEND=noninteractive
apt-get update -y && apt-get upgrade -y
apt-get install -y ca-certificates curl gnupg lsb-release unzip jq vim
timedatectl set-timezone Asia/Seoul

echo "=== 2. 스왑 (2GB) ==="
fallocate -l 2G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab

echo "=== 3. Docker ==="
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg
ARCH=$(dpkg --print-architecture)
CODENAME=$(. /etc/os-release && echo "$VERSION_CODENAME")
echo "deb [arch=$ARCH signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu $CODENAME stable" \
  > /etc/apt/sources.list.d/docker.list
apt-get update -y
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
usermod -aG docker ubuntu

mkdir -p /etc/docker
cat > /etc/docker/daemon.json <<'JSON'
{"log-driver":"json-file","log-opts":{"max-size":"10m","max-file":"5"}}
JSON
systemctl restart docker

echo "=== 4. AWS CLI ==="
curl -s "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o /tmp/awscliv2.zip
unzip -q /tmp/awscliv2.zip -d /tmp
/tmp/aws/install
rm -rf /tmp/aws /tmp/awscliv2.zip

echo "=== 5. GitHub Deploy Key (SSM /agolive/github-deploy-key) ==="
mkdir -p /home/ubuntu/.ssh
chmod 700 /home/ubuntu/.ssh
aws ssm get-parameter \
  --name /agolive/github-deploy-key \
  --with-decryption \
  --query Parameter.Value \
  --output text \
  --region $AWS_REGION > /home/ubuntu/.ssh/agolive_deploy_key
chmod 600 /home/ubuntu/.ssh/agolive_deploy_key
cat > /home/ubuntu/.ssh/config <<'SSH_CFG'
Host github.com
  IdentityFile ~/.ssh/agolive_deploy_key
  StrictHostKeyChecking no
SSH_CFG
chmod 600 /home/ubuntu/.ssh/config
chown -R ubuntu:ubuntu /home/ubuntu/.ssh

echo "=== 6. 레포 클론 ==="
sudo -u ubuntu git clone "$REPO" $APP_DIR
chmod +x $APP_DIR/infra/scripts/*.sh

echo "=== 7. certbot 디렉토리 ==="
mkdir -p $APP_DIR/{certbot/conf,certbot/www}
chown -R ubuntu:ubuntu $APP_DIR

echo "=== 8. SSL 인증서 자동 갱신 cron (매일 새벽 3시) ==="
(crontab -u ubuntu -l 2>/dev/null; echo "0 3 * * * /opt/agolive/infra/scripts/renew-cert.sh >> /var/log/certbot-renew.log 2>&1") | crontab -u ubuntu -

echo "=== 9. .env (SSM /agolive/.env) ==="
aws ssm get-parameter \
  --name /agolive/.env \
  --with-decryption \
  --query Parameter.Value \
  --output text \
  --region $AWS_REGION > $APP_DIR/.env
chmod 600 $APP_DIR/.env
chown ubuntu:ubuntu $APP_DIR/.env

echo "=== 완료 ==="
echo "다음: DNS 설정(EIP → 도메인) 후 infra/scripts/init-ssl.sh 실행"

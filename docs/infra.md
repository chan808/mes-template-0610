# 인프라 런북

## 인프라 구조

```
GitHub Actions
  └── OIDC → AWS IAM Role
        ├── ECR push (이미지 빌드)
        └── SSH → EC2 → deploy.sh (Blue/Green 전환)

EC2 (t3.small, Ubuntu 24.04)
  └── Docker Compose (docker-compose.prod.yml)
        ├── nginx          (80/443, Blue/Green upstream 전환)
        ├── agolive-api-blue / agolive-api-green
        ├── agolive-realtime-blue / agolive-realtime-green
        ├── agolive-frontend
        ├── postgres       (PostgreSQL 16, EC2 내부)
        ├── redis
        ├── prometheus
        ├── node-exporter
        └── grafana

ECR (agolive-api / agolive-realtime / agolive-frontend)
EIP (고정 공인 IP)
```

---

## 최초 배포 (처음 한 번만)

### 1. 로컬 환경 준비

```bash
# AWS CLI 설치 및 인증 설정
aws configure
# AWS Access Key ID, Secret Access Key, Region(ap-northeast-2) 입력

# EC2 SSH 키 생성
ssh-keygen -t ed25519 -f ~/.ssh/agolive_ec2
```

### 2. Terraform으로 AWS 리소스 생성

```bash
cd infra/terraform

# 변수 파일 작성
cp terraform.tfvars.example terraform.tfvars
# terraform.tfvars 편집:
#   allowed_ssh_cidr    = "내_공인IP/32"  (https://ifconfig.me 확인)
#   ec2_public_key_path = "~/.ssh/agolive_ec2.pub"
#   github_org          = "chan808"
#   github_repo         = "agolive"

terraform init
terraform plan   # 생성될 리소스 확인
terraform apply
```

출력값 기록:

```
ec2_public_ip  → EC2_HOST (GitHub Secret, DNS A 레코드)
ecr_registry   → ECR_REGISTRY (EC2 .env에 사용)
gha_role_arn   → AWS_ROLE_ARN (GitHub Secret)
```

### 3. GitHub Secrets / Variables 등록

GitHub 레포 → Settings → Secrets and variables → Actions

**Secrets:**

| 키               | 값                                  |
| ---------------- | ----------------------------------- |
| `AWS_ROLE_ARN`   | terraform output `gha_role_arn`     |
| `AWS_ACCOUNT_ID` | AWS 계정 ID (12자리 숫자)           |
| `EC2_HOST`       | terraform output `ec2_public_ip`    |
| `EC2_SSH_KEY`    | `~/.ssh/agolive_ec2` 파일 전체 내용 |

**Variables:**
| 키 | 값 |
|---|---|
| `AWS_REGION` | `ap-northeast-2` |

### 4. EC2 초기 환경 세팅

```bash
# EC2 접속
ssh -i ~/.ssh/agolive_ec2 ubuntu@<EC2_HOST>

# 초기 세팅 (Docker, AWS CLI, 디렉토리 생성)
curl -fsSL https://raw.githubusercontent.com/chan808/agolive/main/infra/scripts/setup-ec2.sh | bash

# 재접속 (docker 그룹 반영 필수)
exit && ssh -i ~/.ssh/agolive_ec2 ubuntu@<EC2_HOST>

# 레포 클론
git clone https://github.com/chan808/agolive.git /opt/agolive
```

### 5. EC2 .env 작성

```bash
cp /opt/agolive/.env.example /opt/agolive/.env
nano /opt/agolive/.env
```

필수 입력값:

```
POSTGRES_DB=agolive
POSTGRES_USER=agolive
POSTGRES_PASSWORD=<강한_비밀번호>
REDIS_PASSWORD=<강한_비밀번호>
JWT_SECRET=<32자이상_랜덤>
INTERNAL_SECRET=<랜덤>
ECR_REGISTRY=<terraform output ecr_registry>
AWS_REGION=ap-northeast-2
DOMAIN=yourdomain.com
CERTBOT_EMAIL=your@email.com
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=<비밀번호>
```

### 6. DNS 설정

도메인 업체 콘솔에서 A 레코드 등록:

```
yourdomain.com → <terraform output ec2_public_ip>
```

DNS 전파 확인 (수 분~수십 분 소요):

```bash
nslookup yourdomain.com
```

### 7. SSL 인증서 발급 및 서비스 최초 기동

```bash
/opt/agolive/infra/scripts/init-ssl.sh
```

이 스크립트가 수행하는 것:

1. Let's Encrypt 인증서 발급
2. nginx conf.d 파일 생성 (HTTPS 라우팅, upstream 설정)
3. Blue/Green 초기 상태 파일 생성 (`.active-api`, `.active-realtime`)
4. ECR에서 초기 이미지 pull 및 컨테이너 기동

### 8. 인증서 자동 갱신 크론 등록

```bash
(crontab -l 2>/dev/null; echo "0 3 * * * /opt/agolive/infra/scripts/renew-cert.sh") | crontab -
```

---

## 일반 배포

`main` 브랜치에 push하면 GitHub Actions가 자동 실행.
변경된 서비스에 해당하는 workflow만 트리거:

| 변경 경로     | 실행 workflow         |
| ------------- | --------------------- |
| `backend/**`  | `deploy-api.yml`      |
| `realtime/**` | `deploy-realtime.yml` |
| `frontend/**` | `deploy-frontend.yml` |

### Blue/Green 전환 흐름 (api / realtime)

```
현재: api-blue 서비스 중
  1. api-green 이미지 pull
  2. api-green 컨테이너 기동
  3. 헬스체크 통과 확인 (최대 90초)
  4. nginx upstream → api-green 전환 (nginx reload, 무중단)
  5. api-blue 컨테이너 중지
완료: api-green 서비스 중
```

---

## 롤백

### 직전 이미지 태그로 롤백

```bash
ssh -i ~/.ssh/agolive_ec2 ubuntu@<EC2_HOST>

# ECR에서 이전 태그 확인
aws ecr list-images --repository-name agolive-api --region ap-northeast-2

# 해당 태그로 배포 (deploy.sh가 Blue/Green 전환 포함)
/opt/agolive/infra/scripts/deploy.sh api <rollback_tag>
```

---

## 장애 대응

### 서비스 상태 확인

```bash
ssh -i ~/.ssh/agolive_ec2 ubuntu@<EC2_HOST>

# 전체 컨테이너 상태
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# 특정 서비스 로그
docker logs agolive-api-blue --tail 100 -f
docker logs agolive-nginx --tail 50
```

### nginx 설정 오류 시

```bash
docker exec agolive-nginx nginx -t         # 설정 검증
docker exec agolive-nginx nginx -s reload  # 리로드
docker restart agolive-nginx               # 재시작 (최후 수단)
```

### DB 연결 불가 시

```bash
# postgres 컨테이너 상태 확인
docker logs agolive-postgres --tail 50

# postgres 직접 접속
docker exec -it agolive-postgres psql -U agolive -d agolive
```

### 전체 서비스 재시작

```bash
cd /opt/agolive
docker compose --env-file .env -f infra/docker/docker-compose.prod.yml up -d
```

---

## 모니터링

Grafana: `https://<DOMAIN>/grafana`

- 계정: `.env`의 `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD`
- 대시보드: Backend Observability (Spring Boot 메트릭)

---

## 인프라 변경 (Terraform)

```bash
cd infra/terraform
terraform plan    # 변경사항 확인
terraform apply   # 적용
```

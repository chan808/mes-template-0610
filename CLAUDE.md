# Agolive

공간 기반 협업 플랫폼. 사용자가 가상 공간에 아바타로 입장해 실시간 위치를 공유하고 상호작용한다.

## Stack

* **Backend API**: Kotlin / Spring Boot 4 / Spring Modulith / Spring Data JPA / PostgreSQL / Redis
* **Realtime**: Go 1.26 / coder/websocket / Redis Pub/Sub
* **Frontend**: Next.js 16 / React 19 / TypeScript / TanStack Query / Zustand / Tailwind CSS / PixiJS (공간 렌더링)
* **Infra / DevOps**: Docker Compose / GitHub Actions / Nginx / Prometheus + Grafana → EKS (추후)
* **AI Agent**: Python 3.12 / FastAPI / Anthropic SDK (Claude Haiku) / aioboto3

## Services

* `agolive-api` (port 8080): 인증, 방 관리, 채팅 기록, 내부 토큰 검증
* `agolive-realtime` (port 8081): WebSocket 연결, 위치 동기화, 브로드캐스트
* `agolive-agent` (port 8082): AI 에이전트 세션 관리, Claude API SSE 스트리밍

## Docs

* `docs/architecture.md`: 스키마, API, 구현 방식, 비즈니스 룰
* `docs/prd.md`: 서비스 목적, 기능, UX
* `docs/adr/`: 기술 결정 기록

## Commands

```bash
# 인프라 (PostgreSQL, Redis)
docker compose up -d postgres redis

# 전체 서비스
docker compose up -d

# Backend API
cd backend && ./gradlew bootRun

# Realtime
cd realtime && go run .

# AI Agent
cd agolive-agent && uvicorn main:app --reload --port 8082

# Frontend (패키지 매니저: pnpm — package.json packageManager 필드 기준)
cd frontend && pnpm dev    # http://localhost:3000
cd frontend && pnpm lint
cd frontend && pnpm exec tsc --noEmit    # 타입 체크
cd frontend && pnpm test    # 단위 테스트 (vitest)
```

## Principles

* 결정이 필요하면: `docs/architecture.md` 확인 → 과하지 않게 성능과 확장성을 고려한 해결책 → 이유 설명
* 기능 설계 시 `docs/prd.md` 참고
* 새 기술/라이브러리 도입은 필요한 경우에만, 먼저 이유를 설명할 것
* 프로젝트 구조·스택·API·에러 코드 변경 시 CLAUDE.md와 docs/architecture.md도 함께 업데이트할 것

## Comment Style

* 한 줄 주석만 사용, 한국어로 작성
* 형식: `// 동사+목적어` (예: `// 회원의 모든 리프레시 토큰 세션 무효화`, `// 이메일 기준으로 한 계정에 집중되는 분산 IP 공격 탐지용`)
* 코드만으로 의도가 불명확하거나 추가 설명이 필요할 때 작성

## Testing

* CRITICAL: 새 기능 구현 시 테스트를 먼저 작성하고, 테스트가 통과하는 구현을 작성한다 (TDD)
* 핵심 비즈니스 로직은 반드시 테스트한다 (인증, 권한, 상태 변경, 주요 정책)
* 내부 구현이 아닌 입력과 결과 중심으로 검증한다
* 과도한 mock 사용은 지양한다
* 핵심 시나리오(성공/실패)만 테스트하고, 불필요한 케이스는 작성하지 않는다
* 변경에 강하도록 단순하게 유지한다

**Spring (Kotlin)**
* UseCase / Service: JUnit5 + MockK로 단위 테스트. Repository는 mock 처리
* Repository: Testcontainers로 실제 PostgreSQL/Redis를 띄워 통합 테스트
* Controller: 핵심 API에 한해 최소한의 통합 테스트만 작성
* 테스트 메서드명: `fun 상황_행동_기대결과()` 형식
* given/when/then 블록으로 구조화

**Go**
* `testing` 표준 패키지 + testify/assert 사용
* JWT 파싱, 이벤트 핸들러 로직 단위 테스트
* 테스트 함수명: `Test_상황_기대결과` 형식

**Frontend (TypeScript)**
* vitest 사용. 렌더링이 아닌 순수 로직(충돌맵, 이동 큐, 카메라 등 `lib/`)만 단위 테스트
* 테스트 파일 위치: `src/**/__tests__/*.test.ts`
* 테스트명: `it("상황_행동_기대결과")` 형식

## Git

* 커밋 메시지는 conventional commits 형식을 따른다 (`feat:`, `fix:`, `refactor:`, `docs:`, `infra:`, `chore:` 등)

## Logging

**Spring (SLF4J)**
* INFO: UseCase 진입점의 비즈니스 이벤트 (방 생성, 입장 등)
* WARN: 비즈니스 예외 (ROOM_FULL 등 예상된 실패)
* ERROR: 시스템 예외 (DB, 외부 시스템 오류)
* DEBUG: 개발 중 문제 분석용, 기본적으로 비활성화 전제
* 요청 시작 / 주요 상태 변경 / 예외 발생 시에만 로그를 남긴다
* 개인정보(이메일, 비밀번호, 토큰 값)는 로그에 포함하지 않음

**Go (slog)**
* 구조화 로그 JSON 형식으로 출력
* 동일한 레벨 기준 적용 (INFO / WARN / ERROR / DEBUG)

## Deployment

### 인프라 구조

```
GitHub Actions (OIDC)
  ├─ build-push: Docker 빌드 → ECR 푸시
  └─ deploy: SSM Run Command → EC2 deploy.sh

EC2 (t3.small, ap-northeast-2)
  └─ Docker Compose (docker-compose.prod.yml)
       ├─ postgres, redis
       ├─ api-blue / api-green          (Blue/Green)
       ├─ realtime-blue / realtime-green (Blue/Green)
       ├─ agent                          (stateless, B/G 불필요)
       ├─ frontend
       ├─ prometheus, grafana, node-exporter
       └─ nginx (80/443 외부, 8090 내부 전용)
```

> realtime → api 내부 호출은 nginx:8090 경유. deploy.sh가 api upstream을 바꾸면 realtime은 자동으로 새 슬롯을 바라본다.

### 최초 배포 순서

**1. SSM Parameter Store 사전 등록** (AWS 콘솔 또는 CLI, 타입: SecureString)

| 파라미터 경로 | 내용 |
|---|---|
| `/agolive/github-deploy-key` | GitHub Deploy Key 비밀키 (PEM 전체) |
| `/agolive/.env` | `.env.example` 기반 완성된 `.env` 파일 전체 내용 |

```bash
# CLI 예시
aws ssm put-parameter --name /agolive/github-deploy-key \
  --value "$(cat ~/.ssh/agolive_deploy_key)" \
  --type SecureString --region ap-northeast-2

aws ssm put-parameter --name /agolive/.env \
  --value "$(cat .env)" \
  --type SecureString --region ap-northeast-2
```

**2. Terraform 실행**

```bash
cd infra/terraform
terraform init
terraform apply   # EC2 생성 → user_data로 부트스트랩 자동 실행
```

부트스트랩 완료 후 EC2는 repo 클론 + .env 적용 상태. 로그: `/var/log/user-data.log`

**3. GitHub 시크릿 / 변수 등록**

`terraform output` 결과를 GitHub에 등록:

| 종류 | 이름 | 값 |
|---|---|---|
| Secret | `AWS_ROLE_ARN` | `terraform output gha_role_arn` |
| Secret | `AWS_ACCOUNT_ID` | AWS 계정 ID |
| Variable | `AWS_REGION` | `ap-northeast-2` |
| Variable | `EC2_INSTANCE_ID` | `terraform output ec2_instance_id` |
| Variable | `DOMAIN` | 서비스 도메인 (예: agolive.example.com) |

> `EC2_HOST`, `EC2_SSH_KEY`는 더 이상 사용하지 않음. 삭제 권장.

**4. DNS 설정**

EIP(`terraform output ec2_public_ip`) → 도메인 A 레코드 등록. 전파 확인 후 다음 진행.

**5. SSL 인증서 발급 (최초 1회)**

```bash
ssh ubuntu@<EC2_IP>
cd /opt/agolive
infra/scripts/init-ssl.sh
```

**6. 첫 배포 트리거**

GitHub에서 `main` 브랜치에 push하거나 workflow_dispatch로 각 서비스 배포.

### 일상적인 배포

`main` 브랜치에 push → 변경된 서비스(backend/realtime/frontend/agolive-agent)에 해당하는 workflow 자동 트리거.

배포 흐름: test → ECR push → SSM Run Command → deploy.sh (Blue/Green swap)

### EC2 재생성 (처음부터 다시)

```bash
cd infra/terraform
terraform destroy   # EC2, EIP 삭제 (ECR은 유지됨)
terraform apply     # 재생성, user_data 자동 실행
```

SSM 파라미터는 보존되므로 재등록 불필요.

### 배포 로그 확인

```bash
# SSM 명령 결과 (GitHub Actions 콘솔에서도 확인 가능)
aws ssm get-command-invocation \
  --command-id <COMMAND_ID> \
  --instance-id <EC2_INSTANCE_ID> \
  --query "StandardOutputContent" --output text

# EC2 컨테이너 상태
ssh ubuntu@<EC2_IP> 'docker ps --format "table {{.Names}}\t{{.Status}}"'
```

# Agolive

공간 기반 실시간 협업 플랫폼. 사용자가 가상 공간에 아바타로 입장해 위치를 공유하고 채팅한다.

## 서비스 구조

```
Browser ──► Nginx ──► agolive-frontend (Next.js,              :3000)
                  ├──► agolive-api      (Kotlin / Spring Boot, :8080)
                  └──► agolive-realtime (Go,                   :8081)

agolive-api      ──► PostgreSQL (RDS)
agolive-api      ──► Redis
agolive-realtime ──► Redis (Pub/Sub)
```

## 로컬 개발

### 사전 준비
- Docker Desktop
- `.env` 작성 (`.env.example` 복사 후 수정)

### 인프라만 띄우기 (IDE에서 앱 직접 실행 시)
```bash
docker compose -f docker-compose.infra.yml up -d

# 모니터링(Prometheus + Grafana)까지 포함
docker compose -f docker-compose.infra.yml --profile observability up -d
```

### 전체 스택 한 번에 실행
```bash
docker compose up -d
```

## 배포

`main` 브랜치에 push하면 GitHub Actions가 자동으로:
1. 테스트 실행
2. Docker 이미지 빌드 → ECR push
3. EC2에 SSH 접속 → Blue/Green 배포 실행

수동 배포:
```bash
/opt/agolive/infra/scripts/deploy.sh api <image-tag>
/opt/agolive/infra/scripts/deploy.sh realtime <image-tag>
/opt/agolive/infra/scripts/deploy.sh frontend <image-tag>
```

## 기술 스택

| 분류 | 기술 |
|---|---|
| Backend API | Kotlin / Spring Boot 4 / Spring Data JPA / QueryDSL |
| Realtime | Go 1.26 / coder/websocket / Redis Pub/Sub |
| Frontend | Next.js 16 / React 19 / TypeScript / TanStack Query / Zustand |
| DB | PostgreSQL 16 (RDS) / Redis 7 |
| Infra | AWS EC2 + RDS + ECR / Terraform / GitHub Actions |
| Proxy | Nginx (Blue/Green upstream 전환) |
| 인증 | JWT / Spring Security |

## 문서

- [`docs/architecture.md`](docs/architecture.md) — 스키마, API, 비즈니스 룰
- [`docs/prd.md`](docs/prd.md) — 서비스 목적, 기능, UX
- [`docs/infra.md`](docs/infra.md) — 인프라 구조, 배포/롤백/장애 대응 런북
- [`docs/adr/`](docs/adr/) — 기술 결정 기록

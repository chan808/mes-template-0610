# Agolive

공간 기반 협업 플랫폼. 사용자가 가상 공간에 아바타로 입장해 실시간 위치를 공유하고 상호작용한다.

## Stack

* **Backend API**: Kotlin / Spring Boot 4 / Spring Modulith / Spring Data JPA / QueryDSL / PostgreSQL / Redis
* **Realtime**: Go 1.26 / coder/websocket / Redis Pub/Sub
* **Frontend**: Next.js 16 / React 19 / TypeScript / TanStack Query / Zustand / Tailwind CSS
* **Infra / DevOps**: Docker Compose (1차) → EKS (2차) / GitHub Actions / Nginx
* **AI Agent (Phase 2+)**: Python / FastAPI / Anthropic SDK

## Services

* `agolive-api` (port 8080): 인증, 방 관리, 채팅 기록, 내부 토큰 검증
* `agolive-realtime` (port 8081): WebSocket 연결, 위치 동기화, 브로드캐스트

## Docs

* `docs/architecture.md`: 스키마, API, 구현 방식, 비즈니스 룰
* `docs/prd.md`: 서비스 목적, 기능, UX
* `docs/adr/`: 기술 결정 기록

## Principles

* 결정이 필요하면: `docs/architecture.md` 확인 → 과하지 않게 성능과 확장성을 고려한 해결책 → 이유 설명
* 기능 설계 시 `docs/prd.md`  참고
* 새 기술/라이브러리 도입은 필요한 경우에만, 먼저 이유를 설명할 것
* 구현은 minimal but complete. 불필요한 추상화 금지
* 코드 의도가 불명확할 때만 주석 추가
* 모듈 추가·변경, 에러 코드 추가·변경, API 변경 시 docs/architecture.md도 함께 업데이트할 것

## Comment Style
* 한 줄 주석만 사용, 한국어로 작성
* 형식: `// 동사+목적어` (예: `// 회원의 모든 리프레시 토큰 세션 무효화`, `// 이메일 기준으로 한 계정에 집중되는 분산 IP 공격 탐지용`)
* 코드만으로 의도가 불명확하거나 추가 설명이 필요할 때 작성

## Testing

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

## Roadmap

| Phase | 핵심 |
|---|---|
| 1 (current) | 방 생성/입장, 위치 공유, 채팅 |
| 2 | 단일 AI 에이전트 소환, 역할 부여 |
| 3 | 멀티 에이전트 오케스트레이션, 에이전트 간 대화, Human-in-the-loop |
| 4 | 파일 생성 (Excel/PDF), 도구 실행 (실무 기능) |
| 5 | 음성 채팅, 공간감 고도화 |
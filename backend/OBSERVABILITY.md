# Auth Template Backend Observability

## Included Stack

- Spring Boot Actuator
- Micrometer
- Prometheus
- Grafana

## Exposed Endpoints

- `GET /actuator/health`
- `GET /actuator/info`
- `GET /actuator/prometheus`

By default, only `health` and `info` are exposed. Public Prometheus scraping is enabled only when the `observability` profile is active.

## Local Run

```powershell
docker compose --profile observability up -d prometheus grafana
```

Run the backend with:

```powershell
$env:SPRING_PROFILES_ACTIVE='observability'
.\gradlew bootRun
```

Default addresses:

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3001`

Default Grafana account:

- Username: `admin`
- Password: `admin`

You can override these in `.env`:

- `GRAFANA_ADMIN_USER`
- `GRAFANA_ADMIN_PASSWORD`

## Metrics

Included metrics cover:

- JVM, GC, process, and HikariCP metrics
- HTTP request latency histograms and SLO buckets
- Login, logout, refresh-token reissue, and session invalidation outcomes
- Signup, email verification resend, password reset, password change, and withdrawal outcomes

## Production Notes

- In `prod`, only health remains publicly accessible by default.
- `info` and `prometheus` require authenticated access.
- `/actuator/prometheus` is intended to be reachable only from an internal network path or behind admin access controls.
- Prometheus in Docker scrapes `host.docker.internal:8080` by default.

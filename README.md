# DevSecOps Management Portal

A centralized management platform for BBH DevSecOps workflows, pipeline automation, pipeline key management & invalidation, product catalogs, and InfluxDB/Grafana metrics monitoring, fully integrated with the **`DSOEnhanced`** Jenkins Shared Library.

The application is structured as a unified multi-module Gradle project consisting of:
- **`backend`**: Spring Boot 3 service (Java 21) following Hexagonal and MVC architecture: domain logic, Liquibase persistence (H2 in Oracle mode locally, Oracle on OpenShift `rd`/`qc`/`prod`), pipeline key validation, InfluxDB v2 Flux queries, Grafana dashboard links, and REST API endpoints.
- **`frontend`**: Angular single-page application built with the BBH V6 Design System (`@v6/v6-themes`, `@v6/v6-table`, `@v6/v6-icons`), providing user interfaces for pipeline self-service, product & pipeline key management, audit trail / action history grid, library & monitoring administration (`/admin/settings`), and DORA/pipeline metrics visualization.

---

## Architecture Overview

Hexagonal and MVC layers:
- `adapter/in/web`: REST controllers, request/response DTOs, global problem exception handler (`@RestControllerAdvice`).
- `application/<feature>/port/in`: Primary ports implemented by `@UseCase` service components.
- `application/<feature>/port/out`: Secondary ports for persistence, metrics and external adapters.
- `domain/<feature>`: Pure domain entities and value objects (Lombok-annotated, framework-free).
- `adapter/out/persistence`: Spring Data JPA repositories, JPA entities, Liquibase changelogs.
- `adapter/out/influx`: InfluxDB Flux query and telemetry clients.

```
DevSecOpsManagementPortal/
├── backend/                  # Spring Boot application (Java 21)
│   ├── src/main/java/        # Domain model, use cases, REST controllers, adapters
│   ├── src/main/resources/   # Profiles (local H2 / oracle rd,qc,prod), logback-spring.xml
│   ├── src/test/             # Spock unit specs with 60% JaCoCo line coverage gate
│   ├── src/regressionTest/   # Spock API end-to-end regression tests
│   ├── src/smokeTest/        # Health and endpoint smoke tests
│   ├── src/performanceTest/  # p95 latency performance benchmark tests
│   ├── Dockerfile            # Red Hat UBI 9 OpenJDK 21 container image specification
│   └── build.gradle          # Backend build script
├── frontend/                 # Angular single-page application with BBH V6 styles
│   ├── src/app/              # Application routes, components, and services
│   ├── src/common/ui/        # V6 Table helpers, toolbar components, V6 form directives
│   ├── angular.json          # Angular CLI workspace configuration
│   └── package.json          # Frontend dependencies and npm scripts
├── deploy/openshift/         # Kustomize base & overlays (rd, qc, prod) for OpenShift (multi-pod replicas: 2)
├── gradle/                   # Gradle wrapper and version catalog (libs.versions.toml)
├── build.gradle              # Root build script
├── docs/                     # Deployment and architecture documentation
│   └── DEPLOYMENT.md         # Quick guide: local laptop & OpenShift deployment
├── settings.gradle           # BBH Nexus repository configuration
└── README.md
```

See [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) for a quick step-by-step run and deploy guide.

---

## Running Locally in BBH Network

The project is configured for BBH developer laptops using local H2 file database in Oracle compatibility mode (`~/bbh-devsecops/dso-portal/dso-portal`).

### Single Command (Backend + Bundled Frontend on Port 8080)

```bash
./gradlew :backend:bootRun
```
Open **`http://localhost:8080`**.

### Independent Frontend Dev Server (Port 4200 with Hot Reload)

**Terminal 1 — Backend:**
```bash
./gradlew :backend:bootRun -PskipGui=true
```

**Terminal 2 — Frontend:**
```bash
cd frontend
npm start
```
Open **`http://localhost:4200`** (proxies `/api` to `http://localhost:8080`).

---

## Logging & Auditing

- **Logback & SLF4J**: Configured in `backend/src/main/resources/logback-spring.xml` with `SizeAndTimeBasedRollingPolicy`.
- **Rotation Policy**: Daily rollover (`%d{yyyy-MM-dd}.%i.log.gz`) or when log file reaches **10MB**, compressed as `.gz`, retaining 30 days of history up to 2GB total cap.
- **Action History Audit Trail**: Every sensitive user action (pipeline key creation, key invalidation, service config updates, product changes) is logged and stored in `DSO_ACTION_LOG` table, viewable via the filterable and sortable **Action History** grid.

---

## Testing Suites (Groovy & Spock)

| Suite | Description | Minimum Coverage |
|-------|-------------|------------------|
| `test` | Domain rules, use cases, controller MockMvc, architecture rules (ArchUnit) | **>= 60% line coverage** (JaCoCo gate) |
| `regressionTest` | Full API end-to-end on H2 in Oracle mode | 30–40% |
| `smokeTest` | Application startup, health liveness/readiness, key endpoints | 30–40% |
| `performanceTest` | P95 latency measurements for product, service and pipeline operations | 30–40% |

Run all backend suites:
```bash
./gradlew :backend:check
```

---

## OpenShift & Oracle Database Deployment

- **Test & Production Profiles**: OpenShift environments (`rd`, `qc`, `prod`) activate the `oracle` Spring profile group, connecting to Oracle database via HikariCP (`com.oracle.database.jdbc:ojdbc11`) with Liquibase automated migrations.
- **Multi-Pod High Availability**: The base deployment (`deploy/openshift/dso-portal/base/deployment.yaml`) runs **2 replicas** with `RollingUpdate` strategy for seamless zero-downtime rollouts.

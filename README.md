# DevSecOps Management Portal

A centralized management platform for BBH DevSecOps workflows, pipeline automation, pipeline key management & invalidation, product catalogs, and InfluxDB/Grafana metrics monitoring, fully integrated with the **`DSOEnhanced`** Jenkins Shared Library.

The application is structured as a unified multi-module Gradle project consisting of:
- **`backend`**: A Spring Boot service (Java 21) handling domain logic, Liquibase persistence (H2 in Oracle mode locally, Oracle on OpenShift `rd`/`qc`/`prod`), pipeline key validation, InfluxDB v2 Flux queries, Grafana dashboard links, and REST API endpoints.
- **`frontend`**: An Angular 21 single-page application providing user interfaces for pipeline self-service, product & pipeline key management, library & monitoring administration (`/admin/settings`), and DORA/pipeline metrics visualization.

---

## Architecture Overview

```
DevSecOpsManagementPortal/
├── backend/                  # Spring Boot application (Java 21)
│   ├── src/main/java/        # Domain model, use cases, REST controllers, adapters
│   ├── src/main/resources/   # Profiles (local H2 / oracle rd,qc,prod) & Liquibase migrations
│   ├── src/test/             # Unit, migration, and ArchUnit tests (Spock, JUnit 5)
│   ├── src/regressionTest/   # End-to-end API regression tests
│   ├── Dockerfile            # Red Hat UBI 9 OpenJDK 21 container image specification
│   └── build.gradle          # Backend build script with frontend asset bundling
├── frontend/                 # Angular 21 single-page application
│   ├── src/
│   │   ├── app/              # Portal application routes, components, and services
│   │   ├── common/           # Shared UI components, dialogs, styles, and core models
│   │   ├── testFixtures/     # Test utilities and mock API fixtures
│   │   └── regressionTest/   # Browser-based regression tests (Playwright)
│   ├── angular.json          # Angular CLI workspace (BBH @v6 theme + public fallback)
│   ├── package.json          # Frontend dependencies and npm scripts
│   ├── tsconfig.json         # TypeScript configuration with @common/* path mapping
│   └── build.gradle          # Frontend Gradle integration tasks (build, test, verify)
├── deploy/openshift/         # Kustomize base & overlays (rd, qc, prod) for OpenShift + Oracle
├── examples/                 # Example Jenkinsfiles, API scripts, and config payloads
├── gradle/                   # Gradle wrapper and version catalog (libs.versions.toml)
├── build.gradle              # Root build script
├── settings.gradle           # Module definitions and automatic BBH / public repository routing
└── README.md
```

---

## Running Locally (Outside & Inside the BBH Network)

The build automatically detects whether `tools.bbh.com/nexus` is reachable (or you can force the mode with `-PbbhNetwork=false` or `-PbbhNetwork=true`):
- **Outside the BBH network (`bbhNetwork=false`)**: Uses Gradle Plugin Portal, Maven Central, and `registry.npmjs.org`, and builds the Angular frontend with the `public` fallback stylesheet (`src/common/styles-public.scss`).
- **Inside the BBH network (`bbhNetwork=true`)**: Uses BBH Nexus (`tools.bbh.com/nexus`), installs the BBH Design System packages (`@v6/v6-themes@21`, `@v6/v6-table`, `@v6/v6-icons`), and builds the Angular frontend with BBH v6 styles (`src/common/styles.scss`).

### Option 1: Single Command (Backend + Bundled Frontend on Port 8080)

Outside the BBH network:
```bash
./gradlew :backend:bootRun -PbbhNetwork=false
```

Inside the BBH network (with BBH `@v6` Angular styles):
```bash
./gradlew :backend:bootRun -PbbhNetwork=true
```
Open **`http://localhost:8080`**.

### Option 2: Two Terminals (Backend on 8080 + Angular Dev Server with Hot Reload on 4200)

**Terminal 1 — Backend only (skip frontend Gradle build):**
```bash
./gradlew :backend:bootRun -PskipGui=true
```

**Terminal 2 — Frontend (Outside BBH network):**
```bash
cd frontend
npm ci --registry=https://registry.npmjs.org/
npm start
```

**Terminal 2 — Frontend (Inside BBH network with `@v6` styles):**
```bash
cd frontend
npm ci
npm run install:bbh
npm run start:bbh
```
Open **`http://localhost:4200`** (proxies `/api` to `http://localhost:8080`).

---

## Configuring InfluxDB & Grafana via the Management Panel

Open **`http://localhost:8080/admin/settings`** (or click **Admin** -> **Library defaults**) and scroll to **Tools and servers**:
1. **Repository, metrics and agents**:
   - **InfluxDB write URL (`influx.url`)** and **InfluxDB credentials ID (`influx.credentialsId`)**: Sent to `DSOEnhanced` Jenkins pipelines so every pipeline run writes `pipeline_run`, `dora`, `stage_run`, `security_findings`, `sonarqube`, `coverage`, `deployment`, `artifact`, and `goldenfix_run` metrics to InfluxDB.
2. **Portal monitoring (InfluxDB & Grafana)**:
   - **InfluxDB read URL (`dso.influx.url`)**, **InfluxDB organization (`dso.influx.org`)**, **InfluxDB bucket (`dso.influx.bucket`)**, and **InfluxDB read token (`dso.influx.token`)**: Used directly by the Portal backend to query pipeline run history and DORA metrics for all Pipeline Monitoring views.
   - **Grafana instance name / pipeline dashboard URL / security dashboard URL** (primary and optional second instance): Displayed on the Pipeline Monitoring pages with automatic `var-project=<tag>&from=now-<days>d&to=now` query parameters.

---

## OpenShift & Oracle Database Deployment

On OpenShift test and production environments (`rd`, `qc`, `prod`), the portal runs with the `oracle` Spring profile group (`ojdbc11`, `ddl-auto: validate`, Liquibase auto-migration, `RollingUpdate` strategy, and `dso-portal-db` Secret). See [`deploy/openshift/README.md`](deploy/openshift/README.md) for full container build and OpenShift deployment commands.

---

## Testing

Run all backend (Spock unit, Liquibase migration, ArchUnit, JaCoCo) and frontend (Vitest) unit tests:
```bash
./gradlew test
```

Run end-to-end backend API regression tests:
```bash
./gradlew :backend:regressionTest
```

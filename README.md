# BBH DevSecOps Management Portal

The portal onboards products to DevSecOps, manages their pipelines and pipeline keys, shows how the pipelines are
doing, and serves the configuration the Jenkins shared library [DSOEnhanced](https://github.com/mateuszmatan/DSOEnhanced)
reads for every build. It is one Spring Boot service (Java 21, Spring Boot 4) with the Angular GUI inside, packaged as
one jar and one container image.

| Folder | Holds |
|--------|-------|
| `backend/` | the service: REST API, domain, use cases, persistence (Liquibase), InfluxDB and Grafana adapters, Dockerfile |
| `frontend/` | the Angular GUI with the BBH Design System v6 (`@v6/v6-themes`, `@v6/v6-table`, `@v6/v6-icons`) |
| `deploy/openshift/` | Kustomize manifests with `rd`, `qc` and `prod` overlays, and [how to run the image](deploy/openshift/README.md) |
| `examples/` | Jenkinsfiles, REST calls and a rendered configuration, see [examples/README.md](examples/README.md) |

## Build and tests

The build runs inside the BBH network only: Gradle 8.14, plugins and libraries come from the BBH Nexus and npm packages
from the BBH npm registry, both reached directly (set in `settings.gradle`, `gradle.properties` and the Gradle wrapper).
It needs Java 21, and Node.js 20.19 or a later 20.x with npm 10.8 or a later 10.x, on the `PATH`; on Windows `gradle.properties` points `npm.executable` and
`npx.executable` at `C:\Program Files\nodejs`. Use `gradlew.bat` instead of `./gradlew` on Windows.

| Command | Does | Takes about |
|---------|------|-------------|
| `./gradlew :backend:bootJar` | the jar with the GUI inside: `backend/build/libs/dso-portal-<version>.jar` | 1 min the first time, seconds after |
| `./gradlew assemble` | the jar without running any test | as above |
| `./gradlew :backend:test` | backend unit tests, fails below 60% line coverage | 45 s |
| `./gradlew :frontend:test` | Angular unit tests (Vitest), fails below 60% of lines | 45 s |
| `./gradlew regressionTest` | the API end to end on H2, and the GUI journeys in Chromium against a stub API | 1 min 20 s |
| `./gradlew smokeTest performanceTest` | start-up, health and every page; p95 latencies of the key calls | 55 s |
| `./gradlew build` | the jar and every suite above | 3 min 40 s from clean |

Tests are Groovy and Spock; the Angular unit tests stay in Vitest. Smoke and performance tests cover the key functions
only, regression the most important journeys. The GUI suites run in Chromium, which Playwright downloads once through
`njproxy`; `-Dfrontend.browser=<path to chrome.exe>` runs them in an installed Chrome or Edge instead, with no
download. `-Dfrontend.screenshots=true` saves a screenshot of every GUI test under `frontend/build/reports/frontend`.
`-PskipGui` builds the service without its GUI for API work, and `-Dperformance.factor=3` relaxes the performance limits
further on a slow machine (the default is 2). `./gradlew smokeTest -Dsmoke.baseUrl=https://...` runs the backend and GUI
smoke tests against a deployed portal.

## Running locally

On a BBH laptop the portal runs as a plain Java application on an embedded H2 database in Oracle mode.

```bash
./gradlew :backend:bootRun          # GUI and API on http://localhost:8080
```

or `java -jar backend/build/libs/dso-portal-0.1.0-SNAPSHOT.jar` after `bootJar`. The database is the file
`~/bbh-devsecops/dso-portal/dso-portal.mv.db` (`DSO_DATA_DIR` moves the folder); Liquibase creates it on the first
start with the five BBH Business Services and the BBH library defaults, and keeps the data between starts. Logs go to
the console and to `logs/dso-portal.log` (10 MB per file, 30 days, 2 GB in all; `LOG_PATH` moves the folder).

For GUI work, run the service with `-PskipGui` and `npm start` in `frontend/`: the Angular development server on
http://localhost:4200 rebuilds on every change and sends `/api` to port 8080.

| Variable | Default | Meaning |
|----------|---------|---------|
| `PORT` | `8080` | HTTP port |
| `DSO_DATA_DIR` | `~/bbh-devsecops/dso-portal` | folder of the local H2 database |
| `SPRING_PROFILES_ACTIVE` | `local` | `rd`, `qc` or `prod` switch to Oracle (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_POOL_SIZE` of 5, 10 or 20 by default); `rd` logs at DEBUG |
| `INFLUX_URL`, `INFLUX_TOKEN`, `INFLUX_ORG`, `INFLUX_BUCKET` | empty, empty, `DevSecOps`, `DORA-metrics` | the InfluxDB the pipelines report their runs to |
| `GRAFANA_DASHBOARD_URL`, `GRAFANA_SECURITY_DASHBOARD_URL`, `GRAFANA_NAME` | empty, empty, `Grafana` | the DSOEnhanced dashboards linked from Pipeline Monitoring; `GRAFANA_2_*` for a second instance |

The InfluxDB and Grafana settings can also be set in the GUI under Admin, Library defaults, Portal monitoring; values
saved there win over the variables. The InfluxDB token saved there is never shown again, and `INFLUX_TOKEN` is sent
only to `INFLUX_URL`, never to another InfluxDB saved in the GUI.

## Containers and OpenShift

`backend/Dockerfile` builds the image from the boot jar (Red Hat UBI 9 with Java 21, any non-root UID). The test
environments are Docker and OpenShift: in OpenShift every application of the team runs in one project, each in its own
pod, and the portal is the `dso-portal` Deployment. RD, QC and prod use Oracle; Liquibase updates the schema at
start-up. In the image the logs are written to the console and to `/application/logs`, which the pod keeps on an
`emptyDir` of 3 GB. [deploy/openshift/README.md](deploy/openshift/README.md) has the commands.

## How DSOEnhanced reads its configuration

Each Jenkinsfile holds only a pipeline key. Before the stages start, the library sends
`GET $DSO_PORTAL_URL/api/dso/config/<key>?format=json` from a Jenkins agent; `DSO_PORTAL_URL` is a global
environment variable of Jenkins, for example `https://dso-portal.apps.bbh.com`. Jenkins needs no database account and
no other setting.

| Answer | Meaning for the build |
|--------|-----------------------|
| 200 with the configuration | the portal renders it from its database at that moment and records the key's last use |
| 403 `Pipeline key invalidated` | the key was invalidated; the build stops before its stages |
| 404 `Unknown DevSecOps pipeline key` | the key was never issued; the build stops before its stages |

The configuration names Jenkins credentials IDs, never passwords. The library writes its run metrics to InfluxDB, and
the portal reads them back for Pipeline Monitoring. A Nexus IQ GoldenFix pipeline is accepted only for a service with a
Nexus IQ application, because the library refuses to scan without one, and a Full pipeline of a service on virtual
machines only with an UrbanCode Deploy application for DV (environments left empty or including DV), because the
library deploys the lower test region through UrbanCode Deploy environment DV.
`GET /api/pipelines/{id}/config` shows the same configuration in the GUI without recording a use.

Every service signs in to AppScan with its own API key ID (`asoc.keyId`) and, optionally, the Jenkins credential that
holds its secret (`asoc.token`); without that credential the library takes the key ID as the credential's ID. A
service scans one Nexus IQ application. A service whose build is not delivered to Nexus renders
`deploy.<vm|openshift>.rd.nexusDeliveryEnabled: false`, and the forms then ask for none of the delivery settings. A
product saved with `agentLabels` runs the pipelines that save creates on those Jenkins agents instead of the service
template's.

## Importing a service from a config file

`POST /api/service-configs/import` reads a `config.yaml`, a configuration the portal rendered, or its JSON form, and
returns one service form per project with notes on what it did not take over: settings the portal has no field for,
global and pipeline settings, and secrets such as `asoc.keySecret`, `influx.token` or a test job's `token`, which are
never echoed. A value equal to the global settings comes back empty, so the service keeps inheriting it. The file may
hold at most 262,144 characters; a malformed one is refused with the line and column of the problem. The call stores
nothing, so the action history does not record it.

## Architecture

The service is hexagonal with MVC at its edge, and ArchUnit tests keep the layers apart:

- `adapter/in/web`: REST controllers and their request and response records; every change is recorded in the action
  history by `ActionRecorder`.
- `application/<feature>`: use cases (`@UseCase`, transactional) behind the ports in `port/in` and `port/out`.
- `domain/<feature>`: the business rules, with no framework.
- `adapter/out/{persistence,influx,grafana,key}`: JPA on H2 or Oracle, InfluxDB Flux queries, Grafana links, key
  generation.

The GUI calls the groups of products Business Services; the API, the code and the database keep their older name,
department (`/api/departments`, `DSO_DEPARTMENT`).

Logging is SLF4J with Logback (`logback-spring.xml`); each line carries the correlation id of its API call. Lombok
replaces constructors, builders and copies wherever it can. Every change made in the GUI is stored in `DSO_ACTION_LOG`
with who, what, when and the result, and shown under Action history.

## Known gaps

- The portal has no sign-in yet, so every action is recorded as the default administrator. BBH single sign-on and
  roles come later; until then the portal must stay inside the test network.

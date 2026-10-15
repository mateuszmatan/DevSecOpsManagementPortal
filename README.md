# DevSecOps Management Portal

A centralized management platform for DevSecOps workflows, pipeline automation, security keys, product catalogs, and metrics monitoring.

The application is structured as a unified multi-module Gradle project consisting of:
- **`backend`**: A Spring Boot service (Java 21) handling domain logic, persistence, security configurations, and API endpoints.
- **`frontend`**: An Angular single-page application providing user interfaces for pipeline self-service, product management, administration, and metrics visualization.

---

## Architecture Overview

```
DevSecOpsManagementPortal/
├── backend/                  # Spring Boot application (Java 21)
│   ├── src/main/java/        # Domain model, use cases, REST controllers, adapters
│   ├── src/main/resources/   # Configuration files, Liquibase database migrations
│   ├── src/test/             # Unit and integration tests (Spock, JUnit 5)
│   ├── src/regressionTest/   # End-to-end API regression tests
│   ├── Dockerfile            # Container image specification
│   └── build.gradle          # Backend build script with frontend asset bundling
├── frontend/                 # Angular single-page application
│   ├── src/
│   │   ├── app/              # Portal application routes, components, and services
│   │   ├── common/           # Shared UI components, dialogs, styles, and core models
│   │   ├── testFixtures/     # Test utilities and mock API fixtures
│   │   └── regressionTest/   # Browser-based regression tests (Playwright)
│   ├── angular.json          # Angular CLI workspace configuration
│   ├── package.json          # Frontend dependencies and npm scripts
│   ├── tsconfig.json         # TypeScript configuration with @common/* path mapping
│   └── build.gradle          # Frontend Gradle integration tasks (build, test, verify)
├── gradle/                   # Gradle wrapper and version catalog (libs.versions.toml)
├── build.gradle              # Root build script
├── settings.gradle           # Module definitions and repository management
└── README.md
```

---

## Getting Started

### Prerequisites
- **Java**: OpenJDK 21
- **Node.js**: Node 20.x (LTS) or higher
- **npm**: npm 10.x or higher

---

## Building and Running

### Full Build (Gradle)
To compile, run all tests, and package both backend and frontend:
```bash
./gradlew build
```

### Running Backend (Integrated)
Starts the Spring Boot server with the compiled Angular static assets served directly:
```bash
./gradlew :backend:bootRun
```
The application will be available at: `http://localhost:8080`.

---

## Development

### Frontend Development
To run the Angular application standalone with hot reloading and proxy to the backend API:
```bash
cd frontend
npm ci
npm start
```
The UI dev server runs on `http://localhost:4200` and proxies API calls to `http://localhost:8080`.

To run frontend unit tests:
```bash
cd frontend
npm test
```

### Backend Development
To run backend unit tests:
```bash
./gradlew :backend:test
```

To run end-to-end regression suites:
```bash
./gradlew :backend:regressionTest
```

# Changelog

All notable changes to the DevSecOps Management Portal are documented in this file.

## [Unreleased]

### Added
- **SQL / PL/SQL Build Tool Support via Liquibase (`buildTool: liquibase`)**:
  - Added `LIQUIBASE` to the `BuildTool` enum in the backend catalog domain (`com.bbh.itss.dso.portal.domain.catalog.BuildTool`) and frontend TypeScript models (`BuildTool` in `models.ts`).
  - Full command mapping for Liquibase across all pipeline stages:
    - Build stage (`build.liquibase.commands`, `build.liquibase.flags`, `build.liquibase.dir`, `build.liquibase.env`, `build.liquibase.label`, `build.liquibase.returnStdout`).
    - Unit tests and verification stage (`tests.unitTests.liquibase.*`, plus JUnit XML result pattern `tests.unitTests.unitTestResult` and coverage report path `coverage.reportPath` for utPLSQL / SQL test runners).
    - SonarQube static analysis (`tools.sonar.liquibase.*`) for SQL and PL/SQL code quality and security rules.
    - AppScan SAST pre-IRX step (`asoc.liquibase.*`).
  - Automatic default command suggestions in the portal UI (`updateSQL` for build, `--changelog-file=changelog.xml` for flags, `validate` for unit tests, `sonar` for SonarQube) and dynamic field labels ("Liquibase commands", "Liquibase flags").
  - Tailored validation rules: `javaPath` and `buildToolAutoSetup` are not required when `buildTool` is `LIQUIBASE`.
  - Support for both standalone (single-module) SQL / PL/SQL repositories and multi-module / polyglot repositories combining SQL / PL/SQL services with Java, Gradle, Maven, Node.js / Angular (including Stage 0 `build.node`), Python, and Kotlin services in a combined `Jenkinsfile`.
  - Complete YAML/JSON import (`ServiceConfigReader`) and export (`DsoConfigBuilder`) round-trip support for `liquibase` services.
- **Service Creation & Configuration Import / Copy Workflows**:
  - **Import `devsecops_config.yaml` / `config.yaml`**: Added "Import devsecops_config.yaml" in both the Self-service wizard and the Admin product editor, parsing YAML or JSON configurations (`ServiceConfigReader`), pre-filling all matching service fields, and reporting any unimported keys or missing required fields.
  - **Copy Values from Existing Service**: Added "Copy values from existing service" when adding a service in the Self-service wizard and Admin editor, plus "Duplicate service" on existing service cards.
  - **Strict Form Validation on Service Creation**: Disabled "Add service" / "Continue" buttons whenever any required field in the current service form is invalid or incomplete, displaying clickable links to each invalid field.
  - **Standalone `devsecops_config.yaml` Download & Keyless Jenkinsfile**: Product settings download now saves as `devsecops_config.yaml` (with `config.yaml` backward compatibility on import), accompanied by a "Jenkinsfile without a key" option for repositories running without portal key lookup.
- **Jenkins Node / Agent Configuration**:
  - Added Jenkins agents (`pipeline.agentNames`) field when creating a product and in pipeline settings, defaulting to the service template agents.
- **AppScan Credentials & SAST Configuration Alignment**:
  - Removed AppScan API Key ID from the product-level creation/edit form and database table (`V8__drop_product_appscan_key_id.sql` migration); AppScan credentials (`asoc.keyId` and `asoc.token`) are now consistently configured at the service level.
  - Moved `asoc.token` (AppScan secret credentials ID) from Advanced Settings to the main AppScan SAST section.
  - Added `sast.scanName` (custom AppScan SAST scan name) to AppScan SAST Advanced Settings.
- **SonarQube Configuration & Badge Authentication Alternatives**:
  - Moved SonarQube `installationName`, `authToken`, and `badgeToken` into the main SonarQube configuration flow.
  - Added `badgeCredentialsId` (`tools.sonar.badgeCredentialsId`) as an alternative to `badgeToken` (`tools.sonar.badgeToken`), allowing users to provide either a direct badge token or a Jenkins Secret text credentials ID (mutually exclusive validation).
- **Single Nexus IQ Application per Service**:
  - Restricted the service editor UI so a service configures at most one Nexus IQ application (`tools.nexusIq.application`), while preserving read/migration compatibility for legacy multi-application services (`V6__nexus_iq_single_application.sql`).
- **Deployment & Nexus Delivery Controls**:
  - Added "Disable Nexus Delivery" checkbox mapping to `deploy.vm.rd.nexusDeliveryEnabled: false` (for Virtual Machine deployments) and `deploy.openshift.rd.nexusDeliveryEnabled: false` (for OpenShift deployments).
  - When "Disable Nexus Delivery" is checked, delivery-only fields (such as Maven delivery command on VM or artifact name on OpenShift) are automatically hidden and excluded from required-field validation.
- **Multi-Report Code Coverage & Parallel Static Analysis Settings**:
  - Updated `coverage.reportPath` to accept multi-line paths and wildcards (`*`, `?`, `**`), with automatic discovery of JaCoCo XML and LCOV reports across modules when left empty.
  - Added Global Settings toggle "Run Nexus IQ and SonarQube while AppScan scans" (`staticAnalysis.parallel`) to run AppScan SAST in the background in parallel with Nexus IQ + GoldenFix and SonarQube.
- **Node.js Stage 0 Pre-Build & Shared Version Catalogs**:
  - Added optional Node.js pre-build (`build.node.*`, Stage 0) for services with a web frontend (Angular/React/npm) alongside Gradle, Maven, or Liquibase.
  - Added `goldenFix.versionCatalogs` ("Shared version catalogs") to the GoldenFix section and combined multi-service `Jenkinsfile` generation (`pipelineKeys`) for multi-module repositories.
- **Library Defaults Pre-Population & Interactive Field Tooltips**:
  - Automatically pre-populated new service forms with values from Library defaults (Global Settings) and displayed "Library default: ..." hints and `devsecops_config.yaml` key chips on every field.
  - Added help icons (`dso-help`) with descriptive tooltips across all form fields in the portal.

### Fixed
- **OpenShift Targets QC Region Validation**:
  - Fixed validation in the OpenShift targets section so the QC region is completely optional when left empty, and only validates required QC deployment fields when at least one QC field is populated.

# Changelog

All notable changes to the DevSecOps Management Portal are documented in this file.

## [Unreleased]

### Added
- **Liquibase (SQL / PL/SQL) Build Tool**:
  - Added `LIQUIBASE` to `BuildTool` enum in backend catalog domain and frontend TypeScript models.
  - Liquibase command mapping for `build.liquibase.*`, `tests.unitTests.liquibase.*`, `tools.sonar.liquibase.*`, and `asoc.liquibase.*`.
  - Automatic default command suggestions (`updateSQL` for build, `validate` for tests/verification, `--changelog-file=...` for flags).
  - Domain validation rules adjusted so Java paths and compiler tasks are not required for pure SQL changelog services.
  - Documentation and form chip verification in documentation test suite.
- **New Service Creation Workflows**:
  - "Import config from config.yaml": allows importing and pre-filling service configuration directly from an existing `devsecops_config.yaml` or `config.yaml` file.
  - "Copy values from existing service": allows copying configuration from any existing service in the same product.
  - Strict form validation: "Add service" button is disabled whenever any required field is invalid or incomplete.
- **Jenkins Node / Agent Selection**:
  - Configurable Jenkins node / agent label when creating or editing a product.
- **AppScan & Credentials Alignment**:
  - Removed AppScan API Key ID from product-level form; credentials consistently managed at service level.
  - Relocated `asoc.token` from Advanced Settings to the main SAST configuration section.
  - Added `sast.scanName` to SAST Advanced Settings.
- **SonarQube Enhancements**:
  - Moved SonarQube installation name, authToken, and badge token from Advanced Settings to the main SonarQube section.
  - Added `badgeCredentialsId` supporting Jenkins secret credentials alongside direct `badgeToken` input (either option accepted).
- **Nexus IQ Policy Enforcement**:
  - UI restriction ensuring only one Nexus IQ application can be configured per service.
- **Deployment Controls**:
  - Added "Disable Nexus Delivery" checkbox mapping to `deploy.vm.rd.nexusDeliveryEnabled: false` (VM) and `deploy.openshift.rd.nexusDeliveryEnabled: false` (OpenShift).
  - Hides and suppresses validation for delivery fields when delivery is disabled.
- **Library Defaults Pre-Population**:
  - New service form fields are automatically pre-populated with values defined in library defaults.
- **Enterprise Form Tooltips**:
  - Integrated BBH Design System tooltips across form fields detailing purpose and expected input format.

### Fixed
- **OpenShift Targets QC Region Validation**:
  - Resolved issue where QC region section was marked invalid even when optional.
  - QC region configuration is now completely optional; validation activates only when the user begins filling in QC region settings.

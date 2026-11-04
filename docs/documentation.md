# DevSecOps Management Portal - User and Administrator Guide

This guide explains the BBH DevSecOps Management Portal to the people who use it. Product teams use it to set up the DevSecOps pipelines of their products. The DevSecOps team uses it to look after products, pipelines, keys and the settings every pipeline shares. No DevSecOps knowledge is needed to follow the step-by-step chapters.

The guide is written in `docs/documentation.md`. The page `docs/documentation.html` is generated from it by the build (`gradle documentation`), and `gradle check` fails when the page is out of date. Developers find the build, the tests and the architecture in the [README](../README.md).

### Contents

- [1. What the portal does](#1-what-the-portal-does)
- [2. The pages of the portal](#2-the-pages-of-the-portal)
- [3. Products and services](#3-products-and-services)
- [4. Add a service in the Self-service wizard](#4-add-a-service-in-the-self-service-wizard)
- [5. Add a service in the admin editor](#5-add-a-service-in-the-admin-editor)
- [6. Build the frontend with Node.js first (stage 0)](#6-build-the-frontend-with-nodejs-first-stage-0)
- [7. SQL and PL/SQL changes with Liquibase](#7-sql-and-plsql-changes-with-liquibase)
- [8. Shared version catalogs and the combined Jenkinsfile](#8-shared-version-catalogs-and-the-combined-jenkinsfile)
- [9. Pipeline types, pipeline keys, the Jenkinsfile and the Jenkins jobs](#9-pipeline-types-pipeline-keys-the-jenkinsfile-and-the-jenkins-jobs)
- [10. Import a devsecops_config.yaml and copy a service](#10-import-a-devsecops_configyaml-and-copy-a-service)
- [11. GoldenFix](#11-goldenfix)
- [12. The service template](#12-the-service-template)
- [13. Library defaults (Global Settings)](#13-library-defaults-global-settings)
- [14. Pipeline Monitoring and DORA](#14-pipeline-monitoring-and-dora)
- [15. Business Services](#15-business-services)
- [16. Action history](#16-action-history)
- [17. Field reference: from the portal to devsecops_config.yaml](#17-field-reference-from-the-portal-to-devsecops_configyaml)
- [18. Database and environments](#18-database-and-environments)
- [19. Troubleshooting](#19-troubleshooting)

## 1. What the portal does

The portal holds the DevSecOps settings of every BBH product. Jenkins runs the pipelines of the DevSecOps library DSOEnhanced. Each pipeline with a key asks the portal for its settings when it starts, so its settings never live in the repository of the service. A Jenkinsfile without a key makes the library read them from the repository instead, see [A Jenkinsfile without a key](#a-jenkinsfile-without-a-key).

The portal does five things:

1. It records the products, their services and the pipelines of each service.
2. It issues a secret pipeline key for each pipeline. The key is the only thing a Jenkinsfile with a key holds.
3. It renders the settings of a pipeline as a `devsecops_config.yaml` document when Jenkins asks for them with the key.
4. It shows how the pipelines are doing: the latest runs and the delivery performance (DORA).
5. It records every change in the action history.

### Words used in this guide

| Word | Meaning |
|------|---------|
| Business Service | A group of products, for example Custody or Fund Services. The API and the database call it a department. |
| Product | An application that BBH builds and runs, for example Payments Hub. It has a short upper-case code, for example `PAYHUB`. |
| Service | One part of a product that is built and deployed on its own, for example its API or its web front end. |
| Pipeline | The automated build, test and security checks that Jenkins runs on the code of one service. A service has at most one pipeline of each type. |
| Pipeline key | The secret code that the Jenkins job of a pipeline uses to fetch its settings from the portal. |
| Jenkinsfile | The file in the repository of the service that tells Jenkins to run the pipeline. It holds the pipeline key or, without a key, the services and the Jenkins agents of the run; see [A Jenkinsfile without a key](#a-jenkinsfile-without-a-key). |
| devsecops_config.yaml | The settings document that the library reads. The portal renders it from its database each time a pipeline with a key starts. A Jenkinsfile without a key makes the library read it from the root of the repository instead, or `config.yaml`, its former name, when there is none. |
| Library defaults | The values on the Library defaults page (Admin, Library defaults), also called the Global Settings. They apply to every service that leaves a field empty. |
| Service template | The values a new service starts with, such as its build tasks and the name of its Nexus IQ application. |
| GoldenFix | The part of the library that opens pull requests that raise vulnerable dependencies to safe versions. |

### How a pipeline gets its settings

1. Jenkins starts the job. The Jenkinsfile calls the entry point of the pipeline type with the pipeline key.
2. The library sends `GET $DSO_PORTAL_URL/api/dso/config/<key>?format=json` from the Jenkins agent.
3. The portal checks the key. It answers 200 with the settings, 403 `Pipeline key invalidated` or 404 `Unknown DevSecOps pipeline key`.
4. The library runs the stages with these settings. It writes the results of the run to InfluxDB.
5. The portal reads the results back for Pipeline Monitoring.

> [!NOTE]
> The settings name Jenkins credentials IDs, never passwords or tokens. Secrets stay in the Jenkins credentials store.

## 2. The pages of the portal

The menu at the top has five entries.

| Menu | What you do there |
|------|-------------------|
| Pipelines | Choose your Business Service and see every pipeline of its products, with its key state, its Jenkins job and its last run. Open a pipeline to see its key, its settings, its Jenkinsfile and its latest runs. |
| Self-service | Set up the pipelines of a product, or change them, in five guided steps. See [chapter 4](#4-add-a-service-in-the-self-service-wizard). |
| Pipeline Monitoring | See whether the latest runs passed and how often and how safely changes reach production. See [chapter 14](#14-pipeline-monitoring-and-dora). |
| Action history | See who changed what and when, with the result. See [chapter 16](#16-action-history). |
| Admin | The DevSecOps administration with four tabs: Business Services, Products, Service template and Library defaults. |

### The help icons and the key chips

Every field has a help icon. Point at it or move the keyboard focus to it to read what the field does. The grey text under a field is its hint. A hint ends with a code chip such as `build.node.enabled`. The chip is the key the field writes in `devsecops_config.yaml`. [Chapter 17](#17-field-reference-from-the-portal-to-devsecops_configyaml) lists every chip.

A hint that says "Library default: X" shows the value the service takes from the Library defaults page. A hint that says "Library default" alone shows that the field still holds that value.

## 3. Products and services

### The product

A product has these fields:

| Field | Meaning |
|-------|---------|
| Code | Two to 50 upper-case letters, digits, '-' or '_', starting with a letter, for example `PAYHUB`. The portal suggests it from the name. It is used in Jenkins job names and reports. It becomes `pipeline.product` in `devsecops_config.yaml`. |
| Name | The full name of the product. No other product may use it. |
| Business Service | The group the product belongs to. |
| Owner team | The team that looks after the product. |
| Description | What the product does and who uses it. |
| Contact e-mail | Where the DevSecOps team reaches the owners. A team mailbox is better than a personal address. |
| Jenkins agents | Only when you add a product: the agent labels of the pipelines that the save creates, separated by commas. The first one is the default. |

The product page (Admin, Products, then a product) lists the services and their pipelines. Its More menu shows "Settings sent to Jenkins (devsecops_config.yaml)" for the whole product and "Delete product". Its Download button saves the file as devsecops_config.yaml, ready to commit for a Jenkinsfile without a key. "Edit product" opens the admin editor.

### The service

A service has sections. The left-hand list of the service form shows them. A red mark next to a section means that a field in it needs your attention.

| Section | What it holds |
|---------|---------------|
| General | The service name and a description. |
| Build | The build tool (Gradle, Maven, Flutter or SQL and PL/SQL (Liquibase), see [chapter 7](#7-sql-and-plsql-changes-with-liquibase) for the last), its tasks, the artifact, the JDK and the optional Node.js build (stage 0). |
| Database changes | SQL and PL/SQL services only: the Liquibase changelog, the test, RD and QC databases and the package in Nexus. |
| Unit tests and coverage | The unit test command, the test reports and the coverage reports. |
| Test jobs | The smoke, regression and performance test jobs after a deployment. |
| Deployment | Virtual machines or OpenShift, the application and artifact names, and the Nexus delivery. |
| UrbanCode Deploy | Virtual machines only: the UrbanCode Deploy applications and components. |
| SSH targets | Virtual machines only: the hosts of the RD and QC test regions. |
| OpenShift targets | OpenShift only: the build and deployment projects of each region. |
| AppScan SAST and DAST | The AppScan application, the API key and the static and dynamic scans. |
| SonarQube | The SonarQube project and the analysis command. |
| Nexus IQ | The Nexus IQ application and its scan patterns. |
| Bitbucket | The repository where GoldenFix opens its pull requests. |
| GoldenFix | Whether GoldenFix runs, the shared version catalogs and the GoldenFix policy. |
| Monitoring | The InfluxDB tags of the results. |
| Flutter | Flutter only: the modules, the credentials and the Dart analysis. |

The pipeline type decides which sections the Self-service wizard shows:

| Pipeline | Sections |
|----------|----------|
| SAST | General, Build, AppScan SAST and DAST, Bitbucket (and Flutter for a Flutter service, Database changes for a SQL and PL/SQL service) |
| OSA (Nexus IQ GoldenFix) | General, Build, AppScan SAST and DAST, Nexus IQ, Bitbucket, GoldenFix (and Flutter); not for a SQL and PL/SQL service |
| Security | General, Build, Unit tests and coverage, Deployment, OpenShift targets, AppScan SAST and DAST, SonarQube, Nexus IQ, Bitbucket, GoldenFix, Monitoring (and Flutter for a Flutter service, Database changes for a SQL and PL/SQL service) |
| Full | Every section that applies to the build tool and the deployment target |

A section that does not apply to the build tool is never shown. For a SQL and PL/SQL service, the Security pipeline therefore shows General, Build, Database changes, AppScan SAST and DAST, SonarQube, Bitbucket and Monitoring.

The admin editor always shows every section that applies. A SQL and PL/SQL service has no Unit tests and coverage, Deployment, UrbanCode Deploy, SSH targets, OpenShift targets, Nexus IQ, GoldenFix or Flutter section.

### Required fields

Fields marked * are required. Some fields are required only in a situation:

- The service name, the AppScan application ID and the AppScan API key ID are always required. Every pipeline, the Nexus IQ GoldenFix pipeline included, signs in to AppScan with the key of the service.
- The Gradle tasks or Maven goals of the build are required, except for Flutter and SQL and PL/SQL services.
- The JDK path is required for Flutter and while "Set up the build tool automatically" is off.
- The artifact path is required for a Maven service on virtual machines that delivers to Nexus.
- On OpenShift, the application name, the artifact name and the RD build fields are required. The QC region is optional; once one of its fields is set, the fields its deployment needs are required.
- A Full pipeline of a service on virtual machines needs an UrbanCode Deploy application for the environment DV.
- A Nexus IQ GoldenFix pipeline needs a Nexus IQ application and a Bitbucket repository.
- The Bitbucket credentials ID is required once a repository URL is set.
- A Flutter service needs its modules, its tested modules and its three credentials.
- A SQL and PL/SQL service needs its changelog file and a credentials ID for each JDBC URL. While it delivers its package to Nexus, its Full, Security and Extended pipelines need the group ID and the Nexus repositories of [chapter 7](#what-the-pipelines-do-with-a-sql-and-plsql-service).

### Advanced settings

Fields that experts change are under "Advanced settings" at the end of a section. The panel is closed at first. Its summary line names what it holds. When a field inside it needs attention, the panel opens by itself.

### Saving

A service is saved with its product. The admin editor saves with "Add product" or "Save changes". The Self-service wizard saves in its Review step. Pipelines take the new settings the next time they run.

## 4. Add a service in the Self-service wizard

The Self-service wizard is the easiest way to set up the pipelines of a product. It has five steps: Product, Pipeline, Services, Review and Next steps.

### Before you start

Have these at hand:

- The name of each service, its AppScan application ID and the AppScan API key ID. The DevSecOps team gives you the AppScan values.
- Whether each service is built with Gradle or Maven. A developer of the service knows.
- The folder of its `package.json` when Node.js builds its frontend first.
- For the OSA pipeline: the Nexus IQ application and the Bitbucket repository of each service.
- For the Security and Full pipelines: whether each service runs on virtual machines or on OpenShift.
- For a SQL and PL/SQL service: its changelog file, the JDBC URL of each database with the ID of its Jenkins credential, and the Maven group ID of its package. The DBA and the DevSecOps team provide them; see [chapter 7](#before-you-add-a-sql-and-plsql-service).

### Step 1: Product

1. Open Self-service in the menu.
2. Choose the Business Service.
3. Choose "A new product" and type the product name. The portal shows the product code it will use, for example "Its product code will be PAYHUB". Optionally type the owner team and the contact e-mail.
4. Or choose "A product in the portal" and pick the product.
5. Click Next.

### Step 2: Pipeline

1. Choose the pipeline: SAST, OSA (Nexus IQ with GoldenFix), Security or Full. Not sure? Start with Security. You can add another pipeline later.
2. Leave "Jenkins agents" empty to use the agents of the service template.
3. Read the list "Have these at hand for the next step".
4. Click Next.

### Step 3: Services

In a repository with Java and database code, add the SQL and PL/SQL service first.

1. Click "Add a service". The dialog "Add a service" opens. Its tag shows the pipeline.
2. To start from another service of the product, choose it in "Copy values from". To start from a file, click "Import devsecops_config.yaml". Both are optional. See [chapter 10](#10-import-a-devsecops_configyaml-and-copy-a-service).
3. In General, type the service name, for example `backend-api`. The portal then fills in, from the service template: the Bitbucket repository URL, the Nexus IQ application with its scan pattern and, on OpenShift, the application name and the image repositories. It offers the Nexus IQ application once: one you remove does not come back when you change the name. A new service starts with the Bitbucket credentials ID of the service template; the name never fills it in.
4. In Build, choose the build tool. The hint tells you how to recognise it: "The code has a build.gradle file" means Gradle, "The code has a pom.xml file" means Maven, "The code has a pubspec.yaml file" means Flutter and "The code is SQL and PL/SQL with a Liquibase changelog, such as db/changelog-master.xml" means SQL and PL/SQL (Liquibase), described in [chapter 7](#add-a-sql-and-plsql-service-in-the-wizard). The tasks and the artifact path come from the service template. Leave "Set up the build tool automatically" on.
5. If the repository holds a frontend that npm builds, for example an Angular application, tick "Build the frontend with Node.js first". Type the "Folder with package.json", for example `frontend`. Leave the other Node.js fields as they are. See [chapter 6](#6-build-the-frontend-with-nodejs-first-stage-0).
6. In AppScan SAST and DAST, type the AppScan application ID. The AppScan API key ID is filled in when another service of the product already has one; otherwise type it.
7. For the OSA pipeline, check the Nexus IQ application and the Bitbucket repository.
8. For the Full pipeline on virtual machines, add an UrbanCode Deploy application for DV.
9. For the Security and Full pipelines, choose Virtual machine or OpenShift in Deployment. On OpenShift, fill in the RD build fields of OpenShift targets.
10. In GoldenFix, list a shared version catalog only when the services of the repository share one with an unusual name. Most services leave the field empty. See [chapter 8](#8-shared-version-catalogs-and-the-combined-jenkinsfile).
11. Look at the bottom of the dialog. While something is missing, it says "Complete these fields first:" followed by the names of the fields. Click a name: the dialog opens its section and puts the cursor in the field.
12. When nothing is missing, "Add service" is active. Click it.

Add every service of the product in the same way. Under each service the list shows its build tool, the folder of its Node.js build and its repository. Use Change to correct a service and Remove to take it out. Click Next.

### Step 4: Review

Check the product, the pipeline, the Jenkins agents and what happens to each service. Services of one repository that get the pipeline in this save say so: they get one Jenkinsfile and one Jenkins job. When two or more services of that repository already run the pipeline in one Jenkins job on the Jenkins agents of this save, the new pipelines join that job: the new services say that they join it, and the services of that job say that their Jenkinsfile gets the new keys. Both happen only while the service template names a Jenkins job: without one, the new pipelines get no Jenkins job and a Jenkinsfile each, and the review says neither. Any other service that has the pipeline already keeps its Jenkinsfile and Jenkins job. Check the repository URLs here, because a typo in one of them gives two Jenkinsfiles and two jobs. Nothing is saved until you click the save button of this step. A red box lists the pipelines and keys that a removal deletes.

### Step 5: Next steps

1. Copy the Jenkinsfile of each service into a file named `Jenkinsfile` in the top folder of the repository of the service. Merge it, or send it to a developer of the service.
2. Ask your Jenkins administrator for a Pipeline job for each service, with the job names shown. The job reads the Jenkinsfile from the repository (Pipeline script from SCM).
3. Run each job once: open it, choose Build with Parameters, pick an agent and start it.
4. For a Full pipeline, add the test jobs and the deployment details in Admin, or ask the DevSecOps team.
5. For an OSA pipeline, ask a developer to review and merge the golden pull requests.
6. Follow the results in Pipeline Monitoring.

When the pipelines of two or more services share one Bitbucket repository, their Jenkins agents and one Jenkins job, as the pipelines that one save starts for a repository do, the step shows one Jenkinsfile with all their keys, asks for one Jenkins job for each Jenkinsfile and says where Pipeline Monitoring shows the runs of that job. See [chapter 8](#8-shared-version-catalogs-and-the-combined-jenkinsfile).

## 5. Add a service in the admin editor

The admin editor is for the DevSecOps team. It shows every field of a service.

### A new product

1. Open Admin, Products and click "Add product".
2. The dialog "Add product" asks for the product name and the Business Service. Click Continue.
3. The editor opens with the suggested product code, the Jenkins agents of the service template and one new service.
4. Fill in the product fields: Owner team, Description and Contact e-mail are optional.
5. Open the service panel ("Edit settings") and fill in the service as in [step 3 of chapter 4](#step-3-services). A new service starts with "Set up the build tool automatically" on; a Flutter service starts with it off, because Flutter needs the JDK path, and turns it on when you choose Gradle or Maven while you have not changed the checkbox. Typing its name fills in the repository URL, the Bitbucket credentials ID, the Nexus IQ application and its scan pattern from the service template. A SQL and PL/SQL service gets no Nexus IQ application; it has no Nexus IQ section.
6. Every new service gets a Full pipeline. A service on virtual machines therefore needs an UrbanCode Deploy application for DV.
7. Click "Add product". Each service gets a Full pipeline with its own key. The product page opens with the notice "Pipeline keys generated for ..." and asks you to copy each key into the Jenkinsfile of its service; services whose pipelines share a Jenkins job share one Jenkinsfile, shown below the notice. "Show key" and "Copy key" give a key at any time.

### A service of an existing product

1. Open the product page and click "Edit product".
2. Under Services, click one of:
   - "Add service" for an empty service with the service template values;
   - "Copy values from existing service" to copy another service of the product;
   - "Import devsecops_config.yaml" to fill a new service from a file.
3. While a service still has missing fields, the buttons are disabled. The text "Complete these services before adding another:" names each service and its missing fields. Click a field name to open it.
4. Use "Duplicate service", "Move up", "Move down" and "Remove service" on a service panel as needed. Removing a stored service deletes its pipelines and keys when you save.
5. Click "Save changes". New services get a Full pipeline with their own key.

To add a pipeline of another type to a service, use "Add pipeline" on the product page. See [chapter 9](#9-pipeline-types-pipeline-keys-the-jenkinsfile-and-the-jenkins-jobs).

### SQL and PL/SQL services with Liquibase (single-module and multi-module)

Choose **Liquibase** as the Build tool (`buildTool: liquibase`) for a service that manages database schemas, DDL migrations, or stored procedures (Oracle PL/SQL, PostgreSQL PL/pgSQL, T-SQL, ANSI SQL) with Liquibase changelogs (`changelog.xml`, `changelog.yaml`, `changelog.yml`, or formatted `.sql` files).

#### Single-module SQL / PL/SQL repository

When a repository contains only database scripts and Liquibase changelogs:

- **Build (`build.liquibase.*`)**: Set the Liquibase commands (by default `updateSQL`) and flags (for example `--changelog-file=changelog.xml --url=offline:oracle`). No JDK path (`javaPath`) or automatic build tool setup (`buildToolAutoSetup`) is required unless a custom Java runtime is needed by the Liquibase CLI or JDBC drivers.
- **Unit tests and coverage (`tests.unitTests.liquibase.*`)**: Run changelog verification (`validate`, `status`) or PL/SQL unit tests (such as utPLSQL exporting JUnit XML to `tests.unitTests.unitTestResult` and JaCoCo/LCOV coverage to `coverage.reportPath`).
- **Static analysis (`tools.sonar.liquibase.*` and `asoc.liquibase.*`)**:
  - **SonarQube**: Runs `sonar-scanner` against your SQL / PL/SQL sources (`-Dsonar.sources` defaults to the Liquibase directory or `sourceDir`) so SonarQube's PL/SQL and SQL analyzers inspect packages, triggers, procedures, and views.
  - **AppScan SAST**: Packages the SQL / PL/SQL source tree into the `.irx` archive; if `asoc.liquibase.commands` is configured and "Compile before preparing the IRX" (`asoc.doCompile`) is enabled, that Liquibase command runs first (for example `updateSQL` to render SQL scripts before scanning).
  - **Nexus IQ**: Scans any JDBC drivers, Liquibase extensions, or packaged zip/jar archives matching your `scanPatterns`.

#### Multi-module and polyglot repositories (SQL / PL/SQL with Java, Gradle, Maven, Node.js, Python, or Kotlin)

In a multi-module or polyglot repository that combines SQL / PL/SQL database modules with application services (such as a Java / Kotlin backend built with Gradle or Maven, an Angular / React / Node.js frontend, or Python utilities), you can model the product in two ways:

1. **Separate services in the same product and repository (recommended)**:
   - Add one service for each application module (`buildTool: gradle` or `buildTool: maven`, optionally with **Node.js build (stage 0)** enabled for the Angular/Node.js frontend) and a dedicated database service (`buildTool: liquibase`, with `build.liquibase.dir` and `sourceDir` pointing to the database subfolder such as `db` or `sql/plsql`).
   - Give the services of the same repository the same Jenkins job so they run together from a single combined `Jenkinsfile` (`pipelineKeys: [...]`).
   - During the run, the library executes Stage 0 Node.js pre-builds for the web frontend, builds and tests the Gradle/Maven backend and the Liquibase SQL/PL-SQL module, aggregates all JaCoCo XML and LCOV coverage reports across all modules (computing per-service and overall run averages), and runs AppScan SAST, SonarQube, and Nexus IQ + GoldenFix across every module.
2. **Liquibase service with Node.js Stage 0 or Gradle/Maven wrapper**:
   - A `liquibase` service can also enable **Node.js build (stage 0)** (`build.node.enabled: true`) when the database repository uses Node.js tooling (for example SQL linters, documentation generators, or frontend assets packaged alongside migrations).
   - Conversely, if Liquibase is invoked via the `liquibase-maven-plugin` or `org.liquibase.gradle` plugin inside a Java build, keep `buildTool: maven` or `buildTool: gradle` and include the Liquibase goals/tasks (`liquibase:validate`, `liquibase:updateSQL`) in the Maven or Gradle command.

## 6. Build the frontend with Node.js first (stage 0)

Many services have a frontend, for example an Angular application, next to the Java code. The optional Node.js build runs the npm commands of that frontend in an extra stage before the Gradle, Maven, or Liquibase build. The Gradle or Maven build then packages the frontend into the jar.

### When to use it

- Use it when the Gradle, Maven, or Liquibase build needs the built frontend and does not run npm itself.
- Leave it off when the Gradle or Maven build already runs npm, for example through a Gradle Node plugin or the Maven frontend plugin.
- A Flutter service cannot use it: a Flutter build has no Gradle or Maven stage for the Node.js build to run before. Choosing Flutter switches the Node.js build off.

### The fields

The fields are in the Build section, under the heading "Node.js build (stage 0)". Only the first checkbox is needed. Every other field has a working default.

| Field | Default | Meaning |
|-------|---------|---------|
| Build the frontend with Node.js first | off | Switches the stage on for this service. |
| Folder with package.json | the repository root | The folder of the frontend, relative to the repository root. The commands run in it. A leading `./` and a trailing `/` are dropped, and `.` is the repository root. |
| Commands | `npm ci`, then `npm run build` | One shell command per line, run in order. A failing command fails the stage, and the later stages do not run. |
| Scan the npm dependencies with Nexus IQ | on | Adds the `package-lock.json` of the folder to the Nexus IQ scan, so that Nexus IQ reports the npm dependencies and GoldenFix can raise them. New findings can turn the Nexus IQ stage orange. Not shown in a SAST pipeline, which runs no Nexus IQ; the value is kept for the other pipelines. |
| Node.js installation | the Library default, else node and npm from the agent | A NodeJS installation under Manage Jenkins, Tools. It needs the NodeJS plugin of Jenkins. While a Node.js home on the agent is set, its hint says it is not used. |
| Node.js home on the agent (Advanced) | none | An absolute folder of Node.js on the agent, such as `/opt/node-20` or `C:\Program Files\nodejs`. It wins over the Node.js installation, the Library default included. Type the installation or the home, not both; the Library default installation left as prefilled does not count and is sent empty. |
| Node.js timeout (minutes) (Advanced) | 30 | How long the commands may take together. |
| npm registry (Advanced) | the Library default | The registry npm installs from, exported as `npm_config_registry`. It is not used while an .npmrc credentials ID is set; otherwise it wins over the registry line of an .npmrc in the repository. |
| .npmrc credentials ID (Advanced) | the Library default | A Jenkins Secret file credential with an .npmrc, which npm reads as its user configuration, with its registry and login. A registry line of an .npmrc in the repository still wins over it. |
| Node.js environment variables (Advanced) | none | `NAME=value` lines for the Node.js commands and for the GoldenFix verification of npm fixes, for example `NODE_OPTIONS=--max-old-space-size=4096`. |

The portal writes `build.node` to `devsecops_config.yaml` only while the checkbox is ticked. The library then reads the keys under `projects.<service>.build.node`. The Library defaults page can give every service a Node.js installation, an npm registry and an .npmrc credentials ID under `defaults.build.node`. The portal never writes `defaults.build.node.enabled`: every service switches the stage on for itself.

### What the pipelines do

- The stage is named "Node.js build". It runs right after "Monitor source changes" in the Full, Security, SAST scanning and Nexus IQ GoldenFix pipelines when at least one service of the run needs it. When no service needs it, the stage is left out and nothing is logged. The report numbers it 0, or 0.1, 0.2 and so on for several services, and Grafana shows it as Node.js.
- In SAST scanning a service needs the stage only while "Compile before preparing the IRX" is on and the agent is not Windows.
- In a run in which some service builds, each other service logs why it does not: `[NODE] <service>: build.node.enabled is not set - no Node.js build`, or in SAST scanning, for a service whose "Compile before preparing the IRX" is off, `[NODE] <service>: the SAST scan does not compile this service - no Node.js build`.
- The Extended pipeline builds the frontend again right before the release delivery, which rebuilds the artifact.
- Do not put the frontend unit tests, such as `ng test --code-coverage`, into the Commands. The commands run in every pipeline above, and a failing test would stop each of them at stage 0, the release included. Run the frontend tests from the unit test command of the Gradle or Maven build instead: they then run only in the Unit tests stage, a failure turns that stage orange, and the stage reads their LCOV report next to the JaCoCo report. On Windows agents, name both reports in Coverage reports (see Unit tests and coverage).
- The log names where Node.js comes from, for example `[NODE] gateway: Node.js from installation NodeJS-20 in console: npm ci && npm run build`.
- After a Node.js build, the AppScan static scan leaves out `node_modules`. This applies on Linux agents while no included folder is set. The minified bundles of the built frontend are still scanned.

### Package the frontend into the jar

The library does not copy the frontend into the artifact. The build of the service must do it. Follow three rules:

1. Write the frontend output outside the `build` and `target` folders, for example to `frontend/dist`. The Gradle or Maven build usually starts with `clean`, which deletes those folders after the Node.js build.
2. Copy the output into the resources of the jar, as in the snippets below.
3. On OpenShift, the image build receives only the artifact, the Dockerfile and one added file. The frontend therefore reaches OpenShift only inside the jar.

Set the output folder in `frontend/angular.json`, for example `"outputPath": "dist/app"`. Angular then writes the files to `frontend/dist/app/browser`.

Gradle, Groovy DSL (`build.gradle`):

```groovy
tasks.named('processResources') {
    from('frontend/dist/app/browser') {
        into 'static'
    }
}
```

Gradle, Kotlin DSL (`build.gradle.kts`):

```kotlin
tasks.named<ProcessResources>("processResources") {
    from("frontend/dist/app/browser") {
        into("static")
    }
}
```

Maven (`pom.xml`): list the normal resources folder too, because the `resources` element replaces the default list.

```xml
<build>
  <resources>
    <resource>
      <directory>src/main/resources</directory>
    </resource>
    <resource>
      <directory>frontend/dist/app/browser</directory>
      <targetPath>static</targetPath>
    </resource>
  </resources>
</build>
```

Spring Boot serves the files under `static` at the root of the application. Both snippets accept a missing folder. This matters for GoldenFix: its verification build runs in a fresh copy of the repository without the Node.js output.

### Nexus IQ and GoldenFix for npm

- With "Scan the npm dependencies with Nexus IQ" on, the library adds `<folder>/package-lock.json` to the scan patterns of the Nexus IQ application of the service. It never creates a scan for a service without scan patterns.
- GoldenFix verifies an npm fix inside the Node.js environment of the service: the same Node.js, registry and .npmrc. With the default npm command, it first refreshes a `package-lock.json` next to the `package.json` with `npm install --package-lock-only --ignore-scripts` and commits the refreshed file in the pull request. An npm command set in the GoldenFix section of the service runs as it is, without that refresh.
- GoldenFix also raises npm `overrides` and yarn `resolutions` entries that pin a vulnerable version.

## 7. SQL and PL/SQL changes with Liquibase

Many products keep Oracle SQL and PL/SQL code in a repository: tables, views, packages, triggers and types. A Liquibase changelog lists the changes of that code. The portal sets up such code as a service with the build tool "SQL and PL/SQL (Liquibase)", written as `buildTool: liquibase`. The pipelines then check the changes, install them in a test database for the utPLSQL tests, scan the source files with AppScan and SonarQube, package them into a zip in Nexus and deploy them to the RD and QC databases. They never deploy to production and never roll a database back.

This chapter describes the portal steps. The guide of the DevSecOps library DSOEnhanced describes every stage, message and setting of the library in its chapter 14, "Integration Guide: SQL and PL/SQL with Liquibase".

| Word | Meaning |
|------|---------|
| Liquibase | The tool that records which changes a database already has and applies the others. |
| Changelog | The file that lists the changes, in XML, YAML, JSON or formatted SQL. It may include other files, such as one SQL file for each package. |
| Changeset | One change of the changelog, with an author and an ID. Liquibase records every applied changeset in the table `DATABASECHANGELOG` of the database. |
| Context | A label of a changeset that a deployment can select or leave out. Test packages carry the context `test`. |
| utPLSQL | The unit test framework of PL/SQL. Its test packages run inside the database. |
| Test database | A schema that only the pipelines use, for the utPLSQL tests. RD and QC are the lower and the higher test region. |
| Package (zip) | The zip of the changelog and the files it includes, which the pipelines upload to Nexus. It is not a PL/SQL package. |

### When a service is a SQL and PL/SQL service

One rule decides which build tool a folder gets:

- A folder of Liquibase changelogs is its own SQL and PL/SQL service. This holds also when the folder is a module of a Gradle or Maven multi-module build.
- A Gradle or Maven service whose build applies a Liquibase plugin stays a Gradle or Maven service. The pipelines never deploy its changesets. To have them deployed by the pipelines, move the changelog folder into a SQL and PL/SQL service of its own.

The three usual repositories give these services:

| Repository | Services, in this order | Example product |
|------------|-------------------------|-----------------|
| Only SQL and PL/SQL, with the changelog `db/changelog-master.xml` | `orders-db`: SQL and PL/SQL (Liquibase), Source folder `.` | `ORDERSDB` |
| A Gradle multi-module build with the Liquibase module `orders-db` and a Node.js frontend in `frontend` | `orders-db`: SQL and PL/SQL, Source folder `orders-db`, Search folder `src/main/resources`; then `orders-app`: Gradle, Source folder `.`, AppScan excludes `orders-db`, Node.js build of `frontend` | `ORDERSGRADLE` |
| Independent folders: `orders-db` with a YAML changelog, a Maven API in `api` and a Node.js frontend in `web` | `orders-db`: SQL and PL/SQL, Source folder `orders-db`; then `orders-api`: Maven, Directory `api`, AppScan excludes `orders-db`, Node.js build of `web` | `ORDERSMAVEN` |

Follow these rules in every repository:

- **Add the SQL and PL/SQL service first.** The services of one repository run in their order, so the database changes reach RD before the application that needs them. The library warns when a database service comes after an application of the same run.
- **Give every folder to exactly one AppScan scope.** Add the database folder to "Excluded folders" (`excludedDirs`) of the Java service. AppScan compares an excluded folder with every path as a part of it: `db` would also leave out `src/main/java/com/bbh/orders/db/`. Give the database folder a distinctive name with a hyphen, such as `orders-db`. A Java package name cannot contain a hyphen.
- **Keep Spring Boot away from the changelog.** A Spring Boot application with `liquibase-core` on its classpath runs the changelog itself when it starts, with its own database user, and then competes with the pipelines in RD and QC. Set `spring.liquibase.enabled=false` in the application, or keep the changelog out of its jar.
- **Use Linux or macOS agents.** A SQL and PL/SQL service does not run on Windows agents. The portal does not check the operating system behind an agent label, so give every pipeline of the service the labels of Linux or macOS agents. The services of a combined Jenkinsfile share their agents.
- **Use Oracle.** The pipelines support Oracle databases only.

### Before you add a SQL and PL/SQL service

Four parties prepare a SQL and PL/SQL service.

**The team of the product** provides:

- the path of the changelog file, relative to its search folder, such as `db/changelog-master.xml`;
- the JDBC URL of each database the pipelines deploy to: the test database, RD and QC;
- the Maven group ID of the package in Nexus, such as `com.bbh.orders`.

**The DBA** provides, for each database:

- A schema and a deploy user. The best deploy user is the owner of the schema, or a proxy user that connects as the owner. For a proxy user the DBA runs `ALTER USER ORDERS GRANT CONNECT THROUGH deployer`, and the Jenkins credential holds the user name `deployer[ORDERS]`. A deployment then works as the owner and needs no default schema.
- For a deploy user that is not the owner: the privileges `ALTER ANY PROCEDURE`, `ALTER ANY TRIGGER` and `ALTER ANY TYPE`, and read access to the rows of the schema in `ALL_OBJECTS` and `ALL_ERRORS`. The check for invalid objects after each deployment needs them.
- `CREATE TABLE` in the Liquibase schema, where Liquibase keeps its tables `DATABASECHANGELOG` and `DATABASECHANGELOGLOCK`.
- For the test database: one test schema for each pipeline, in a database with the utPLSQL framework installed. Two branches or jobs that share a test schema see each other's changes.
- For coverage of code that another schema owns: `CREATE ANY PROCEDURE` for the test user. Code compiled as `NATIVE` has no coverage.
- Network access from the Jenkins agents to the database ports, usually 1521, or 2484 for TCPS.

**The DevSecOps team** sets, in the Library defaults (see [Library defaults for SQL and PL/SQL services](#library-defaults-for-sql-and-plsql-services)):

- the allowed databases. Until the list allows the host of a database, no service can be saved with its JDBC URL;
- where the agents find Liquibase, the Oracle JDBC driver and utPLSQL-cli;
- the Nexus repositories of the database packages.

**The Jenkins administrator** adds, in the Jenkins folder of the product, one credential of the kind *Username with password* for each database, with the user and the password of its deploy user. The folder holds no production credentials.

### Add a SQL and PL/SQL service in the wizard

1. In Step 1, choose the product. In Step 2, choose the Full, Security or SAST pipeline. The OSA pipeline (Nexus IQ GoldenFix) takes no SQL and PL/SQL service: it scans open source dependencies, which such a service does not have.
2. In Step 3, click "Add a service". In a repository with Java and database code, add the SQL and PL/SQL service first.
3. In General, type the service name, such as `orders-db`. The service template fills in the Bitbucket repository URL. It gives a SQL and PL/SQL service no Nexus IQ application.
4. In Build, choose "SQL and PL/SQL (Liquibase)". Its hint reads "The code is SQL and PL/SQL with a Liquibase changelog, such as db/changelog-master.xml". Type the Source folder: the folder of the changelogs, such as `orders-db`, or `.` when the repository holds only the database code. Leave "Set up the build tool automatically" on: it selects a JDK of the Java version in the Library defaults, 17 unless the DevSecOps team set another. With it off, type the JDK path of a JDK 17 or newer on the agent.
5. Open Database changes. Type the Changelog file, such as `db/changelog-master.xml`. For a module of a Gradle or Maven build, also type the Search folder, such as `src/main/resources`.
6. Under "Test database, for the utPLSQL tests", "RD, the lower test region" and "QC, the higher test region", type the JDBC URL and the Credentials ID of each database the pipelines deploy to. Leave a database empty when the pipelines do not deploy to it.
7. To run the utPLSQL tests, tick "Run the utPLSQL tests" under the test database. Type the "Folder of the packages" and the "Folder of the test packages", such as `db/code` and `db/test`.
8. Under "Package and Nexus delivery", type the Group ID, such as `com.bbh.orders`. The Full and Security pipelines need it while "Deliver the package to Nexus" is ticked. While the Library defaults name no Nexus repositories for database packages, the repository fields appear here as well and need values; otherwise they are under Advanced settings.
9. In AppScan SAST and DAST, type the AppScan application ID and the AppScan API key ID, as for every service.
10. For the Security and Full pipelines, type the SonarQube project key when the SonarQube server analyses PL/SQL.
11. When nothing is missing, click "Add service".

The wizard checks the database settings while you type. A JDBC URL that the Library defaults do not allow, a test database that is also the RD or QC database, and a QC context filter that selects the test changesets are refused under their field; [When a SQL and PL/SQL service stops](#when-a-sql-and-plsql-service-stops) lists every message.

Under each SQL and PL/SQL service the list of Step 3 shows "SQL and PL/SQL changes with Liquibase" and its repository. With the OSA pipeline, Step 3 says "A Nexus IQ GoldenFix pipeline scans open source dependencies, which SQL and PL/SQL services do not have. Remove these services from this pipeline:" followed by their names, and Next stays disabled.

The Review step warns when a SQL and PL/SQL service comes after a Gradle or Maven service of the same repository: "It comes after `<service>` of the same repository, but the services of one repository run in their order, so the database changes reach RD before the application that needs them. Ask the DevSecOps team to move it up in the admin editor." The wizard cannot change the order of saved services.

### Add a SQL and PL/SQL service in the admin editor

1. Open the product page and click "Edit product". Under Services, click "Add service". For a new product, use "Add product" as in [chapter 5](#a-new-product).
2. Fill in the service as in [Add a SQL and PL/SQL service in the wizard](#add-a-sql-and-plsql-service-in-the-wizard). The admin editor shows the sections General, Build, Database changes, Test jobs, AppScan SAST and DAST, SonarQube, Bitbucket and Monitoring. A SQL and PL/SQL service has no Unit tests and coverage, Deployment, UrbanCode Deploy, SSH targets, OpenShift targets, Nexus IQ, GoldenFix or Flutter section.
3. Every new service gets a Full pipeline. A service that delivers its package to Nexus therefore needs its group ID and the Nexus repositories.
4. When the service comes after a Gradle or Maven service of the same repository, its panel says "Move this service up, before `<service>`: the services of one repository run in their order, so the database changes reach RD before the application that needs them." Click "Move up" until the note disappears.
5. Click "Save changes". The services of one repository that share a Jenkins job get one combined Jenkinsfile, with their keys in the order of the services.

Before you save, a service whose build tool changes to SQL and PL/SQL keeps its other values in the form, hidden, so a change back loses nothing. The save drops what a SQL and PL/SQL service does not use, such as its build command, its Node.js build, its Nexus IQ application and its deployment targets. A service with a Nexus IQ GoldenFix pipeline cannot change to SQL and PL/SQL: delete that pipeline first.

### The fields of a SQL and PL/SQL service

**Build.** A SQL and PL/SQL service has no build command, no artifact path, no Node.js build and no Advanced settings in Build: the pipelines package the changelog and the files it includes into a zip.

| Field | Default | Meaning |
|-------|---------|---------|
| Build tool | the build tool of the Library defaults | Choose "SQL and PL/SQL (Liquibase)". |
| Source folder | `.`, the repository root | The folder of the SQL and PL/SQL code, such as `orders-db`. The AppScan scan and the search folder start from it. Choose it once, before the first deployment: Liquibase records every change under a path that depends on this folder and on the search folder, so a later change makes Liquibase treat every change as new. |
| Set up the build tool automatically | on | Selects a JDK of the Java version of the Library defaults, 17 unless set there, installed on the agent. |
| JDK path | none | Shown while the automatic setup is off, and then required: the absolute path of a JDK 17 or newer on the agent. Liquibase and utPLSQL run with it. |

**Database changes.** The section starts with the changelog:

| Field | Default | Meaning |
|-------|---------|---------|
| Changelog file | none, required | The main Liquibase changelog, relative to the search folder. It ends with `.xml`, `.yaml`, `.yml`, `.json` or `.sql`. Its path is part of the identity of every change in the database, so it must not change after the first deployment. |
| Search folder | `.`, the source folder | Liquibase looks for the changelog and every file it includes in this folder, relative to the source folder, and reads nothing outside it. Like the changelog path, it must not change after the first deployment. |
| Changelog parameters | none | Values that the changelog uses as `${name}`, one `name=value` per line, such as `app_role=orders_app_role`. They are written into the package, so they must not hold a secret: a name that contains password, passwd, pwd, secret, token or credential is refused. |

Each database has a block of its own: "Test database, for the utPLSQL tests" (`database.test`), "RD, the lower test region" (`database.rd`) and "QC, the higher test region" (`database.qc`).

| Field | Default | Meaning |
|-------|---------|---------|
| JDBC URL | none | The Oracle thin URL of the database, without a user or a password. Leave it empty when the pipelines do not deploy to this database. The forms and the allowed databases are described below the table. |
| Credentials ID | none | The ID of the *Username with password* credential of the deploy user, in the Jenkins folder of the product. Required with the JDBC URL. The portal never sees the password. |

The JDBC URL takes one of four forms:

- `jdbc:oracle:thin:@//rd-db.bbh.com:1521/ORDERS_RD`, a host, a port and a service name;
- `jdbc:oracle:thin:@tcps://qc-db.bbh.com:2484/ORDERS_QC?ssl_server_dn_match=true`, the same over TCPS;
- `jdbc:oracle:thin:@rd-db.bbh.com:1521:ORDERS`, a host, a port and a SID;
- `jdbc:oracle:thin:@(DESCRIPTION=...)`, a connect descriptor.

Several hosts may be separated by commas. TNS aliases, Oracle wallets and IPv6 addresses are not supported. Every host of the URL must be allowed by the Library defaults. The test database must be a database of its own, never the RD or QC database: it receives the test packages.

The test database block also holds:

| Field | Default | Meaning |
|-------|---------|---------|
| Empty the test schema before each deployment | off | Drops every object of the test schema before the changes are installed, so that the changes of other branches do not remain. Tick it only for a schema that holds nothing but the test objects of this service. Needs the JDBC URL of the test database. |
| Run the utPLSQL tests | off | After the changes are installed in the test database, runs the utPLSQL test packages there and reads their results and coverage. Needs the JDBC URL of the test database, with the utPLSQL framework installed. |
| Test suites | every suite of the owner of the tests | The suites or packages to run, separated by commas, such as `orders_tests`. |
| Owner of the tests | the default schema of the test database, else the schema of the database user | The schema that owns the packages and their tests. |
| Folder of the packages | none | Relative to the search folder, such as `db/code`. The folder must exist in the repository. The coverage report then names the source files. |
| Folder of the test packages | none | Relative to the search folder, such as `db/test`. The folder must exist in the repository. SonarQube then treats these files as tests. |
| utPLSQL timeout (minutes) | 30 | How long the tests may take together, from 1 to 240 minutes. |

"Package and Nexus delivery" holds:

| Field | Default | Meaning |
|-------|---------|---------|
| Deliver the package to Nexus | on | Ticked, the Full and Security pipelines upload the zip of the changes to the snapshot repository, the Full and Extended pipelines release it after the release gate, and the Extended pipeline deploys the zip that the last delivery uploaded. Unticked, nothing is uploaded, and each pipeline deploys the changes of its own checkout. |
| Group ID | none | The Maven group ID of the zip in Nexus, such as `com.bbh.orders`. Shown while the package is delivered. The Full, Security and Extended pipelines need it. |
| Artifact ID | the service name | Names the zip and the tag of each deployment, also when the package is not delivered to Nexus. |
| Snapshot repository ID and URL, Release repository ID and URL | the Library defaults | The Nexus repositories of the zip. A field is shown here while the Library defaults have no value for it, and under Advanced settings otherwise. The ID is the server ID in the Maven settings of the agents, which supplies the upload credentials. |

Advanced settings, with the summary "Maven home, contexts, labels, schemas, the checks after a deployment, the options of each database and SonarQube", holds:

| Field | Default | Meaning |
|-------|---------|---------|
| Maven home | `mvn` on the path of the agent | Shown while the package is delivered. The folder that holds `bin/mvn`, which uploads and fetches the zip. |
| Oracle version | 19.0 | The Oracle version that the offline check of the changelog assumes. Use the version of RD and QC, such as 19.0 or 23.0. |
| Contexts | `!test` | The Liquibase context filter of RD and QC. `!test` leaves out the changesets with the context `test`, which only the test database receives. QC must never receive them. |
| Labels | every label | A Liquibase label filter for every deployment. |
| Lock wait (minutes) | 5 | How long a deployment waits for another deployment to release the Liquibase lock of the database, from 0 to 60 minutes. |
| Default schema | the schema of the database user | The schema of the objects that the changes name without a schema. Every step that connects to a database uses it. |
| Liquibase schema | the default schema | The schema of the tables in which Liquibase records the deployed changes and its lock. Every step that connects to a database uses it. |
| Require a rollback for every change | off | Ticked, the Unit tests stage checks that every change can be rolled back, and a change without a working rollback turns the stage orange. |
| Tag each deployment | on | After a deployment that applied changes, tags the RD or QC database with the artifact ID and the version. A DBA can roll the database back to a tag by hand. That undoes the changes that have a rollback; PL/SQL code keeps its new version, because its changes have an empty rollback. |
| Check for invalid objects after a deployment | on | After each deployment, lists the invalid PL/SQL objects of the schema. An object that the deployment made invalid fails the stage; objects that were invalid before it are only listed. |
| Recompile invalid objects first | on | Before the check, recompiles the invalid objects of the schema with their own compiler settings, so that only the objects that stay invalid are listed. |
| Objects the check ignores | none | One object name per line, at most 50, such as an old view that is invalid on purpose. |
| Files the static checks ignore | none | One path per line, relative to the search folder, at most 50. The checks for passwords and unsafe commands still read these files. |

Under Advanced settings each database has its options too, under the same headings `database.test`, `database.rd` and `database.qc`:

| Field | Default | Meaning |
|-------|---------|---------|
| Contexts | `test` for the test database; the Contexts of the service for RD and QC | The context filter for this database. QC must not select `test`. |
| Labels | the Labels of the service | |
| Default schema | the value of the service | |
| Liquibase schema | the value of the service | |
| Changelog parameters | none | Values for this database only. A name set here replaces the value of the service for this database. |
| Timeout (minutes) | 30 | How long each step that connects to this database may take, from 1 to 240 minutes. |

"SonarQube analysis of SQL and PL/SQL", the last block of Advanced settings, holds Sources, the folders SonarQube analyses, by default the folder the package is made of, and SonarScanner version. A SonarScanner version selects the Jenkins tool installation named `sonar-scanner-cli-` followed by the version; left empty, the `sonar-scanner` on the path of the agent runs.

**The other sections.** Test jobs works as for every service, without the "Required" box: a test stage without jobs is not required, so it never turns the run orange. AppScan SAST and DAST sends the source files to the static scan without compiling them, so it has no "Compile before preparing the IRX", no "Scan source code only" and no dynamic scan. SonarQube has no analysis command: the analysis runs only when the SonarQube server has a PL/SQL analyzer and the service has a project key. Bitbucket and Monitoring work as for every service.

### What the pipelines do with a SQL and PL/SQL service

| Stage | What happens for a SQL and PL/SQL service | Full | Security | Extended | SAST |
|-------|-------------------------------------------|------|----------|----------|------|
| Monitor source changes | Checks the settings of the service and, in the Full and Security pipelines, that the changelog file and the utPLSQL folders exist. A wrong setting stops the run with a message that names its key. | yes | yes | yes | yes |
| Node.js build | Never runs for a SQL and PL/SQL service. | | | | |
| Unit tests | Checks the changelog offline, runs the static checks of the source files, writes the SQL that RD and QC would receive, and builds and checks the zip. With a test database, installs the changes there with the context `test`, after emptying the schema when "Empty the test schema" is ticked; with utPLSQL, runs the tests and reads their results and coverage. Without a test database or without utPLSQL, the coverage is not required. | yes | yes | | |
| Dependencies scan (Nexus IQ) | Not required: the service has no open source dependencies. | yes | yes | | |
| SAST | AppScan scans the SQL and PL/SQL source files without compiling them. | yes | yes | | yes |
| SCA (SonarQube) | Runs the SonarScanner when the SonarQube server analyses PL/SQL and the service has a project key; otherwise not required. | yes | yes | | |
| Nexus delivery (Static analysis passed) | Uploads the zip to the snapshot repository. Not required while "Deliver the package to Nexus" is unticked. | yes | yes | | |
| Lower test region deployment | Deploys to RD: lists the pending changes, runs the update with the contexts of RD, checks for invalid objects, checks that nothing is left pending and tags the database. Not required without an RD JDBC URL. | yes | | yes | |
| Regression, Smoke, Performance tests | The test jobs of the service; not required without jobs. | yes | | yes | |
| DAST | Not required. | yes | | yes | |
| Nexus delivery - Safe Artifact | After the release gate allows it, uploads the same zip to the release repository. | yes | | yes | |
| Higher test environment deployment | Deploys to QC as to RD, only with the run parameter `DEPLOY_HIGHER_ENV` and after the release gate allows it. Not required without a QC JDBC URL. | yes | | yes | |

- **Security** runs up to the snapshot delivery and can then start its Extended pipeline, as for every service.
- **Extended** fetches the zip that its Security pipeline delivered last, deploys exactly that zip to RD and QC, and releases it. With "Deliver the package to Nexus" unticked, it packages the changes of its own checkout instead.
- **SAST** checks the settings and runs the static scan. It runs no Liquibase step, so its agents need no Liquibase.
- **Nexus IQ GoldenFix** is refused for a SQL and PL/SQL service: the portal does not offer it in the pipeline dialog, and the API answers 409 Conflict.

**The version and the package.** Each run that packages the changes gives them a version made of the build number and the UTC time of the build, such as `341-20261031-101500`. The zip is named `<artifact ID>-<version>.zip`, such as `orders-db-341-20261031-101500.zip`. The snapshot delivery uploads it as `com.bbh.orders:orders-db:341-20261031-101500-SNAPSHOT`, the release delivery as `com.bbh.orders:orders-db:341-20261031-101500`, and the tag of RD and QC is `orders-db-341-20261031-101500`. The zip holds the first folder of the changelog path, `db` for `db/changelog-master.xml`, or the whole search folder when the changelog has no folder. It holds no `liquibase*.properties` file. At its root it holds the manifest `dso-deploy.properties`, which names the changelog, the context filter, the label filter and the schemas of QC, and the names of the changelog parameters. The manifest holds no URL, user or password.

**What the delivery needs.** While "Deliver the package to Nexus" is ticked, each pipeline type needs:

| Pipeline | Group ID | Snapshot repository ID and URL | Release repository ID and URL |
|----------|----------|--------------------------------|-------------------------------|
| Full | required | required | required |
| Security | required | required | not needed |
| Extended | required | required, to fetch the delivered zip | required |
| SAST | not needed | not needed | not needed |

Each repository value is taken from the service, else from the Library defaults. The form names the first missing item under its field: "Add the group ID of the package, or untick Deliver the package to Nexus: the Full pipeline delivers it", or "Add the snapshot repository ID, set it in the Library defaults (SQL and PL/SQL), or untick Deliver the package to Nexus". A save through the API is refused with 409 Conflict and, for example, "Service orders-db delivers its database package to Nexus in the Full pipeline but has no group ID. Set it under Database changes, or untick Deliver the package to Nexus".

### Rules for the changelog of a SQL and PL/SQL service

The pipelines deploy what Liquibase Community deploys. The library guide explains each rule in its chapter 14, "Writing a changelog for PL/SQL".

1. Put one PL/SQL unit in each file, deployed by a `sqlFile` changeset with `splitStatements="false"` and `stripComments="false"`, and end the file with a `/` on a line of its own. A file with the package specification and body together also works with `endDelimiter="\n/"`.
2. Mark the changesets of code objects (packages, views, triggers, types) `runOnChange="true"` and write the code as `CREATE OR REPLACE`. A change of white space only does not run the changeset again.
3. Write tables in formatted SQL, one DDL statement for each changeset, each with a `--rollback`. Oracle commits each DDL statement on its own, so a changeset with two of them that fails halfway is left half applied. Never edit an applied changeset: add a new one.
4. A formatted SQL changeset that holds a PL/SQL unit needs `splitStatements:false` and `stripComments:false` on its `--changeset` line. Formatted SQL removes comments by default, and the utPLSQL annotations are comments.
5. Views and plain SQL keep the default splitting.
6. Use no SQL*Plus commands, such as `SET`, `SHOW ERRORS`, `@file`, `PROMPT` or `EXIT`.
7. `includeAll` does not pick up `.pks`, `.pkb`, `.vw` or `.trg` files. Give every code file a `sqlFile` changeset.
8. Give the test packages `context="test"`. RD and QC use the filter `!test`, and QC must not deploy them.
9. Keep the identity of every change from the first deployment on: the changelog path relative to the search folder, a `logicalFilePath` and the format of the main changelog must not change afterwards.
10. Write a main changelog that includes other files in XML, YAML or JSON. Liquibase Community has no `include` in formatted SQL.
11. Give code changesets an empty `<rollback/>`, so that the check of "Require a rollback for every change" works. A rollback then leaves the new code in place.
12. Put no password and no agent commands in a changeset. Values that differ between databases are changelog parameters, written `${app_role}` in the changelog.
13. `runAlways` changesets, such as grants and synonyms, run on every deployment, so every deployment is tagged.

### The first deployment to an existing schema

The first deployment to a schema that already holds the objects of the changelog fails: `CREATE TABLE` of a table that exists ends with ORA-00955. Choose one of two ways before that deployment:

- **The DBA marks the changes as applied, once, for each database.** The DBA runs `changelog-sync` (or first `changelog-sync-sql`, to read what it records) with the delivered zip as the search path, and with the changelog and the contexts of the manifest `dso-deploy.properties` in the zip, as in [Production deployments from the package](#production-deployments-from-the-package).
- **The table changesets skip existing tables.** Give each table changeset the precondition `<preConditions onFail="MARK_RAN"><not><tableExists tableName="ORDERS"/></not></preConditions>`. Liquibase then records it as applied when the table exists.

The pipelines never run `changelog-sync`.

### A repository with Java, Node.js and a database

The product `ORDERSGRADLE` keeps a Gradle multi-module build in one repository. The module `orders-db` holds the changelog `orders-db/src/main/resources/db/changelog/db.changelog-master.xml`, and Node.js builds the frontend in `frontend`. The product has two services in this order:

1. `orders-db`: SQL and PL/SQL (Liquibase), Source folder `orders-db`, Search folder `src/main/resources`, Changelog file `db/changelog/db.changelog-master.xml`.
2. `orders-app`: Gradle, Source folder `.`, Excluded folders `orders-db`, Node.js build of `frontend`.

The settings sent to Jenkins hold, shortened:

```yaml
projects:
  orders-db:
    buildTool: liquibase
    sourceDir: orders-db
    buildToolAutoSetup: true
    dast:
      enabled: false
    database:
      changelogFile: db/changelog/db.changelog-master.xml
      searchPath: src/main/resources
      package:
        groupId: com.bbh.orders
      test:
        url: jdbc:oracle:thin:@//dev-db.bbh.com:1521/ORDERS_CI
        credentialsId: orders-db-test
        utplsql:
          enabled: true
          sourcePath: db/changelog/code
          testPath: db/changelog/test
      rd:
        url: jdbc:oracle:thin:@//rd-db.bbh.com:1521/ORDERS_RD
        credentialsId: orders-db-rd
      qc:
        url: jdbc:oracle:thin:@//qc-db.bbh.com:1521/ORDERS_QC
        credentialsId: orders-db-qc
  orders-app:
    buildTool: gradle
    deployTarget: vm
    sourceDir: .
    excludedDirs: orders-db
    build:
      gradle:
        tasks:
        - clean
        - build
      node:
        enabled: true
        dir: frontend
```

The SQL and PL/SQL service has no `deploy`, `delivery`, `goldenFix` or `tools.nexusIq` settings, and `orders-app` keeps every setting of a Gradle service, its UrbanCode Deploy application included. Both services name the same Bitbucket repository, so they share one Jenkins job and one combined Jenkinsfile, with the key of `orders-db` first:

```groovy
@Library('DevSecOpsJenkinsLibrary') _

devSecOpsPipeline(pipelineKeys: [
    '<pipeline key of orders-db>',
    '<pipeline key of orders-app>',
])
```

A Full run then builds and tests both services, scans both, deploys the database changes to RD and then the application, and releases both after the release gate.

The product `ORDERSMAVEN` keeps independent folders: `orders-db` with the YAML changelog `orders-db/changelog-master.yaml`, a Maven API in `api` and its frontend in `web`. Its services are `orders-db` (SQL and PL/SQL, Source folder `orders-db`, Changelog file `changelog-master.yaml`) and `orders-api` (Maven, Source folder `.`, Excluded folders `orders-db`, Directory `api`, Node.js build of `web`), in this order. That changelog has no folder, so the zip holds the whole folder `orders-db`. The service `orders-api` writes `build.maven.dir: api`: Maven runs in the folder that holds the `pom.xml`. The AppScan scope of `orders-api` is the repository without `orders-db`, so `api` and `web` are scanned once by `orders-api`, and the SQL and PL/SQL code once by `orders-db`.

### Database credentials stay in Jenkins

- The portal stores only the credentials IDs of the databases. It has no field for a user or a password.
- A JDBC URL with a user or a password, such as `jdbc:oracle:thin:scott/tiger@//rd-db.bbh.com:1521/ORDERS_RD` or one with `password=`, is refused when the product is saved.
- No message of the portal repeats a JDBC URL, a changelog parameter value or a credentials ID.
- Changelog parameters are written into the package, so a name that looks like a secret is refused.
- The import of a devsecops_config.yaml never takes a database password, a JDBC URL with a user or a password, or a production database; see [chapter 10](#10-import-a-devsecops_configyaml-and-copy-a-service).
- The pipelines pass the password to Liquibase and utPLSQL without writing it to the console, the report or the archived files.

### Library defaults for SQL and PL/SQL services

The section "SQL and PL/SQL (Liquibase)" of the Library defaults page has five blocks. Every value applies to every SQL and PL/SQL service. A tool left empty is taken from the path of the agent.

| Block | Field | Meaning |
|-------|-------|---------|
| Liquibase | Liquibase folder on the agent | The folder that holds the `liquibase` launcher, such as `/opt/liquibase/4.33.0`. It wins over the download. |
| Liquibase | Java version | The JDK version that a service with automatic build tool setup gets, from 17 to 25. Empty: 17. |
| Liquibase | Liquibase download, Its SHA-256 | The Nexus address of a Liquibase `.tar.gz`, `.tgz` or `.zip`, and the 64 lower-case hexadecimal digits of its SHA-256. A file with another checksum is deleted and the run stops. |
| Oracle JDBC driver | Driver on the agent | The absolute path of an `ojdbc11` or `ojdbc17` jar. It wins over the download. Liquibase 5 and a trimmed Liquibase 4.33 need it. |
| Oracle JDBC driver | Driver download, Its SHA-256 | The Nexus address of the driver jar and its SHA-256. |
| utPLSQL | utPLSQL-cli folder on the agent | The folder that holds `bin/utplsql`. Only services that run utPLSQL tests need utPLSQL-cli 3.2.2. |
| utPLSQL | utPLSQL-cli download, Its SHA-256 | The Nexus address of `utPLSQL-cli.zip` and its SHA-256. |
| Allowed databases | Allowed databases | The databases the pipelines may deploy to, one per line. An empty list allows none. |
| Nexus repositories of the database packages | Snapshot repository ID and URL, Release repository ID and URL | Where the Full, Security and Extended pipelines upload and fetch the zips, for every service that names none of its own. |

**Which Liquibase.** Installing Liquibase 4.33.0 on the agents is recommended. Its core is under the Apache-2.0 licence, but its download also holds commercial components under the Liquibase Pro licence (`internal/lib/liquibase-commercial.jar` and the folder `internal/extensions`) and JDBC drivers under their own licences. A trimmed 4.33.0 removes the commercial components and the bundled drivers and keeps `LICENSE`; it then needs the Oracle JDBC driver of the Library defaults. Liquibase 5.0 is under the Functional Source License FSL-1.1-ALv2, which becomes Apache-2.0 two years after each release: internal use is allowed, offering Liquibase itself as a competing product is not. Use Liquibase 5.0 only after the BBH open-source office has accepted FSL-1.1-ALv2. It ships no JDBC driver, so it needs the Oracle JDBC driver too. utPLSQL-cli is under Apache-2.0, and the Oracle drivers under the Oracle Free Use Terms and Conditions.

| File | SHA-256 |
|------|---------|
| Liquibase 4.33.0, `.tar.gz` from GitHub | `689acfcdc97bad0d4c150d1efab9c851e251b398cb3d6326f75e8aafe40ed578` |
| Liquibase 5.0.4, `.tar.gz` from GitHub | `bb08638d70dddd6af8cca6e033148576f8214b5996831b1f8864eb3528f4cfce` |
| utPLSQL-cli 3.2.2, `utPLSQL-cli.zip` from GitHub | `15a3addd921b7f2e415537b47c18a4a9d3e0b70ff0e403943fd4c03b0ba02006` |
| ojdbc11 23.26.4.0.0, jar from Maven Central | `df977e9291499cd4ceee7d577462c285987dd2ad0216637c0865eb98956a9e6e` |

**How the downloads reach Nexus.** The Jenkins agents reach only the BBH network. The DevSecOps team therefore downloads the release assets of Liquibase and utPLSQL-cli from GitHub and the driver jar from Maven Central, and uploads them to a raw hosted Nexus repository, for example as `https://tools.bbh.com/nexus/repository/raw-tools/utplsql/utPLSQL-cli/3.2.2/utPLSQL-cli.zip`. It enters the Nexus address and the SHA-256 of the original file, which the copy in Nexus keeps. A trimmed 4.33.0 is a new file with its own SHA-256, computed with `sha256sum liquibase-4.33.0-trimmed.tar.gz`.

**The allowed databases.** Each line is a host or a host and a service, such as `rd-db*.bbh.com` or `qc-db.bbh.com/ORDERS_QC`. In a line, `*` stands for any run of letters, digits, dots and hyphens. The comparison ignores upper and lower case. A JDBC URL is allowed when every host of the URL matches a line, and that line names no service or names the service of the URL.

| Lines | JDBC URL | Allowed |
|-------|----------|---------|
| `rd-db*.bbh.com` | `jdbc:oracle:thin:@//RD-DB1.bbh.com:1521/ORDERS_RD` | yes |
| `qc-db.bbh.com/ORDERS_QC` | `jdbc:oracle:thin:@//qc-db.bbh.com:1521/ORDERS_RD` | no: another service |
| `rd-db1.bbh.com` | `jdbc:oracle:thin:@rd-db1.bbh.com:1521,rd-db2.bbh.com:1521/ORDERS` | no: `rd-db2` is not allowed |
| `*.rd.bbh.com` | `jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCPS)(HOST=a.rd.bbh.com)(PORT=2484))(CONNECT_DATA=(SERVICE_NAME=ORDERS)))` | yes |
| none | any | no |

The portal checks every JDBC URL against the list when a product is saved, and the library checks it again at every run. The list is a guard against mistakes, not an access control: an IP address or a name that is not listed is refused, but a listed name that points to production is not detected. The real control is that the Jenkins folder of a product holds no production credentials. A line removed later is not checked against the saved services at that moment: the next run of such a service stops, and the next save of its product is refused.

### A database service in a Jenkinsfile without a key

A Jenkinsfile without a key reads devsecops_config.yaml from the repository and takes the Library defaults from the copy kept in the library (see [A Jenkinsfile without a key](#a-jenkinsfile-without-a-key)). That copy holds the SQL and PL/SQL block with empty values in this release. Until the DevSecOps team ships the allowed databases in a library release, a SQL and PL/SQL service without a key therefore cannot deploy to any database. Such a service needs:

- no `database.test`, `database.rd` or `database.qc` block. Remove them from the downloaded file: a JDBC URL that the copy does not allow stops the run at its start. Without them, the deployment stages are not required;
- Liquibase, and utPLSQL-cli when tests run, on the path of the agent, or `tools.liquibase`, `tools.oracleJdbc` and `tools.utplsql` added by hand under the service in the file;
- the Nexus repositories under the service: type them in the service before you download the file, or untick "Deliver the package to Nexus".

The smallest `projects:` part for the Full or Security pipeline, before any database is allowed, is:

```yaml
projects:
  orders-db:
    appId:     "<AppScan application id>"
    buildTool: liquibase
    javaPath:  /usr/lib/jvm/java-17-openjdk        # or buildToolAutoSetup: true
    asoc:
      keyId: "<AppScan key id>"
    database:
      changelogFile: db/changelog-master.xml
      package:
        groupId: com.bbh.orders
        snapshotRepository: {id: bbh-db-snapshots, url: "https://tools.bbh.com/nexus/repository/db-snapshots"}
        releaseRepository:  {id: bbh-db-releases,  url: "https://tools.bbh.com/nexus/repository/db-releases"}
```

The SAST pipeline needs only `appId`, `buildTool`, `javaPath` or `buildToolAutoSetup`, `asoc.keyId` and `database.changelogFile`. Without Nexus, `database.package.nexusDelivery: false` replaces the group ID and the repositories.

### Production deployments from the package

The pipelines never deploy to production. A DBA deploys the released zip by hand, following the runbook in chapter 14 of the library guide, "Production (by hand, outside the library)":

1. From the release repository in Nexus, download the release zip of the version that QC received.
2. Take the manifest out of the zip into the same folder, and check what is pending:

```bash
unzip orders-db-341-20261031-101500.zip dso-deploy.properties
liquibase --defaults-file=dso-deploy.properties --search-path=orders-db-341-20261031-101500.zip --url=<URL> --username=<USER> --password=<PASSWORD> status --verbose
```

3. Run `update-sql` with the same options and attach its output to the change ticket.
4. After the approval, run `update`, then `tag` with the artifact ID and the version.

Set the production values of the changelog parameters that the manifest lists. The changelog path and the search folder must be exactly those of the manifest, or Liquibase treats every change as new.

### When a SQL and PL/SQL service stops

The portal refuses the settings that the pipelines would refuse. While you type, the form shows the problem under its field, in most cases with the text below starting with a capital letter. A save through the API answers with the field and the text.

| Message | Field | What to do |
|---------|-------|------------|
| "is required for the build tool SQL and PL/SQL (Liquibase)" | Changelog file | Type the path of the changelog. |
| "has no folder and the search root is the repository root: put the changelog in a folder, or set the search folder or the source folder to the folder of the changelog" | Changelog file | The zip would hold the whole repository. Do one of the three. |
| "set the JDK path or enable automatic build tool setup: Liquibase and utPLSQL need Java 17 or newer" | JDK path | Tick "Set up the build tool automatically", or type the path of a JDK 17 or newer. |
| "must not contain a user or a password: keep them in the Jenkins credentials of the credentials ID" | JDBC URL | Remove the user and the password from the URL. |
| "must be an Oracle thin URL: jdbc:oracle:thin:@//host:1521/SERVICE, @host:1521:SID, @tcps://host:2484/SERVICE?ssl_server_dn_match=true or @(DESCRIPTION=...)" | JDBC URL | Use one of these forms. TNS aliases and wallets are not supported. |
| "is required with the JDBC URL: the ID of the Jenkins credentials of the database user" | Credentials ID | Type the ID of the *Username with password* credential. |
| "is set without a JDBC URL" | Credentials ID | Type the JDBC URL, or empty the credentials ID. |
| "is also the URL of the RD or QC database: the test database receives the test packages and must be a database of its own" | JDBC URL of the test database | Ask the DBA for a test schema of its own. |
| "deploys the test changesets: QC needs a context filter such as "!test"" | Contexts of the service or of QC | Leave both empty, or use a filter that leaves out `test`. |
| "needs the JDBC URL of the test database" | Run the utPLSQL tests, Empty the test schema before each deployment | Type the JDBC URL of the test database, or untick the box. |
| "is set, but the Library defaults allow no database yet: ..." | JDBC URL | Ask the DevSecOps team to fill in the allowed databases. |
| "names a database that the Library defaults do not allow: ..." | JDBC URL | Check the host and the service, or ask the DevSecOps team to allow the database. |
| "each line must be name=value, ..." | Changelog parameters | Start each name with a lower-case letter, and use only lower-case letters, digits, `_` and `.`. |
| "holds a name that looks like a secret: ..." | Changelog parameters | Remove the parameter, and keep the secret in a Jenkins credential. |
| "holds a value that is not printable ASCII of at most 500 characters, ..." | Changelog parameters | Use plain ASCII without blanks at the start or the end and without `${`. |
| `Service <service> delivers its database package to Nexus in the <type> pipeline but has no <item>` | the save | Set the item under Database changes or in the Library defaults, or untick "Deliver the package to Nexus". |
| `Service <service> changes a database with SQL and PL/SQL and has no open source dependencies` | Add pipeline, the save | Choose another pipeline type. |

The library checks the settings again when a pipeline starts, together with what the portal cannot see: whether the changelog file and the utPLSQL folders exist in the checkout, and whether the allowed databases have changed. Its messages, and the messages of Liquibase and utPLSQL, are explained in chapter 14 of the library guide, under "Troubleshooting". [Chapter 19](#19-troubleshooting) of this guide describes the portal refusals in more detail.

## 8. Shared version catalogs and the combined Jenkinsfile

A multimodule repository often holds several services that share one list of dependency versions. That list is a version catalog. GoldenFix raises a version there once, for every service. It verifies a Gradle catalog in the builds that use it, and a Maven pom in the aggregator pom that builds it with the poms that use it.

### What GoldenFix finds by itself

You do not need to list these files:

- Gradle version catalogs: `gradle/libs.versions.toml` and any other `*.versions.toml` file.
- The catalogs that `settings.gradle` or `settings.gradle.kts` imports with `from(files(...))` in `versionCatalogs { }`, under any name, and the `version()` and `library()` entries written there.
- The files that `apply from:` names, such as `gradle/dependencies.gradle` with `ext.versions = [...]`.
- Gradle `java-platform` modules with their `constraints`.
- Maven parent poms (through `relativePath`) and BOM poms imported with `<scope>import</scope>` inside the repository, when they are named `pom.xml`.

### When to list a catalog

Use the field "Shared version catalogs" in the GoldenFix section of the service. Write one path per line, relative to the repository root. `*` matches within one folder and `**` matches across folders, for example `catalog/*.toml`. A leading `./` is dropped. Name a file, not its folder: a path that ends in `/` is refused, also in an imported file. List a file only when GoldenFix misses it, for example a parent or BOM pom with another file name, such as `platform/bom.xml`. A listed `.toml` file is read as a Gradle catalog, an `.xml` file as a Maven pom, and any other file as a Gradle build file. A listed TOML file that no `settings.gradle` imports is used by no build, so it is raised without verification. The field belongs to the service: it is kept whether the GoldenFix policy is inherited or overridden. It renders as `goldenFix.versionCatalogs`.

A listed path that matches nothing gives the run note `version catalog <path> not found in the repository`. A path that leaves the repository gives `version catalog <path> is outside the repository`.

### External version catalog repository

When version catalog files or BOMs are maintained in an external Bitbucket or Git repository shared across multiple projects, configure the repository in the GoldenFix section:

- `goldenFix.versionCatalogRepo.url`: The browser or clone address of the repository that holds the shared version catalog files or Gradle/Maven BOMs.
- `goldenFix.versionCatalogRepo.credentialsId`: The Jenkins credential used to sign in and push dependency updates to the external repository. Left empty, GoldenFix uses the credential configured in the Bitbucket (SCM) section.
- `goldenFix.versionCatalogRepo.branch`: The branch of the external catalog repository to check out, upgrade and open pull requests against. Left empty, the default branch is used.
- `goldenFix.versionCatalogRepo.path`: The relative path to the catalog file or directory within the external repository (for example `catalogs/libs.versions.toml`).

GoldenFix checks out the external catalog repository into an isolated workspace directory, applies version upgrades to the specified catalog, verifies the changes, and submits pull requests directly to the external repository.

### What the pull request contains

- The version is raised in the catalog, once. A declaration of the same dependency elsewhere is raised to at least the catalog version. The pull request lists it as `declared outside the catalog <file> (<alias>)`, so that reviewers can move it into the catalog.
- The report, the pull request description and the commit message name the catalog file and the alias.
- A changed catalog is verified in each Gradle build that uses it. A catalog that no build uses is logged as `GoldenFix: no Gradle build found for <file> - not verified`.
- A changed parent or BOM pom is verified in the folder of the multi-module pom whose modules reach it and a pom that uses it. Without such an aggregator, GoldenFix builds the root `pom.xml`, or else the shared pom alone, so the poms that use it are not built.
- A dependency that a BOM or a platform manages gets the reason `managed by BOM g:a:v imported in <file>: raise the BOM or add an override to the catalog`.

### One Jenkins job for each repository

Services that share a repository and a catalog should run in one Jenkins job. One GoldenFix pull request then covers the whole repository. Two jobs of one repository that run in the same minute compute the same branch name, and the second job fails with `[GOLDENFIX] The branch <branch> already exists in <repository> - another GoldenFix job of this repository ran in the same minute; run one GoldenFix job for all services of a repository (pipelineKeys)`.

The library takes the Jenkins agents of a run from the first key, so only pipelines that run on the same agents share a job. The portal treats pipelines as one job when they have the same repository, pipeline type, Jenkins agents and Jenkins job:

- The pipelines that one save starts for the services of a repository get one Jenkins job, the job of the first of them. A new pipeline joins a job that two or more pipelines of its type, repository and agents run in already. Otherwise it gets the job of the service template, so the services of a product that run in jobs of their own keep doing so. Both need a Jenkins job in the service template: without one, a new pipeline gets no Jenkins job and a Jenkinsfile of its own.
- The product page shows a notice for each repository and pipeline type whose pipelines share one job. It names the services and offers "Show the Jenkinsfile". The Jenkinsfile item of each of these pipelines and the pipeline page show the same combined Jenkinsfile. A pipeline with a job of its own keeps its own Jenkinsfile.
- The Next steps of the Self-service wizard show one Jenkinsfile for the services whose pipelines share a job and ask for one Jenkins job for each Jenkinsfile.
- To combine services that run in jobs of their own, give their pipelines the same Jenkins job with "Edit pipeline settings" and put the combined Jenkinsfile into the repository. The More menu of a pipeline offers it as "Jenkinsfile for several services" when other services have a pipeline of the same type. When their Jenkins agents differ, the dialog warns that the job runs every service on the agents of the first key.

Pipeline Monitoring shows the runs of a shared job under the service of the first key: the library reports every run with the monitoring project tag of that service. To see the runs on the other services too, give them the same Project tag (Monitoring section) and the shared job as the Jenkins job of their pipelines. With a shared tag, the portal matches the runs to a pipeline by its Jenkins job, so each pipeline of the group then shows the runs of the shared job.

The combined Jenkinsfile passes every key in `pipelineKeys`. The first key belongs to the primary service:

```groovy
@Library('DevSecOpsJenkinsLibrary') _

devSecOpsNexusIqGoldenFixPipeline(pipelineKeys: [
    '<key of the first service>',
    '<key of the second service>',
])
```

In one run, the first service with fixes opens the pull request. Each further service adds its upgrades to the same pull request and a comment under the heading `### GoldenFix for <service>`. A service whose upgrades the pull request already carries ends with `Already raised to <component> <version> in the shared pull request <url>`. When the comment cannot be added, the run logs `[GOLDENFIX] The pull request comment could not be added: <reason>` and goes on.

### Limits

- Versions in `buildSrc` (for example `const val jackson = "2.15.2"` in `Versions.kt`) are not changed. The fix gets the reason `version <name> is defined in buildSrc/<file>: not updated automatically`.
- A catalog published as an artifact (`from("g:a:v")` in `settings.gradle`) is outside the repository. The fix gets the reason `managed by the version catalog artifact g:a:v outside this repository`.
- The `[plugins]` and bundle entries of a TOML catalog are not changed. A version range keeps a note and is not edited.
- The pub ecosystem is mapped from the Nexus IQ data by name or package ID. This has not been checked against a real Nexus IQ pub payload.

## 9. Pipeline types, pipeline keys, the Jenkinsfile and the Jenkins jobs

### Pipeline types

| Type | Name in Self-service | Entry point | `{type}` in job names | What it runs |
|------|----------------------|-------------|------------------------|--------------|
| Full | Full | `devSecOpsPipeline` | `full` | Build, unit tests, Nexus IQ, SAST, SonarQube, deployment to RD, smoke, regression and performance tests, DAST, deployment to QC and the release |
| Security | Security | `devSecOpsSecurityPipeline` | `security` | Build and security scans. It can start an Extended pipeline after its scans. |
| Extended | (admin only) | `devSecOpsExtendedPipeline` | `extended` | The deployment and tests started by a Security pipeline |
| SAST scanning | SAST | `devSecOpsSASTScanningPipeline` | `sast` | The AppScan static scan of the sources only |
| Nexus IQ GoldenFix | OSA | `devSecOpsNexusIqGoldenFixPipeline` | `nexusiq` | The Nexus IQ scan; GoldenFix opens a pull request with safe versions in the Bitbucket repository |

`pipeline.type` holds the value in the fourth column (`full` for the Full pipeline) and `pipeline.entryPoint` the entry point.

A SQL and PL/SQL service takes the Full, Security, Extended and SAST pipelines. The Nexus IQ GoldenFix pipeline is refused for it, because it has no open source dependencies to scan; see [chapter 7](#what-the-pipelines-do-with-a-sql-and-plsql-service).

### Pipeline execution flow and stages

The Full and Security pipelines follow an optimized, decoupled execution order:

1. **Build artifact (Stage 1)**: As the initial active stage after pipeline initialization, the pipeline builds the deployable service artifacts without running unit tests (e.g. `clean assemble -x test` or `compile`). If a Node.js build is configured (`build.node.enabled: true`), npm commands run as step 0 at the start of this stage before compiling the main application.
2. **Parallel verification stages**: Four verification stages execute simultaneously:
   - **Nexus IQ**: Evaluates open-source dependencies against security and license policies.
   - **SonarQube**: Performs code analysis and verifies quality gate standards.
   - **Unit tests**: Executes automated unit tests and aggregates JaCoCo / LCOV code coverage reports.
   - **SAST**: Initiates and monitors static security analysis in HCL AppScan on Cloud asynchronously.
3. **Deployment and automated test stages** (Full pipeline): Following successful verification, the pipeline deploys the artifact to the lower test environment (RD), executes Smoke, Regression, and Performance test jobs, runs DAST against the deployed application, and proceeds to QC deployment and release delivery.

### Pipeline settings

"Add pipeline" on the product page and "Edit pipeline settings" open the pipeline dialog:

| Field | Meaning |
|-------|---------|
| Pipeline type | Fixed once the pipeline exists. A service has at most one pipeline of each type. The dialog does not offer Nexus IQ GoldenFix for a SQL and PL/SQL service. |
| Jenkins agents | Agent labels or label expressions, separated by commas. The first is the default. A run uses the one chosen in its AGENT_NAME parameter. Written as `pipeline.agentNames`. |
| Jenkins job | The path of the job, for example `DevSecOps/CERT/backend-api-full`, or its full address. The portal links the pipeline and its runs to it. It is not written to `devsecops_config.yaml`. |
| Extended pipeline job | Security only, optional: the Extended pipeline started after the scans. Written as `jenkins.pipeline.extendedPipeline`. |
| Security pipeline job | Extended only: the Security pipeline whose build this pipeline deploys and tests. Written as `pipeline.securityPipeline`. |
| Description | A note for the people who look after the pipeline. It is not written to `devsecops_config.yaml`. |

### Pipeline keys

Each pipeline has one active key. The pipeline page and the product page show the key hidden. Use "Show key" and "Copy key".

| Action | Effect |
|--------|--------|
| Replace key | Issues a new key. The old key stops working at once. Put the new key in the Jenkinsfile. |
| Invalidate key | Stops the key and records your reason. The pipeline is refused its settings until a new key is issued. |
| Regenerate key | Issues a new key for a pipeline whose key was invalidated. |
| Key history | Lists the earlier keys with when they were issued and invalidated. |
| Delete pipeline | Deletes the pipeline and its key history. Its Jenkins job stops at its next start. |

> [!IMPORTANT]
> Treat a pipeline key like a password. Anyone who has it can read the settings of the pipeline. Replace the key when it may have leaked.

The portal records when Jenkins last used a key. "Settings sent to Jenkins (devsecops_config.yaml format)" shows the settings without counting as a use.

### The Jenkinsfile

The Jenkinsfile holds only the library name and the key:

```groovy
@Library('DevSecOpsJenkinsLibrary') _

devSecOpsPipeline(pipelineKey: '<pipeline key>')
```

The library name comes from the Library defaults (`platform.jenkinsLibrary`). Copy the Jenkinsfile from the pipeline page, from the product page or from the Next steps of the Self-service wizard. For a pipeline that shares the Jenkins job of its repository with other services, each of these shows the combined Jenkinsfile with the keys of all of them. When such a key is invalidated, the pipeline page asks you to replace the shared Jenkinsfile once the key is regenerated.

### A Jenkinsfile without a key

A Jenkinsfile without a key also works. The library then reads the settings from the repository: devsecops_config.yaml at its root or, when there is none, config.yaml, the name the file had before the portal. Commit two files at the root of the repository:

1. devsecops_config.yaml: on the product page, More, "Settings sent to Jenkins (devsecops_config.yaml)", Download. If the browser saves it as `devsecops_config (1).yaml`, rename it to exactly `devsecops_config.yaml`.
2. The Jenkinsfile: on the product page, the More menu of the pipeline, "Jenkinsfile without a key". For example:

```groovy
@Library('DevSecOpsJenkinsLibrary') _

devSecOpsPipeline(projectNames: 'gateway', agentNames: ['linux-agent'])
```

- `projectNames` names the services of the repository. The downloaded file holds every service of the product; without `projectNames` the library would build all of them from this repository.
- `agentNames` lists the Jenkins agents the pipeline may run on. It is required without a key.
- Such a pipeline does not ask the portal for its settings. A change in the portal reaches it only when you download and commit the file again. The Library defaults written under `defaults`, such as the security limits, the GoldenFix policy and the Node.js build defaults, reach it through the copy kept in the library, with the next library version; that copy leaves out the order of the static scans. The values written to `platform`, such as the AppScan, proxy, Nexus snapshot repository and iOS build agent values, never reach it; see [chapter 13](#13-library-defaults-global-settings).
- A security pipeline that should start its extended pipeline needs `jenkins.pipeline.extendedPipeline` under its service in the file. The product download leaves it out, because the portal keeps it with the security pipeline.
- The pipeline report says "No pipeline key: settings from devsecops_config.yaml in the repository".
- The Jenkins administrators can require a key for every pipeline; such a Jenkinsfile then stops at its start.
- A Jenkinsfile with a key never reads the file. Delete the file from the repository once its pipelines have keys.

### Jenkins jobs

- Each pipeline needs a Jenkins Pipeline job that reads the Jenkinsfile from the repository (Pipeline script from SCM).
- The service template names the job, for example `DevSecOps/{CODE}/{service}-{type}` gives `DevSecOps/PAYHUB/gateway-full`.
- While the service template names a job, the pipelines that one save starts for the services of a repository share one job and one Jenkinsfile with `pipelineKeys`. A new pipeline joins a job only when two or more pipelines of its type, repository and Jenkins agents run in it already. See [chapter 8](#8-shared-version-catalogs-and-the-combined-jenkinsfile).
- Jenkins needs the global environment variable `DSO_PORTAL_URL`, for example `https://dso-portal.apps.bbh.com`.

## 10. Import a devsecops_config.yaml and copy a service

### Import a devsecops_config.yaml

"Import devsecops_config.yaml" reads a `devsecops_config.yaml` or `config.yaml` of the library, a configuration the portal rendered or its JSON form. It fills a new service form. Nothing is stored until you save.

- A file with several projects asks which service to import.
- The notes under the form list what the portal did not take over ("Not imported") and what you should check ("Check").
- Secrets in the file, such as `asoc.keySecret`, are never echoed.
- A value equal to a Library default comes back empty, so the service keeps following the Library default. The same holds for the library's own Node.js defaults: `npm ci` then `npm run build`, 30 minutes and the Nexus IQ scan of `package-lock.json`.
- `build.node` without `enabled: true` comes back switched off, with its values kept.
- A Flutter file with `build.node.enabled: true` comes back switched off with the note "Check: the Node.js build is not used with Flutter".
- `goldenFix.versionCatalogs` fills "Shared version catalogs".
- A project with `buildTool: liquibase` imports its `database` block, `tools.sonar.sources` and `tools.sonar.sonarScannerVersion`. `build.maven.mvnPath` fills "Maven home" under Database changes when the file has no `database.package.mvnPath`. Repositories equal to the Library defaults, and an artifact ID equal to the service name, come back empty. The notes of the database keys are in the table below.
- In the admin editor, the imported service keeps what the file gives: an empty repository URL, Bitbucket credentials ID or Nexus IQ application stays empty, also when you change its name. So a service without a repository does not come to need a Bitbucket credentials ID. A new name only replaces the values that still hold what the service template gives for the old name, such as the repository URL, the Nexus IQ application and the OpenShift names, and on OpenShift it fills in an empty application name. In the Self-service dialog, the imported service follows its name like a new service: the service template fills in an empty repository URL and Nexus IQ application, and a repository URL filled in that way makes the Bitbucket credentials ID required. In both, the Bitbucket credentials ID stays as the file gives it, even when it is empty. The notes do not list the values filled in, so check them in the Bitbucket and Nexus IQ sections.
- The Jenkins agents of the imported pipeline fill the Jenkins agents of the product while you have not changed them.
- The file may hold at most 262,144 characters and 50 projects.
- You may choose several files at once, for example both files of a repository. The import then reads devsecops_config.yaml and, when it is not among them, config.yaml, as the library does, and a note under "Check" names the files it did not read. It refuses several files of which none has either name. The help icon next to the button says the same.

Database passwords, users inside JDBC URLs and production databases are never imported. The notes name the key, never its value:

| In the file | Note |
|-------------|------|
| `database.rd.password`, or a `username` or `user` of a database | "not imported: the portal keeps no secrets, only the IDs of Jenkins credentials" |
| A JDBC URL with a user or a password, such as `jdbc:oracle:thin:scott/tiger@//qc-db.bbh.com:1521/ORDERS_QC` | "not imported: the URL holds a user or a password; the portal keeps only the ID of the Jenkins credentials" |
| `database.prod`, `database.production`, `database.prd` or `database.live` | "not imported: the pipelines never deploy to production" |
| `database.allowedTargets` under a project | "not imported: a global setting, the same for every service" |
| `tools.liquibase`, `tools.oracleJdbc` or `tools.utplsql` under a project | "not imported: the portal sets the SQL and PL/SQL tools for every service in the Library defaults" |
| `database` under a Gradle, Maven or Flutter project | "not imported: database settings belong to a service with buildTool liquibase" |
| `deploy`, `delivery`, `build.node`, `goldenFix`, `tools.nexusIq` or another key a SQL and PL/SQL project does not use | "not imported: a SQL and PL/SQL service (buildTool liquibase) does not use it" |

### Copy a service

"Copy values from existing service" (admin editor), "Duplicate service" (service panel) and "Copy values from" (Self-service dialog) copy every setting of another service of the product, the Node.js build and the shared version catalogs included. What identifies the source service is not copied:

- The name. A duplicate is named `<source>-copy`.
- The SonarQube project name and key, the monitoring project tag and the SAST scan name stay empty.
- The OpenShift application name, the image repositories and the Nexus IQ application follow the name you type.
- The repository URL follows the name too while it is the URL the service template gives for the name of the source. When both services build from one repository, set the repository URL of the copy back to that of its source.

A copy keeps the Bitbucket credentials ID of its source, even an empty one. In the admin editor, a copy also keeps an empty repository URL, so a copy of a service without a repository needs no credentials ID. In the Self-service dialog, the service template fills in an empty repository URL from the name, and a copy without a Bitbucket credentials ID then needs one.

A new service in the Self-service wizard also takes the AppScan API key ID of another service of the product.

## 11. GoldenFix

GoldenFix runs in the Nexus IQ stage of the Full, Security and Nexus IQ GoldenFix pipelines. When Nexus IQ reports a policy violation, it looks up a safe version, changes the build files, builds the service and opens a pull request in the Bitbucket repository of the service. A developer of the service reviews and merges it.

### What GoldenFix changes

| Ecosystem | Files |
|-----------|-------|
| maven | `pom.xml` files, their properties, parent poms and BOMs of the repository |
| Gradle (with maven) | `build.gradle`, `build.gradle.kts`, `gradle.properties`, `*.versions.toml`, `settings.gradle(.kts)` catalogs and `apply from:` files |
| npm | `package.json`, with `overrides` and `resolutions`, and a refreshed `package-lock.json` |
| pypi | requirements, constraints and `pyproject.toml` files |
| pub | `pubspec.yaml` |

### The GoldenFix section of a service

- "Run GoldenFix": Library default, On or Off.
- "Shared version catalogs": see [chapter 8](#8-shared-version-catalogs-and-the-combined-jenkinsfile).
- "Inherit the Library defaults" follows the GoldenFix policy on the Library defaults page, including later changes. The note under it summarises that policy.
- "Override for this service" shows the policy fields below. A field left empty keeps the Library default; its hint shows that value.

### The policy fields

| Field | Meaning |
|-------|---------|
| Ecosystems | The kinds of dependencies GoldenFix upgrades: maven, npm, pypi and pub. Hold Ctrl (Cmd on a Mac) to choose several. |
| Minimum threat level | Only violations that Nexus IQ rates at least this high, from 0 to 10. 2 and above is moderate, 4 and above severe, 8 and above critical. |
| Dependencies | Direct dependencies only, or direct and transitive ones. |
| Golden version types | The Nexus IQ remediation types labelled Nexus IQ Golden Version in the pull request. |
| Excluded folders | Folder names, at any depth, whose manifests GoldenFix leaves alone. A list replaces the library list of `.git`, `node_modules`, `target`, `build` and similar folders. |
| Verification | Whether GoldenFix builds the fix before it opens the pull request. When the build fails, it lowers the versions and builds again. |
| Attempts | How many verification builds GoldenFix may run. |
| Timeout (minutes) | The limit of each check: one check for each folder in which a changed manifest or catalog is built. |
| Maven, Gradle, npm, pip and pub command | The command that builds the fix. Left empty, the library runs `mvn -B -q -DskipTests compile`, `./gradlew --no-daemon --console=plain classes`, `npm ci --ignore-scripts`, `python3 -m pip install --dry-run` or `flutter pub get`. |
| Author name, Author e-mail | The author of the GoldenFix commit. |
| Time zone | The time zone of the branch and pull request name `GoldenFix-YYYYMMDDHHMM`. |

### Where the verification runs

- Maven: in the folder of each changed `pom.xml`. A parent or BOM pom that other poms use is verified in the folder of the multi-module pom that builds them together. When a shared pom with a name other than `pom.xml` is built in its own folder, the library adds `-f` and its file name at the end of the command. It does so for a Maven command you set in the policy fields too, so write that command as one `mvn` call, for example `mvn -B -q -DskipTests verify`.
- Gradle: in the folder of each changed build or properties file. A catalog, a settings file or a platform is verified in the folder of each build that uses it.
- npm: in the folder of each changed `package.json`, in the Node.js environment of the service while its Node.js build is on.
- pip and pub: in the folder of each changed file.

## 12. The service template

Admin, Service template holds the values a new service starts with. A saved service keeps its own values.

| Block | Fields |
|-------|--------|
| Pipelines | Jenkins agents of a new pipeline; Jenkins job, for example `DevSecOps/{CODE}/{service}-{type}` |
| Build | Gradle tasks and Gradle artifact; Maven goals and Maven artifact; Nexus delivery goals (Maven services on virtual machines) |
| Nexus IQ and Bitbucket | Nexus IQ application, for example `{code}-{service}`; Bitbucket repository; Bitbucket credentials ID; the Gradle, Maven and Flutter scan patterns |
| OpenShift | OpenShift project (the projects end in -build, -rd and -qc); Image registry; Health check path |

The placeholders are:

| Placeholder | Becomes |
|-------------|---------|
| `{CODE}` | the product code, as in CERT |
| `{code}` | the product code in lower case, as in cert |
| `{service}` | the service name, as in backend-api |
| `{type}` | the pipeline type, in the Jenkins job only: full, security, extended, sast or nexusiq |

The page shows examples of the filled-in values. Both the Self-service wizard and the admin editor fill a new service from the template when its name is typed.

## 13. Library defaults (Global Settings)

Admin, Library defaults holds the values every pipeline shares. A service that leaves a field empty takes the value from here. These values change only when a tool moves; the DevSecOps team then enters the new value. The page has eight sections.

| Section | What it holds |
|---------|---------------|
| Tools and servers | Jenkins (Jenkins URL, shared library), HCL AppScan (service URL, scan client downloads, proxy), SonarQube and Nexus IQ servers, the Nexus snapshot repository, InfluxDB, the iOS build agent and the Portal monitoring block (the InfluxDB the portal reads and the Grafana dashboards) |
| Deployment defaults | The UrbanCode Deploy site and process, and the RD and QC test servers with their SSH user, deploy script and version file |
| Security limits | The most critical, high and medium findings each scan (SAST, SCA, Nexus IQ, DAST) may report |
| Scans and coverage | The minimum line coverage, how long the pipelines wait for each scan, and the order of the static scans |
| Release gate | Which scans the release gate checks, whether it requires the coverage, and its result file |
| Service defaults | The build tool (Gradle, Maven or Flutter), deployment target and source folder a new service starts with; the test jobs at the same time; the Node.js build defaults |
| SQL and PL/SQL (Liquibase) | Liquibase, the Oracle JDBC driver and utPLSQL-cli, the allowed databases and the Nexus repositories of the database packages |
| GoldenFix defaults | Whether GoldenFix runs by default and the GoldenFix policy |

A Jenkinsfile without a key does not read this page. The values written under `defaults` reach it through the copy the DevSecOps library keeps of them, which the DevSecOps team updates from "Shared settings sent to Jenkins (devsecops_config.yaml format)" before each library release. That copy leaves out `staticAnalysis.parallel`, the order of the static scans, so such a Jenkinsfile does not follow the box "Run Nexus IQ and SonarQube while AppScan scans". The per-service servers and the Deployment defaults reach it in devsecops_config.yaml, once the file is downloaded and committed again. The values written to `platform`, such as the AppScan on Cloud URL of the scans, the scan client downloads, the proxy, the OIS host, the Nexus snapshot repository and the iOS build agent, never reach it: the library takes them from the environment of the Jenkins agent or uses its own.

### Node.js build defaults

The block "Node.js build" in Service defaults has three fields: Node.js installation, npm registry and .npmrc credentials ID. A new service shows them as its "Library default". A service that leaves them empty takes them later as well, through `defaults.build.node` in `devsecops_config.yaml`. A service that names its own installation or a Node.js home on the agent uses that instead. The BBH seed leaves all three empty.

### SQL and PL/SQL defaults

The section "SQL and PL/SQL (Liquibase)" gives every SQL and PL/SQL service the locations of Liquibase, the Oracle JDBC driver and utPLSQL-cli, the databases its pipelines may deploy to and the Nexus repositories of its package. The portal writes these values under `defaults.tools.liquibase`, `defaults.tools.oracleJdbc`, `defaults.tools.utplsql` and `defaults.database`, and only while they are set, so the settings of every other service stay as they were. The allowed databases apply to every service; a service cannot set its own. A service may name its own Nexus repositories. The BBH seed leaves the section empty, so set the allowed databases and the repositories before the first SQL and PL/SQL service is saved. [Chapter 7](#library-defaults-for-sql-and-plsql-services) describes each field, the licences, the checksums and the allowed databases.

### Decoupled build, concurrent scans, and SonarQube running last

The pipeline executes stages in an optimized sequence:
1. **Build without tests**: The primary build artifact is compiled first (with optional Node.js build executing as step 0).
2. **Concurrent Scans and Tests**: SAST scan submission queues while Unit tests (including Cypress tests) and Nexus IQ dependency scans run concurrently. Once testing and dependency evaluation finish, the pipeline retrieves the SAST analysis results.
3. **SonarQube**: SonarQube runs last, ensuring that all Unit test reports and JaCoCo / LCOV / Cypress code coverage files are available for ingestion into the SonarQube quality gate.

The block "Speed" in Scans and coverage provides the checkbox "Run Nexus IQ and SonarQube while AppScan scans" (`staticAnalysis.parallel`, ticked by default). When enabled, SAST submission, unit tests, and Nexus IQ run concurrently before SonarQube runs last. Unticked, scans run sequentially. The Jenkins environment variable `DSO_STATIC_ANALYSIS_PARALLEL`, when set to `true` or `false`, overrides this setting. A Jenkinsfile without a key follows `staticAnalysis.parallel: true` in `devsecops_config.yaml`.

### Which values follow later changes

- Saved services follow later changes of: the tools and servers, the deployment defaults, the security limits, the scan waiting times, the order of the static scans, the release gate, the test jobs at the same time, the Node.js build defaults, the SQL and PL/SQL defaults and the GoldenFix policy of services that inherit it.
- The build tool, the deployment target and the source folder only prefill a new service.

### Portal monitoring

The Portal monitoring block sets the InfluxDB the portal reads (URL, organization, bucket and read token) and up to two Grafana instances. Values saved here win over the variables `INFLUX_URL`, `INFLUX_ORG`, `INFLUX_BUCKET`, `INFLUX_TOKEN` and `GRAFANA_*`. The read token is stored and never shown again. These settings are for the portal itself and are not written to `devsecops_config.yaml`.

## 14. Pipeline Monitoring and DORA

Pipeline Monitoring reads the results the pipelines write to InfluxDB.

- The overview shows the latest results of every product, by Business Service, the runs per day and the delivery performance (DORA) of the last 30 days.
- A product page lists its pipelines with their latest runs.
- A pipeline page shows the latest run, its stages, the recent runs, the runs per day and the Grafana dashboards. Choose the period: 7, 30, 90 or 180 days.

The four DORA figures are:

| Figure | Meaning | Elite | High | Medium | Low |
|--------|---------|-------|------|--------|-----|
| Deployment frequency | How often a change reaches production | 7 or more a week | 1 or more a week | 1 or more a month | less |
| Lead time for changes | How long a change takes from commit to production (median) | under a day | under a week | under a month | longer |
| Change failure rate | The share of deployments that failed | 5% or less | 10% or less | 15% or less | more |
| Time to restore | How long it takes to recover after a failed deployment (average) | under an hour | under a day | under a week | longer |

A pipeline that has not deployed successfully since a failed deployment shows "Not recovered yet".

The results of a pipeline are stored under the tags of the Monitoring section of its service: the project tag (`influx.project`, by default `<CODE>-<service>`) and the environment tag (`influx.env`, by default `test`). Pipelines other than Full add their type to the project tag, for example `CERT-guisecurity`. The pipeline page shows them as Monitoring tags. Without an InfluxDB, the monitoring status says that the metrics store is not configured and the runs stay empty.

### Execution Report (pipeline-report.html)

After each pipeline execution, the DevSecOps pipeline automatically sends the complete build status (build number, job, branch, commit, duration, stage tallies) and the full HTML content of `pipeline-report.html` to the portal via `POST /api/dso/reports`.

The Pipeline Monitoring page embeds this report directly in an interactive card, displaying:
- Complete visual stage flow, step tallies, and execution times.
- Status badges (PASS, FAIL, WARN) for each development, scanning, testing, and deployment stage.
- Direct clickable links to Nexus IQ policy evaluations, HCL AppScan SAST/DAST reports, SonarQube dashboards, remote Jenkins test jobs, and delivery artifacts, identical to the Jenkins report view.
- A button to open the full HTML report in a separate browser tab.

The portal provides the following endpoints for pipeline reports:
- `POST /api/dso/reports`: Ingests build status and HTML report from the pipeline.
- `GET /api/pipelines/{id}/report`: Returns report metadata and execution summary as JSON.
- `GET /api/pipelines/{id}/report/html`: Renders the latest HTML execution report directly in the browser.
- `GET /api/pipelines/{id}/runs/{build}/report/html`: Renders the HTML execution report for a specific build.

## 15. Business Services

Admin, Business Services lists the groups of products with their product, service and pipeline counts.

- "Add business service" creates one. A name must be unique.
- A Business Service can be renamed or deleted. A Business Service that still holds products cannot be deleted.
- The portal starts with the five BBH Business Services: AI Lab, Capital Partners, Corporate Technology, Custody and Fund Services.

The Pipelines page shows the pipelines of one Business Service at a time. Its first table counts the pipelines of each Business Service, active and with an invalidated key.

## 16. Action history

The Action history records every change made in the portal: who did it, when, what was done, on which resource and with which result. Search by user, resource or details, and filter by action, resource and status.

The recorded actions are: create, update and delete a product; create, update and delete a pipeline; issue a new key; invalidate a key; create, rename and delete a business service; update the service template; update the library defaults; initialize the portal. An import of a devsecops_config.yaml or config.yaml stores nothing and is not recorded.

The portal has no sign-in yet. Every action is recorded as the default administrator until BBH single sign-on is added.

## 17. Field reference: from the portal to devsecops_config.yaml

This chapter lists every field that writes to `devsecops_config.yaml`. The column "Key" shows the key in the code chip of the field. Service keys sit under `projects.<service>`. A chip that shows only the last part of a key is listed with its full key. A field that is empty writes nothing, and a checkbox that is off writes nothing unless the table says otherwise.

### Product and pipeline

| Field | Key | Notes |
|-------|-----|-------|
| Product code | `pipeline.product` | |
| Service name | `pipeline.projectNames`, `projects.<service>` | |
| Jenkins agents (product and pipeline) | `pipeline.agentNames` | |
| Pipeline type | `pipeline.type`, `pipeline.entryPoint` | |
| Extended pipeline job (Security) | `jenkins.pipeline.extendedPipeline` | under the service |
| Security pipeline job (Extended) | `pipeline.securityPipeline` | |

### General

| Field | Key | Notes |
|-------|-----|-------|
| Service name | the name of the project under `projects` | letters, digits, '.', '-' and '_' |
| Description | none | shown in the portal only |

### Build

| Field | Key | Notes |
|-------|-----|-------|
| Build tool | `buildTool` | `gradle`, `maven`, `flutter` or `liquibase` |
| Artifact path | `build.buildPath` | |
| Set up the build tool automatically | `buildToolAutoSetup` | |
| JDK path | `javaPath` | |
| Source folder (Advanced) | `sourceDir` | the folder the AppScan static scan prepares; for a SQL and PL/SQL service a main field, the folder of its code |
| Build the frontend with Node.js first | `build.node.enabled` | written as `true` only; nothing under `build.node` is written while it is off |
| Folder with package.json | `build.node.dir` | |
| Commands | `build.node.commands` | |
| Scan the npm dependencies with Nexus IQ | `build.node.nexusIqScan` | written only as `false` |
| Node.js installation | `build.node.installationName` | |
| Node.js home on the agent (Advanced) | `build.node.nodePath` | |
| Node.js timeout (minutes) (Advanced) | `build.node.timeoutMin` | |
| npm registry (Advanced) | `build.node.registry` | |
| .npmrc credentials ID (Advanced) | `build.node.npmrcCredentialsId` | |
| Node.js environment variables (Advanced) | `build.node.env` | a map of `NAME: value` |

The Gradle, Maven, or Liquibase command of the build has the command fields of the next table.

### Command fields

Five steps share the same command fields: the build, the unit tests, the SonarQube analysis, the compile of the AppScan static scan and the Nexus delivery of a Maven service on virtual machines. The keys depend on the build tool.

| Field | Build | Unit tests | SonarQube | AppScan compile | Nexus delivery |
|-------|-------|------------|-----------|-----------------|----------------|
| Gradle tasks | `build.gradle.tasks` | `tests.unitTests.gradle.tasks` | `tools.sonar.gradle.tasks` | `asoc.gradle.tasks` | |
| Maven goals | `build.maven.goals` | `tests.unitTests.maven.goals` | `tools.sonar.maven.goals` | `asoc.maven.goals` | `delivery.maven.goals` |
| Liquibase commands | `build.liquibase.commands` | `tests.unitTests.liquibase.commands` | `tools.sonar.liquibase.commands` | `asoc.liquibase.commands` | |
| Directory (Gradle) | `build.gradle.dir` | `tests.unitTests.gradle.dir` | `tools.sonar.gradle.dir` | `asoc.gradle.dir` | |
| Directory (Maven) | `build.maven.dir` | `tests.unitTests.maven.dir` | `tools.sonar.maven.dir` | `asoc.maven.dir` | `delivery.maven.dir` |
| Directory (Liquibase) | `build.liquibase.dir` | `tests.unitTests.liquibase.dir` | `tools.sonar.liquibase.dir` | `asoc.liquibase.dir` | |
| Maven home | `build.maven.mvnPath` | `tests.unitTests.maven.mvnPath` | `tools.sonar.maven.mvnPath` | `asoc.maven.mvnPath` | `delivery.maven.mvnPath` |
| Flags (Gradle) | `build.gradle.flags` | `tests.unitTests.gradle.flags` | `tools.sonar.gradle.flags` | `asoc.gradle.flags` | |
| Flags (Maven) | `build.maven.flags` | `tests.unitTests.maven.flags` | `tools.sonar.maven.flags` | `asoc.maven.flags` | `delivery.maven.flags` |
| Flags (Liquibase) | `build.liquibase.flags` | `tests.unitTests.liquibase.flags` | `tools.sonar.liquibase.flags` | `asoc.liquibase.flags` | |
| Environment variables (Gradle) | `build.gradle.env` | `tests.unitTests.gradle.env` | `tools.sonar.gradle.env` | `asoc.gradle.env` | |
| Environment variables (Maven) | `build.maven.env` | `tests.unitTests.maven.env` | `tools.sonar.maven.env` | `asoc.maven.env` | `delivery.maven.env` |
| Environment variables (Liquibase) | `build.liquibase.env` | `tests.unitTests.liquibase.env` | `tools.sonar.liquibase.env` | `asoc.liquibase.env` | |
| Step label (Gradle) | `build.gradle.label` | `tests.unitTests.gradle.label` | `tools.sonar.gradle.label` | `asoc.gradle.label` | |
| Step label (Maven) | `build.maven.label` | `tests.unitTests.maven.label` | `tools.sonar.maven.label` | `asoc.maven.label` | `delivery.maven.label` |
| Step label (Liquibase) | `build.liquibase.label` | `tests.unitTests.liquibase.label` | `tools.sonar.liquibase.label` | `asoc.liquibase.label` | |
| Return the output to the pipeline (Gradle) | `build.gradle.returnStdout` | `tests.unitTests.gradle.returnStdout` | `tools.sonar.gradle.returnStdout` | `asoc.gradle.returnStdout` | |
| Return the output to the pipeline (Maven) | `build.maven.returnStdout` | `tests.unitTests.maven.returnStdout` | `tools.sonar.maven.returnStdout` | `asoc.maven.returnStdout` | `delivery.maven.returnStdout` |
| Return the output to the pipeline (Liquibase) | `build.liquibase.returnStdout` | `tests.unitTests.liquibase.returnStdout` | `tools.sonar.liquibase.returnStdout` | `asoc.liquibase.returnStdout` | |

### Database changes

Only a SQL and PL/SQL service writes `database`, right after `build`. A value equal to its library default is not written.

| Field | Key | Notes |
|-------|-----|-------|
| Changelog file | `database.changelogFile` | required |
| Search folder | `database.searchPath` | written unless `.` |
| Changelog parameters | `database.parameters` | a map of `name: value` |
| Deliver the package to Nexus | `database.package.nexusDelivery` | written only as `false` |
| Group ID | `database.package.groupId` | |
| Artifact ID | `database.package.artifactId` | written unless it is the service name |
| Snapshot repository ID | `database.package.snapshotRepository.id` | written when set on the service; the Library default is written under `defaults` |
| Snapshot repository URL | `database.package.snapshotRepository.url` | as above |
| Release repository ID | `database.package.releaseRepository.id` | as above |
| Release repository URL | `database.package.releaseRepository.url` | as above |
| Maven home (Advanced) | `database.package.mvnPath` | |
| Oracle version (Advanced) | `database.oracleVersion` | written unless 19.0 |
| Contexts (Advanced) | `database.contexts` | written unless `!test` |
| Labels (Advanced) | `database.labels` | |
| Lock wait (minutes) (Advanced) | `database.lockWaitMinutes` | written unless 5 |
| Default schema (Advanced) | `database.defaultSchema` | |
| Liquibase schema (Advanced) | `database.liquibaseSchema` | |
| Require a rollback for every change (Advanced) | `database.requireRollback` | written only as `true` |
| Tag each deployment (Advanced) | `database.tag` | written only as `false` |
| Check for invalid objects after a deployment (Advanced) | `database.compileCheck.enabled` | written only as `false` |
| Recompile invalid objects first (Advanced) | `database.compileCheck.recompile` | written only as `false` |
| Objects the check ignores (Advanced) | `database.compileCheck.ignoreObjects` | a list |
| Files the static checks ignore (Advanced) | `database.checks.ignoreFiles` | a list |
| Sources (Advanced) | `tools.sonar.sources` | |
| SonarScanner version (Advanced) | `tools.sonar.sonarScannerVersion` | |

Each database has its own section: `database.test` for the test database, `database.rd` for RD and `database.qc` for QC.

| Field | Key in the database | Notes |
|-------|---------------------|-------|
| JDBC URL | `url` | for example `database.rd.url` |
| Credentials ID | `credentialsId` | required with a JDBC URL |
| Contexts (Advanced) | `contexts` | written unless empty |
| Labels (Advanced) | `labels` | |
| Default schema (Advanced) | `defaultSchema` | |
| Liquibase schema (Advanced) | `liquibaseSchema` | |
| Changelog parameters (Advanced) | `parameters` | a map, merged name by name over `database.parameters` |
| Timeout (minutes) (Advanced) | `timeoutMinutes` | written unless 30 |

The test database also has:

| Field | Key in `database.test` | Notes |
|-------|------------------------|-------|
| Empty the test schema before each deployment | `reset` | `database.test.reset`, written only as `true` |
| Run the utPLSQL tests | `utplsql.enabled` | `database.test.utplsql.enabled`, written only as `true` |
| Test suites | `utplsql.paths` | `database.test.utplsql.paths` |
| Owner of the tests | `utplsql.owner` | `database.test.utplsql.owner` |
| Folder of the packages | `utplsql.sourcePath` | `database.test.utplsql.sourcePath` |
| Folder of the test packages | `utplsql.testPath` | `database.test.utplsql.testPath` |
| utPLSQL timeout (minutes) | `utplsql.timeoutMinutes` | `database.test.utplsql.timeoutMinutes`, written unless 30 |

A SQL and PL/SQL service also writes `dast.enabled: false`. It writes no `deployTarget`, `build.buildPath`, `build.gradle`, `build.maven`, `build.node`, `coverage`, `tests.unitTests`, `deploy`, `delivery`, `goldenFix`, `flutter` or `tools.nexusIq`.

### Unit tests and coverage

| Field | Key | Notes |
|-------|-----|-------|
| Test results | `tests.unitTests.unitTestResult` | Gradle builds |
| Coverage reports | `coverage.reportPath` | Usually left empty; one path or pattern per line |
| Enable Cypress unit tests | `tests.unitTests.cypress.enabled` | Runs Cypress tests and collects coverage |
| Cypress command | `tests.unitTests.cypress.command` | Defaults to `npx cypress run --component` |
| Cypress reports folder | `tests.unitTests.cypress.reportsDir` | Directory containing JUnit results |
| Root folder (Advanced) | `tests.unitTests.rootDir` | Maven builds |
| Report folder (Advanced) | `tests.unitTests.reportOutDir` | Maven builds |
| Accept a run without test results (Advanced) | `tests.unitTests.allowEmptyResults` | Maven builds |

Leave Coverage reports empty unless the pipeline reads the wrong reports. Empty, the Unit tests stage finds every JaCoCo XML and LCOV report of the service by itself: in the Directory of its unit test command, otherwise of its build command, otherwise in the Source folder; also in the Folder with package.json of its Node.js build; and, when it finds none there, in the whole repository. On Windows agents the stage does not look for LCOV reports: it reads the usual JaCoCo report of the build tool, and when that is missing every `jacoco.xml` of the workspace, or `total_lcov.info` for Flutter, so a service on a Windows agent names every report in Coverage reports, the LCOV report of the frontend included. A JaCoCo report needs `python3` on the agent. In a folder that several services share, each service takes the reports of its own build tool, and the report of the run says when a report also counts for another service. The line coverage of the service is the average of its reports. The report of the run lists every report it found, and says why a report is not counted, for example because it is a copy of another one or combines other reports.

For Node.js frontends or fullstack applications using Cypress, ticking **Enable Cypress unit tests** (`tests.unitTests.cypress.enabled`) runs Cypress unit and component tests during the Unit tests stage. You can configure a custom command in **Cypress command** (`tests.unitTests.cypress.command`) and the test report folder in **Cypress reports folder** (`tests.unitTests.cypress.reportsDir`). Code coverage is automatically collected from `coverage/lcov.info` generated by Cypress instrumentation.

Filled in, only the reports named in the field count. Name the report of the frontend too, and for a Flutter service the `lcov.info` of each package: the library now reads the field for Flutter as well, where it ignored it before. Paths are relative to the repository root; `*`, `?` and `**` are allowed. When none of them exists, the stage searches as if the field were empty. An imported `coverage.reportPath` longer than the 500 characters the portal keeps is left out with a note.

### Test jobs

| Field | Key | Notes |
|-------|-----|-------|
| Parallel jobs, every stage | `tests.maxParallel` | Library default: test jobs at the same time |
| Parallel jobs (smoke, regression, performance) | `tests.smoke.maxParallel`, `tests.regression.maxParallel`, `tests.performance.maxParallel` | |
| Poll interval (seconds) of a stage | `tests.smoke.pollIntervalSec`, `tests.regression.pollIntervalSec`, `tests.performance.pollIntervalSec` | |
| Required | `tests.smoke.required`, `tests.regression.required`, `tests.performance.required` | written only as `false` |
| Stage of a job | `tests.smoke.jobs`, `tests.regression.jobs`, `tests.performance.jobs` | the list the job is written to |

Each job is an entry of its stage list:

| Field | Key in the entry | Notes |
|-------|------------------|-------|
| Name | `name` | |
| Jenkins job | `job`, or `url` for the full address of a job on another Jenkins | |
| Runs on | `type` | `local` or `remote`; left empty, a full address runs on another Jenkins |
| Timeout (minutes) | `timeoutMin` | |
| Parameters | `parameters` | `NAME=value` lines |
| Remote Jenkins | `remoteJenkins` | another Jenkins only |
| Remote Jenkins URL | `remoteJenkinsUrl` | another Jenkins only |
| Credentials ID | `credentialsId` | another Jenkins only |
| Poll interval (seconds) | `pollIntervalSec` | another Jenkins only |
| Token credentials ID | `tokenCredentialsId` | another Jenkins only |
| Abort the remote job with this run | `abortTriggeredJob` | another Jenkins only |
| Wait for an idle remote job | `preventRemoteBuildQueue` | another Jenkins only |
| Cache the crumb | `useCrumbCache` | another Jenkins only |
| Trust every certificate | `trustAllCertificates` | another Jenkins only |
| Override the remote Jenkins' certificate trust | `overrideTrustAllCertificates` | another Jenkins only |
| Cache the job information | `useJobInfoCache` | another Jenkins only |

### Deployment

| Field | Key | Notes |
|-------|-----|-------|
| Virtual machine or OpenShift | `deployTarget` | `vm` or `openshift` |
| Disable Nexus Delivery | `deploy.vm.rd.nexusDeliveryEnabled`, `deploy.openshift.rd.nexusDeliveryEnabled` | written only as `false`, in the section of the deployment target |
| Application name | `appName` | required on OpenShift |
| Artifact name | `artifactName` | required on OpenShift |
| Built file name (Advanced) | `baseArtifactName` | |
| Delivery (Maven on virtual machines) | `delivery.maven.goals` and the other command fields | |

### UrbanCode Deploy

The settings of the section sit under `deploy.vm.dod`.

| Field | Key | Notes |
|-------|-----|-------|
| Site name | `deploy.vm.dod.siteName` | Library default |
| Deployment process | `deploy.vm.dod.deployProcess` | Library default |
| Deploy with a snapshot | `deployWithSnapshot` | `deploy.vm.dod.deployWithSnapshot` |
| Snapshot holds only the deployed versions | `includeOnlyDeployVersions` | `deploy.vm.dod.includeOnlyDeployVersions` |
| Update the snapshot's components | `updateSnapshotComp` | `deploy.vm.dod.updateSnapshotComp` |
| Deploy only changed versions | `deployOnlyChanged` | `deploy.vm.dod.deployOnlyChanged` |
| Do not wait for the deployment result | `skipWait` | `deploy.vm.dod.skipWait` |
| Deployment description | `deploy.vm.dod.deployDescription` | |
| Request properties | `deploy.vm.dod.requestProperties` | |
| Applications | `deploy.vm.dod.applications` | a list |

Each application is an entry of `deploy.vm.dod.applications`:

| Field | Key in the entry | Notes |
|-------|------------------|-------|
| Application name | `applicationName` | |
| Order | `order` | |
| Environments | `environments` | left empty: all of them |
| Snapshot name | `snapshotName` | |
| Site name | `siteName` | left empty: the setting above |
| Deployment process | `deployProcess` | left empty: the setting above |
| Deploy with a snapshot, Snapshot holds only the deployed versions, Update the snapshot's components, Deploy only changed versions, Do not wait for the result | `deployWithSnapshot`, `includeOnlyDeployVersions`, `updateSnapshotComp`, `deployOnlyChanged`, `skipWait` | Setting above, Yes or No |
| Description | `description` | |
| Deployment description | `deployDescription` | |
| Request properties | `requestProperties` | |
| Components | `components` | a list |

Each component is an entry of `components`:

| Field | Key in the entry |
|-------|------------------|
| Component name | `componentName` |
| Base folder | `baseDir` |
| Version prefix | `versionPrefix` |
| Version | `version` |
| Files to include | `fileIncludePatterns` |
| Files to exclude | `fileExcludePatterns` |
| Extensions | `extensions` |
| Charset | `charset` |
| Push description | `pushDescription` |
| Version description | `versionDescription` |
| Version properties | `versionProperties` |
| Incremental version | `incrementalVersion` |

### SSH targets

Each region has its own section: `deploy.vm.rd` for RD, the lower test region, and `deploy.vm.qc` for QC, the higher test region.

| Field | Key in the region | Notes |
|-------|-------------------|-------|
| Host | `host` | Library default: the RD or QC test server |
| User | `user` | Library default |
| Deployment folder | `deployDir` | left empty: /opt/ta/deployment |
| Deployment script | `deployScript` | Library default |
| Version file | `versionFile` | Library default |

### OpenShift targets

Each region has its own section: `deploy.openshift.rd` and `deploy.openshift.qc`.

| Field | Key in the region | Notes |
|-------|-------------------|-------|
| Build project | `projectBuildR` | required in RD |
| BuildConfig file | `buildConfigPath` | required in RD |
| Dockerfile | `dockerFilePath` | required in RD |
| Build context | `buildContext` | required in RD |
| File added to the image | `addFile` | |
| Image pushed to | `qcDockerRepoPush` | required in RD |
| Image pulled from | `qcDockerRepoPull` | |
| OpenShift certificates folder | `openshiftCertDir` | |
| Nexus auth file | `nexus.authfile` | required in RD |
| Deployment project | `projectDeploymentR` | |
| Deployment file | `deployConfigPath` | |
| Configuration file | `configPathR` | |
| Health check path | `healthCheckUrl` | |
| Route host name | `routeHostnameR` | |
| Deployment path | `deploymentPath` | RD only |
| Skip deploying the configuration | `skipConfigDeploy` | |
| Repository URL (deployment repository) | `deploymentRepo.url` | RD only |
| Branch (deployment repository) | `deploymentRepo.branch` | |
| Credentials ID (deployment repository) | `deploymentRepo.credentials` | |
| Build tag | `buildTag` | |
| Internal image URL | `internalDockerUrl` | RD only |

### AppScan SAST and DAST

| Field | Key | Notes |
|-------|-----|-------|
| AppScan application ID | `appId` | required |
| AppScan API key ID | `asoc.keyId` | required |
| API key secret credentials ID | `asoc.token` | left empty: the key ID names the credential |
| Run DAST against the deployed application | `dast.enabled` | Full pipeline |
| DAST target URL | `dast.targetUrl` | |
| Presence ID | `dast.presenceId` | |
| SAST scan name (Advanced) | `sast.scanName` | left empty: the SonarQube project name followed by -sast |
| AppScan client path (Advanced) | `appscanPath` | Windows agents |
| Included folders (Advanced) | `includedDirs` | written comma-separated |
| Excluded folders (Advanced) | `excludedDirs` | written comma-separated |
| Compile before preparing the IRX (Advanced) | `asoc.doCompile` | on by default; written only as `false` |
| Scan source code only (Advanced) | `asoc.sourceCodeOnly` | |
| Use the repository's AppScan configuration (Advanced) | `asoc.useAppScanConfig` | |
| Accept any TLS certificate (Advanced) | `asoc.insecureTls` | |
| Compile command (Advanced) | `asoc.gradle.tasks`, `asoc.maven.goals` and the other command fields | Another command than the build command makes the full and security pipelines run Nexus IQ, SAST and SonarQube one after another. |

The address of AppScan on Cloud comes from the Library defaults and is written to every service as `asoc.url`.

### SonarQube

| Field | Key | Notes |
|-------|-----|-------|
| Project name | `tools.sonar.projectName` | |
| Project key | `tools.sonar.projectKey` | |
| Jenkins installation | `tools.sonar.installationName` | Library default |
| Analysis command | `tools.sonar.gradle.tasks`, `tools.sonar.maven.goals` and the other command fields | required with a project key |
| Token credentials ID (Advanced) | `tools.sonar.authToken` | |
| Scanner credentials ID (Advanced) | `tools.sonar.credentialsId` | |
| Server URL (Advanced) | `tools.sonar.serverUrl` | Library default |
| Badge token (Advanced) | `tools.sonar.badgeToken` | |
| Badge token credentials ID (Advanced) | `tools.sonar.badgeCredentialsId` | give the badge token or its credentials ID, not both |
| Badges in the Jenkins build description (Advanced) | `tools.sonar.addBadges` | |
| Every badge (Advanced) | `tools.sonar.fullBadges` | |

### Nexus IQ

A service scans one Nexus IQ application, written under `tools.nexusIq.application`.

| Field | Key | Notes |
|-------|-----|-------|
| Application | `application` | `tools.nexusIq.application` |
| Stage | `stage` | `tools.nexusIq.stage`, by default `build` |
| Treat a network error as a failed scan | `failOnNetworkError` | `tools.nexusIq.failOnNetworkError` |
| Scan patterns | `scanPatterns` | `tools.nexusIq.scanPatterns` |
| Server URL (Advanced) | `tools.nexusIq.serverUrl` | Library default |
| Credentials ID (Advanced) | `tools.nexusIq.credentialsId` | Library default |

### Bitbucket

| Field | Key | Notes |
|-------|-----|-------|
| Repository URL | `scm.bitbucket.url` | |
| Credentials ID | `scm.bitbucket.credentialsId` | required with a repository URL |
| Target branch | `scm.bitbucket.targetBranch` | left empty: the branch the pipeline runs on |
| Reviewers | `scm.bitbucket.reviewers` | |
| Sign-in (Advanced) | `scm.bitbucket.authType` | `basic` or `bearer` |
| Bitbucket (Advanced) | `scm.bitbucket.type` | `server` or `cloud`; left empty: detected from the URL |
| Clone URL (Advanced) | `scm.bitbucket.cloneUrl` | |
| Bitbucket API URL (Advanced) | `scm.bitbucket.apiUrl` | |
| Workspace (Advanced) | `scm.bitbucket.workspace` | Bitbucket Cloud |
| Project key (Advanced) | `scm.bitbucket.projectKey` | Data Center |
| Repository slug (Advanced) | `scm.bitbucket.repoSlug` | |

### GoldenFix

| Field | Key | Notes |
|-------|-----|-------|
| Run GoldenFix | `goldenFix.enabled` | Library default, On or Off |
| Shared version catalogs | `goldenFix.versionCatalogs` | the service's own list, outside the inherited policy |
| External catalog repository URL | `goldenFix.versionCatalogRepo.url` | optional separate Git/Bitbucket repository |
| External catalog credentials ID | `goldenFix.versionCatalogRepo.credentialsId` | left empty: uses SCM credentials |
| External catalog branch | `goldenFix.versionCatalogRepo.branch` | left empty: default branch |
| External catalog path | `goldenFix.versionCatalogRepo.path` | relative path to catalog file or directory |
| Ecosystems | `goldenFix.ecosystems` | Override only |
| Minimum threat level | `minThreatLevel` | `goldenFix.minThreatLevel` |
| Dependencies | `onlyDirectDependencies` | `goldenFix.onlyDirectDependencies` |
| Golden version types | `goldenVersionTypes` | `goldenFix.goldenVersionTypes` |
| Excluded folders | `excludeDirs` | `goldenFix.excludeDirs` |
| Verification | `verify.enabled` | `goldenFix.verify.enabled` |
| Attempts | `verify.maxAttempts` | `goldenFix.verify.maxAttempts` |
| Timeout (minutes) | `verify.timeoutMinutes` | `goldenFix.verify.timeoutMinutes` |
| Maven command | `verify.commands.maven` | `goldenFix.verify.commands.maven` |
| Gradle command | `verify.commands.gradle` | `goldenFix.verify.commands.gradle` |
| npm command | `verify.commands.npm` | `goldenFix.verify.commands.npm` |
| pip command | `verify.commands.pip` | `goldenFix.verify.commands.pip` |
| pub command | `verify.commands.pub` | `goldenFix.verify.commands.pub` |
| Author name | `commitAuthorName` | `goldenFix.commitAuthorName` |
| Author e-mail | `commitAuthorEmail` | `goldenFix.commitAuthorEmail` |
| Time zone | `timeZone` | `goldenFix.timeZone` |

### Monitoring

| Field | Key | Notes |
|-------|-----|-------|
| Write pipeline metrics to InfluxDB | `influx.enabled` | |
| Project tag | `influx.project` | left empty: `<CODE>-<service>` |
| Environment tag | `influx.env` | left empty: test |
| InfluxDB write URL (Advanced) | `influx.url` | Library default |
| Credentials ID (Advanced) | `influx.credentialsId` | Library default |

### Flutter

| Field | Key | Notes |
|-------|-----|-------|
| Platform | `flutter.platform` | |
| Modules | `tools.flutter.flutterModules` | |
| Tested modules | `tests.modules` | |
| Tested submodules | `tests.submodules` | |
| Tested subplugins | `tests.subplugins` | |
| Certificate password, Production licence, Test licence | `build.credentialsId` | a list in this order |
| Group (Delivery) | `delivery.group` | required on virtual machines |
| Artifact (Delivery) | `delivery.artifact` | |
| Maven plugin (Delivery) | `delivery.plugin` | |
| Sources | `tools.sonar.sources` | |
| Tests | `tools.sonar.tests` | |
| SonarScanner version | `tools.sonar.sonarScannerVersion` | |
| Dart analyze command | `tools.sonar.dartAnalyzeCommand` | |
| Flutter plugin | `tools.sonar.flutterPlugin` | |

### Library defaults

The values of Tools and servers are written to `platform`, and the per-service values (AppScan, SonarQube, Nexus IQ and InfluxDB servers) to each project unless the service sets its own. The other values are written under `defaults`.

| Field | Key | Notes |
|-------|-----|-------|
| Jenkins URL | `platform.jenkinsUrl` | |
| Shared library | `platform.jenkinsLibrary` | the name in `@Library` |
| AppScan on Cloud URL | `asoc.url` | also `platform.environment.APPSCAN_SERVER_URL` and `APPSCAN_HOST` |
| Scan client download for Linux | `SA_LINUX_URL` | `platform.environment.SA_LINUX_URL` |
| Scan client download for Windows | `SA_WIN_URL` | `platform.environment.SA_WIN_URL` |
| Proxy host | `PROXY_HOST` | `platform.environment.PROXY_HOST` |
| Proxy port | `PROXY_PORT` | `platform.environment.PROXY_PORT` |
| Proxy user | `PROXY_USER` | `platform.environment.PROXY_USER` |
| OIS host | `platform.oisHost` | |
| SonarQube server URL | `tools.sonar.serverUrl` | per project |
| SonarQube installation | `tools.sonar.installationName` | per project |
| Nexus IQ server URL | `tools.nexusIq.serverUrl` | per project |
| Nexus IQ credentials ID | `tools.nexusIq.credentialsId` | per project |
| Nexus snapshot repository URL | `platform.nexusSnapshotRepositoryUrl` | |
| Nexus snapshot repository ID | `platform.nexusSnapshotRepositoryId` | |
| InfluxDB write URL | `influx.url` | per project |
| InfluxDB credentials ID | `influx.credentialsId` | per project |
| iOS build agent | `platform.iosBuildAgent` | |
| UrbanCode Deploy site name | `deploy.vm.dod.siteName` | filled into a service with UrbanCode Deploy settings |
| UrbanCode Deploy process | `deploy.vm.dod.deployProcess` | filled in the same way |
| RD test server | `deploy.vm.rd.host` | filled into a service on virtual machines |
| QC test server | `deploy.vm.qc.host` | filled in the same way |
| SSH user | `deploy.vm.<region>.user` | filled in the same way |
| Deploy script | `deploy.vm.<region>.deployScript` | filled in the same way |
| Version file | `deploy.vm.<region>.versionFile` | filled in the same way |
| Security limits | `defaults.sast`, `defaults.sca`, `defaults.tools.nexusIq` and `defaults.dast`, each with `maxCritical`, `maxHigh` and `maxMedium` | |
| Minimum line coverage (%) | `coverage.minLine` | `defaults.coverage.minLine` |
| SAST: longest preparation | `sast.prepareTimeoutMin` | `defaults.sast.prepareTimeoutMin`; not applied yet by the library |
| SAST: longest wait for the result | `sast.pollTimeoutMin` | `defaults.sast.pollTimeoutMin` |
| SAST: check for the result every | `sast.pollIntervalSec` | `defaults.sast.pollIntervalSec` |
| DAST: longest wait for the result | `dast.pollTimeoutMin` | `defaults.dast.pollTimeoutMin` |
| DAST: check for the result every | `dast.pollIntervalSec` | `defaults.dast.pollIntervalSec` |
| DAST: longest wait for the report | `dast.reportTimeoutMin` | `defaults.dast.reportTimeoutMin` |
| DAST: check for the report every | `dast.reportIntervalSec` | `defaults.dast.reportIntervalSec` |
| Wait for the quality gate | `tools.sonar.qualityGate.waitForQualityGate` | `defaults.tools.sonar.qualityGate.waitForQualityGate` |
| Longest wait (minutes) for the quality gate | `tools.sonar.qualityGate.timeoutMinutes` | `defaults.tools.sonar.qualityGate.timeoutMinutes` |
| Run Nexus IQ and SonarQube while AppScan scans | `staticAnalysis.parallel` | `defaults.staticAnalysis.parallel`; ticked by default |
| Release gate scans | `releaseGate.scanners` | written as `sast`, `sca`, `niq` and `dast` |
| Also require the minimum line coverage | `releaseGate.requireCoverage` | `defaults.releaseGate.requireCoverage` |
| Result file | `releaseGate.stateFile` | `defaults.releaseGate.stateFile`, fixed |
| Build tool | `buildTool` | `defaults.buildTool` |
| Runs on | `deployTarget` | `defaults.deployTarget` |
| Source folder | `sourceDir` | `defaults.sourceDir` |
| Test jobs at the same time | `tests.maxParallel` | `defaults.tests.maxParallel` |
| Node.js installation | `build.node.installationName` | `defaults.build.node.installationName` |
| npm registry | `build.node.registry` | `defaults.build.node.registry` |
| .npmrc credentials ID | `build.node.npmrcCredentialsId` | `defaults.build.node.npmrcCredentialsId` |
| Liquibase folder on the agent | `tools.liquibase.home` | `defaults.tools.liquibase.home` |
| Java version (Liquibase) | `tools.liquibase.javaVersion` | `defaults.tools.liquibase.javaVersion` |
| Liquibase download | `tools.liquibase.url` | `defaults.tools.liquibase.url` |
| Its SHA-256 (Liquibase) | `tools.liquibase.sha256` | `defaults.tools.liquibase.sha256` |
| Driver on the agent | `tools.oracleJdbc.path` | `defaults.tools.oracleJdbc.path` |
| Driver download | `tools.oracleJdbc.url` | `defaults.tools.oracleJdbc.url` |
| Its SHA-256 (driver) | `tools.oracleJdbc.sha256` | `defaults.tools.oracleJdbc.sha256` |
| utPLSQL-cli folder on the agent | `tools.utplsql.home` | `defaults.tools.utplsql.home` |
| utPLSQL-cli download | `tools.utplsql.url` | `defaults.tools.utplsql.url` |
| Its SHA-256 (utPLSQL-cli) | `tools.utplsql.sha256` | `defaults.tools.utplsql.sha256` |
| Allowed databases | `database.allowedTargets` | `defaults.database.allowedTargets`, a list; a service cannot set it |
| Snapshot repository ID | `database.package.snapshotRepository.id` | `defaults.database.package.snapshotRepository.id` |
| Snapshot repository URL | `database.package.snapshotRepository.url` | `defaults.database.package.snapshotRepository.url` |
| Release repository ID | `database.package.releaseRepository.id` | `defaults.database.package.releaseRepository.id` |
| Release repository URL | `database.package.releaseRepository.url` | `defaults.database.package.releaseRepository.url` |
| GoldenFix runs by default | `goldenFix.enabled` | `defaults.goldenFix.enabled` |
| The GoldenFix policy fields | as in the GoldenFix table above | under `defaults.goldenFix` |

The SQL and PL/SQL values are written only while they are set.

The Portal monitoring block sets the portal itself and is not written to `devsecops_config.yaml`:

| Field | Portal setting |
|-------|----------------|
| InfluxDB read URL | `dso.influx.url` |
| InfluxDB organization | `dso.influx.org` |
| InfluxDB bucket | `dso.influx.bucket` |
| InfluxDB read token | `dso.influx.token` |
| Grafana instance name | `dso.grafana.instances[0].name` |
| Grafana pipeline dashboard URL | `dso.grafana.instances[0].dashboard-url` |
| Grafana security dashboard URL | `dso.grafana.instances[0].security-dashboard-url` |
| Second Grafana instance name | `dso.grafana.instances[1].name` |
| Second Grafana pipeline dashboard URL | `dso.grafana.instances[1].dashboard-url` |
| Second Grafana security dashboard URL | `dso.grafana.instances[1].security-dashboard-url` |

### Headings with keys

Some headings of the service form name the section they write to: `deploy.vm.rd` and `deploy.vm.qc` (SSH targets), `deploy.openshift.rd` and `deploy.openshift.qc` (OpenShift targets), `tools.nexusIq.application` (Nexus IQ), `deploy.vm.dod.applications` and `components` (UrbanCode Deploy), `build.credentialsId` (Flutter credentials) and `database.test`, `database.rd` and `database.qc` (Database changes).

## 18. Database and environments

### Where the portal runs

| Environment | Database | How it starts |
|-------------|----------|---------------|
| A BBH laptop | H2 in Oracle mode, in the file `~/bbh-devsecops/dso-portal/dso-portal.mv.db` (`DSO_DATA_DIR` moves the folder) | `gradlew :backend:bootRun` or `java -jar backend/build/libs/dso-portal-<version>.jar` |
| RD | Oracle | the `dso-portal` Deployment in OpenShift, profile `rd` |
| QC | Oracle | the same image, profile `qc` |
| Production | Oracle | the same image, profile `prod` |

`SPRING_PROFILES_ACTIVE` chooses the profile; `local` is the default. The profiles `rd`, `qc` and `prod` use Oracle with `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`, and a pool of 5, 10 or 20 connections by default (`DB_POOL_SIZE`). `PORT` sets the HTTP port (8080 by default). [deploy/openshift/README.md](../deploy/openshift/README.md) describes the OpenShift deployment.

### Liquibase rules

Liquibase updates the schema when the portal starts. The changesets are in `backend/src/main/resources/db/changelog`.

1. Never change a changeset that a database has already applied. Liquibase refuses to start when its checksum changes.
2. Add a new file `oracle/<next number>-<name>.sql` that starts with `--liquibase formatted sql`.
3. Give each changeset the header `--changeset dso-portal:<id> dbms:oracle,h2` and a `--rollback` that undoes it.
4. Include the file in `db.changelog-master.yaml`.
5. Keep names of constraints at 30 characters or fewer, as Oracle requires.
6. Test the update, the rollback and the update again on H2, and on Oracle Free 23 when a container can run it.

### Upgrade note: changeset 031

Changeset 031 moves the AppScan key ID and the secret credentials ID of each product to its services. It also adds the SAST scan name (`APPSCAN_SCAN_NAME`), the Sonar badge credentials ID (`SONAR_BADGE_CREDENTIALS_ID`) and the Nexus delivery switch (`NEXUS_DELIVERY_DISABLED`) to `DSO_SERVICE`.

> [!WARNING]
> A product without services loses both AppScan values, and a rollback cannot restore them. Record them before the upgrade and enter them on the first service of the product afterwards.

### Upgrade note: changeset 032

Changeset 032 adds the Node.js build and the shared version catalogs. It has three changesets:

- `032-node-build` adds to `DSO_SERVICE` the columns `NODE_ENABLED` (0 or 1, 0 by default), `NODE_DIRECTORY`, `NODE_COMMANDS`, `NODE_INSTALLATION_NAME`, `NODE_PATH`, `NODE_ENVIRONMENT`, `NODE_REGISTRY`, `NODE_NPMRC_CREDENTIALS_ID`, `NODE_TIMEOUT_MINUTES` and `NODE_NEXUS_IQ_SCAN`, with the checks `CK_DSO_SERVICE_NODE_ENABLED` and `CK_DSO_SERVICE_NODE_IQ_SCAN`.
- `032-version-catalogs` adds `GOLDEN_FIX_VERSION_CATALOGS` to `DSO_SERVICE`.
- `032-node-defaults` adds `NODE_INSTALLATION_NAME`, `NPM_REGISTRY` and `NPMRC_CREDENTIALS_ID` to `DSO_GLOBAL_SETTINGS`.

Every service starts with the Node.js build off and no catalogs, so every configuration stays as it was. A rollback drops the columns with whatever they hold.

### Upgrade note: changeset 033

Changeset `033-parallel-static-analysis` adds `PARALLEL_STATIC_ANALYSIS` (0 or 1, 1 by default) to `DSO_GLOBAL_SETTINGS`, with the check `CK_DSO_GLOBAL_PARALLEL_SCANS`. The saved settings get 1, so "Run Nexus IQ and SonarQube while AppScan scans" is ticked after the upgrade and the settings sent to Jenkins hold `defaults.staticAnalysis.parallel: true`. A library version without this order ignores the key. Untick the box before the next runs to keep the order Nexus IQ, SAST, SonarQube. A rollback drops the column.

### Upgrade note: changeset 034

Changeset 034 adds SQL and PL/SQL services. It has eight changesets, one DDL statement each:

- `034-service-database` adds the `DB_` columns of the Database changes section to `DSO_SERVICE`, and `034-service-database-flags` adds their check `CK_DSO_SERVICE_DB_FLAGS`.
- `034-service-database-target` creates `DSO_SERVICE_DATABASE_TARGET`, with one row for each test, RD or QC database of a service.
- `034-global-database` adds the `DB_` columns of the SQL and PL/SQL section to `DSO_GLOBAL_SETTINGS`.
- `034-service-build-tool-old` and `034-service-build-tool-new` recreate `CK_DSO_SERVICE_BUILD_TOOL` with `LIQUIBASE`; `034-global-build-tool-old` and `034-global-build-tool-new` do the same for `CK_DSO_GLOBAL_BUILD_TOOL`.

Every service keeps its build tool, and its settings sent to Jenkins stay as they were. The SQL and PL/SQL section of the Library defaults starts empty, so set the allowed databases and the Nexus repositories of the database packages before the first SQL and PL/SQL service is saved. The Full, Security and Extended pipelines of a SQL and PL/SQL service that delivers its package need a group ID and the repositories of [chapter 7](#what-the-pipelines-do-with-a-sql-and-plsql-service).

> [!WARNING]
> A rollback of 034 stops at the build tool checks while a service has the build tool SQL and PL/SQL (Liquibase). Change the build tool of those services, or delete them, and run the same rollback again. It then drops the columns and the table with whatever they hold.

## 19. Troubleshooting

### "Add service" stays disabled

The text next to the button names the missing fields, for example "Complete these fields first: AppScan API key ID". Click a field name. The form opens its section, under Advanced settings if needed, and puts the cursor in the field.

### A service shows "Needs attention"

A field of the service has a problem. Open the service and look for the red marks in the list of sections. Saving opens the first section with a problem.

### "give the Node.js installation or its home on the agent, not both"

The service names a Node.js installation and a Node.js home on the agent. Empty one of them. The Library default installation that a new service, a copy or an import shows as prefilled does not count while you leave it unchanged. The home wins in the library, so keep the home when the agent has a fixed Node.js folder.

### "must be off for Flutter: a Flutter build has no Gradle or Maven stage for the Node.js build to run before"

A Flutter service cannot use the Node.js build. Switch it off, or choose Gradle or Maven when the service is not a Flutter app.

### "must be off for SQL and PL/SQL (Liquibase): a database service has no build for a Node.js build to run before"

A request to the API asked for the Node.js build of a SQL and PL/SQL service. Such a service has no Gradle or Maven build that could package a frontend. Send the Node.js build switched off, as the form does, and build the frontend in the Gradle or Maven service of the repository.

### The Node.js build does not run

- The report shows no "Node.js build" stage and the log has no `[NODE]` line: no service of the run needs the build. Tick "Build the frontend with Node.js first" for the service. In SAST scanning, also turn on "Compile before preparing the IRX", and run the pipeline on an agent that is not Windows.
- The log says `[NODE] <service>: build.node.enabled is not set - no Node.js build`: another service of the run builds, this one does not. Tick "Build the frontend with Node.js first" for the service.
- The log says `[NODE] <service>: the SAST scan does not compile this service - no Node.js build`: in SAST scanning a service builds only while "Compile before preparing the IRX" is on.

### The Node.js build fails

- `npm: not found` or `node: not found`: the agent has no Node.js on its path. Set the Node.js installation (NodeJS plugin, Manage Jenkins, Tools) or the Node.js home on the agent.
- npm cannot reach its registry: set the npm registry, or an .npmrc credentials ID whose file names the registry and its login. A registry line of an .npmrc in the repository wins over that file, so change that line or remove it. The npm registry is ignored while an .npmrc credentials ID is set; otherwise it wins over the registry line of an .npmrc in the repository, so leave it empty when that .npmrc names a registry the packages need.
- The stage times out: raise the Node.js timeout (30 minutes by default).
- A failing command fails the stage, and the later stages are shown as blocked.

### The frontend is missing from the jar

- Write the Node.js output outside `build` and `target`. The `clean` of the Gradle or Maven build deletes those folders.
- Copy the output into the jar with the Gradle `processResources` or Maven `resources` snippet of [chapter 6](#package-the-frontend-into-the-jar).
- On OpenShift, the image gets only the artifact. Put the frontend into the jar, not next to it.

### The Nexus IQ stage turns orange after the Node.js build was switched on

Nexus IQ now scans the npm dependencies of `package-lock.json`. Fix them with GoldenFix, or untick "Scan the npm dependencies with Nexus IQ" when another pipeline scans them.

### `Service <service> changes a database with SQL and PL/SQL and has no open source dependencies`

The message goes on: "so a Nexus IQ GoldenFix pipeline would scan nothing. Choose another pipeline type, or delete the Nexus IQ GoldenFix pipeline of the service before you change its build tool to SQL and PL/SQL (Liquibase)". The portal refuses a Nexus IQ GoldenFix pipeline for a SQL and PL/SQL service, also when a saved service with such a pipeline changes its build tool to SQL and PL/SQL. Choose the Full, Security, Extended or SAST pipeline. To change the build tool, delete the Nexus IQ GoldenFix pipeline of the service on the product page first. In the Self-service wizard, the OSA pipeline asks you to remove such services instead.

### `Service <service> delivers its database package to Nexus in the <type> pipeline but has no <item>`

The item is the group ID, the snapshot repository ID or URL, or the release repository ID or URL. The Full and Extended pipelines need all five, the Security pipeline the group ID and the snapshot repository; see [chapter 7](#what-the-pipelines-do-with-a-sql-and-plsql-service). Set the group ID under Database changes, "Package and Nexus delivery". Set a repository there, or ask the DevSecOps team to set it in the Library defaults, section "SQL and PL/SQL (Liquibase)". When the package need not reach Nexus, untick "Deliver the package to Nexus".

### "is set, but the Library defaults allow no database yet"

The message goes on: "the pipelines deploy only to the databases the DevSecOps team allows in the Global Settings". The list "Allowed databases" of the Library defaults is empty, so no JDBC URL can be saved. Ask the DevSecOps team to add the hosts of the test, RD and QC databases. Until then, leave the JDBC URLs empty: the service can be saved, and its deployment stages are not required.

### "names a database that the Library defaults do not allow"

The JDBC URL names a host or a service that no line of "Allowed databases" matches. Check the URL for a typing error; every host of a URL with several hosts must be allowed. When the database is right, ask the DevSecOps team to add it. The library refuses the same URL when a pipeline starts, also when the line was removed after the service was saved.

### "must not contain a user or a password: keep them in the Jenkins credentials of the credentials ID"

The JDBC URL holds a user or a password, such as `scott/tiger@` or `password=`. Remove them from the URL. The user and the password belong in the *Username with password* credential that the Credentials ID names. An import never takes such a URL; its note says that the URL holds a user or a password.

### "must not be SQL and PL/SQL (Liquibase): a database service needs its own changelog, so every new service starts with Gradle, Maven or Flutter"

The build tool of Service defaults in the Library defaults cannot be SQL and PL/SQL. The page offers only Gradle, Maven and Flutter, and the API refuses the value. Choose SQL and PL/SQL (Liquibase) on each service instead.

### `[GOLDENFIX] The branch <branch> already exists in <repository>`

Two GoldenFix jobs of one repository ran in the same minute. Run one job for all services of the repository with the combined Jenkinsfile. See [chapter 8](#one-jenkins-job-for-each-repository).

### `version catalog <path> not found in the repository`

A path in "Shared version catalogs" matches no file, or the file lies in a folder that GoldenFix excludes. Check the spelling and the excluded folders of the GoldenFix policy. A path must be relative to the repository root.

### `version catalog <path> is outside the repository`

The path starts with `/` or uses `..`. Write it relative to the repository root.

### `GoldenFix: no Gradle build found for <file> - not verified`

No `settings.gradle` of the repository uses the changed catalog. GoldenFix still raises the version but cannot verify it. Review the pull request with care.

### `managed by BOM g:a:v imported in <file>: raise the BOM or add an override to the catalog`

A BOM or platform decides the version of the dependency. Raise the BOM, or add an entry for the dependency to the catalog.

### `managed by the version catalog artifact g:a:v outside this repository` or `version <name> is defined in buildSrc/<file>: not updated automatically`

GoldenFix does not change a catalog outside the repository or a version in `buildSrc`. Raise the version by hand.

### `[GOLDENFIX] The pull request comment could not be added: <reason>`

The upgrades are in the shared pull request, but the comment that lists them failed. Check that the Bitbucket credentials of the service may comment on pull requests.

### Jenkins answers 403 or 404 before the stages

- 403 `Pipeline key invalidated`: the key was invalidated. Issue a new key on the pipeline page and put it in the Jenkinsfile.
- 404 `Unknown DevSecOps pipeline key`: the Jenkinsfile holds a key the portal never issued. Copy the Jenkinsfile again from the portal.

### The import of a devsecops_config.yaml or config.yaml is refused

The portal names the line and the column of the problem. It also refuses a file that names no project setting, a file larger than 262,144 characters and a file with more than 50 projects. It also refuses several files of which none is named devsecops_config.yaml or config.yaml.

### Saving fails because someone else changed the product

Another user saved the product after you opened it. Reload the page and make your change again.

### Pipeline Monitoring shows no runs

The InfluxDB of the portal is not set, or the pipeline has not run yet. Set it in Library defaults, Portal monitoring, or with `INFLUX_URL`, and check that the service writes its metrics ("Write pipeline metrics to InfluxDB"). A service that runs in the shared job of its repository, but not with the first key, gets no runs of its own: see "One Jenkins job for each repository" in [chapter 8](#8-shared-version-catalogs-and-the-combined-jenkinsfile).

### The AppScan key of a product disappeared after the upgrade

Changeset 031 moved the key to the services. A product without services lost it. Enter the key on the first service of the product.

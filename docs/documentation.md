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
- [7. Shared version catalogs and the combined Jenkinsfile](#7-shared-version-catalogs-and-the-combined-jenkinsfile)
- [8. Pipeline types, pipeline keys, the Jenkinsfile and the Jenkins jobs](#8-pipeline-types-pipeline-keys-the-jenkinsfile-and-the-jenkins-jobs)
- [9. Import a config.yaml and copy a service](#9-import-a-configyaml-and-copy-a-service)
- [10. GoldenFix](#10-goldenfix)
- [11. The service template](#11-the-service-template)
- [12. Library defaults (Global Settings)](#12-library-defaults-global-settings)
- [13. Pipeline Monitoring and DORA](#13-pipeline-monitoring-and-dora)
- [14. Business Services](#14-business-services)
- [15. Action history](#15-action-history)
- [16. Field reference: from the portal to config.yaml](#16-field-reference-from-the-portal-to-configyaml)
- [17. Database and environments](#17-database-and-environments)
- [18. Troubleshooting](#18-troubleshooting)

## 1. What the portal does

The portal holds the DevSecOps settings of every BBH product. Jenkins runs the pipelines of the DevSecOps library DSOEnhanced. Each pipeline asks the portal for its settings when it starts. The settings never live in the repository of the service.

The portal does five things:

1. It records the products, their services and the pipelines of each service.
2. It issues a secret pipeline key for each pipeline. The key is the only thing the Jenkinsfile holds.
3. It renders the settings of a pipeline as a `config.yaml` document when Jenkins asks for them with the key.
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
| Jenkinsfile | The file in the repository of the service that tells Jenkins to run the pipeline. It holds only the pipeline key. |
| config.yaml | The settings document that the library reads. The portal renders it from its database each time a pipeline starts. |
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
| Pipeline Monitoring | See whether the latest runs passed and how often and how safely changes reach production. See [chapter 13](#13-pipeline-monitoring-and-dora). |
| Action history | See who changed what and when, with the result. See [chapter 15](#15-action-history). |
| Admin | The DevSecOps administration with four tabs: Business Services, Products, Service template and Library defaults. |

### The help icons and the key chips

Every field has a help icon. Point at it or move the keyboard focus to it to read what the field does. The grey text under a field is its hint. A hint ends with a code chip such as `build.node.enabled`. The chip is the key the field writes in `config.yaml`. [Chapter 16](#16-field-reference-from-the-portal-to-configyaml) lists every chip.

A hint that says "Library default: X" shows the value the service takes from the Library defaults page. A hint that says "Library default" alone shows that the field still holds that value.

## 3. Products and services

### The product

A product has these fields:

| Field | Meaning |
|-------|---------|
| Code | Two to 50 upper-case letters, digits, '-' or '_', starting with a letter, for example `PAYHUB`. The portal suggests it from the name. It is used in Jenkins job names and reports. It becomes `pipeline.product` in `config.yaml`. |
| Name | The full name of the product. No other product may use it. |
| Business Service | The group the product belongs to. |
| Owner team | The team that looks after the product. |
| Description | What the product does and who uses it. |
| Contact e-mail | Where the DevSecOps team reaches the owners. A team mailbox is better than a personal address. |
| Jenkins agents | Only when you add a product: the agent labels of the pipelines that the save creates, separated by commas. The first one is the default. |

The product page (Admin, Products, then a product) lists the services and their pipelines. Its More menu shows "Settings sent to Jenkins (config.yaml)" for the whole product and "Delete product". "Edit product" opens the admin editor.

### The service

A service has sections. The left-hand list of the service form shows them. A red mark next to a section means that a field in it needs your attention.

| Section | What it holds |
|---------|---------------|
| General | The service name and a description. |
| Build | The build tool, its tasks, the artifact, the JDK and the optional Node.js build (stage 0). |
| Unit tests and coverage | The unit test command, the test reports and the coverage report. |
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
| SAST | General, Build, AppScan SAST and DAST, Bitbucket (and Flutter for a Flutter service) |
| OSA (Nexus IQ GoldenFix) | General, Build, AppScan SAST and DAST, Nexus IQ, Bitbucket, GoldenFix (and Flutter) |
| Security | General, Build, Unit tests and coverage, Deployment, OpenShift targets, AppScan SAST and DAST, SonarQube, Nexus IQ, Bitbucket, GoldenFix, Monitoring (and Flutter) |
| Full | Every section that applies to the build tool and the deployment target |

The admin editor always shows every section that applies.

### Required fields

Fields marked * are required. Some fields are required only in a situation:

- The service name, the AppScan application ID and the AppScan API key ID are always required. Every pipeline, the Nexus IQ GoldenFix pipeline included, signs in to AppScan with the key of the service.
- The Gradle tasks or Maven goals of the build are required, except for Flutter.
- The JDK path is required for Flutter and while "Set up the build tool automatically" is off.
- The artifact path is required for a Maven service on virtual machines that delivers to Nexus.
- On OpenShift, the application name, the artifact name and the RD build fields are required. The QC region is optional; once one of its fields is set, the fields its deployment needs are required.
- A Full pipeline of a service on virtual machines needs an UrbanCode Deploy application for the environment DV.
- A Nexus IQ GoldenFix pipeline needs a Nexus IQ application and a Bitbucket repository.
- The Bitbucket credentials ID is required once a repository URL is set.
- A Flutter service needs its modules, its tested modules and its three credentials.

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

1. Click "Add a service". The dialog "Add a service" opens. Its tag shows the pipeline.
2. To start from another service of the product, choose it in "Copy values from". To start from a file, click "Import config.yaml". Both are optional. See [chapter 9](#9-import-a-configyaml-and-copy-a-service).
3. In General, type the service name, for example `backend-api`. The portal then fills in, from the service template: the Bitbucket repository URL, the Bitbucket credentials ID, the Nexus IQ application with its scan pattern and, on OpenShift, the application name and the image repositories. It offers the Nexus IQ application once: one you remove does not come back when you change the name.
4. In Build, choose the build tool. The hint tells you how to recognise it: "The code has a build.gradle file" means Gradle, "The code has a pom.xml file" means Maven and "The code has a pubspec.yaml file" means Flutter. The tasks and the artifact path come from the service template. Leave "Set up the build tool automatically" on.
5. If the repository holds a frontend that npm builds, for example an Angular application, tick "Build the frontend with Node.js first". Type the "Folder with package.json", for example `frontend`. Leave the other Node.js fields as they are. See [chapter 6](#6-build-the-frontend-with-nodejs-first-stage-0).
6. In AppScan SAST and DAST, type the AppScan application ID. The AppScan API key ID is filled in when another service of the product already has one; otherwise type it.
7. For the OSA pipeline, check the Nexus IQ application and the Bitbucket repository.
8. For the Full pipeline on virtual machines, add an UrbanCode Deploy application for DV.
9. For the Security and Full pipelines, choose Virtual machine or OpenShift in Deployment. On OpenShift, fill in the RD build fields of OpenShift targets.
10. In GoldenFix, list a shared version catalog only when the services of the repository share one with an unusual name. Most services leave the field empty. See [chapter 7](#7-shared-version-catalogs-and-the-combined-jenkinsfile).
11. Look at the bottom of the dialog. While something is missing, it says "Complete these fields first:" followed by the names of the fields. Click a name: the dialog opens its section and puts the cursor in the field.
12. When nothing is missing, "Add service" is active. Click it.

Add every service of the product in the same way. Under each service the list shows its build tool, the folder of its Node.js build and its repository. Use Change to correct a service and Remove to take it out. Click Next.

### Step 4: Review

Check the product, the pipeline, the Jenkins agents and what happens to each service. Services of one repository that get the pipeline in this save say so: they get one Jenkinsfile and one Jenkins job. A service that has the pipeline already keeps its Jenkinsfile and Jenkins job. Check the repository URLs here, because a typo in one of them gives two Jenkinsfiles and two jobs. Nothing is saved until you click the save button of this step. A red box lists the pipelines and keys that a removal deletes.

### Step 5: Next steps

1. Copy the Jenkinsfile of each service into a file named `Jenkinsfile` in the top folder of the repository of the service. Merge it, or send it to a developer of the service.
2. Ask your Jenkins administrator for a Pipeline job for each service, with the job names shown. The job reads the Jenkinsfile from the repository (Pipeline script from SCM).
3. Run each job once: open it, choose Build with Parameters, pick an agent and start it.
4. For a Full pipeline, add the test jobs and the deployment details in Admin, or ask the DevSecOps team.
5. For an OSA pipeline, ask a developer to review and merge the golden pull requests.
6. Follow the results in Pipeline Monitoring.

When the pipelines of two or more services share one Bitbucket repository, their Jenkins agents and one Jenkins job, as the pipelines that one save starts for a repository do, the step shows one Jenkinsfile with all their keys, asks for one Jenkins job for each repository and says where Pipeline Monitoring shows the runs of that job. See [chapter 7](#7-shared-version-catalogs-and-the-combined-jenkinsfile).

## 5. Add a service in the admin editor

The admin editor is for the DevSecOps team. It shows every field of a service.

### A new product

1. Open Admin, Products and click "Add product".
2. The dialog "Add product" asks for the product name and the Business Service. Click Continue.
3. The editor opens with the suggested product code, the Jenkins agents of the service template and one new service.
4. Fill in the product fields: Owner team, Description and Contact e-mail are optional.
5. Open the service panel ("Edit settings") and fill in the service as in [step 3 of chapter 4](#step-3-services). A new service starts with "Set up the build tool automatically" on; a Flutter service starts with it off, because Flutter needs the JDK path, and turns it on when you choose Gradle or Maven while you have not changed the checkbox. Typing its name fills in the repository URL, the Bitbucket credentials ID, the Nexus IQ application and its scan pattern from the service template.
6. Every new service gets a Full pipeline. A service on virtual machines therefore needs an UrbanCode Deploy application for DV.
7. Click "Add product". Each service gets a Full pipeline with its own key. The product page opens with the notice "Pipeline keys generated for ..." and asks you to copy each key into the Jenkinsfile of its service; services whose pipelines share the Jenkins job of their repository share one Jenkinsfile, shown below the notice. "Show key" and "Copy key" give a key at any time.

### A service of an existing product

1. Open the product page and click "Edit product".
2. Under Services, click one of:
   - "Add service" for an empty service with the service template values;
   - "Copy values from existing service" to copy another service of the product;
   - "Import config.yaml" to fill a new service from a file.
3. While a service still has missing fields, the buttons are disabled. The text "Complete these services before adding another:" names each service and its missing fields. Click a field name to open it.
4. Use "Duplicate service", "Move up", "Move down" and "Remove service" on a service panel as needed. Removing a stored service deletes its pipelines and keys when you save.
5. Click "Save changes". New services get a Full pipeline with their own key.

To add a pipeline of another type to a service, use "Add pipeline" on the product page. See [chapter 8](#8-pipeline-types-pipeline-keys-the-jenkinsfile-and-the-jenkins-jobs).

## 6. Build the frontend with Node.js first (stage 0)

Many services have a frontend, for example an Angular application, next to the Java code. The optional Node.js build runs the npm commands of that frontend in an extra stage before the Gradle or Maven build. The Gradle or Maven build then packages the frontend into the jar.

### When to use it

- Use it when the Gradle or Maven build needs the built frontend and does not run npm itself.
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

The portal writes `build.node` to `config.yaml` only while the checkbox is ticked. The library then reads the keys under `projects.<service>.build.node`. The Library defaults page can give every service a Node.js installation, an npm registry and an .npmrc credentials ID under `defaults.build.node`. The portal never writes `defaults.build.node.enabled`: every service switches the stage on for itself.

### What the pipelines do

- The stage is named "Node.js build". It runs right after "Monitor source changes" in the Full, Security, SAST scanning and Nexus IQ GoldenFix pipelines when at least one service of the run needs it. When no service needs it, the stage is left out and nothing is logged. The report numbers it 0, or 0.1, 0.2 and so on for several services, and Grafana shows it as Node.js.
- In SAST scanning a service needs the stage only while "Compile before preparing the IRX" is on and the agent is not Windows.
- In a run in which some service builds, each other service logs why it does not: `[NODE] <service>: build.node.enabled is not set - no Node.js build`, or in SAST scanning, for a service whose "Compile before preparing the IRX" is off, `[NODE] <service>: the SAST scan does not compile this service - no Node.js build`.
- The Extended pipeline builds the frontend again right before the release delivery, which rebuilds the artifact.
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

## 7. Shared version catalogs and the combined Jenkinsfile

A multimodule repository often holds several services that share one list of dependency versions. That list is a version catalog. GoldenFix raises a version there once, for every service. It verifies a Gradle catalog in the builds that use it, and a Maven pom in the aggregator pom that builds it with the poms that use it.

### What GoldenFix finds by itself

You do not need to list these files:

- Gradle version catalogs: `gradle/libs.versions.toml` and any other `*.versions.toml` file.
- The catalogs that `settings.gradle` or `settings.gradle.kts` imports with `from(files(...))` in `versionCatalogs { }`, under any name, and the `version()` and `library()` entries written there.
- The files that `apply from:` names, such as `gradle/dependencies.gradle` with `ext.versions = [...]`.
- Gradle `java-platform` modules with their `constraints`.
- Maven parent poms (through `relativePath`) and BOM poms imported with `<scope>import</scope>` inside the repository, when they are named `pom.xml`.

### When to list a catalog

Use the field "Shared version catalogs" in the GoldenFix section of the service. Write one path per line, relative to the repository root. `*` matches within one folder and `**` matches across folders, for example `catalog/*.toml`. A leading `./` is dropped. Name a file, not its folder: a path that ends in `/` is refused, also in an imported config.yaml. List a file only when GoldenFix misses it, for example a parent or BOM pom with another file name, such as `platform/bom.xml`. A listed `.toml` file is read as a Gradle catalog, an `.xml` file as a Maven pom, and any other file as a Gradle build file. A listed TOML file that no `settings.gradle` imports is used by no build, so it is raised without verification. The field belongs to the service: it is kept whether the GoldenFix policy is inherited or overridden. It renders as `goldenFix.versionCatalogs`.

A listed path that matches nothing gives the run note `version catalog <path> not found in the repository`. A path that leaves the repository gives `version catalog <path> is outside the repository`.

### What the pull request contains

- The version is raised in the catalog, once. A declaration of the same dependency elsewhere is raised to at least the catalog version. The pull request lists it as `declared outside the catalog <file> (<alias>)`, so that reviewers can move it into the catalog.
- The report, the pull request description and the commit message name the catalog file and the alias.
- A changed catalog is verified in each Gradle build that uses it. A catalog that no build uses is logged as `GoldenFix: no Gradle build found for <file> - not verified`.
- A changed parent or BOM pom is verified in the folder of the multi-module pom whose modules reach it and a pom that uses it. Without such an aggregator, GoldenFix builds the root `pom.xml`, or else the shared pom alone, so the poms that use it are not built.
- A dependency that a BOM or a platform manages gets the reason `managed by BOM g:a:v imported in <file>: raise the BOM or add an override to the catalog`.

### One Jenkins job for each repository

Services that share a repository and a catalog should run in one Jenkins job. One GoldenFix pull request then covers the whole repository. Two jobs of one repository that run in the same minute compute the same branch name, and the second job fails with `[GOLDENFIX] The branch <branch> already exists in <repository> - another GoldenFix job of this repository ran in the same minute; run one GoldenFix job for all services of a repository (pipelineKeys)`.

The library takes the Jenkins agents of a run from the first key, so only pipelines that run on the same agents share a job. The portal treats pipelines as one job when they have the same repository, pipeline type, Jenkins agents and Jenkins job:

- The pipelines that one save starts for the services of a repository get one Jenkins job, the job of the first of them. A new pipeline joins a job that two or more pipelines of its type, repository and agents run in already. Otherwise it gets the job of the service template, so the services of a product that run in jobs of their own keep doing so.
- The product page shows a notice for each repository and pipeline type whose pipelines share one job. It names the services and offers "Show the Jenkinsfile". The Jenkinsfile item of each of these pipelines and the pipeline page show the same combined Jenkinsfile. A pipeline with a job of its own keeps its own Jenkinsfile.
- The Next steps of the Self-service wizard show one Jenkinsfile for the services whose pipelines share a job and ask for one Jenkins job for each repository.
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

## 8. Pipeline types, pipeline keys, the Jenkinsfile and the Jenkins jobs

### Pipeline types

| Type | Name in Self-service | Entry point | `{type}` in job names | What it runs |
|------|----------------------|-------------|------------------------|--------------|
| Full | Full | `devSecOpsPipeline` | `full` | Build, unit tests, Nexus IQ, SAST, SonarQube, deployment to RD, smoke, regression and performance tests, DAST, deployment to QC and the release |
| Security | Security | `devSecOpsSecurityPipeline` | `security` | Build and security scans. It can start an Extended pipeline after its scans. |
| Extended | (admin only) | `devSecOpsExtendedPipeline` | `extended` | The deployment and tests started by a Security pipeline |
| SAST scanning | SAST | `devSecOpsSASTScanningPipeline` | `sast` | The AppScan static scan of the sources only |
| Nexus IQ GoldenFix | OSA | `devSecOpsNexusIqGoldenFixPipeline` | `nexusiq` | The Nexus IQ scan; GoldenFix opens a pull request with safe versions in the Bitbucket repository |

`pipeline.type` holds the value in the fourth column (`full` for the Full pipeline) and `pipeline.entryPoint` the entry point.

### Pipeline settings

"Add pipeline" on the product page and "Edit pipeline settings" open the pipeline dialog:

| Field | Meaning |
|-------|---------|
| Pipeline type | Fixed once the pipeline exists. A service has at most one pipeline of each type. |
| Jenkins agents | Agent labels or label expressions, separated by commas. The first is the default. A run uses the one chosen in its AGENT_NAME parameter. Written as `pipeline.agentNames`. |
| Jenkins job | The path of the job, for example `DevSecOps/CERT/backend-api-full`, or its full address. The portal links the pipeline and its runs to it. It is not written to `config.yaml`. |
| Extended pipeline job | Security only, optional: the Extended pipeline started after the scans. Written as `jenkins.pipeline.extendedPipeline`. |
| Security pipeline job | Extended only: the Security pipeline whose build this pipeline deploys and tests. Written as `pipeline.securityPipeline`. |
| Description | A note for the people who look after the pipeline. It is not written to `config.yaml`. |

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

The portal records when Jenkins last used a key. "Settings sent to Jenkins (config.yaml)" shows the settings without counting as a use.

### The Jenkinsfile

The Jenkinsfile holds only the library name and the key:

```groovy
@Library('DevSecOpsJenkinsLibrary') _

devSecOpsPipeline(pipelineKey: '<pipeline key>')
```

The library name comes from the Library defaults (`platform.jenkinsLibrary`). Copy the Jenkinsfile from the pipeline page, from the product page or from the Next steps of the Self-service wizard. For a pipeline that shares the Jenkins job of its repository with other services, each of these shows the combined Jenkinsfile with the keys of all of them. When such a key is invalidated, the pipeline page asks you to replace the shared Jenkinsfile once the key is regenerated.

### Jenkins jobs

- Each pipeline needs a Jenkins Pipeline job that reads the Jenkinsfile from the repository (Pipeline script from SCM).
- The service template names the job, for example `DevSecOps/{CODE}/{service}-{type}` gives `DevSecOps/PAYHUB/gateway-full`.
- The pipelines that one save starts for the services of a repository share one job and one Jenkinsfile with `pipelineKeys`. A new pipeline joins a job only when two or more pipelines of its repository run in it already. See [chapter 7](#7-shared-version-catalogs-and-the-combined-jenkinsfile).
- Jenkins needs the global environment variable `DSO_PORTAL_URL`, for example `https://dso-portal.apps.bbh.com`.

## 9. Import a config.yaml and copy a service

### Import a config.yaml

"Import config.yaml" reads a `config.yaml` of the library, a configuration the portal rendered or its JSON form. It fills a new service form. Nothing is stored until you save.

- A file with several projects asks which service to import.
- The notes under the form list what the portal did not take over ("Not imported") and what you should check ("Check").
- Secrets in the file, such as `asoc.keySecret`, are never echoed.
- A value equal to a Library default comes back empty, so the service keeps following the Library default. The same holds for the library's own Node.js defaults: `npm ci` then `npm run build`, 30 minutes and the Nexus IQ scan of `package-lock.json`.
- `build.node` without `enabled: true` comes back switched off, with its values kept.
- A Flutter file with `build.node.enabled: true` comes back switched off with the note "Check: the Node.js build is not used with Flutter".
- `goldenFix.versionCatalogs` fills "Shared version catalogs".
- In the admin editor, the imported service keeps what the file gives: an empty repository URL, Bitbucket credentials ID or Nexus IQ application stays empty. Once you change its name, the service template fills in an empty repository URL and Bitbucket credentials ID. It does not add a Nexus IQ application: that stays as the file gives it. In the Self-service dialog, the imported service follows its name like a new service: the service template fills in an empty repository URL and Nexus IQ application. The Bitbucket credentials ID stays as the file gives it, even when it is empty. The notes do not list the values filled in, so check them in the Bitbucket and Nexus IQ sections.
- The Jenkins agents of the imported pipeline fill the Jenkins agents of the product while you have not changed them.
- The file may hold at most 262,144 characters and 50 projects.

### Copy a service

"Copy values from existing service" (admin editor), "Duplicate service" (service panel) and "Copy values from" (Self-service dialog) copy every setting of another service of the product, the Node.js build and the shared version catalogs included. What identifies the source service is not copied:

- The name. A duplicate is named `<source>-copy`.
- The SonarQube project name and key, the monitoring project tag and the SAST scan name stay empty.
- The OpenShift application name, the image repositories and the Nexus IQ application follow the name you type.

A new service in the Self-service wizard also takes the AppScan API key ID of another service of the product.

## 10. GoldenFix

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
- "Shared version catalogs": see [chapter 7](#7-shared-version-catalogs-and-the-combined-jenkinsfile).
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

- Maven: in the folder of each changed `pom.xml`. A parent or BOM pom that other poms use is verified in the folder of the multi-module pom that builds them together.
- Gradle: in the folder of each changed build or properties file. A catalog, a settings file or a platform is verified in the folder of each build that uses it.
- npm: in the folder of each changed `package.json`, in the Node.js environment of the service while its Node.js build is on.
- pip and pub: in the folder of each changed file.

## 11. The service template

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

## 12. Library defaults (Global Settings)

Admin, Library defaults holds the values every pipeline shares. A service that leaves a field empty takes the value from here. These values change only when a tool moves; the DevSecOps team then enters the new value. The page has seven sections.

| Section | What it holds |
|---------|---------------|
| Tools and servers | Jenkins (Jenkins URL, shared library), HCL AppScan (service URL, scan client downloads, proxy), SonarQube and Nexus IQ servers, the Nexus snapshot repository, InfluxDB, the iOS build agent and the Portal monitoring block (the InfluxDB the portal reads and the Grafana dashboards) |
| Deployment defaults | The UrbanCode Deploy site and process, and the RD and QC test servers with their SSH user, deploy script and version file |
| Security limits | The most critical, high and medium findings each scan (SAST, SCA, Nexus IQ, DAST) may report |
| Scans and coverage | The minimum line coverage and how long the pipelines wait for each scan |
| Release gate | Which scans the release gate checks, whether it requires the coverage, and its result file |
| Service defaults | The build tool, deployment target and source folder a new service starts with; the test jobs at the same time; the Node.js build defaults |
| GoldenFix defaults | Whether GoldenFix runs by default and the GoldenFix policy |

### Node.js build defaults

The block "Node.js build" in Service defaults has three fields: Node.js installation, npm registry and .npmrc credentials ID. A new service shows them as its "Library default". A service that leaves them empty takes them later as well, through `defaults.build.node` in `config.yaml`. A service that names its own installation or a Node.js home on the agent uses that instead. The BBH seed leaves all three empty.

### Which values follow later changes

- Saved services follow later changes of: the tools and servers, the deployment defaults, the security limits, the scan waiting times, the release gate, the test jobs at the same time, the Node.js build defaults and the GoldenFix policy of services that inherit it.
- The build tool, the deployment target and the source folder only prefill a new service.

### Portal monitoring

The Portal monitoring block sets the InfluxDB the portal reads (URL, organization, bucket and read token) and up to two Grafana instances. Values saved here win over the variables `INFLUX_URL`, `INFLUX_ORG`, `INFLUX_BUCKET`, `INFLUX_TOKEN` and `GRAFANA_*`. The read token is stored and never shown again. These settings are for the portal itself and are not written to `config.yaml`.

## 13. Pipeline Monitoring and DORA

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

## 14. Business Services

Admin, Business Services lists the groups of products with their product, service and pipeline counts.

- "Add business service" creates one. A name must be unique.
- A Business Service can be renamed or deleted. A Business Service that still holds products cannot be deleted.
- The portal starts with the five BBH Business Services: AI Lab, Capital Partners, Corporate Technology, Custody and Fund Services.

The Pipelines page shows the pipelines of one Business Service at a time. Its first table counts the pipelines of each Business Service, active and with an invalidated key.

## 15. Action history

The Action history records every change made in the portal: who did it, when, what was done, on which resource and with which result. Search by user, resource or details, and filter by action, resource and status.

The recorded actions are: create, update and delete a product; create, update and delete a pipeline; issue a new key; invalidate a key; create, rename and delete a business service; update the service template; update the library defaults; initialize the portal. An import of a config.yaml stores nothing and is not recorded.

The portal has no sign-in yet. Every action is recorded as the default administrator until BBH single sign-on is added.

## 16. Field reference: from the portal to config.yaml

This chapter lists every field that writes to `config.yaml`. The column "Key" shows the key in the code chip of the field. Service keys sit under `projects.<service>`. A chip that shows only the last part of a key is listed with its full key. A field that is empty writes nothing, and a checkbox that is off writes nothing unless the table says otherwise.

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
| Build tool | `buildTool` | `gradle`, `maven` or `flutter` |
| Artifact path | `build.buildPath` | |
| Set up the build tool automatically | `buildToolAutoSetup` | |
| JDK path | `javaPath` | |
| Source folder (Advanced) | `sourceDir` | the folder the AppScan static scan prepares |
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

The Gradle or Maven command of the build has the command fields of the next table.

### Command fields

Five steps share the same command fields: the build, the unit tests, the SonarQube analysis, the compile of the AppScan static scan and the Nexus delivery of a Maven service on virtual machines. The keys depend on the build tool.

| Field | Build | Unit tests | SonarQube | AppScan compile | Nexus delivery |
|-------|-------|------------|-----------|-----------------|----------------|
| Gradle tasks | `build.gradle.tasks` | `tests.unitTests.gradle.tasks` | `tools.sonar.gradle.tasks` | `asoc.gradle.tasks` | |
| Maven goals | `build.maven.goals` | `tests.unitTests.maven.goals` | `tools.sonar.maven.goals` | `asoc.maven.goals` | `delivery.maven.goals` |
| Directory (Gradle) | `build.gradle.dir` | `tests.unitTests.gradle.dir` | `tools.sonar.gradle.dir` | `asoc.gradle.dir` | |
| Directory (Maven) | `build.maven.dir` | `tests.unitTests.maven.dir` | `tools.sonar.maven.dir` | `asoc.maven.dir` | `delivery.maven.dir` |
| Maven home | `build.maven.mvnPath` | `tests.unitTests.maven.mvnPath` | `tools.sonar.maven.mvnPath` | `asoc.maven.mvnPath` | `delivery.maven.mvnPath` |
| Flags (Gradle) | `build.gradle.flags` | `tests.unitTests.gradle.flags` | `tools.sonar.gradle.flags` | `asoc.gradle.flags` | |
| Flags (Maven) | `build.maven.flags` | `tests.unitTests.maven.flags` | `tools.sonar.maven.flags` | `asoc.maven.flags` | `delivery.maven.flags` |
| Environment variables (Gradle) | `build.gradle.env` | `tests.unitTests.gradle.env` | `tools.sonar.gradle.env` | `asoc.gradle.env` | |
| Environment variables (Maven) | `build.maven.env` | `tests.unitTests.maven.env` | `tools.sonar.maven.env` | `asoc.maven.env` | `delivery.maven.env` |
| Step label (Gradle) | `build.gradle.label` | `tests.unitTests.gradle.label` | `tools.sonar.gradle.label` | `asoc.gradle.label` | |
| Step label (Maven) | `build.maven.label` | `tests.unitTests.maven.label` | `tools.sonar.maven.label` | `asoc.maven.label` | `delivery.maven.label` |
| Return the output to the pipeline (Gradle) | `build.gradle.returnStdout` | `tests.unitTests.gradle.returnStdout` | `tools.sonar.gradle.returnStdout` | `asoc.gradle.returnStdout` | |
| Return the output to the pipeline (Maven) | `build.maven.returnStdout` | `tests.unitTests.maven.returnStdout` | `tools.sonar.maven.returnStdout` | `asoc.maven.returnStdout` | `delivery.maven.returnStdout` |

### Unit tests and coverage

| Field | Key | Notes |
|-------|-----|-------|
| Test results | `tests.unitTests.unitTestResult` | Gradle builds |
| Coverage report | `coverage.reportPath` | |
| Root folder (Advanced) | `tests.unitTests.rootDir` | Maven builds |
| Report folder (Advanced) | `tests.unitTests.reportOutDir` | Maven builds |
| Accept a run without test results (Advanced) | `tests.unitTests.allowEmptyResults` | Maven builds |

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
| Compile command (Advanced) | `asoc.gradle.tasks`, `asoc.maven.goals` and the other command fields | |

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
| GoldenFix runs by default | `goldenFix.enabled` | `defaults.goldenFix.enabled` |
| The GoldenFix policy fields | as in the GoldenFix table above | under `defaults.goldenFix` |

The Portal monitoring block sets the portal itself and is not written to `config.yaml`:

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

Some headings of the service form name the section they write to: `deploy.vm.rd` and `deploy.vm.qc` (SSH targets), `deploy.openshift.rd` and `deploy.openshift.qc` (OpenShift targets), `tools.nexusIq.application` (Nexus IQ), `deploy.vm.dod.applications` and `components` (UrbanCode Deploy) and `build.credentialsId` (Flutter credentials).

## 17. Database and environments

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

## 18. Troubleshooting

### "Add service" stays disabled

The text next to the button names the missing fields, for example "Complete these fields first: AppScan API key ID". Click a field name. The form opens its section, under Advanced settings if needed, and puts the cursor in the field.

### A service shows "Needs attention"

A field of the service has a problem. Open the service and look for the red marks in the list of sections. Saving opens the first section with a problem.

### "give the Node.js installation or its home on the agent, not both"

The service names a Node.js installation and a Node.js home on the agent. Empty one of them. The Library default installation that a new service, a copy or an import shows as prefilled does not count while you leave it unchanged. The home wins in the library, so keep the home when the agent has a fixed Node.js folder.

### "must be off for Flutter: a Flutter build has no Gradle or Maven stage for the Node.js build to run before"

A Flutter service cannot use the Node.js build. Switch it off, or choose Gradle or Maven when the service is not a Flutter app.

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

### `[GOLDENFIX] The branch <branch> already exists in <repository>`

Two GoldenFix jobs of one repository ran in the same minute. Run one job for all services of the repository with the combined Jenkinsfile. See [chapter 7](#one-jenkins-job-for-each-repository).

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

### The import of a config.yaml is refused

The portal names the line and the column of the problem. It also refuses a file that names no project setting, a file larger than 262,144 characters and a file with more than 50 projects.

### Saving fails because someone else changed the product

Another user saved the product after you opened it. Reload the page and make your change again.

### Pipeline Monitoring shows no runs

The InfluxDB of the portal is not set, or the pipeline has not run yet. Set it in Library defaults, Portal monitoring, or with `INFLUX_URL`, and check that the service writes its metrics ("Write pipeline metrics to InfluxDB"). A service that runs in the shared job of its repository, but not with the first key, gets no runs of its own: see "One Jenkins job for each repository" in [chapter 7](#7-shared-version-catalogs-and-the-combined-jenkinsfile).

### The AppScan key of a product disappeared after the upgrade

Changeset 031 moved the key to the services. A product without services lost it. Enter the key on the first service of the product.

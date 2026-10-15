# Examples

Jenkinsfiles, REST calls and a rendered configuration for the DevSecOps Management Portal, and the screenshots of
both GUIs. The API scripts run against a portal started as in [Running locally](../README.md#running-locally); the
portal starts empty, so `api/create-product.sh` first adds the product the other scripts read, or the scripts take the
products you added in the portal. Every pipeline key in these files is a placeholder: take the real one from the
product page, or from `api/pipeline-key.sh`.

| File | What it shows |
|------|---------------|
| [jenkins/full-pipeline.Jenkinsfile](jenkins/full-pipeline.Jenkinsfile) | a service's full pipeline: `devSecOpsPipeline` with one key |
| [jenkins/security-pipeline.Jenkinsfile](jenkins/security-pipeline.Jenkinsfile) | a security pipeline: `devSecOpsSecurityPipeline` |
| [jenkins/extended-pipeline.Jenkinsfile](jenkins/extended-pipeline.Jenkinsfile) | an extended pipeline: `devSecOpsExtendedPipeline` |
| [jenkins/sast-pipeline.Jenkinsfile](jenkins/sast-pipeline.Jenkinsfile) | a SAST scanning pipeline: `devSecOpsSASTScanningPipeline` |
| [jenkins/nexus-iq-pipeline.Jenkinsfile](jenkins/nexus-iq-pipeline.Jenkinsfile) | a Nexus IQ GoldenFix pipeline: `devSecOpsNexusIqGoldenFixPipeline` |
| [jenkins/multi-service/gateway.Jenkinsfile](jenkins/multi-service/gateway.Jenkinsfile) | the full pipeline of Payments Hub `gateway` |
| [jenkins/multi-service/ledger.Jenkinsfile](jenkins/multi-service/ledger.Jenkinsfile) | the full pipeline of Payments Hub `ledger` |
| [jenkins/multi-service/payhub-full.Jenkinsfile](jenkins/multi-service/payhub-full.Jenkinsfile) | one full run that builds every Payments Hub service, `gateway` first |
| [api/list-departments.sh](api/list-departments.sh) | the departments with their product, service and pipeline counts |
| [api/list-products.sh](api/list-products.sh) | the products, optionally filtered by a search text |
| [api/create-product.sh](api/create-product.sh) with [api/new-product.json](api/new-product.json) | a new product with two services, as the Self-service wizard creates it |
| [api/product-pipelines.sh](api/product-pipelines.sh) | the services of a product with their pipelines and active keys |
| [api/pipeline-key.sh](api/pipeline-key.sh) | the active key of one pipeline, found by product code, service and type |
| [api/rotate-key.sh](api/rotate-key.sh) | a new key for a pipeline |
| [api/revoke-key.sh](api/revoke-key.sh) | invalidating a pipeline's key with a reason |
| [api/dso-config.sh](api/dso-config.sh) | the configuration the library reads for a key, as JSON and as YAML |
| [api/monitoring.sh](api/monitoring.sh) | the monitoring status, every product, all pipelines together, one product and one pipeline |
| [config/payhub-gateway-full.json](config/payhub-gateway-full.json), [.yaml](config/payhub-gateway-full.yaml) | the configuration of the full pipeline of the sample product Payments Hub, service `gateway` |

## Jenkinsfiles

Each file is the whole Jenkinsfile of one Jenkins job, in the form the Self-service wizard and the product page
generate: the `@Library` line loads the shared library named in the library defaults (`platform.jenkinsLibrary`,
`DevSecOpsJenkinsLibrary` by default), and the entry point of the pipeline type receives the pipeline key as a string.
Nothing else is in the file: no `config.yaml`, no credentials ID and no tool server. The library reads everything else
from the portal by the key, at `DSO_PORTAL_URL`, as described in
[How DSOEnhanced reads its configuration](../README.md#how-dsoenhanced-reads-its-configuration). During the cutover,
pin the portal-integrated version in the `@Library` line and in the library defaults, for example
`DevSecOpsJenkinsLibrary@main`.

Save a file as `Jenkinsfile` in the top folder of the service's Bitbucket repository and point a Pipeline job (Pipeline
script from SCM) at it. A service with pipelines of several types has one job per type, and the Jenkinsfile of each
job passes the key of its own pipeline. The service template names these jobs `DevSecOps/<CODE>/<service>-<type>`,
for example `DevSecOps/PAYHUB/gateway-full`.

- `full-pipeline.Jenkinsfile` runs `devSecOpsPipeline`: build, scans, tests, deployment to RD and QC, and release.
- `security-pipeline.Jenkinsfile` runs `devSecOpsSecurityPipeline`: build and security scans; when the pipeline names
  an extended pipeline job in the portal, it starts that job.
- `extended-pipeline.Jenkinsfile` runs `devSecOpsExtendedPipeline`: the deployment and tests that the security
  pipeline starts. It reads the security run's state, so the first extended run after the cutover needs one security
  build made by the portal-integrated library.
- `sast-pipeline.Jenkinsfile` runs `devSecOpsSASTScanningPipeline`: an AppScan static scan of the sources only, with
  nothing built or deployed.
- `nexus-iq-pipeline.Jenkinsfile` runs `devSecOpsNexusIqGoldenFixPipeline`: it checks out the sources, builds each
  service so that the Nexus IQ scan patterns find the built artifacts, and scans the dependencies with Nexus IQ. On a
  policy violation GoldenFix raises the golden pull request with the upgrades in the service's Bitbucket repository.
  Nothing is deployed. The service template names these jobs `DevSecOps/<CODE>/<service>-nexusiq`, for example
  `DevSecOps/PAYHUB/gateway-nexusiq`.
- `multi-service/gateway.Jenkinsfile` and `multi-service/ledger.Jenkinsfile` are the full pipelines of two services of
  the sample product Payments Hub, each in its own repository with its own key. The key in `gateway.Jenkinsfile`
  (`00000000-0000-4000-8000-000000000000`) stands for the pipeline whose configuration `config/` shows.
- `multi-service/payhub-full.Jenkinsfile` is the Jenkinsfile the product page offers as "Jenkinsfile for several
  services": `pipelineKeys` lists the keys of the full pipelines of `gateway`, `ledger`, `notifications` and
  `mobile-app`, the primary service first. One run builds them all. Extended pipelines join such a run only when they
  name the same security pipeline.

The keys stop working when they are invalidated or replaced: the next build of the job is refused with 403 and the
Jenkinsfile needs the pipeline's new key.

## API scripts

The scripts need bash, `curl` 7.76 or later and `jq`. Each one reads `PORTAL_URL` (default `http://localhost:8080`)
and prints the answer through `jq` (YAML as it comes). When the portal refuses a request, the script prints the
problem detail it sent and exits with a non-zero status.

```bash
export PORTAL_URL=http://localhost:8080
examples/api/list-departments.sh
examples/api/list-products.sh PAYHUB
PRODUCT_ID=$(examples/api/list-products.sh PAYHUB | jq '.[0].id')
examples/api/product-pipelines.sh "$PRODUCT_ID"
KEY=$(examples/api/pipeline-key.sh PAYHUB gateway FULL)
examples/api/dso-config.sh "$KEY"
```

- `list-departments.sh` calls `GET /api/departments`.
- `list-products.sh [SEARCH]` calls `GET /api/products?search=`; the search also matches the department name.
- `create-product.sh [DEPARTMENT]` looks up the department's ID by name (`Custody` by default), puts it into
  `new-product.json` and posts that to `POST /api/products?pipelineType=FULL`, as the Self-service wizard does after the
  choice of a full pipeline. The body is the product Trade Archive with `archive-api`, a Maven build on OpenShift with
  the RD and QC targets the wizard derives from the OpenShift project `cus-archive`, and `archive-gui`, a Gradle build
  on virtual machines. It carries the values the wizard fills in; the fields the wizard sends empty (`null`, `[]` or
  their defaults) are left out, which the API reads the same way. Each service gets a full pipeline with an active key.
  A second run answers 409, because the product code is taken.
- `product-pipelines.sh PRODUCT_ID` calls `GET /api/products/{id}/pipelines` and shows each pipeline's ID, type,
  entry point, Jenkins job and active key.
- `pipeline-key.sh CODE SERVICE [TYPE]` finds the product by its code and prints the active key of the service's
  pipeline of that type (`FULL` by default, `NEXUS_IQ` for the Nexus IQ GoldenFix pipeline), for use in the other
  scripts.
- `rotate-key.sh PIPELINE_ID` calls `POST /api/pipelines/{id}/keys`. An active key is invalidated with the reason
  "Replaced by a new key"; on a pipeline whose key was invalidated this regenerates it.
- `revoke-key.sh PIPELINE_ID REASON` calls `POST /api/pipelines/{id}/keys/revoke` with `{"reason": ...}`. The pipeline
  is disabled until a new key is issued; a pipeline without an active key answers 409.
- `dso-config.sh KEY` calls `GET /api/dso/config/{key}?format=json` and `GET /api/dso/config/{key}`, the requests the
  library sends, and records the key's last use. A revoked key answers 403, an unknown one 404.
- `monitoring.sh PRODUCT_ID PIPELINE_ID [RANGE]` calls `GET /api/monitoring/status`, `/products`,
  `/activity?range=`, `/products/{id}` and `/pipelines/{id}?range=` (`30d` by default) and shortens each answer;
  without `INFLUX_URL` the status says that the metrics store is not configured and the runs stay empty.

`create-product.sh`, `rotate-key.sh` and `revoke-key.sh` change the portal's data. Rotating or revoking a key of a
pipeline no Jenkins job uses changes nothing in Jenkins.

## Configuration sample

`config/payhub-gateway-full.json` and `config/payhub-gateway-full.yaml` are what the library received for the full
pipeline of the sample product Payments Hub, service `gateway`, generated with the two requests of `dso-config.sh`
from a portal that held that product. The JSON is the same document as the YAML, pretty-printed; the key itself does not appear in either.
The document has four parts: `pipeline` (type, entry point, product, the service in `projectNames` and the agent
labels), `platform` and `defaults` (from the library defaults) and `projects.gateway` (the service's build, scans, tests,
OpenShift targets, Bitbucket repository and InfluxDB tags). It names Jenkins credentials IDs, never their secrets.
`platform.jenkinsUrl` is the Jenkins URL of the library defaults. Run `api/dso-config.sh` to see the configuration
of the portal you run.

## Screenshots

`screenshots/v1` to `screenshots/v18` hold screenshots of the GUIs, one folder per version, taken while Beadle and
the DevSecOps Management Portal were still one portal with two menus; the pages look the same in the two applications
of today, each under its own header. The screenshots of the Change Evidence page are gone from every version, as the
page itself is. `screenshots/v1` to `screenshots/v13`: v7 shows the
departments, v8 the ten demo integrations with the charts of Product Management and Pipeline Monitoring, v9 the
ServiceNow production change raised from Beadle, v10 the Self-service wizard and the Admin tabs of DevSecOps
Management, Beadle Admin with the ServiceNow defaults of a product, and the production change by FixVersion, v11
the Nexus IQ GoldenFix pipeline: the Self-service wizard choosing it, the product and pipeline pages of DevSecOps Admin
and Pipeline Monitoring, and two pages of the DSOEnhanced report of the new pipeline, v12 Beadle synchronised with ProTech: the Changes tab filtered and sorted in its headers, the
ProTech workflow progress of a change, an update edited by its department, published to ProTech and applied, the New
Change wizard and Beadle Admin with departments, products and the change template of a product, and v13 the
guided New Change wizard step by step (generic request data with the magnifier lookups, Jira, approval and
notification, the schedule with a downtime window, planning, privileged access, the risk assessment lists and secure
coding, then review and raised), the change page with the new fields, its edit page and the Beadle Admin change
template in the same sections.

`screenshots/v14` shows every function of Beadle after its review: the Changes tab of a department filtered and
sorted in its headers, a change read from ProTech with its workflow progress, an escalated approval, an update ProTech
did not apply and a closed change, an open change edited in ProTech's sections, published and applied, the New Change
wizard step by step with the short description and description written on the Jira step, the review and the raised
change followed in Beadle, Beadle Admin with departments, products added without any DevSecOps setting, the change
template of a product and the refusal to delete a product whose services live in DevSecOps Management, and the
pages in a narrow window.

`screenshots/v15` shows DevSecOps Management: the Pipelines tab of your department filtered and sorted in its
headers, the page of a pipeline with its key, settings, Jenkinsfile and recent runs, a pipeline with an invalidated
key, the Self-service wizard with the defaults of the library and the service template, Pipeline Monitoring, and the
Admin tabs with the pipeline form filled from the template and the Service template page itself.

`screenshots/v16` shows step 2 of the Self-service wizard with the pipelines in their new order and names: SAST
(Static Application Security Tests) - HCL AppScan, OSA (Open Source Analysis) (NexusIQ with Golden Fix and Golden Pull
Request), Security and Full with what each one runs in smaller print, for a new product and for a product in the
portal, and in a narrow window.

`screenshots/v17` shows the New Change wizard with the template values as defaults everywhere: step 1 without a
product description, a FixVersion released in the past and the Schedule planned on the next day at the template's
times, the risk assessment with the template's answers and its option lists exactly as ProTech has them, with no "Not
assessed" entry, the review, and in Beadle Admin the change template of a product without a product description, its
risk assessment and a new product whose answers start at the first option of each list.

`screenshots/v18` shows Pipeline Monitoring reading a real InfluxDB: the overview with the delivery performance, a
product and a pipeline over 30 days, and the pipeline page with the dashboard cards of two Grafana instances.

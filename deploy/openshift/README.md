# Running the applications as containers and on OpenShift

Each application ships as one container image: its Spring Boot jar with its Angular GUI inside, on the Red Hat UBI 9
OpenJDK 17 runtime. The images run as a non-root user and also under the arbitrary UID (group 0) that OpenShift
assigns, so they fit the default `restricted-v2` security context constraint. The two applications are deployed
apart, each from its own manifests, and either one can be deployed without the other.

| Application | Dockerfile | Jar it copies | Port | Manifests |
|-------------|------------|---------------|------|-----------|
| DevSecOps Management Portal | `dso-backend/Dockerfile` | `dso-backend/build/libs/dso-portal-<version>.jar` | 8080 | `deploy/openshift/dso-portal` |
| Beadle | `beadle-backend/Dockerfile` | `beadle-backend/build/libs/beadle-<version>.jar` | 8081 | `deploy/openshift/beadle` |

## Build the images

```bash
./gradlew dsoJar beadleJar
docker build -f dso-backend/Dockerfile -t dso-portal:0.1.0-SNAPSHOT .
docker build -f beadle-backend/Dockerfile -t beadle:0.1.0-SNAPSHOT .
```

The build context is the repository root, whose `.dockerignore` lets only the two application jars through, so build
the jar first (`./gradlew dsoJar` or `./gradlew beadleJar` builds one application alone; any Gradle command that runs
`bootJar`, such as `./gradlew build`, builds both). To build on an internal mirror of the base image, pass
`--build-arg BASE_IMAGE=<mirror>/ubi9/openjdk-17-runtime:<tag>`. Podman and Buildah accept the same arguments.

## Run the images locally

```bash
docker run --rm -p 8080:8080 -v dso-portal-data:/application/data dso-portal:0.1.0-SNAPSHOT
docker run --rm -p 8081:8081 beadle:0.1.0-SNAPSHOT
```

The portal keeps its H2 database in `/application/data` (`DSO_DATA_DIR` is set to it in the image), so the volume is
what makes the data outlive the container; it starts empty but for the five BBH departments and the library
defaults. Add `-e INFLUX_URL=... -e INFLUX_TOKEN=... -e GRAFANA_DASHBOARD_URL=...` to show the metrics from your
InfluxDB and Grafana. Beadle starts the `local` profile: H2 in `/application/data` (`BEADLE_DATA_DIR`) with demo
data; give it a volume too when the demo changes should survive a restart, or `-e SPRING_PROFILES_ACTIVE=rd` with
the `DB_*` variables to run it on Oracle.

## Deploy to OpenShift

The manifests of each application are a Kustomize base (Deployment, Service, Route and a ConfigMap; the portal's
also a PersistentVolumeClaim) with one overlay per environment: `rd`, `qc` and `prod`, each starting the Spring
profile of the same name.

### The DevSecOps Management Portal

1. Push the image to a registry the cluster can pull from, then point the overlay at it:

   ```bash
   cd deploy/openshift/dso-portal/overlays/rd
   kustomize edit set image dso-portal=<registry>/<project>/dso-portal:0.1.0-SNAPSHOT
   ```

2. Set `INFLUX_URL` and the Grafana dashboard links in the overlay's `configMapGenerator` (add them next to
   `SPRING_PROFILES_ACTIVE`), with the `GRAFANA_2_*` links when a second Grafana instance holds dashboards too. The
   links must be reachable from the users' browsers, because the monitoring pages embed the dashboards. The InfluxDB
   token is optional, in its own secret:

   ```bash
   oc create secret generic dso-portal-influx --from-literal=INFLUX_TOKEN='<token>'
   ```

3. Apply the overlay:

   ```bash
   oc apply -k deploy/openshift/dso-portal/overlays/rd
   oc rollout status deployment/dso-portal
   oc get route dso-portal
   ```

The portal's data is the H2 file on the claim `dso-portal-data` (2 GiB, `ReadWriteOnce`), mounted at
`/application/data`. One pod owns the file, so the Deployment runs one replica with the `Recreate` strategy: a
rollout stops the old pod before the new one starts, and the portal is away for the seconds the new pod takes to
start. Back the claim up as any persistent volume of the cluster; the storage class of the project decides where it
lives. Liquibase updates the schema in that file when the pod starts.

| Variable | Source | Purpose |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | ConfigMap `dso-portal-config` | `rd`, `qc` or `prod` (`rd` logs at DEBUG) |
| `DSO_DATA_DIR` | Deployment | `/application/data`, the mounted claim |
| `INFLUX_URL` | ConfigMap | Your InfluxDB with the DORA metrics DSOEnhanced writes |
| `INFLUX_ORG`, `INFLUX_BUCKET` | ConfigMap (optional) | Default to `DevSecOps` and `DORA-metrics`, as DSOEnhanced writes them |
| `INFLUX_TOKEN` | Secret `dso-portal-influx` (optional) | InfluxDB read token |
| `GRAFANA_DASHBOARD_URL` | ConfigMap (optional) | Link to the DSOEnhanced pipeline dashboard on your Grafana; without any Grafana link the pipeline page shows no dashboard |
| `GRAFANA_SECURITY_DASHBOARD_URL` | ConfigMap (optional) | Link to the DSOEnhanced security dashboard, used for SECURITY, SAST and NEXUS_IQ pipelines |
| `GRAFANA_NAME` | ConfigMap (optional) | Name of that Grafana instance on the pipeline page, `Grafana` by default |
| `GRAFANA_2_DASHBOARD_URL`, `GRAFANA_2_SECURITY_DASHBOARD_URL`, `GRAFANA_2_NAME` | ConfigMap (optional) | The same links and name (`Grafana 2` by default) for a second Grafana instance; the pipeline page shows both |

Jenkins reaches the portal through its route: set the global Jenkins variable `DSO_PORTAL_URL` to it, for example
`https://dso-portal.apps.bbh.com`, so that the DSOEnhanced library reads each pipeline's configuration from
`/api/dso/config/<key>`.

### Beadle

1. Push the image and point the overlay at it:

   ```bash
   cd deploy/openshift/beadle/overlays/rd
   kustomize edit set image beadle=<registry>/<project>/beadle:0.1.0-SNAPSHOT
   ```

2. Create the database secret in the target project. The values never go into the repository:

   ```bash
   oc create secret generic beadle-db \
     --from-literal=DB_URL='jdbc:oracle:thin:@//<host>:1521/<service>' \
     --from-literal=DB_USERNAME='<user>' \
     --from-literal=DB_PASSWORD='<password>'
   ```

   The CyberTrack token is optional, in its own secret:

   ```bash
   oc create secret generic beadle-cybertrack --from-literal=CYBERTRACK_TOKEN='<token>'
   ```

3. Set `CYBERTRACK_URL` in the overlay's `configMapGenerator` to the Jira that holds the project SCP to create real
   secure coding tickets; empty keeps the demo. Then apply the overlay:

   ```bash
   oc apply -k deploy/openshift/beadle/overlays/rd
   oc rollout status deployment/beadle
   oc get route beadle
   ```

Liquibase updates the Oracle schema when the pod starts. Beadle keeps no file of its own, so its Deployment rolls
out with `RollingUpdate` and no downtime.

| Variable | Source | Purpose |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | ConfigMap `beadle-config` | `rd`, `qc` or `prod`; each runs on Oracle |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Secret `beadle-db` | Oracle connection |
| `DB_POOL_SIZE` | ConfigMap (optional) | Connection pool size, defaults per profile |
| `DSO_SIGNED_IN_USER` | ConfigMap (optional) | The name Beadle shows as the signed-in user until BBH single sign-on exists |
| `CYBERTRACK_URL` | ConfigMap (optional) | Base URL of the Jira that holds CyberTrack; empty creates demo secure coding tickets |
| `CYBERTRACK_PROJECT_KEY`, `CYBERTRACK_ISSUE_TYPE` | ConfigMap (optional) | The Jira project and issue type of a secure coding ticket, `SCP` and `Task` by default |
| `CYBERTRACK_TOKEN` | Secret `beadle-cybertrack` (optional) | Personal access token of a Jira user allowed to create issues in that project |

### Both

The startup probe allows three minutes for Liquibase, and the liveness and readiness probes use
`/actuator/health/liveness` and `/actuator/health/readiness`. The routes terminate TLS at the edge and redirect plain
HTTP. Memory is limited to 1 GiB per pod with the heap at 75% of it; change `resources` in an overlay patch if an
environment needs more.

# Running the DevSecOps Management Portal as a Container and on OpenShift

The DevSecOps Management Portal ships as a single container image: its Spring Boot jar with the compiled Angular GUI inside, running on the Red Hat UBI 9 OpenJDK 21 runtime. The image runs as a non-root user (`185`) and supports the arbitrary UID (group `0`) assigned by OpenShift (`restricted-v2` SCC).

| Application | Dockerfile | Jar it copies | Port | Manifests |
|-------------|------------|---------------|------|-----------|
| DevSecOps Management Portal | `backend/Dockerfile` | `backend/build/libs/dso-portal-<version>.jar` | 8080 | `deploy/openshift/dso-portal` |

## Build the image

```bash
./gradlew :backend:bootJar
docker build -f backend/Dockerfile -t dso-portal:0.1.0-SNAPSHOT .
```

The build context is the repository root, whose `.dockerignore` lets only the boot jar `backend/build/libs/dso-portal-<version>.jar` through. To build on an internal mirror of the base image, pass `--build-arg BASE_IMAGE=<mirror>/ubi9/openjdk-21-runtime:<tag>`. Podman and Buildah accept the same arguments.

## Run the image locally

### With embedded H2 database (default `local` profile)

```bash
docker run --rm -p 8080:8080 -v dso-portal-data:/application/data dso-portal:0.1.0-SNAPSHOT
```

### With Oracle database (`rd`, `qc`, `prod` or `oracle` profile)

```bash
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=rd \
  -e DB_URL='jdbc:oracle:thin:@//<host>:1521/<service>' \
  -e DB_USERNAME='<user>' \
  -e DB_PASSWORD='<password>' \
  dso-portal:0.1.0-SNAPSHOT
```

InfluxDB and Grafana are set either in the GUI under Admin, Library defaults, Portal monitoring (InfluxDB & Grafana), or with the environment variables `INFLUX_URL`, `INFLUX_ORG`, `INFLUX_BUCKET`, `INFLUX_TOKEN`, `GRAFANA_DASHBOARD_URL` and `GRAFANA_SECURITY_DASHBOARD_URL`; values saved in the GUI win.

## Deploy to OpenShift (with Oracle database)

The team's applications share one OpenShift project, each in its own pod; the portal is the `dso-portal` Deployment
with one pod, its Service and its Route, so it sits next to the other applications without clashing. The manifests in `deploy/openshift/dso-portal` provide a Kustomize base (`Deployment`, `Service`, `Route`, and `ConfigMap`) with three environment overlays: `rd`, `qc`, and `prod`. Each overlay activates the matching Spring profile (`rd`, `qc`, `prod`), which belongs to the `oracle` profile group.

1. Push the image to a registry the cluster can pull from, then point the overlay at it:

   ```bash
   cd deploy/openshift/dso-portal/overlays/rd
   kustomize edit set image dso-portal=<registry>/<project>/dso-portal:0.1.0-SNAPSHOT
   ```

2. Create the Oracle database secret in the target OpenShift project:

   ```bash
   oc create secret generic dso-portal-db \
     --from-literal=DB_URL='jdbc:oracle:thin:@//<host>:1521/<service>' \
     --from-literal=DB_USERNAME='<user>' \
     --from-literal=DB_PASSWORD='<password>'
   ```

   Optionally create `dso-portal-influx` to give `INFLUX_TOKEN` as a Secret instead of saving it under Admin, Library defaults:

   ```bash
   oc create secret generic dso-portal-influx --from-literal=INFLUX_TOKEN='<token>'
   ```

3. Apply the overlay:

   ```bash
   oc apply -k deploy/openshift/dso-portal/overlays/rd
   oc rollout status deployment/dso-portal
   oc get route dso-portal
   ```

Liquibase automatically migrates the Oracle schema when the pod starts, and Hibernate validates the schema (`ddl-auto: validate`). Because the database is external Oracle, the Deployment uses `RollingUpdate` for zero-downtime rollouts.

| Variable | Source | Purpose |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | ConfigMap `dso-portal-config` | `rd`, `qc` or `prod` (activates `oracle` profile group; `rd` logs at `DEBUG`) |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Secret `dso-portal-db` | Oracle JDBC connection (`ojdbc11`) |
| `DB_POOL_SIZE` | ConfigMap (optional) | Hikari connection pool size (`5` in `rd`, `10` in `qc`, `20` in `prod`) |
| `INFLUX_URL`, `INFLUX_ORG`, `INFLUX_BUCKET` | ConfigMap | InfluxDB v2 connection when Library defaults leave it empty |
| `INFLUX_TOKEN` | Secret `dso-portal-influx` | InfluxDB read token when Library defaults leave it empty |
| `GRAFANA_DASHBOARD_URL`, `GRAFANA_SECURITY_DASHBOARD_URL`, `GRAFANA_NAME` | ConfigMap | first Grafana instance when Library defaults leave it empty |
| `GRAFANA_2_DASHBOARD_URL`, `GRAFANA_2_SECURITY_DASHBOARD_URL`, `GRAFANA_2_NAME` | ConfigMap | second Grafana instance when Library defaults leave it empty |

Jenkins reaches the portal through its route: set `DSO_PORTAL_URL` (or pass `portalUrl` to `DSOEnhanced`) so the Jenkins Shared Library fetches each pipeline's configuration from `/api/dso/config/<key>?format=json`.

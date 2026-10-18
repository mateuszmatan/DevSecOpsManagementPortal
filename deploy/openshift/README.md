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

InfluxDB and Grafana are set as described in [Running locally](../../README.md#running-locally).

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

3. Apply the overlay from its folder, where step 1 left you. `oc rollout restart` starts a new pod, which pulls an image
   rebuilt under the same tag and reads changed Secrets; `oc apply` alone rolls the pod only when the Deployment or the
   ConfigMap changed.

   ```bash
   oc apply -k .
   oc rollout restart deployment/dso-portal
   oc rollout status deployment/dso-portal
   oc get route dso-portal
   ```

Liquibase automatically migrates the Oracle schema when the pod starts, and Hibernate validates the schema (`ddl-auto: validate`). Because the database is external Oracle, the Deployment uses `RollingUpdate` for zero-downtime rollouts. The pod logs to its console, which OpenShift collects, and to `/application/logs` on an `emptyDir` of 3 GB that lives as long as the pod.

The pod reads the variables of [Running locally](../../README.md#running-locally) from:

| Variable | Source |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | ConfigMap `dso-portal-config`, set by the overlay |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Secret `dso-portal-db` |
| `DB_POOL_SIZE` | ConfigMap (optional) |
| `INFLUX_URL`, `INFLUX_ORG`, `INFLUX_BUCKET`, `GRAFANA_*` | ConfigMap |
| `INFLUX_TOKEN` | Secret `dso-portal-influx` (optional) |

Jenkins reaches the portal through its route: a Jenkins administrator sets the global environment variable `DSO_PORTAL_URL` to the route's address, and DSOEnhanced fetches each pipeline's configuration from `/api/dso/config/<key>?format=json`.

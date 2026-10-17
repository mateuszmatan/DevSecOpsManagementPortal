# DevSecOps Management Portal - Quick Deployment & Run Guide

A concise guide to running the **DevSecOps Management Portal** locally and deploying it to **OpenShift**.

---

## 1. Running Locally (Developer Laptop)

### Prerequisites
- **Java 21**
- **Node.js 20.x** & **npm 10.x**

### Option A: Gradle (Recommended for Local Development)
Runs backend and Angular GUI together on port **8080** using embedded H2 database (in Oracle compatibility mode).

```bash
# Start backend with bundled GUI
./gradlew :backend:bootRun
```
- **URL**: `http://localhost:8080`
- **Default Admin User**: `Mateusz Matan` (`ADMIN` role)
- **Local DB**: File-based H2 at `~/bbh-devsecops/dso-portal/dso-portal`

*(Optional) Independent Frontend Dev Server with Hot-Reload:*
```bash
# Terminal 1: Backend API only
./gradlew :backend:bootRun -PskipGui=true

# Terminal 2: Angular frontend dev server
cd frontend
npm start
# Opens http://localhost:4200 (proxies /api to localhost:8080)
```

### Option B: Docker Container
```bash
# 1. Package the jar with the Angular GUI inside
./gradlew :backend:bootJar

# 2. Build the container image
docker build -f backend/Dockerfile -t dso-portal:latest .

# 3. Run the container locally (H2 mode)
docker run --rm -p 8080:8080 -v dso-portal-data:/application/data dso-portal:latest
```

---

## 2. Deploying to OpenShift (Multi-Pod on Oracle DB)

### Step 1: Build and Push Image
```bash
./gradlew :backend:bootJar
docker build -f backend/Dockerfile -t <registry>/<project>/dso-portal:latest .
docker push <registry>/<project>/dso-portal:latest
```

### Step 2: Create Secrets
Create the Oracle database secret (and optional InfluxDB secret) in your OpenShift project:
```bash
oc create secret generic dso-portal-db \
  --from-literal=DB_URL='jdbc:oracle:thin:@//<oracle-host>:1521/<service>' \
  --from-literal=DB_USERNAME='<db-user>' \
  --from-literal=DB_PASSWORD='<db-password>'

# Optional InfluxDB token secret (or configure via UI at /admin/settings)
oc create secret generic dso-portal-influx \
  --from-literal=INFLUX_TOKEN='<influx-token>'
```

### Step 3: Deploy with Kustomize
Select the target environment overlay (`rd`, `qc`, or `prod`):
```bash
cd deploy/openshift/dso-portal/overlays/rd

# Update image tag
kustomize edit set image dso-portal=<registry>/<project>/dso-portal:latest

# Apply manifests (2 replicas with RollingUpdate)
oc apply -k .
```

### Step 4: Verify Deployment
```bash
# Check pod rollout (2 pods running)
oc rollout status deployment/dso-portal
oc get pods -l app=dso-portal

# Get public application URL
oc get route dso-portal
```

# Jenkins Setup Guide — Order Payment System
# DockerHub: raishyam | Java 17 | Spring Boot 3.x

## Step 1 — Start All Services

```bash
# Start Jenkins + SonarQube + all infra
docker-compose up -d

# Verify all containers are running
docker-compose ps
```

Expected containers running:
- jenkins          → http://localhost:8080
- sonarqube        → http://localhost:9000
- sonarqube-db
- postgres
- kafka
- zookeeper
- order-payment-app

---

## Step 2 — Jenkins Initial Setup

### 2a. Get Admin Password
```bash
docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

### 2b. Open Jenkins UI
- Go to: http://localhost:8080
- Paste the password from above
- Click: "Install suggested plugins"
- Create your admin user

### 2c. Install Required Plugins
Go to: Manage Jenkins → Plugins → Available plugins

Search and install:
- [x] Pipeline
- [x] Git
- [x] Docker Pipeline
- [x] SonarQube Scanner
- [x] HTML Publisher
- [x] Timestamper
- [x] Blue Ocean (optional — better UI)

Click "Install" → Restart Jenkins after install.

---

## Step 3 — Configure Credentials

Go to: Manage Jenkins → Credentials → System → Global credentials → Add Credential

### 3a. DockerHub Credentials
| Field | Value |
|---|---|
| Kind | Username with password |
| Username | raishyam |
| Password | your DockerHub password or Access Token |
| ID | dockerhub-credentials |
| Description | DockerHub - raishyam |

> Tip: Use a DockerHub Access Token (hub.docker.com → Account Settings → Security → New Access Token)

### 3b. GitHub Credentials
| Field | Value |
|---|---|
| Kind | Username with password |
| Username | your GitHub username |
| Password | GitHub Personal Access Token |
| ID | github-credentials |
| Description | GitHub PAT |

### 3c. SonarQube Token (after Step 4)
| Field | Value |
|---|---|
| Kind | Secret text |
| Secret | (paste token from SonarQube — see Step 4) |
| ID | sonarqube-token |
| Description | SonarQube Token |

---

## Step 4 — Configure SonarQube

### 4a. Open SonarQube
- Go to: http://localhost:9000
- Login: admin / admin
- Change password when prompted

### 4b. Generate Token
- Go to: My Account → Security → Generate Tokens
- Name: `jenkins-token`
- Type: Global Analysis Token
- Expiry: No expiry (or set your preference)
- Click Generate → **copy the token**
- Paste it into Jenkins credential `sonarqube-token` (Step 3c)

### 4c. Create Project
- Go to: Projects → Create Project → Manually
- Project key: `order-payment-system`
- Display name: `Order Payment System`
- Click Set Up → Use existing token → paste token → Continue

### 4d. Configure SonarQube in Jenkins
Go to: Manage Jenkins → Configure System → SonarQube servers

- [x] Enable injection of SonarQube server configuration
- Name: `SonarQube`  ← must match Jenkinsfile exactly
- Server URL: `http://sonarqube:9000`  ← container name (same Docker network)
- Server authentication token: select `sonarqube-token`

Click Save.

### 4e. Configure Webhook (Quality Gate callback)
In SonarQube: Administration → Configuration → Webhooks → Create

| Field | Value |
|---|---|
| Name | Jenkins |
| URL | http://jenkins:8080/sonarqube-webhook/ |
| Secret | (leave empty for local) |

---

## Step 5 — Install SonarQube Scanner in Jenkins

Go to: Manage Jenkins → Tools → SonarQube Scanner

- Click "Add SonarQube Scanner"
- Name: `SonarQubeScanner`
- [x] Install automatically
- Version: latest

Click Save.

---

## Step 6 — Update Jenkinsfile

Open `Jenkinsfile` and replace the GitHub URL:
```groovy
url: 'https://github.com/YOUR_GITHUB_USERNAME/order-payment-system.git'
```
→ Replace `YOUR_GITHUB_USERNAME` with your actual GitHub username.

---

## Step 7 — Create Jenkins Pipeline Job

1. Jenkins Dashboard → New Item
2. Name: `order-payment-system`
3. Type: **Pipeline** → OK

### Configure:
- Build Triggers: [x] Poll SCM → Schedule: `H/5 * * * *` (every 5 min)
  OR
- Build Triggers: [x] GitHub hook trigger for GITScm polling (if webhook configured)

### Pipeline Definition:
- Definition: **Pipeline script from SCM**
- SCM: Git
- Repository URL: `https://github.com/YOUR_GITHUB_USERNAME/order-payment-system.git`
- Credentials: `github-credentials`
- Branch: `*/main`
- Script Path: `Jenkinsfile`

Click Save.

---

## Step 8 — Add .env variables

Make sure your `.env` file has these (add if missing):
```env
# SonarQube DB (for docker-compose sonarqube-db service)
SONAR_DB_USERNAME=sonar
SONAR_DB_PASSWORD=sonar
```

---

## Step 9 — First Build

1. Go to your pipeline job
2. Click **Build with Parameters**
3. BRANCH_NAME: `main`
4. IMAGE_TAG: `1.0.0`
5. Click **Build**

### Watch the stages:
```
Checkout → Build → Test → SonarQube Analysis → Quality Gate
→ Docker Build → Docker Push → Deploy
```

### Verify:
- DockerHub: https://hub.docker.com/r/raishyam/order-payment-system
- SonarQube: http://localhost:9000/dashboard?id=order-payment-system
- App health: http://localhost:9090/actuator/health

---

## Troubleshooting

### Jenkins can't reach Docker
```bash
# Fix Docker socket permissions
docker exec -u root jenkins chmod 666 /var/run/docker.sock
```

### SonarQube won't start (vm.max_map_count error)
```bash
# Run on host machine (not inside container)
sudo sysctl -w vm.max_map_count=262144
```
On Windows (WSL2):
```bash
wsl -d docker-desktop sysctl -w vm.max_map_count=262144
```

### Quality Gate always pending
- Check SonarQube webhook is configured (Step 4e)
- URL must be: `http://jenkins:8080/sonarqube-webhook/`
- Both containers must be on the same Docker network (`app-network`)

### Docker push fails
```bash
# Verify credentials ID matches Jenkinsfile exactly
# Jenkinsfile: credentials('dockerhub-credentials')
# Jenkins Credential ID must be: dockerhub-credentials
```
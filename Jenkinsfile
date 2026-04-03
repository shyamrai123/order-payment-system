// ============================================================
// Jenkinsfile — CI/CD Pipeline for Order Payment System
// DockerHub: raishyam
// Stages: Checkout → Build → Test → Wait for SonarQube
//         → SonarQube Analysis → Quality Gate
//         → Docker Build → Docker Push → Deploy
// ============================================================

pipeline {

    agent any

    // ---- Parameters ----
    parameters {
        string(name: 'BRANCH_NAME',  defaultValue: 'main',   description: 'Git branch to build')
        string(name: 'IMAGE_TAG',    defaultValue: 'latest', description: 'Docker image tag (e.g. 1.0.0, latest)')
    }

    // ---- Environment ----
    environment {
        DOCKER_CREDENTIALS  = credentials('dockerhub-credentials')   // Jenkins credential ID
        DOCKER_REGISTRY     = 'raishyam'
        IMAGE_NAME          = "${DOCKER_REGISTRY}/order-payment-system"
        FULL_IMAGE_TAG      = "${IMAGE_NAME}:${params.IMAGE_TAG}"

        SONAR_TOKEN         = credentials('sonarqube-token')          // Jenkins credential ID
        SONAR_HOST_URL      = 'http://sonarqube:9000'                 // FIX: explicit URL for Maven JVM DNS
        MAVEN_OPTS          = '-Xmx512m -XX:+UseContainerSupport'
    }

    // ---- Options ----
    options {
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
        disableConcurrentBuilds()
    }

    stages {

        // ================================================================
        // Stage 1: Checkout
        // ================================================================
        stage('Checkout') {
            steps {
                echo "========== Checkout: branch=${params.BRANCH_NAME} =========="
                checkout([
                    $class: 'GitSCM',
                    branches: [[name: "*/${params.BRANCH_NAME}"]],
                    userRemoteConfigs: [[
                        url: 'https://github.com/shyamrai123/order-payment-system.git',
                        credentialsId: 'github-credentials'
                    ]]
                ])
                sh 'git log --oneline -5'
            }
        }

        // ================================================================
        // Stage 2: Build (skip tests here — dedicated test stage below)
        // ================================================================
        stage('Build') {
            steps {
                echo '========== Build: mvn clean package =========='
                sh 'mvn clean package -DskipTests -B'
            }
            post {
                success {
                    archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
                    echo 'JAR archived successfully.'
                }
            }
        }

        // ================================================================
        // Stage 3: Test (Unit + Integration)
        // JaCoCo report generated via jacoco:report goal
        // ================================================================
        stage('Test') {
            steps {
                echo '========== Test: Unit + Integration =========='
                // jacoco:report generates target/site/jacoco/jacoco.xml
                sh 'mvn test jacoco:report -B'
            }
            post {
                always {
                    // Publish JaCoCo HTML coverage report (requires HTML Publisher plugin)
                    publishHTML([
                        allowMissing: true,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: 'target/site/jacoco',
                        reportFiles: 'index.html',
                        reportName: 'JaCoCo Coverage Report'
                    ])
                }
                failure {
                    echo '❌ Tests FAILED — pipeline will abort.'
                }
            }
        }

        // ================================================================
        // Stage 4: Wait for SonarQube to be fully UP
        // Polls /api/system/status every 15s, up to 20 attempts (5 min)
        // ================================================================
        stage('Wait for SonarQube') {
            steps {
                echo '========== Waiting for SonarQube to be ready =========='
                sh '''
                    for i in $(seq 1 20); do
                        STATUS=$(curl -s http://sonarqube:9000/api/system/status \
                            | grep -o '"status":"[^"]*"' \
                            | cut -d: -f2 \
                            | tr -d '"')
                        echo "Attempt $i — SonarQube status: $STATUS"
                        if [ "$STATUS" = "UP" ]; then
                            echo "✅ SonarQube is UP and ready"
                            exit 0
                        fi
                        sleep 15
                    done
                    echo "❌ SonarQube did not become ready within 5 minutes"
                    exit 1
                '''
            }
        }

        // ================================================================
        // Stage 5: SonarQube Analysis
        // FIX: Added -Dsonar.host.url explicitly so Maven JVM can resolve
        //      the SonarQube hostname (bypasses JVM DNS resolution issue).
        // SONAR_TOKEN is injected automatically by withSonarQubeEnv.
        // Single quotes prevent Groovy from interpolating secrets.
        // ================================================================
        stage('SonarQube Analysis') {
            steps {
                echo '========== SonarQube: Static Analysis =========='
                withSonarQubeEnv('SonarQube') {
                    sh '''
                        mvn sonar:sonar \
                            -Dsonar.projectKey=order-payment-system \
                            -Dsonar.projectName="Order Payment System" \
                            -Dsonar.host.url=http://sonarqube:9000 \
                            -Dsonar.java.coveragePlugin=jacoco \
                            -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
                            -B
                    '''
                }
            }
        }

        // ================================================================
        // Stage 6: Quality Gate
        // SonarQube webhook must be configured to notify Jenkins:
        //   SonarQube > Administration > Webhooks
        //   URL: http://<jenkins-host>:8080/sonarqube-webhook/
        // ================================================================
        stage('Quality Gate') {
            steps {
                echo '========== Quality Gate: Checking SonarQube result =========='
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        // ================================================================
        // Stage 7: Docker Build
        // ================================================================
        stage('Docker Build') {
            steps {
                echo "========== Docker Build: ${FULL_IMAGE_TAG} =========="
                sh """
                    docker build \\
                        --build-arg BUILD_DATE=\$(date -u +%Y-%m-%dT%H:%M:%SZ) \\
                        --build-arg GIT_COMMIT=\$(git rev-parse --short HEAD) \\
                        -t ${FULL_IMAGE_TAG} \\
                        -t ${IMAGE_NAME}:latest \\
                        .
                """
            }
        }

        // ================================================================
        // Stage 8: Docker Push → DockerHub (raishyam)
        // ================================================================
        stage('Docker Push') {
            steps {
                echo "========== Docker Push: ${FULL_IMAGE_TAG} → DockerHub =========="
                sh """
                    echo ${DOCKER_CREDENTIALS_PSW} | docker login \\
                        -u ${DOCKER_CREDENTIALS_USR} --password-stdin
                    docker push ${FULL_IMAGE_TAG}
                    docker push ${IMAGE_NAME}:latest
                    docker logout
                """
            }
        }

        // ================================================================
        // Stage 9: Deploy
        // Runs only on main or release/* branches
        // Pulls new image and restarts app container
        // ================================================================
        stage('Deploy') {
            when {
                anyOf {
                    branch 'main'
                    branch 'release/*'
                }
            }
            steps {
                echo "========== Deploy: ${FULL_IMAGE_TAG} =========="
                sh """
                    export IMAGE_TAG=${params.IMAGE_TAG}

                    # Pull latest image from DockerHub
                    docker pull ${FULL_IMAGE_TAG}

                    # Restart only the app container (infra containers stay up)
                    docker-compose up -d --no-deps --pull always order-payment-app

                    echo 'Waiting 45s for Spring Boot startup...'
                    sleep 45

                    # Health check on actuator endpoint
                    curl --fail http://localhost:9090/actuator/health || \\
                        (echo '❌ Health check failed!' && \\
                         docker-compose logs --tail=50 order-payment-app && \\
                         exit 1)

                    echo '✅ Deployment successful!'
                """
            }
        }
    }

    // ---- Post Actions ----
    post {
        success {
            echo "✅ Pipeline SUCCESS — Image: ${FULL_IMAGE_TAG}"
        }
        failure {
            echo '❌ Pipeline FAILED — Check logs above.'
        }
        cleanup {
            // Remove dangling images to reclaim disk space on Jenkins agent
            sh 'docker image prune -f || true'
        }
    }
}
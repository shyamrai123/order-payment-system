pipeline {

    agent any

    parameters {
        string(name: 'BRANCH_NAME', defaultValue: 'main')
        string(name: 'IMAGE_TAG', defaultValue: 'latest')
    }

    environment {
        DOCKER_CREDENTIALS = credentials('dockerhub-credentials')
        IMAGE_NAME = "raishyam/order-payment-system"
        FULL_IMAGE_TAG = "${IMAGE_NAME}:${params.IMAGE_TAG}"
    }

    options {
        timeout(time: 60, unit: 'MINUTES')
    }

    stages {

        stage('Checkout') {
            steps {
                git branch: "${params.BRANCH_NAME}",
                    url: 'https://github.com/shyamrai123/order-payment-system.git'
            }
        }

        stage('Maven Build') {
            steps {
                sh "mvn clean package -DskipTests -B -ntp"
            }
        }

        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('SonarQube') {
                    sh """
                        mvn sonar:sonar \
                        -Dsonar.projectKey=order-payment-system \
                        -Dsonar.scm.disabled=true
                    """
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh """
                    docker build \
                    -t ${FULL_IMAGE_TAG} \
                    -t ${IMAGE_NAME}:latest .
                """
            }
        }

        stage('Docker Push') {
            steps {
                sh """
                    echo \${DOCKER_CREDENTIALS_PSW} | docker login \
                    -u \${DOCKER_CREDENTIALS_USR} --password-stdin

                    docker push ${FULL_IMAGE_TAG}
                    docker push ${IMAGE_NAME}:latest
                """
            }
        }

        stage('Deploy') {
            steps {
                sh """
                    docker pull ${FULL_IMAGE_TAG}
                    docker compose up -d --no-deps order-payment-app
                """
            }
        }
    }

    post {
        success {
            echo "✅ SUCCESS: ${FULL_IMAGE_TAG}"
        }
        failure {
            echo "❌ FAILED"
        }
    }
}
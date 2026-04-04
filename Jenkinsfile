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

        MAVEN_OPTS = "-Xmx512m -Djava.net.preferIPv4Stack=true"
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

        stage('Build') {
            steps {
                sh 'mvn clean package -DskipTests -B'
            }
        }

        stage('Test') {
            steps {
                sh 'mvn test -B'
            }
        }

        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('SonarQube') {
                    sh '''
                        mvn sonar:sonar \
                        -Dsonar.projectKey=order-payment-system \
                        -Dsonar.host.url=http://sonarqube:9000 \
                        -Dsonar.login=${SONAR_AUTH_TOKEN}
                    '''
                }
            }
        }

        stage('Quality Gate') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh """
                    docker build --network=host \
                    -t ${FULL_IMAGE_TAG} \
                    -t ${IMAGE_NAME}:latest .
                """
            }
        }

        stage('Docker Push') {
            steps {
                sh """
                    echo ${DOCKER_CREDENTIALS_PSW} | docker login \
                    -u ${DOCKER_CREDENTIALS_USR} --password-stdin

                    docker push ${FULL_IMAGE_TAG}
                    docker push ${IMAGE_NAME}:latest
                """
            }
        }

        stage('Deploy') {
            steps {
                sh """
                    docker pull ${FULL_IMAGE_TAG}
                    docker-compose up -d --no-deps order-payment-app
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
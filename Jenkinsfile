pipeline {
    agent any

    environment {
        AWS_REGION = 'eu-north-1'
        ECR_REGISTRY = '735726038284.dkr.ecr.eu-north-1.amazonaws.com'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Verify Tools') {
            steps {
                sh '''
                    java -version
                    mvn -version
                    docker --version
                    aws --version
                '''
            }
        }

        stage('Build') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Validate Docker Compose') {
            steps {
                sh 'docker compose config -q'
            }
        }

        stage('Build Docker Images') {
            steps {
                sh '''
                    IMAGE_TAG="1.${BUILD_NUMBER}"

                    docker build -t user-service:${IMAGE_TAG} ./user-service
                    docker build -t product-service:${IMAGE_TAG} ./product-service
                    docker build -t order-service:${IMAGE_TAG} ./order-service
                '''
            }
        }

        stage('ECR Authentication') {
            steps {
                sh '''
                    aws ecr get-login-password --region ${AWS_REGION} |
                    docker login --username AWS --password-stdin ${ECR_REGISTRY}
                '''
            }
        }

        stage('Tag Images') {
            steps {
                sh '''
                    IMAGE_TAG="1.${BUILD_NUMBER}"

                    for service in user-service product-service order-service; do
                        docker tag ${service}:${IMAGE_TAG} ${ECR_REGISTRY}/${service}:${IMAGE_TAG}
                    done
                '''
            }
        }

        stage('Push Images to ECR') {
            steps {
                sh '''
                    IMAGE_TAG="1.${BUILD_NUMBER}"

                    for service in user-service product-service order-service; do
                        docker push ${ECR_REGISTRY}/${service}:${IMAGE_TAG}
                    done
                '''
            }
        }
    }
}

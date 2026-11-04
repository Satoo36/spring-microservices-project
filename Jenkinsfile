pipeline {
    agent any

    environment {
        PATH = "/usr/bin:/usr/local/bin:${env.PATH}"
    }

    stages {
        stage('Checkout') {
            steps {
                echo 'Checking out microservices source code'
                checkout scm
            }
        }

        stage('Verify Tools') {
            steps {
                sh 'java -version'
                sh 'mvn -version'
                sh 'docker --version'
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
                    docker build -t eureka-server:1.0 ./eureka-server
                    docker build -t config-server:1.0 ./config-server
                    docker build -t user-service:1.0 ./user-service
                    docker build -t product-service:1.0 ./product-service
                    docker build -t order-service:1.0 ./order-service
                    docker build -t api-gateway:1.0 ./api-gateway
                '''
            }
        }
    }
}

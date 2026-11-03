pipeline {
    agent any

    environment {
        // Build with JDK 21 (the java on PATH is 25)
        JAVA_HOME = 'C:\\Users\\sathyaraj.ramar\\.jdks\\jdk-21.0.12.1+1'
        PATH = "${JAVA_HOME}\\bin;${env.PATH}"
    }

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '10'))
        disableConcurrentBuilds()
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                // clean build of all modules (common-lib first, so no stale jar)
                bat 'mvnw.cmd -B clean install -DskipTests'
            }
        }

        stage('Test') {
            steps {
                // keep going past failing tests so we still get the report
                bat 'mvnw.cmd -B test -Dmaven.test.failure.ignore=true'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Archive') {
            steps {
                archiveArtifacts artifacts: '**/target/*.jar', excludes: '**/target/*-plain.jar', fingerprint: true
            }
        }
    }

    post {
        success { echo 'Build OK - jars archived.' }
        failure { echo 'Build FAILED - check the console log.' }
    }
}

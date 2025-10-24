pipeline {
    agent {label 'worker'}
    stages {
        stage('Trigger edc-identityhub pipeline') {
            steps {
                script {
                    build job: 'DS4H/Microservices/edc-identityhub', parameters: [
                    ]
                }
            }
        }
    }
}
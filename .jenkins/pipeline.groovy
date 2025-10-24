pipeline {
    agent any

    environment {
        DOCKER_IMAGE = "${env.JOB_NAME.tokenize('/').last().toLowerCase()}"
        DOCKER_TAG = "${env.BUILD_NUMBER}"
        IMAGE_NAME = "${DOCKER_IMAGE}:${DOCKER_TAG}"
        COMMIT_AUTHOR = sh(script: "git show -s --pretty='%an <%ae>' ${GIT_COMMIT}", returnStdout: true).trim()

        GRADLEW = "./gradlew"
    }

    parameters {
        booleanParam(name: 'PUBLISH_IMAGE', defaultValue: false, description: 'Publish Docker image after build')
    }

    stages {
        stage('Lint') {
            steps {
                sh """
                    docker build --target builder -t ${IMAGE_NAME} .
                    docker run --rm -v \$PWD:/workspace -w /workspace ${IMAGE_NAME} \
                        chmod +x ${GRADLEW} && ${GRADLEW} checkstyleMain checkstyleTest
                """
            }
            post {
                always {
                    archiveArtifacts artifacts: 'build/reports/checkstyle/*.html', allowEmptyArchive: true
                }
            }
        }
        
        stage('Build') {
            steps {
                sh """
                    docker run --rm -v \$PWD:/workspace -w /workspace ${IMAGE_NAME} \
                        chmod +x ${GRADLEW} && ${GRADLEW} shadowJar --no-daemon -x test
                """
            }
        }
        
        stage('Test') {
            steps {
                sh """
                    docker run --rm -v \$PWD:/workspace -w /workspace ${IMAGE_NAME} \
                        chmod +x ${GRADLEW} && ${GRADLEW} test
                """
            }
            post {
                always {
                    junit testResults: 'build/test-results/**/*.xml', allowEmptyResults: true
                }
            }
        }

        stage("Code Quality Check (SonarQube)") {
            steps {
                withSonarQubeEnv('sonar') {
                    script {
                        def scannerHome = tool "sonar"
                        sh """
                            ${scannerHome}/bin/sonar-scanner -X \
                                -Dsonar.projectKey=${DOCKER_IMAGE} \
                                -Dsonar.sources=. \
                                -Dsonar.java.binaries=extensions/**/build/classes/java/main \
                                -Dsonar.java.libraries=build/libs,extensions/**/build/libs
                        """
                    }
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                sh "docker build -t ${IMAGE_NAME} ."
            }
        }

        stage("Security Scan (Trivy)") {
            steps {
                sh """
                    docker run --rm \
                        -v /var/run/docker.sock:/var/run/docker.sock \
                        -v \$PWD/built/reports/trivy:/output \
                        -v \$PWD/.jenkins/trivy-html.tpl:/template.tpl \
                        aquasec/trivy:latest \
                        image ${IMAGE_NAME} \
                        --exit-code 1 --timeout 15m --severity HIGH,CRITICAL \
                        --format template --template "@/template.tpl" --output /output/trivy.html
                """
            }
            post {
                always {
                    archiveArtifacts 'built/reports/trivy/trivy.html'
                }
            }
        }

        stage('Publish Docker Image') {
            when {
                expression { return params.PUBLISH_IMAGE == true }
            }
            steps {
                // Replace with your internal/private registry push command
                sh """
                    echo "Pushing to private registry..."
                    docker push ${IMAGE_NAME}
                """
            }
        }
    }

    post {
        always {
            script {
                cleanWs()
                sh """
                    docker rmi -f ${IMAGE_NAME}
                """

                // Detect if build was triggered manually or by SCM
                def causes = currentBuild.getBuildCauses()
                def isManual = causes.any { it.toString().contains('UserIdCause') }
                def isSCM = causes.any { it.toString().contains('SCMTrigger') || it.toString().contains('GitLabWebHookCause') }

                if (isManual) {
                    echo "Build test triggered manually — sending email to Requester"
                    emailext(
                        recipientProviders: [[$class: 'RequesterRecipientProvider']],
                        subject: "Manual Build ${currentBuild.currentResult}: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                        body: "The manually triggered Jenkins build (${env.JOB_NAME} #${env.BUILD_NUMBER}) has completed with result: ${currentBuild.currentResult}.\n\nDetails: ${env.BUILD_URL}"
                    )
                } else if (isSCM) {
                    echo "Build triggered by SCM — sending email to Developers"
                    emailext(
                        recipientProviders: [[$class: 'DevelopersRecipientProvider']],
                        subject: "Committed Build ${currentBuild.currentResult}: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                        body: "The Jenkins build was triggered by a code commit and has completed with result: ${currentBuild.currentResult}.\n\nDetails: ${env.BUILD_URL}"
                    )
                } else {
                    echo "Build triggered by another cause — sending default notification"
                    emailext(
                        recipientProviders: [[$class: 'DevelopersRecipientProvider']],
                        subject: "Build ${currentBuild.currentResult}: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                        body: "This build was triggered automatically or by another cause with result: ${currentBuild.currentResult}..\n\nDetails: ${env.BUILD_URL}"
                    )
                }
            }
        }
    }
}

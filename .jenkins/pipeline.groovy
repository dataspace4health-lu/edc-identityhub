pipeline {
    agent any

    environment {
        DOCKER_IMAGE = "${env.JOB_NAME.tokenize('/').dropRight(1).takeRight(1).join('-').toLowerCase()}"
        DOCKER_TAG = "${env.BUILD_NUMBER}"
        IMAGE_NAME = "${DOCKER_IMAGE}:${DOCKER_TAG}"
    }

    parameters {
        choice(name: 'PUBLISH_IMAGE', choices: ['', 'ds4h-registry:5432', 'ds4hacrshared.azurecr.io'], description: 'Publish Docker image after build to this repo')
    }

    stages {
        stage('Lint') {
            steps {
                sh """
                    docker build --target builder -t ${IMAGE_NAME} .
                    docker run --rm -v \$PWD:/workspace -w /workspace ${IMAGE_NAME} \
                        chmod +x ./gradlew && ./gradlew checkstyleMain checkstyleTest
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
                        chmod +x ./gradlew && ./gradlew shadowJar --no-daemon -x test
                """
            }
        }
        
        stage('Test') {
            steps {
                sh """
                    docker run --rm -v \$PWD:/workspace -w /workspace ${IMAGE_NAME} \
                        chmod +x ./gradlew && ./gradlew test
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
                                -Dsonar.java.libraries=build/libs,extensions/**/build/libs \
                                -Dsonar.branch.name=${env.GIT_BRANCH.replaceFirst("^origin/", "")}
                        """

                        // // Wait for the Quality Gate result
                        // timeout(time: 10, unit: 'MINUTES') {
                        //     def qg = waitForQualityGate()
                        //     if (qg.status != 'OK') {
                        //         error "❌ Quality Gate failed: ${qg.status}"
                        //     } else {
                        //         echo "✅ Quality Gate passed: ${qg.status}"
                        //     }
                        // }
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
                expression { return params.PUBLISH_IMAGE != '' }
            }
            steps {
                script {
                    // Get latest tag
                    def tag = sh(
                        script: "git describe --tags --abbrev=0 2>/dev/null || echo \"0.0.0\"", 
                        returnStdout: true
                    ).trim()
                    tag = tag.replaceFirst(/^v/, "") // Trim leading v
                    // Get version defined in the code
                    def version = sh(
                        script: "grep '^version' build.gradle.kts | head -1 | sed -E 's/version *= *\"([^\"]*)\"/\\1/'",
                        returnStdout: true
                    ).trim()
                    version = version.replaceFirst(/^v/, "") // Trim leading v
                    version = version ?: "0.0.0" // default to 0.0.0
                    if (version != tag) {
                        error("❌ Version and Tag mismatch")
                    }
                    
                    // Set the Tag to: 
                    // 1. <version number> if there is a tag and the commit is the one of the tag
                    // 2. <version number>-<commit hash> if there is a tag but the commit is ahead
                    // 3. 0.0.0 if no tag exists
                    tag = sh(
                        script: "git describe --tags --exact-match 2>/dev/null || git describe --tags 2>/dev/null || echo 0.0.0", 
                        returnStdout: true
                    ).trim()
                    tag = tag.replaceFirst(/^v/, "") // Trim leading v
                    
                    sh """
                        echo "Tagging image ${IMAGE_NAME} ${params.PUBLISH_IMAGE}/${DOCKER_IMAGE}:${tag}."
                        docker tag ${IMAGE_NAME} ${params.PUBLISH_IMAGE}/${DOCKER_IMAGE}:${tag}
                        echo "Pushing image ${params.PUBLISH_IMAGE}/${DOCKER_IMAGE}:${tag}."
                        docker push ${params.PUBLISH_IMAGE}/${DOCKER_IMAGE}:${tag}
                    """
                    // Save it in the build description
                    currentBuild.description = "BuiltImage=${params.PUBLISH_IMAGE}/${DOCKER_IMAGE}:${tag}"
                }
            }
        }
    }

    post {
        always {
            script {
                cleanWs()
                
                // Detect if build was triggered manually or by SCM
                def causes = currentBuild.getBuildCauses()
                def isManual = causes.any { it.toString().contains('UserIdCause') }
                def isSCM = causes.any { it.toString().contains('SCMTrigger') || it.toString().contains('GitLabWebHookCause') }
                def isUpstream = causes.any { it.toString().contains('UpstreamCause') }

                sh """
                    docker rmi -f ${IMAGE_NAME}
                """

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

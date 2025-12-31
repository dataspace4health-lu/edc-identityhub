/*
 *  Copyright (c) 2023 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - Initial API and Implementation
 *
 */
plugins {
    `java-library`
    id("checkstyle")
    id("application")
    id("test-report-aggregation")
    id("jacoco")
    alias(libs.plugins.shadow)
}

version = ""

allprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
        mavenLocal()
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
        }
    }

}

dependencies {

    implementation(libs.edc.bom.identityhub)
    
    // SQL persistence for PostgreSQL
    runtimeOnly(libs.edc.bom.identityhub.sql)
    
    implementation(libs.edc.boot)
    implementation(libs.edc.config.fs)
    implementation(libs.edc.ext.http)
    implementation(libs.edc.ext.jsonld)
    implementation(libs.edc.core.runtime)
    implementation(libs.edc.core.api)
    
    implementation(libs.edc.ih.spi)
    implementation(libs.edc.ih.spi.credentials)
    implementation(libs.edc.ih.spi.did)
    
    implementation(libs.edc.did.web)
    implementation(libs.edc.did.core)

    implementation(libs.edc.vault.hashicorp)
    
    // Superuser seed extension
    runtimeOnly(project(":extensions:superuser-seed"))
    implementation(libs.edc.bom.identityhub.sql)
    implementation(libs.edc.ih.core.participant)
    implementation(libs.edc.bom.identityhub)
    
    implementation(libs.edc.ih.participant.validator)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    
    testImplementation(libs.edc.spi.identity.did)
    testImplementation(libs.edc.lib.crypto)
    testImplementation(libs.edc.lib.keys)

    implementation(project(":extensions:user-seeding"))
    implementation(project(":extensions:service-loader"))

    implementation(project(":spi:manage-participant"))
    
    implementation(project(":services"))

    testReportAggregation(project(":extensions:user-seeding"))
    testReportAggregation(project(":extensions:service-loader"))
    testReportAggregation(project(":extensions:superuser-seed"))
    testReportAggregation(project(":spi:manage-participant"))
    testReportAggregation(project(":services"))
    
}

application {
    mainClass.set("org.eclipse.edc.boot.system.runtime.BaseRuntime")
}

sourceSets {
    main {
        java {
            // First, clear default srcDirs by removing them one by one
            setSrcDirs(emptySet<File>())

            // Find all directories named 'main' under extensions/**/src/main, spi/**/src/main, services/**/src/main
            val srcFolders = listOf("extensions", "spi", "services").flatMap { folder ->
                file(folder).walkTopDown()
                    .filter { it.isDirectory && it.name == "main" && it.parent.endsWith("src") }
                    .toList()
            }.toSet()

            // Add those directories as source dirs
            setSrcDirs(srcFolders)
        }
    }
}

checkstyle {
    toolVersion = "12.0.0"
    configFile = file("config/checkstyle/checkstyle.xml")
    isShowViolations = true
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    mergeServiceFiles()
    archiveClassifier.set("")
}

tasks.withType<Checkstyle> {
    reports {
        xml.required.set(false)
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.file("reports/checkstyle/lint.html"))
    }
}

subprojects {
    plugins.apply("java")
    plugins.apply("jacoco")
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // optional per-task report locations; not necessary for aggregation
        
        testLogging {
            events("FAILED", "PASSED", "SKIPPED")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showExceptions = true
            showCauses = true
            showStackTraces = true
            showStandardStreams = true
        }

        reports {
            junitXml.required.set(true)
            junitXml.outputLocation.set(layout.buildDirectory.dir("reports/junit/xml"))
            html.required.set(true)
            html.outputLocation.set(layout.buildDirectory.dir("reports/junit/html"))
        }
        finalizedBy(tasks.jacocoTestReport)
    }
    jacoco {
        toolVersion = "0.8.10"
    }
    tasks.jacocoTestReport {
        dependsOn(tasks.test)
        reports {
            xml.required.set(true)
            xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/xml/jacoco.xml"))
            html.required.set(true)
            html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/html"))
            csv.required.set(false)
        }
    }
}

// 1) Register a single aggregation task
val allTests by tasks.register<TestReport>("allTests") {
    destinationDirectory.set(layout.buildDirectory.dir("reports/junit/html"))
    // Collect results from all subprojects’ "test" tasks
    testResults.from(subprojects.map { it.tasks.named("test") })
}

// 2) Ensure aggregation runs even if tests fail
// finalizedBy makes Gradle run 'allTests' regardless of the outcome of 'test'
tasks.named("test") {
    finalizedBy(allTests)
}
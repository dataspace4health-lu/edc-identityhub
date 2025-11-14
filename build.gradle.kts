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
    
    // Superuser seed extension
    runtimeOnly(project(":extensions:superuser-seed"))
    
    testImplementation(libs.edc.spi.identity.did)
    testImplementation(libs.edc.lib.crypto)
    testImplementation(libs.edc.lib.keys)
}

application {
    mainClass.set("org.eclipse.edc.boot.system.runtime.BaseRuntime")
}

sourceSets {
    main {
        java {
            // First, clear default srcDirs by removing them one by one
            setSrcDirs(emptySet<File>())

            // Find all directories named 'src' under 'extensions/**/src'
            val srcFolders = file("extensions").walkTopDown()
                .filter { it.isDirectory && it.name == "src" }
                .toSet()

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
        html.outputLocation.set(file("$buildDir/reports/checkstyle/lint.html"))
    }
}

tasks.test {
    // Change the directory for HTML reports
    reports.html.outputLocation.set(file("$buildDir/test-results/html/test.html"))

    // Change the directory for JUnit XML test results
    reports.junitXml.outputLocation.set(file("$buildDir/test-results/xml/test.xml"))
}
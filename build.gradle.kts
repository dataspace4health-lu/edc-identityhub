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


 java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
        // If you MUST pin a vendor, use Adoptium:
        // vendor.set(JvmVendorSpec.ADOPTIUM)
    }
}


plugins {
    `java-library`
    id("application")
    alias(libs.plugins.shadow)
}

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
    
    testImplementation(libs.edc.spi.identity.did)
    testImplementation(libs.edc.lib.crypto)
    testImplementation(libs.edc.lib.keys)
}

application {
    mainClass.set("org.eclipse.edc.boot.system.runtime.BaseRuntime")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    mergeServiceFiles()
    archiveFileName.set("identity-hub.jar")
}
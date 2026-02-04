plugins {
    `java-library`
}

dependencies {
    implementation(libs.edc.ih.spi.credentials)
    implementation(libs.edc.ih.spi)
    implementation(libs.edc.ih.core.participant)
    implementation(libs.edc.ih.participant.validator)
    implementation(libs.edc.http.client)
    implementation(libs.edc.pc.core)
    implementation(libs.edc.lib.sql)
    implementation(libs.edc.spi.transaction.datasource)

    implementation(project(":services"))
    implementation(project(":spi"))
    implementation(project(":spi:manage-participant"))
    
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj.core)
}

tasks.test {
    useJUnitPlatform()
}
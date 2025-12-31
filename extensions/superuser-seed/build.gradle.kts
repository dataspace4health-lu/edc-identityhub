plugins {
    `java-library`
}

dependencies {
    implementation(libs.edc.ih.spi.credentials)
    implementation(libs.edc.ih.spi)
    
    testImplementation(libs.edc.junit)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

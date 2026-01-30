plugins {
    `java-library`
}

dependencies {
    implementation(libs.edc.http.client)
    testImplementation(libs.edc.junit)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
}

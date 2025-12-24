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
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
    testRuntimeOnly(libs.junit.jupiter.engine)
}

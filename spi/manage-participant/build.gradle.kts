plugins {
    `java-library`
}


dependencies {
    implementation(libs.edc.ih.spi.credentials)
    implementation(libs.edc.ih.spi)
    implementation(libs.edc.ih.core.participant)
    implementation(libs.edc.bom.identityhub)
    implementation(libs.edc.bom.identityhub.sql)
    implementation(libs.edc.ih.participant.validator)
    implementation(libs.edc.http.client)
    
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    implementation(project(":services"))
    
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj.core)
}

tasks.test {
    useJUnitPlatform()
}


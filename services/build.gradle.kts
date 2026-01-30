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
    implementation(libs.edc.pc.config.store)
    implementation(libs.edc.pc.core)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}


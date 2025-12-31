plugins {
    `java-library`
}


dependencies {

    implementation(libs.edc.core.runtime)

    implementation(project(":services"))
    implementation(project(":spi"))
    implementation(project(":spi:manage-participant"))
}


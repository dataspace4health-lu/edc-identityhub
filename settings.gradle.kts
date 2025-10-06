plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "edc-identity-hub"

// Include all subprojects here
include(":extensions:did-example-resolver")
include(":extensions:superuser-seed")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "keystone"

include(
    "libs:common-events",
    "services:order-service",
    "services:inventory-service",
    "services:payment-service",
    "e2e-tests"
)

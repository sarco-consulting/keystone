import java.time.Duration

plugins {
    java
    alias(libs.plugins.spring.dependency.management)
}

// Forces those projects to be configured before this one, so their bootJar
// tasks already exist when referenced below — without this, Gradle may not
// have configured them yet at this point in the build.
evaluationDependsOn(":services:order-service")
evaluationDependsOn(":services:inventory-service")
evaluationDependsOn(":services:payment-service")

// Not a Spring Boot application itself — just a JUnit harness that launches
// the three services' actual built jars as real OS processes against
// Testcontainers-managed infra. Importing spring-boot-dependencies anyway
// for consistent, pre-vetted version numbers (junit, jackson, assertj)
// instead of pinning them by hand.
dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
        mavenBom("org.testcontainers:testcontainers-bom:${libs.versions.testcontainers.get()}")
    }
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:redpanda")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()

    dependsOn(
        ":services:order-service:bootJar",
        ":services:inventory-service:bootJar",
        ":services:payment-service:bootJar",
    )

    systemProperty("e2e.order-service.jar", project(":services:order-service").tasks.getByName("bootJar").outputs.files.singleFile.absolutePath)
    systemProperty("e2e.inventory-service.jar", project(":services:inventory-service").tasks.getByName("bootJar").outputs.files.singleFile.absolutePath)
    systemProperty("e2e.payment-service.jar", project(":services:payment-service").tasks.getByName("bootJar").outputs.files.singleFile.absolutePath)
    systemProperty("e2e.wiremock-mappings-dir", rootProject.file("infra/wiremock/mappings").absolutePath)

    // The saga plays out over real async hops (HTTP -> outbox -> Kafka ->
    // consumer -> outbox -> Kafka -> consumer...) across three real JVMs;
    // give it real time rather than a unit-test-scale timeout.
    timeout.set(Duration.ofMinutes(5))
}

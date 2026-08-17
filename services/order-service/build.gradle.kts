plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

// The OTel BOM has to be imported through the io.spring.dependency-management
// plugin's own DSL, not Gradle's native platform(): with both in play, the
// plugin's version constraints silently win over platform()'s, leaving a
// mismatched io.opentelemetry:opentelemetry-api on the classpath and a
// ClassNotFoundException at startup for a class the starter expects to exist.
dependencyManagement {
    imports {
        mavenBom("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom:${libs.versions.opentelemetry.instrumentation.get()}")
        mavenBom("org.testcontainers:testcontainers-bom:${libs.versions.testcontainers.get()}")
    }
}

dependencies {
    implementation(project(":libs:common-events"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${libs.versions.springdoc.openapi.get()}")
    implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter")
    implementation("io.micrometer:micrometer-registry-prometheus")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}

// This is an application, not a library — nothing depends on its plain jar,
// and leaving it enabled produces a *-plain.jar alongside the executable
// one in build/libs, which breaks the Dockerfile's single-file glob COPY.
tasks.named<Jar>("jar") {
    enabled = false
}

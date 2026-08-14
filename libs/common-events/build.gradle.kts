plugins {
    `java-library`
    alias(libs.plugins.spring.dependency.management)
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
        mavenBom("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom:${libs.versions.opentelemetry.instrumentation.get()}")
    }
}

dependencies {
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // compileOnly: these give @MappedSuperclass and the abstract outbox
    // classes access to JPA/Kafka/OTel annotations and types at compile time
    // without forcing that runtime on every consumer of this library (e.g. a
    // future notification-service reading DomainEvent types wouldn't need
    // any of them).
    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa")
    compileOnly("org.springframework.kafka:spring-kafka")
    compileOnly("io.opentelemetry:opentelemetry-api")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

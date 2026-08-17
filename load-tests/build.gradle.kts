plugins {
    id("io.gatling.gradle") version "3.15.1.2"
}

// Deliberately no Testcontainers/bootJar wiring, unlike e2e-tests — this
// module targets the already-running "Starting everything" stack
// (docs/runbook.md) on its real ports, specifically so the run is visible
// on the Grafana dashboard that's already provisioned, not an ephemeral
// Testcontainers instance nobody can watch live.

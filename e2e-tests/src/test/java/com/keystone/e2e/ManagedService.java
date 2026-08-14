package com.keystone.e2e;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Launches one of the service jars as a real OS process — not an in-JVM
 * Spring context — pointed at whatever ports Testcontainers assigned this
 * run. This is what makes the test genuinely end-to-end: it's the same
 * artifact a deploy would ship, talking real HTTP and real Kafka to the
 * other two, not a wired-up ApplicationContext with mocked collaborators.
 */
final class ManagedService {

    private final String name;
    private final int port;
    private final Process process;
    private final File logFile;

    private ManagedService(String name, int port, Process process, File logFile) {
        this.name = name;
        this.port = port;
        this.process = process;
        this.logFile = logFile;
    }

    static ManagedService start(String name, String jarPath, int port, Map<String, String> env) throws IOException {
        File logFile = File.createTempFile("keystone-e2e-" + name + "-", ".log");
        ProcessBuilder builder = new ProcessBuilder("java", "-jar", jarPath);
        builder.environment().putAll(env);
        builder.environment().put("SERVER_PORT", String.valueOf(port));
        builder.redirectOutput(ProcessBuilder.Redirect.to(logFile));
        builder.redirectErrorStream(true);
        Process process = builder.start();
        return new ManagedService(name, port, process, logFile);
    }

    void waitUntilHealthy(Duration timeout) {
        HttpClient client = HttpClient.newHttpClient();
        Instant deadline = Instant.now().plus(timeout);
        Exception lastFailure = null;
        while (Instant.now().isBefore(deadline)) {
            if (!process.isAlive()) {
                throw new IllegalStateException(name + " exited before becoming healthy — see " + logFile);
            }
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health")).GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200 && response.body().contains("\"UP\"")) {
                    return;
                }
            } catch (Exception e) {
                lastFailure = e;
            }
            sleep();
        }
        throw new IllegalStateException(name + " did not become healthy within " + timeout + " — see " + logFile, lastFailure);
    }

    void stop() {
        process.destroy();
        try {
            if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
        try {
            Files.deleteIfExists(logFile.toPath());
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

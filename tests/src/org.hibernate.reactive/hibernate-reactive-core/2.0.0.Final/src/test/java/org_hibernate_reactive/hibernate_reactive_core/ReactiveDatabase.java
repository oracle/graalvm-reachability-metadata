/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_reactive.hibernate_reactive_core;

import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.cfg.Configuration;
import org.hibernate.reactive.provider.ReactiveServiceRegistryBuilder;
import org.hibernate.reactive.stage.Stage;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

final class ReactiveDatabase implements AutoCloseable {
    private final String container = "reactive-coverage-" + UUID.randomUUID();
    private StandardServiceRegistry registry;
    private SessionFactory factory;

    ReactiveDatabase(Map<String, Object> settings, Class<?>... entities) throws Exception {
        try {
            docker("run", "--detach", "--rm", "--name", container,
                    "--publish", "127.0.0.1::5432", "--env", "POSTGRES_PASSWORD=secret",
                    "--env", "POSTGRES_DB=coverage", "postgres:18-alpine");
            waitForDatabase();
            String address = docker("port", container, "5432/tcp").trim();
            String port = address.substring(address.lastIndexOf(':') + 1);
            Configuration configuration = new Configuration();
            configuration.setProperty("hibernate.connection.url", "postgresql://localhost:" + port + "/coverage");
            configuration.setProperty("hibernate.connection.username", "postgres");
            configuration.setProperty("hibernate.connection.password", "secret");
            configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
            configuration.setProperty("hibernate.temp.use_jdbc_metadata_defaults", "false");
            configuration.setProperty("hibernate.hbm2ddl.auto", "create-drop");
            configuration.setProperty("hibernate.connection.pool_size", "2");
            configuration.getProperties().putAll(settings);
            for (Class<?> entity : entities) {
                configuration.addAnnotatedClass(entity);
            }
            registry = new ReactiveServiceRegistryBuilder().applySettings(configuration.getProperties()).build();
            factory = configuration.buildSessionFactory(registry);
        } catch (Exception | Error failure) {
            try {
                close();
            } catch (Exception cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    Stage.SessionFactory sessions() {
        return factory.unwrap(Stage.SessionFactory.class);
    }

    static <T> T await(CompletionStage<T> operation) throws Exception {
        return operation.toCompletableFuture().get(60, TimeUnit.SECONDS);
    }

    private void waitForDatabase() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        while (System.nanoTime() < deadline) {
            Process probe = new ProcessBuilder("docker", "exec", container,
                    "pg_isready", "--host=127.0.0.1", "--username=postgres", "--dbname=coverage")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!probe.waitFor(10, TimeUnit.SECONDS)) {
                probe.destroyForcibly();
                throw new IllegalStateException("Database readiness probe timed out");
            }
            if (probe.exitValue() == 0) {
                return;
            }
            Thread.sleep(1000);
        }
        throw new IllegalStateException("PostgreSQL did not become ready within 90 seconds");
    }

    private static String docker(String... arguments) throws Exception {
        String[] command = new String[arguments.length + 1];
        command[0] = "docker";
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        if (!process.waitFor(90, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Docker command timed out");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Docker command failed: " + output);
        }
        return output;
    }

    @Override
    public void close() throws Exception {
        try {
            if (factory != null) {
                factory.close();
            } else if (registry != null) {
                ReactiveServiceRegistryBuilder.destroy(registry);
            }
        } finally {
            docker("rm", "--force", container);
        }
    }
}

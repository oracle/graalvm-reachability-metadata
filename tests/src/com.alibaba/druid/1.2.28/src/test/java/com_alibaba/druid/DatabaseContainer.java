/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class DatabaseContainer implements AutoCloseable {
    static final String PASSWORD = "Druid_Test_Password42!";
    private final String id;
    private final String address;

    private DatabaseContainer(String id, String address) {
        this.id = id;
        this.address = address;
    }

    static DatabaseContainer mysql() throws Exception {
        return start("container-registry.oracle.com/mysql/community-server:26.7.1", 3306,
                "port: 3306", "MYSQL_ROOT_PASSWORD=" + PASSWORD, "MYSQL_ROOT_HOST=%",
                "MYSQL_DATABASE=druid", "MYSQL_USER=druid", "MYSQL_PASSWORD=" + PASSWORD);
    }

    static DatabaseContainer oracle() throws Exception {
        return start("gvenzl/oracle-free:23-slim-faststart", 1521, "DATABASE IS READY TO USE!",
                "ORACLE_PASSWORD=" + PASSWORD, "APP_USER=druid", "APP_USER_PASSWORD=" + PASSWORD);
    }

    static DatabaseContainer sqlServer() throws Exception {
        return start("mcr.microsoft.com/mssql/server:2022-CU14-ubuntu-22.04", 1433,
                "Recovery is complete", "ACCEPT_EULA=Y", "MSSQL_SA_PASSWORD=" + PASSWORD);
    }

    String address() {
        return address;
    }

    private static DatabaseContainer start(String image, int port, String ready, String... environment)
            throws Exception {
        List<String> arguments = new ArrayList<>(List.of("run", "--detach", "--rm", "--publish",
                "127.0.0.1::" + port));
        for (String value : environment) {
            arguments.add("--env");
            arguments.add(value);
        }
        arguments.add(image);
        String id = docker(arguments.toArray(String[]::new)).trim();
        boolean started = false;
        try {
            String address = docker("port", id, port + "/tcp").trim();
            long deadline = System.nanoTime() + Duration.ofSeconds(100).toNanos();
            while (System.nanoTime() < deadline) {
                if (docker("logs", id).contains(ready)) {
                    started = true;
                    return new DatabaseContainer(id, address);
                }
                Thread.sleep(1000);
            }
            throw new IOException("Database did not become ready: " + docker("logs", id));
        } finally {
            if (!started) {
                docker("rm", "--force", id);
            }
        }
    }

    @Override
    public void close() throws Exception {
        docker("rm", "--force", id);
    }

    private static String docker(String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("docker"));
        command.addAll(Arrays.asList(arguments));
        Path output = Files.createTempFile("druid-database-", ".log");
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(output.toFile()).start();
            try {
                if (!process.waitFor(30, TimeUnit.SECONDS)) {
                    throw new IOException("Docker command timed out: " + command);
                }
                String result = Files.readString(output);
                if (process.exitValue() != 0) {
                    throw new IOException("Docker command failed: " + result);
                }
                return result;
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                    if (!process.waitFor(10, TimeUnit.SECONDS)) {
                        throw new IOException("Docker command did not terminate: " + command);
                    }
                }
            }
        } finally {
            Files.deleteIfExists(output);
        }
    }
}

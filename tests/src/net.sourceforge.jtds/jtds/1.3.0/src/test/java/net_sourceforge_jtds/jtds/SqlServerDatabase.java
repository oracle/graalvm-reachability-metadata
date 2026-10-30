/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_jtds.jtds;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import net.sourceforge.jtds.jdbcx.JtdsDataSource;

final class SqlServerDatabase implements AutoCloseable {
    private static final String PASSWORD = "Jtds_Test_Password42!";
    private final Path output;
    private final String containerId;
    private final int port;

    private SqlServerDatabase(Path output, String containerId, int port) {
        this.output = output;
        this.containerId = containerId;
        this.port = port;
    }

    static SqlServerDatabase start(Path directory) throws Exception {
        Path output = directory.resolve("docker-output.txt");
        String response = docker(output, "run", "--detach", "--rm",
                "--publish", "127.0.0.1::1433", "--env", "ACCEPT_EULA=Y",
                "--env", "MSSQL_SA_PASSWORD=" + PASSWORD,
                "mcr.microsoft.com/mssql/server:2022-CU14-ubuntu-22.04");
        List<String> lines = response.lines().toList();
        String containerId = lines.get(lines.size() - 1).trim();
        boolean ready = false;
        try {
            int port = Integer.parseInt(docker(output, "inspect", "--format",
                    "{{(index (index .NetworkSettings.Ports \"1433/tcp\") 0).HostPort}}",
                    containerId).trim());
            long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
            while (System.nanoTime() < deadline) {
                String logs = docker(output, "logs", containerId);
                if (logs.contains("SQL Server is now ready for client connections")
                        && logs.contains("Recovery is complete")) {
                    ready = true;
                    return new SqlServerDatabase(output, containerId, port);
                }
                Thread.sleep(500);
            }
            throw new IOException("SQL Server did not become ready: " + docker(output, "logs", containerId));
        } finally {
            if (!ready) {
                docker(output, "rm", "--force", containerId);
            }
        }
    }

    Connection openConnection(String bindAddress) throws SQLException {
        JtdsDataSource source = new JtdsDataSource();
        source.setServerName("127.0.0.1");
        source.setPortNumber(port);
        source.setDatabaseName("master");
        source.setUser("sa");
        source.setPassword(PASSWORD);
        source.setLoginTimeout(15);
        source.setSocketTimeout(15);
        source.setBindAddress(bindAddress);
        return source.getConnection();
    }

    @Override
    public void close() throws Exception {
        try {
            System.out.println(docker(output, "logs", containerId));
        } finally {
            docker(output, "stop", "--time", "10", containerId);
        }
    }

    private static String docker(Path output, String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.addAll(Arrays.asList(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(output.toFile()).start();
        try {
            if (!process.waitFor(120, TimeUnit.SECONDS)) {
                throw new IOException("Docker command timed out: " + command);
            }
            String response = Files.readString(output);
            if (process.exitValue() != 0) {
                throw new IOException("Docker command failed: " + command + "\n" + response);
            }
            return response;
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    throw new IOException("Docker command did not terminate: " + command);
                }
            }
        }
    }
}

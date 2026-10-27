/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;
import oracle.jdbc.util.OracleServerProcessWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

public class OracleServerProcessWrapperTest {
    @TempDir
    Path oracleHome;

    @Test
    @Timeout(value = 50, unit = TimeUnit.SECONDS)
    void startsAndSignalsTheConfiguredServerProcess() throws Exception {
        Path binDirectory = Files.createDirectories(oracleHome.resolve("bin"));
        Path executable = binDirectory.resolve("oracle");
        Files.writeString(executable, "#!/bin/sh\nexec sleep 30\n");
        Files.setPosixFilePermissions(
                executable,
                EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE));

        OracleServerProcessWrapper process = new OracleServerProcessWrapper(oracleHome.toString());
        process.start();
        try {
            assertThat(process.getInputStream()).isNotNull();
            assertThatCode(process::sendInterrupt).doesNotThrowAnyException();
        } finally {
            process.terminate();
        }
    }
}

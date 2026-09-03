/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import oracle.jdbc.OracleDriver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TcpFastOpenLibraryInstallerTest {
    @TempDir
    Path installationDirectory;

    @Test
    void installsThePackagedTcpFastOpenLibrary() throws Exception {
        String previousOsName = System.setProperty("os.name", "Linux");
        String previousOsArchitecture = System.setProperty("os.arch", "x86_64");
        try {
            OracleDriver.main(new String[] {"install-tfo", "--path", installationDirectory.toString()});
        } finally {
            restoreProperty("os.name", previousOsName);
            restoreProperty("os.arch", previousOsArchitecture);
        }

        Path installedLibrary = installationDirectory.resolve("libtfojdbc1.so");
        assertThat(installedLibrary).isRegularFile();
        assertThat(Files.size(installedLibrary)).isPositive();
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}

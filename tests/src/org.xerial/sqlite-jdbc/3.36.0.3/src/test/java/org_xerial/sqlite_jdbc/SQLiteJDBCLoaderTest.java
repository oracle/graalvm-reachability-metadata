/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_xerial.sqlite_jdbc;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteJDBCLoader;

import static org.assertj.core.api.Assertions.assertThat;

public class SQLiteJDBCLoaderTest {
    @TempDir
    Path sqliteTempDir;

    @Test
    public void initializeExtractsPackagedNativeLibraryResource() throws Exception {
        String previousTempDir = System.getProperty("org.sqlite.tmpdir");
        String previousLibPath = System.clearProperty("org.sqlite.lib.path");
        String previousLibName = System.clearProperty("org.sqlite.lib.name");
        System.setProperty("org.sqlite.tmpdir", sqliteTempDir.toString());
        try {
            assertThat(SQLiteJDBCLoader.initialize()).isTrue();
            assertThat(SQLiteJDBCLoader.isNativeMode()).isTrue();
        } finally {
            restoreProperty("org.sqlite.tmpdir", previousTempDir);
            restoreProperty("org.sqlite.lib.path", previousLibPath);
            restoreProperty("org.sqlite.lib.name", previousLibName);
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}

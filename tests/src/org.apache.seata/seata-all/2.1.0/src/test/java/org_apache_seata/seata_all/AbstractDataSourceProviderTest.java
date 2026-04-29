/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.sql.DataSource;

import org.apache.seata.core.store.db.AbstractDataSourceProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AbstractDataSourceProviderTest {
    @TempDir
    Path classPathRoot;

    @Test
    void scansJdbcDirectoryForMysqlDriverCandidatesWhenProviderClassInitializes() throws Exception {
        Path jdbcDirectory = Files.createDirectories(classPathRoot.resolve("jdbc"));
        Files.createDirectories(jdbcDirectory.resolve("mysql-connector-java-test"));
        String originalClassPath = System.getProperty("java.class.path");
        String originalConfigType = System.getProperty("config.type");
        String originalConfigFileName = System.getProperty("config.file.name");
        System.setProperty("java.class.path", classPathRoot.toString());
        System.setProperty("config.type", "file");
        System.setProperty("config.file.name", "file.conf");
        try {
            TestDataSourceProvider provider = new TestDataSourceProvider();

            assertThat(provider.provide()).isNull();
        } finally {
            restoreProperty("java.class.path", originalClassPath);
            restoreProperty("config.type", originalConfigType);
            restoreProperty("config.file.name", originalConfigFileName);
        }
    }

    private static void restoreProperty(String name, String originalValue) {
        if (originalValue == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, originalValue);
        }
    }

    private static final class TestDataSourceProvider extends AbstractDataSourceProvider {
        @Override
        public DataSource doGenerate() {
            return null;
        }
    }
}

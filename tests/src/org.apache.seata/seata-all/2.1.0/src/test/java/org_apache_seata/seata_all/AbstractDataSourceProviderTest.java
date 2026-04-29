/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.sql.DataSource;

import org.apache.seata.core.store.db.AbstractDataSourceProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AbstractDataSourceProviderTest {
    private static final String DRIVER_CLASS_NAME = TestDataSourceProvider.class.getName();

    @TempDir
    static Path classPathRoot;

    private static String originalClassPath;
    private static String originalConfigType;
    private static String originalConfigFileName;
    private static String originalDriverClassName;

    @BeforeAll
    static void configureSeataAndJdbcDriverPath() throws Exception {
        Path jdbcDirectory = Files.createDirectories(classPathRoot.resolve("jdbc"));
        Files.createDirectories(jdbcDirectory.resolve("mysql-connector-java-test"));
        originalClassPath = System.getProperty("java.class.path");
        originalConfigType = System.getProperty("config.type");
        originalConfigFileName = System.getProperty("config.file.name");
        originalDriverClassName = System.getProperty("store.db.driverClassName");
        System.setProperty("java.class.path", classPathRoot.toString());
        System.setProperty("config.type", "file");
        System.setProperty("config.file.name", "file.conf");
        System.setProperty("store.db.driverClassName", DRIVER_CLASS_NAME);
    }

    @AfterAll
    static void restoreSystemProperties() {
        restoreProperty("java.class.path", originalClassPath);
        restoreProperty("config.type", originalConfigType);
        restoreProperty("config.file.name", originalConfigFileName);
        restoreProperty("store.db.driverClassName", originalDriverClassName);
    }

    @Test
    void scansJdbcDirectoryForMysqlDriverCandidatesWhenProviderClassInitializes() {
        TestDataSourceProvider provider = new TestDataSourceProvider();

        assertThat(provider.provide()).isNull();
    }

    @Test
    void validatesConfiguredDriverWithContextClassLoader() {
        TestDataSourceProvider provider = new TestDataSourceProvider();

        assertThatNoException().isThrownBy(provider::validate);
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

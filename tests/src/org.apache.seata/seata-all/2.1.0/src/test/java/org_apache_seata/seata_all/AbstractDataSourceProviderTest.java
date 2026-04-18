/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.sql.DataSource;

import org.apache.seata.common.ConfigurationKeys;
import org.apache.seata.config.ConfigurationCache;
import org.apache.seata.core.store.db.AbstractDataSourceProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class AbstractDataSourceProviderTest {
    private static final String CONFIG_TYPE_PROPERTY = "config.type";
    private static final String CONFIG_FILE_NAME_PROPERTY = "config.file.name";
    private String previousJavaClassPath;
    private String previousDriverClassName;
    private String previousConfigType;
    private String previousConfigFileName;

    @AfterEach
    void restoreSystemProperties() {
        ConfigurationCache.clear();
        if (previousJavaClassPath == null) {
            System.clearProperty("java.class.path");
        } else {
            System.setProperty("java.class.path", previousJavaClassPath);
        }
        if (previousDriverClassName == null) {
            System.clearProperty(ConfigurationKeys.STORE_DB_DRIVER_CLASS_NAME);
        } else {
            System.setProperty(ConfigurationKeys.STORE_DB_DRIVER_CLASS_NAME, previousDriverClassName);
        }
        if (previousConfigType == null) {
            System.clearProperty(CONFIG_TYPE_PROPERTY);
        } else {
            System.setProperty(CONFIG_TYPE_PROPERTY, previousConfigType);
        }
        if (previousConfigFileName == null) {
            System.clearProperty(CONFIG_FILE_NAME_PROPERTY);
        } else {
            System.setProperty(CONFIG_FILE_NAME_PROPERTY, previousConfigFileName);
        }
    }

    @Test
    void validateUsesTheThreadContextClassLoaderToResolveTheConfiguredDriver(@TempDir Path tempDir)
            throws IOException {
        configureSeata(tempDir.resolve("file.conf"), DriverMarker.class.getName(), tempDir.toString());
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        RecordingClassLoader recordingClassLoader = new RecordingClassLoader(originalContextClassLoader);

        try {
            Thread.currentThread().setContextClassLoader(recordingClassLoader);

            assertThatCode(() -> new TestDataSourceProvider().validate()).doesNotThrowAnyException();
            assertThat(recordingClassLoader.getLastRequestedClassName()).isEqualTo(DriverMarker.class.getName());
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private void configureSeata(Path configFile, String driverClassName, String javaClassPath) throws IOException {
        Files.writeString(configFile, "# test configuration\n");
        ConfigurationCache.clear();

        previousJavaClassPath = System.getProperty("java.class.path");
        previousDriverClassName = System.getProperty(ConfigurationKeys.STORE_DB_DRIVER_CLASS_NAME);
        previousConfigType = System.getProperty(CONFIG_TYPE_PROPERTY);
        previousConfigFileName = System.getProperty(CONFIG_FILE_NAME_PROPERTY);
        System.setProperty("java.class.path", javaClassPath);
        System.setProperty(ConfigurationKeys.STORE_DB_DRIVER_CLASS_NAME, driverClassName);
        System.setProperty(CONFIG_TYPE_PROPERTY, "file");
        System.setProperty(CONFIG_FILE_NAME_PROPERTY, configFile.toString());
    }

    public static final class DriverMarker {
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private String lastRequestedClassName;

        private RecordingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            lastRequestedClassName = name;
            return super.loadClass(name);
        }

        private String getLastRequestedClassName() {
            return lastRequestedClassName;
        }
    }

    public static final class TestDataSourceProvider extends AbstractDataSourceProvider {
        public ClassLoader driverClassLoader() {
            return getDriverClassLoader();
        }

        @Override
        public DataSource doGenerate() {
            return null;
        }
    }

}

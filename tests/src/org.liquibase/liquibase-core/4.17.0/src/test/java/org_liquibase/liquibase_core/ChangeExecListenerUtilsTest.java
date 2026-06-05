/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.changelog.visitor.AbstractChangeExecListener;
import liquibase.changelog.visitor.ChangeExecListener;
import liquibase.database.Database;
import liquibase.database.core.H2Database;
import liquibase.integration.commandline.ChangeExecListenerUtils;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.ResourceAccessor;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class ChangeExecListenerUtilsTest {
    private final Database database = new H2Database();
    private final ResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor();

    @Test
    void createsListenerWithDatabaseAndPropertiesConstructor() throws Exception {
        Path propertiesFile = listenerPropertiesFile();

        ChangeExecListener listener = getChangeExecListener(
                ListenerWithDatabaseAndProperties.class,
                propertiesFile);

        ListenerWithDatabaseAndProperties typedListener = (ListenerWithDatabaseAndProperties) listener;
        assertThat(typedListener.database).isSameAs(database);
        assertThat(typedListener.properties).containsEntry("listener.name", "database-properties");
    }

    @Test
    void createsListenerWithPropertiesAndDatabaseConstructor() throws Exception {
        Path propertiesFile = listenerPropertiesFile();

        ChangeExecListener listener = getChangeExecListener(
                ListenerWithPropertiesAndDatabase.class,
                propertiesFile);

        ListenerWithPropertiesAndDatabase typedListener = (ListenerWithPropertiesAndDatabase) listener;
        assertThat(typedListener.properties).containsEntry("listener.name", "database-properties");
        assertThat(typedListener.database).isSameAs(database);
    }

    @Test
    void createsListenerWithDatabaseConstructor() throws Exception {
        ChangeExecListener listener = getChangeExecListener(ListenerWithDatabase.class, null);

        ListenerWithDatabase typedListener = (ListenerWithDatabase) listener;
        assertThat(typedListener.database).isSameAs(database);
    }

    @Test
    void createsListenerWithPropertiesConstructor() throws Exception {
        Path propertiesFile = listenerPropertiesFile();

        ChangeExecListener listener = getChangeExecListener(ListenerWithProperties.class, propertiesFile);

        ListenerWithProperties typedListener = (ListenerWithProperties) listener;
        assertThat(typedListener.properties).containsEntry("listener.name", "database-properties");
    }

    @Test
    void createsListenerWithNoArgumentConstructor() throws Exception {
        ChangeExecListener listener = getChangeExecListener(ListenerWithNoArguments.class, null);

        assertThat(listener).isInstanceOf(ListenerWithNoArguments.class);
    }

    private ChangeExecListener getChangeExecListener(
            Class<? extends ChangeExecListener> listenerClass,
            Path propertiesFile) throws Exception {
        String propertiesFileName = propertiesFile == null ? null : propertiesFile.toString();
        return ChangeExecListenerUtils.getChangeExecListener(
                database,
                resourceAccessor,
                listenerClass.getName(),
                propertiesFileName);
    }

    private static Path listenerPropertiesFile() throws Exception {
        Path propertiesFile = Files.createTempFile("liquibase-change-listener", ".properties");
        Files.writeString(propertiesFile, "listener.name=database-properties\n");
        propertiesFile.toFile().deleteOnExit();
        return propertiesFile;
    }

    public static final class ListenerWithDatabaseAndProperties extends AbstractChangeExecListener {
        private final Database database;
        private final Properties properties;

        public ListenerWithDatabaseAndProperties(Database database, Properties properties) {
            this.database = database;
            this.properties = properties;
        }
    }

    public static final class ListenerWithPropertiesAndDatabase extends AbstractChangeExecListener {
        private final Properties properties;
        private final Database database;

        public ListenerWithPropertiesAndDatabase(Properties properties, Database database) {
            this.properties = properties;
            this.database = database;
        }
    }

    public static final class ListenerWithDatabase extends AbstractChangeExecListener {
        private final Database database;

        public ListenerWithDatabase(Database database) {
            this.database = database;
        }
    }

    public static final class ListenerWithProperties extends AbstractChangeExecListener {
        private final Properties properties;

        public ListenerWithProperties(Properties properties) {
            this.properties = properties;
        }
    }

    public static final class ListenerWithNoArguments extends AbstractChangeExecListener {
        public ListenerWithNoArguments() {
        }
    }
}

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class ChangeExecListenerUtilsTest {

    @TempDir
    private Path tempDir;

    @Test
    void createsListenerUsingDatabasePropertiesConstructorFirst() throws Exception {
        Database database = new H2Database();
        Path propertiesFile = writePropertiesFile();

        ChangeExecListener listener = ChangeExecListenerUtils.getChangeExecListener(
                database,
                null,
                DatabasePropertiesListener.class.getName(),
                propertiesFile.toString()
        );

        assertThat(listener).isInstanceOf(DatabasePropertiesListener.class);
        DatabasePropertiesListener typedListener = (DatabasePropertiesListener) listener;
        assertThat(typedListener.database).isSameAs(database);
        assertThat(typedListener.properties).containsEntry("listener.name", "test-listener");
    }

    @Test
    void createsListenerUsingPropertiesDatabaseConstructorWhenPreferredConstructorIsMissing() throws Exception {
        Database database = new H2Database();
        Path propertiesFile = writePropertiesFile();

        ChangeExecListener listener = ChangeExecListenerUtils.getChangeExecListener(
                database,
                null,
                PropertiesDatabaseListener.class.getName(),
                propertiesFile.toString()
        );

        assertThat(listener).isInstanceOf(PropertiesDatabaseListener.class);
        PropertiesDatabaseListener typedListener = (PropertiesDatabaseListener) listener;
        assertThat(typedListener.properties).containsEntry("listener.name", "test-listener");
        assertThat(typedListener.database).isSameAs(database);
    }

    @Test
    void createsListenerUsingDatabaseConstructorWhenPropertiesConstructorsAreMissing() throws Exception {
        Database database = new H2Database();

        ChangeExecListener listener = ChangeExecListenerUtils.getChangeExecListener(
                database,
                null,
                DatabaseListener.class.getName(),
                null
        );

        assertThat(listener).isInstanceOf(DatabaseListener.class);
        DatabaseListener typedListener = (DatabaseListener) listener;
        assertThat(typedListener.database).isSameAs(database);
    }

    @Test
    void createsListenerUsingPropertiesConstructorWhenDatabaseConstructorsAreMissing() throws Exception {
        Path propertiesFile = writePropertiesFile();

        ChangeExecListener listener = ChangeExecListenerUtils.getChangeExecListener(
                null,
                null,
                PropertiesListener.class.getName(),
                propertiesFile.toString()
        );

        assertThat(listener).isInstanceOf(PropertiesListener.class);
        PropertiesListener typedListener = (PropertiesListener) listener;
        assertThat(typedListener.properties).containsEntry("listener.name", "test-listener");
    }

    @Test
    void createsListenerUsingNoArgsConstructorWhenNoOtherConstructorMatches() throws Exception {
        ChangeExecListener listener = ChangeExecListenerUtils.getChangeExecListener(
                null,
                null,
                NoArgsListener.class.getName(),
                null
        );

        assertThat(listener).isInstanceOf(NoArgsListener.class);
        NoArgsListener typedListener = (NoArgsListener) listener;
        assertThat(typedListener.createdWithNoArgsConstructor).isTrue();
    }

    private Path writePropertiesFile() throws Exception {
        Path propertiesFile = tempDir.resolve("change-exec-listener.properties");
        Files.writeString(propertiesFile, "listener.name=test-listener\n");
        return propertiesFile;
    }

    public static class DatabasePropertiesListener extends AbstractChangeExecListener {

        private final Database database;
        private final Properties properties;

        public DatabasePropertiesListener(Database database, Properties properties) {
            this.database = database;
            this.properties = properties;
        }
    }

    public static class PropertiesDatabaseListener extends AbstractChangeExecListener {

        private final Properties properties;
        private final Database database;

        public PropertiesDatabaseListener(Properties properties, Database database) {
            this.properties = properties;
            this.database = database;
        }
    }

    public static class DatabaseListener extends AbstractChangeExecListener {

        private final Database database;

        public DatabaseListener(Database database) {
            this.database = database;
        }
    }

    public static class PropertiesListener extends AbstractChangeExecListener {

        private final Properties properties;

        public PropertiesListener(Properties properties) {
            this.properties = properties;
        }
    }

    public static class NoArgsListener extends AbstractChangeExecListener {

        private final boolean createdWithNoArgsConstructor;

        public NoArgsListener() {
            this.createdWithNoArgsConstructor = true;
        }
    }
}

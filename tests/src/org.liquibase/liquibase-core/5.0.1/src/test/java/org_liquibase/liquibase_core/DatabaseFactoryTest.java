/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.database.ConnectionServiceFactory;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.core.H2Database;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.ResourceAccessor;
import org.h2.Driver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class DatabaseFactoryTest {

    @BeforeEach
    void resetFactoriesBeforeTest() {
        DatabaseFactory.reset();
        ConnectionServiceFactory.reset();
    }

    @AfterEach
    void resetFactoriesAfterTest() {
        DatabaseFactory.reset();
        ConnectionServiceFactory.reset();
    }

    @Test
    void openDatabaseUsesConfiguredDatabaseDriverAndPropertyProviderClasses() throws Exception {
        final String url = "jdbc:h2:mem:databaseFactoryConfiguredClasses;DB_CLOSE_DELAY=-1";

        try (ResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor()) {
            Database database = DatabaseFactory.getInstance().openDatabase(
                    url,
                    "sa",
                    "",
                    Driver.class.getName(),
                    H2Database.class.getName(),
                    null,
                    Properties.class.getName(),
                    resourceAccessor
            );

            try {
                assertThat(database).isInstanceOf(H2Database.class);
                assertThat(database.getConnection().getURL()).startsWith("jdbc:h2:mem:databaseFactoryConfiguredClasses");
                assertThat(database.getConnection().isClosed()).isFalse();
            } finally {
                database.close();
            }
        }
    }
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import org.hsqldb.dbinfo.DatabaseInformation;
import org.junit.jupiter.api.Test;

public class DatabaseInformationTest {

    @Test
    void factoryReturnsMetadataProducerWhenFullImplementationCannotBeCreated() {
        DatabaseInformation databaseInformation = DatabaseInformation.newDatabaseInformation(null);

        assertThat(databaseInformation).isNotNull();
        assertThat(databaseInformation.getSystemTable(null, "SYSTEM_TABLES")).isNull();
    }
}

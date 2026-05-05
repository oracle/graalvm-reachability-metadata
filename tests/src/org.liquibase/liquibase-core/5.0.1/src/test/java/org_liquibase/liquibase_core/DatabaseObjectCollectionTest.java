/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.database.core.H2Database;
import liquibase.structure.DatabaseObjectCollection;
import liquibase.structure.core.Table;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class DatabaseObjectCollectionTest {

    @Test
    void returnsSerializableFieldValueForStoredDatabaseObjectType() {
        DatabaseObjectCollection collection = new DatabaseObjectCollection(new H2Database());
        Table table = new Table().setName("person");
        collection.add(table);

        Object serializableValue = collection.getSerializableFieldValue(Table.class.getName());

        assertThat(serializableValue)
                .isInstanceOf(Set.class);
        Set<?> tables = (Set<?>) serializableValue;
        assertThat(tables).hasSize(1);
        assertThat(tables.iterator().next()).isSameAs(table);
    }
}

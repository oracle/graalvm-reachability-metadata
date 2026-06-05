/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.database.core.H2Database;
import liquibase.structure.DatabaseObject;
import liquibase.structure.DatabaseObjectCollection;
import liquibase.structure.core.Table;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class DatabaseObjectCollectionTest {

    @Test
    void serializableFieldValueResolvesObjectsBySerializedClassName() {
        DatabaseObjectCollection collection = new DatabaseObjectCollection(new H2Database());
        Table orders = new Table().setName("orders");
        Table customers = new Table().setName("customers");
        collection.add(orders);
        collection.add(customers);

        assertThat(collection.getSerializableFields()).containsExactly(Table.class.getName());
        String tableField = collection.getSerializableFields().iterator().next();

        @SuppressWarnings("unchecked")
        Set<DatabaseObject> fieldValue = (Set<DatabaseObject>) collection.getSerializableFieldValue(tableField);

        assertThat(fieldValue)
                .extracting(DatabaseObject::getName)
                .containsExactly("customers", "orders");
    }
}

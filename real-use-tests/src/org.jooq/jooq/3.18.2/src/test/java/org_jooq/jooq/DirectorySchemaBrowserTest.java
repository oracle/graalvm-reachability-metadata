/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jooq.jooq;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Meta;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises jOOQ's schema-introspection subsystem to build an administrative directory browser without generated tables.
 */
public class DirectorySchemaBrowserTest {
    @Test
    @Timeout(60)
    public void discoversColumnsAndRelationshipsAndBrowsesRowsInPrimaryKeyOrder() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:directory_schema_browser", "sa", "")) {
            DSLContext database = DSL.using(connection, SQLDialect.H2);
            Table<Record> customers = DSL.table(DSL.name("directory", "customer"));
            Table<Record> contacts = DSL.table(DSL.name("directory", "contact"));
            Field<Integer> customerId = DSL.field(DSL.name("customer_id"), Integer.class);
            Field<Integer> contactId = DSL.field(DSL.name("contact_id"), Integer.class);
            Field<String> label = DSL.field(DSL.name("label"), String.class);
            Field<String> email = DSL.field(DSL.name("email"), String.class);

            database.createSchema("directory").execute();
            database.createTable(customers)
                    .column(customerId, SQLDataType.INTEGER.nullable(false))
                    .column(label, SQLDataType.VARCHAR(80).nullable(false))
                    .primaryKey(customerId)
                    .execute();
            database.createTable(contacts)
                    .column(customerId, SQLDataType.INTEGER.nullable(false))
                    .column(contactId, SQLDataType.INTEGER.nullable(false))
                    .column(email, SQLDataType.VARCHAR(120))
                    .primaryKey(customerId, contactId)
                    .constraint(DSL.constraint("contact_customer_fk")
                            .foreignKey(customerId).references(customers, customerId))
                    .execute();
            database.insertInto(customers, customerId, label)
                    .values(2, "Grace's Workshop")
                    .values(1, "Ada's Studio")
                    .execute();
            database.insertInto(contacts, customerId, contactId, email)
                    .values(2, 1, "grace@example.test")
                    .values(1, 2, (String) null)
                    .values(1, 1, "ada@example.test")
                    .execute();

            // A generic administration screen discovers only the application schema.
            Meta directory = database.meta().filterSchemas(schema -> schema.getName().equals("directory"));
            assertEquals(List.of("contact", "customer"), directory.getTables().stream()
                    .map(Table::getName).sorted().toList());
            Table<?> discoveredContacts = directory.getTables("contact").get(0);
            Table<?> discoveredCustomers = directory.getTables("customer").get(0);
            assertEquals(List.of(
                    new Column("customer_id", false),
                    new Column("contact_id", false),
                    new Column("email", true)), columns(discoveredContacts));
            assertEquals(List.of(new Column("customer_id", false), new Column("label", false)),
                    columns(discoveredCustomers));
            assertNotNull(discoveredContacts.getPrimaryKey());
            assertEquals(List.of("customer_id", "contact_id"), discoveredContacts.getPrimaryKey()
                    .getFields().stream().map(Field::getName).toList());

            // The UI can offer navigation from a contact to its owning customer.
            assertEquals(1, discoveredContacts.getReferences().size());
            ForeignKey<?, ?> owner = discoveredContacts.getReferences().get(0);
            assertEquals("customer", owner.getKey().getTable().getName());
            assertEquals(List.of("customer_id"), owner.getFields().stream().map(Field::getName).toList());
            assertEquals(List.of("customer_id"), owner.getKey().getFields().stream()
                    .map(Field::getName).toList());

            // Build the listing from discovered fields, not the setup's hand-written table definitions.
            Field<Integer> discoveredCustomerId = discoveredContacts.field("customer_id", Integer.class);
            Field<Integer> discoveredContactId = discoveredContacts.field("contact_id", Integer.class);
            Field<String> discoveredEmail = discoveredContacts.field("email", String.class);
            assertEquals(List.of(
                    new Contact(1, 1, "ada@example.test"),
                    new Contact(1, 2, null),
                    new Contact(2, 1, "grace@example.test")),
                    database.selectFrom(discoveredContacts)
                            .orderBy(discoveredContacts.getPrimaryKey().getFields())
                            .fetch(row -> new Contact(row.get(discoveredCustomerId),
                                    row.get(discoveredContactId), row.get(discoveredEmail))));

            Field<Integer> ownerId = discoveredCustomers.field(owner.getKey().getFields().get(0).getName(),
                    Integer.class);
            Field<String> ownerLabel = discoveredCustomers.field("label", String.class);
            assertEquals("Ada's Studio", database.select(ownerLabel).from(discoveredCustomers)
                    .where(ownerId.eq(1)).fetchSingle(ownerLabel));
        }
    }

    private static List<Column> columns(Table<?> table) {
        return Arrays.stream(table.fields())
                .map(field -> new Column(field.getName(), field.getDataType().nullable())).toList();
    }

    private record Column(String name, boolean nullable) {
    }

    private record Contact(int customerId, int contactId, String email) {
    }
}

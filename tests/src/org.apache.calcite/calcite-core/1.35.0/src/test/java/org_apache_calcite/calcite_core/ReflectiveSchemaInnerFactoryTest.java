/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.adapter.java.ReflectiveSchema;
import org.apache.calcite.linq4j.Enumerator;
import org.apache.calcite.schema.ScannableTable;
import org.apache.calcite.schema.Schema;
import org.apache.calcite.schema.Table;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectiveSchemaInnerFactoryTest {
    @Test
    public void createsSchemaUsingPublicNoArgumentConstructor() {
        Schema schema = new ReflectiveSchema.Factory().create(
            null,
            "constructorSchema",
            Map.of("class", ConstructorBackedSchema.class.getName()));

        assertThat(schema.getTableNames()).contains("ENTRIES");
        assertTableContainsLabel(schema, "constructor-created");
    }

    @Test
    public void createsSchemaUsingConfiguredStaticFactoryMethod() {
        Schema schema = new ReflectiveSchema.Factory().create(
            null,
            "staticFactorySchema",
            Map.of(
                "class", StaticFactoryBackedSchema.class.getName(),
                "staticMethod", "create"));

        assertThat(schema.getTableNames()).contains("ENTRIES");
        assertTableContainsLabel(schema, "static-method-created");
    }

    private static void assertTableContainsLabel(Schema schema, String expectedLabel) {
        Table table = schema.getTable("ENTRIES");
        assertThat(table).isInstanceOf(ScannableTable.class);

        Enumerator<Object[]> enumerator = ((ScannableTable) table).scan(null).enumerator();
        try {
            assertThat(enumerator.moveNext()).isTrue();
            assertThat(enumerator.current()).contains(expectedLabel);
            assertThat(enumerator.moveNext()).isFalse();
        } finally {
            enumerator.close();
        }
    }

    public static final class ConstructorBackedSchema {
        public final Entry[] ENTRIES = {
            new Entry(1, "constructor-created")
        };

        public ConstructorBackedSchema() {
        }
    }

    public static final class StaticFactoryBackedSchema {
        public final Entry[] ENTRIES;

        private StaticFactoryBackedSchema(String label) {
            this.ENTRIES = new Entry[] {
                new Entry(2, label)
            };
        }

        public static StaticFactoryBackedSchema create() {
            return new StaticFactoryBackedSchema("static-method-created");
        }
    }

    public static final class Entry {
        public final int id;
        public final String label;

        Entry(int id, String label) {
            this.id = id;
            this.label = label;
        }
    }
}

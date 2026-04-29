/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.apache.calcite.adapter.java.ReflectiveSchema;
import org.apache.calcite.schema.Schema;
import org.junit.jupiter.api.Test;

public class ReflectiveSchemaInnerFactoryTest {
    @Test
    void createBuildsReflectiveSchemaFromPublicNoArgumentConstructor() {
        ReflectiveSchema.Factory factory = new ReflectiveSchema.Factory();
        Map<String, Object> operand = new HashMap<>();
        operand.put("class", ConstructorBackedSchema.class.getName());

        Schema schema = factory.create(null, "constructorBacked", operand);

        assertThat(schema).isInstanceOf(ReflectiveSchema.class);
        ReflectiveSchema reflectiveSchema = (ReflectiveSchema) schema;
        assertThat(reflectiveSchema.getTarget())
                .isInstanceOf(ConstructorBackedSchema.class);
        assertThat(reflectiveSchema.getTableNames()).contains("ROWS");
    }

    @Test
    void createBuildsReflectiveSchemaFromPublicStaticFactoryMethod() {
        ReflectiveSchema.Factory factory = new ReflectiveSchema.Factory();
        Map<String, Object> operand = new HashMap<>();
        operand.put("class", StaticMethodBackedSchema.class.getName());
        operand.put("staticMethod", "instance");

        Schema schema = factory.create(null, "staticMethodBacked", operand);

        assertThat(schema).isInstanceOf(ReflectiveSchema.class);
        ReflectiveSchema reflectiveSchema = (ReflectiveSchema) schema;
        assertThat(reflectiveSchema.getTarget())
                .isSameAs(StaticMethodBackedSchema.INSTANCE);
        assertThat(reflectiveSchema.getTableNames()).contains("ROWS");
    }

    public static class ConstructorBackedSchema {
        public final Row[] ROWS = {
                new Row(1, "constructor")
        };

        public ConstructorBackedSchema() {
        }
    }

    public static class StaticMethodBackedSchema {
        static final StaticMethodBackedSchema INSTANCE = new StaticMethodBackedSchema();

        public final Row[] ROWS = {
                new Row(2, "static-method")
        };

        public static StaticMethodBackedSchema instance() {
            return INSTANCE;
        }
    }

    public static class Row {
        public final int id;
        public final String source;

        public Row(int id, String source) {
            this.id = id;
            this.source = source;
        }
    }
}

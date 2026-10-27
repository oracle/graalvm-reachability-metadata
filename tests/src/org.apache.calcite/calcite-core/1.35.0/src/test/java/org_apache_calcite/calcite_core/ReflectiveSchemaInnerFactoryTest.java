/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.adapter.java.ReflectiveSchema;
import org.apache.calcite.schema.Schema;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.tools.Frameworks;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectiveSchemaInnerFactoryTest {
    @Test
    void createsSchemaFromPublicConstructor() {
        Schema schema = createSchema(Map.of("class", ConstructorDirectory.class.getName()));

        assertThat(schema.getTableNames()).contains("people");
    }

    @Test
    void createsSchemaFromStaticFactoryMethod() {
        Schema schema = createSchema(Map.of(
                "class", StaticDirectory.class.getName(),
                "staticMethod", "instance"));

        assertThat(schema.getTableNames()).contains("people");
    }

    private Schema createSchema(Map<String, Object> operand) {
        SchemaPlus rootSchema = Frameworks.createRootSchema(true);
        return new ReflectiveSchema.Factory().create(rootSchema, "reflective", operand);
    }

    public static class ConstructorDirectory {
        public final Person[] people = {new Person("Ada")};
    }

    public static class StaticDirectory {
        public final Person[] people = {new Person("Lin")};

        public static StaticDirectory instance() {
            return new StaticDirectory();
        }
    }

    public static class Person {
        public final String name;

        public Person(String name) {
            this.name = name;
        }
    }
}

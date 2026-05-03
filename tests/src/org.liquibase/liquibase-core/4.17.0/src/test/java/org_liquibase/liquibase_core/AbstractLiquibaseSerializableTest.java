/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.parser.core.ParsedNode;
import liquibase.parser.core.ParsedNodeException;
import liquibase.resource.ResourceAccessor;
import liquibase.serializer.AbstractLiquibaseSerializable;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractLiquibaseSerializableTest {

    @Test
    void loadInstantiatesSerializableFieldsAndCollections() throws Exception {
        ContainerSerializable container = new ContainerSerializable();
        ParsedNode nestedItems = new ParsedNode(null, "nestedItems")
                .addChild(new ParsedNode(null, "nestedItem").addChild(null, "name", "from-field-collection"));
        ParsedNode root = new ParsedNode(null, "container")
                .addChild(nestedItems)
                .addChild(new ParsedNode(null, "singleChild").addChild(null, "name", "from-direct-field"))
                .addChild(new ParsedNode(null, "nestedItem").addChild(null, "name", "from-element-name"));

        container.load(root, null);

        assertThat(container.getNestedItems())
                .extracting(NestedItemSerializable::getName)
                .containsExactly("from-field-collection", "from-element-name");
        assertThat(container.getSingleChild().getName()).isEqualTo("from-direct-field");
    }

    @Test
    void loadConvertsEscapedValuesWithReflectiveConstructors() throws Exception {
        EscapedValuesSerializable values = new EscapedValuesSerializable();
        ParsedNode root = new ParsedNode(null, "escapedValues")
                .addChild(null, "dateValue", "2020-01-02!{java.sql.Date}")
                .addChild(null, "stringValue", "liquibase!{java.lang.StringBuilder}");

        values.load(root, null);

        assertThat(values.getDateValue()).isInstanceOf(Date.class);
        assertThat(values.getDateValue().toString()).isEqualTo("2020-01-02");
        assertThat(values.getStringValue()).isInstanceOf(StringBuilder.class);
        assertThat(values.getStringValue().toString()).isEqualTo("liquibase");
    }

    public static class ContainerSerializable extends BaseSerializable {
        private final List<NestedItemSerializable> nestedItems = new ArrayList<>();
        private NestedItemSerializable singleChild;

        @Override
        public String getSerializedObjectName() {
            return "container";
        }

        public List<NestedItemSerializable> getNestedItems() {
            return nestedItems;
        }

        public NestedItemSerializable getSingleChild() {
            return singleChild;
        }
    }

    public static class NestedItemSerializable extends BaseSerializable {
        private String name;

        @Override
        public String getSerializedObjectName() {
            return "nestedItem";
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class EscapedValuesSerializable extends BaseSerializable {
        private Object dateValue;
        private Object stringValue;

        @Override
        public String getSerializedObjectName() {
            return "escapedValues";
        }

        public Object getDateValue() {
            return dateValue;
        }

        public void setDateValue(Object dateValue) {
            this.dateValue = dateValue;
        }

        public Object getStringValue() {
            return stringValue;
        }

        public void setStringValue(Object stringValue) {
            this.stringValue = stringValue;
        }
    }

    public abstract static class BaseSerializable extends AbstractLiquibaseSerializable {

        @Override
        public String getSerializedObjectNamespace() {
            return "test";
        }

        @Override
        public void load(ParsedNode parsedNode, ResourceAccessor resourceAccessor) throws ParsedNodeException {
            super.load(parsedNode, resourceAccessor);
        }
    }
}

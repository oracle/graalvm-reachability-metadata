/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.parser.core.ParsedNode;
import liquibase.serializer.AbstractLiquibaseSerializable;
import liquibase.serializer.LiquibaseSerializable;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AbstractLiquibaseSerializableTest {

    @Test
    void loadInstantiatesNestedSerializableFieldsAndEscapedValues() throws Exception {
        final RootSerializable root = new RootSerializable();
        final ParsedNode rootNode = new ParsedNode(null, "root")
                .addChild(collectionFieldNode())
                .addChild(new ParsedNode(null, "child").addChild(null, "name", "direct-child"))
                .addChild(new ParsedNode(null, "orphan").addChild(null, "name", "orphan-child"))
                .addChild(null, "stringValue", escaped("stored", StringBackedValue.class))
                .addChild(null, "dateValue", escaped("2020-01-02T03:04:05", Date.class))
                .addChild(null, "enumValue", escaped("SECOND", ExampleEnum.class));

        root.load(rootNode, null);

        assertEquals(1, root.getChildren().size());
        assertEquals("collection-child", root.getChildren().get(0).getName());
        assertNotNull(root.getChild());
        assertEquals("direct-child", root.getChild().getName());
        assertEquals(1, root.getOrphanChildren().size());
        assertEquals("orphan-child", root.getOrphanChildren().get(0).getName());
        assertEquals("stored", root.getStringValue().getValue());
        assertInstanceOf(Date.class, root.getDateValue());
        assertEquals(ExampleEnum.SECOND, root.getEnumValue());
    }

    private static ParsedNode collectionFieldNode() throws Exception {
        final ParsedNode childrenNode = new ParsedNode(null, "children");
        childrenNode.addChild(new ParsedNode(null, "child").addChild(null, "name", "collection-child"));
        return childrenNode;
    }

    private static String escaped(String value, Class<?> type) {
        return value + "!{" + type.getName() + "}";
    }

    public static class RootSerializable extends AbstractLiquibaseSerializable {
        private final List<ChildSerializable> children = new ArrayList<>();
        private final List<OrphanSerializable> orphanChildren = new ArrayList<>();
        private ChildSerializable child;
        private StringBackedValue stringValue;
        private java.util.Date dateValue;
        private ExampleEnum enumValue;

        @Override
        public String getSerializedObjectName() {
            return "root";
        }

        @Override
        public String getSerializedObjectNamespace() {
            return LiquibaseSerializable.GENERIC_CHANGELOG_EXTENSION_NAMESPACE;
        }

        public List<ChildSerializable> getChildren() {
            return children;
        }

        public List<OrphanSerializable> getOrphanChildren() {
            return orphanChildren;
        }

        public ChildSerializable getChild() {
            return child;
        }

        public void setChild(ChildSerializable child) {
            this.child = child;
        }

        public StringBackedValue getStringValue() {
            return stringValue;
        }

        public void setStringValue(StringBackedValue stringValue) {
            this.stringValue = stringValue;
        }

        public java.util.Date getDateValue() {
            return dateValue;
        }

        public void setDateValue(java.util.Date dateValue) {
            this.dateValue = dateValue;
        }

        public ExampleEnum getEnumValue() {
            return enumValue;
        }

        public void setEnumValue(ExampleEnum enumValue) {
            this.enumValue = enumValue;
        }
    }

    public static class ChildSerializable extends NamedSerializable {
        @Override
        public String getSerializedObjectName() {
            return "child";
        }
    }

    public static class OrphanSerializable extends NamedSerializable {
        @Override
        public String getSerializedObjectName() {
            return "orphan";
        }
    }

    public abstract static class NamedSerializable extends AbstractLiquibaseSerializable {
        private String name;

        @Override
        public String getSerializedObjectNamespace() {
            return LiquibaseSerializable.GENERIC_CHANGELOG_EXTENSION_NAMESPACE;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class StringBackedValue {
        private final String value;

        public StringBackedValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public enum ExampleEnum {
        FIRST,
        SECOND
    }
}

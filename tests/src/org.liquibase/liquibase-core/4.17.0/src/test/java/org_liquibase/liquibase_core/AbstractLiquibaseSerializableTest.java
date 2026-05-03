/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.parser.core.ParsedNode;
import liquibase.parser.core.ParsedNodeException;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.serializer.AbstractLiquibaseSerializable;
import liquibase.serializer.LiquibaseSerializable.SerializationType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.math.BigInteger;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AbstractLiquibaseSerializableTest {

    @Test
    void loadCreatesSerializableCollectionsNestedObjectsAndEscapedValues() throws Exception {
        ParentSerializable parent = new ParentSerializable();
        ParsedNode root = new ParsedNode(null, "parent");
        root.addChild(collectionFieldNode());
        root.addChild(standaloneCollectionElementNode());
        root.addChild(nestedFieldNode());
        root.addChild(null, "dateValue", "2020-01-02!{java.sql.Date}");
        root.addChild(null, "numberValue", "12345!{java.math.BigInteger}");

        parent.load(root, new ClassLoaderResourceAccessor());

        assertEquals(Arrays.asList("from collection field", "from standalone element"), parent.getChildNames());
        assertNotNull(parent.getNested());
        assertEquals("from nested field", parent.getNested().getName());
        assertEquals(Date.valueOf("2020-01-02"), parent.getDateValue());
        assertEquals(new BigInteger("12345"), parent.getNumberValue());
    }

    private static ParsedNode collectionFieldNode() throws ParsedNodeException {
        ParsedNode collectionNode = new ParsedNode(null, "children");
        collectionNode.addChild(childNode("from collection field"));
        return collectionNode;
    }

    private static ParsedNode standaloneCollectionElementNode() throws ParsedNodeException {
        return childNode("from standalone element");
    }

    private static ParsedNode nestedFieldNode() throws ParsedNodeException {
        ParsedNode nestedNode = new ParsedNode(null, "nested");
        nestedNode.addChild(null, "name", "from nested field");
        return nestedNode;
    }

    private static ParsedNode childNode(String name) throws ParsedNodeException {
        ParsedNode childNode = new ParsedNode(null, "child");
        childNode.addChild(null, "name", name);
        return childNode;
    }

    public static class ParentSerializable extends AbstractLiquibaseSerializable {
        private final List<ChildSerializable> children = new ArrayList<>();
        private NestedSerializable nested;
        private Date dateValue;
        private BigInteger numberValue;

        public ParentSerializable() {
        }

        @Override
        public String getSerializedObjectName() {
            return "parent";
        }

        @Override
        public Set<String> getSerializableFields() {
            return new LinkedHashSet<>(Arrays.asList("children", "nested", "dateValue", "numberValue"));
        }

        @Override
        public Object getSerializableFieldValue(String field) {
            switch (field) {
                case "children":
                    return children;
                case "nested":
                    return nested;
                case "dateValue":
                    return dateValue;
                case "numberValue":
                    return numberValue;
                default:
                    throw new IllegalArgumentException("Unknown field: " + field);
            }
        }

        @Override
        protected Class getSerializableFieldDataTypeClass(String field) {
            switch (field) {
                case "children":
                    return Collection.class;
                case "nested":
                    return NestedSerializable.class;
                case "dateValue":
                    return Date.class;
                case "numberValue":
                    return BigInteger.class;
                default:
                    throw new IllegalArgumentException("Unknown field: " + field);
            }
        }

        @Override
        protected Type[] getSerializableFieldDataTypeClassParameters(String field) {
            if ("children".equals(field)) {
                return new Type[] {ChildSerializable.class};
            }
            return new Type[0];
        }

        @Override
        protected void setSerializableFieldValue(String field, Object value) {
            if ("nested".equals(field)) {
                nested = (NestedSerializable) value;
                return;
            }
            throw new IllegalArgumentException("Unknown field: " + field);
        }

        @Override
        public String getSerializedObjectNamespace() {
            return GENERIC_CHANGELOG_EXTENSION_NAMESPACE;
        }

        public List<String> getChildNames() {
            List<String> childNames = new ArrayList<>();
            for (ChildSerializable child : children) {
                childNames.add(child.getName());
            }
            return childNames;
        }

        public NestedSerializable getNested() {
            return nested;
        }

        public Date getDateValue() {
            return dateValue;
        }

        public void setDateValue(Date dateValue) {
            this.dateValue = dateValue;
        }

        public BigInteger getNumberValue() {
            return numberValue;
        }

        public void setNumberValue(BigInteger numberValue) {
            this.numberValue = numberValue;
        }
    }

    public static class ChildSerializable extends AbstractLiquibaseSerializable {
        private String name;

        public ChildSerializable() {
        }

        @Override
        public String getSerializedObjectName() {
            return "child";
        }

        @Override
        public Set<String> getSerializableFields() {
            return new LinkedHashSet<>(Arrays.asList("name"));
        }

        @Override
        public Object getSerializableFieldValue(String field) {
            if ("name".equals(field)) {
                return name;
            }
            throw new IllegalArgumentException("Unknown field: " + field);
        }

        @Override
        protected Class getSerializableFieldDataTypeClass(String field) {
            if ("name".equals(field)) {
                return String.class;
            }
            throw new IllegalArgumentException("Unknown field: " + field);
        }

        @Override
        public String getSerializedObjectNamespace() {
            return GENERIC_CHANGELOG_EXTENSION_NAMESPACE;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class NestedSerializable extends AbstractLiquibaseSerializable {
        private String name;

        public NestedSerializable() {
        }

        @Override
        public String getSerializedObjectName() {
            return "nested";
        }

        @Override
        public Set<String> getSerializableFields() {
            return new LinkedHashSet<>(Arrays.asList("name"));
        }

        @Override
        public Object getSerializableFieldValue(String field) {
            if ("name".equals(field)) {
                return name;
            }
            throw new IllegalArgumentException("Unknown field: " + field);
        }

        @Override
        protected Class getSerializableFieldDataTypeClass(String field) {
            if ("name".equals(field)) {
                return String.class;
            }
            throw new IllegalArgumentException("Unknown field: " + field);
        }

        @Override
        public String getSerializableFieldNamespace(String field) {
            return GENERIC_CHANGELOG_EXTENSION_NAMESPACE;
        }

        @Override
        public SerializationType getSerializableFieldType(String field) {
            return SerializationType.NAMED_FIELD;
        }

        @Override
        public String getSerializedObjectNamespace() {
            return GENERIC_CHANGELOG_EXTENSION_NAMESPACE;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}

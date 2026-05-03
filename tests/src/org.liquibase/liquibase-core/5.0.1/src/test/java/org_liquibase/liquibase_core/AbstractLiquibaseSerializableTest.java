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

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractLiquibaseSerializableTest {

    @Test
    void loadsCollectionWhenNodeMatchesCollectionField() throws Exception {
        ParentWithChildren parent = new ParentWithChildren();
        ParsedNode rootNode = new ParsedNode(null, "parent");
        ParsedNode childrenNode = new ParsedNode(null, "children");
        childrenNode.addChild(childNodeWithValue("from-collection-field"));
        rootNode.addChild(childrenNode);

        parent.load(rootNode, null);

        assertThat(parent.getChildren())
                .extracting(ChildSerializable::getValue)
                .containsExactly("from-collection-field");
    }

    @Test
    void loadsCollectionWhenNodeMatchesElementName() throws Exception {
        ParentWithChildren parent = new ParentWithChildren();
        ParsedNode rootNode = new ParsedNode(null, "parent");
        rootNode.addChild(childNodeWithValue("from-element-name"));

        parent.load(rootNode, null);

        assertThat(parent.getChildren())
                .extracting(ChildSerializable::getValue)
                .containsExactly("from-element-name");
    }

    @Test
    void loadsNestedSerializableObject() throws Exception {
        ParentWithChild parent = new ParentWithChild();
        ParsedNode rootNode = new ParsedNode(null, "parent");
        rootNode.addChild(childNodeWithValue("nested-child"));

        parent.load(rootNode, null);

        assertThat(parent.getChild()).isNotNull();
        assertThat(parent.getChild().getValue()).isEqualTo("nested-child");
    }

    @Test
    void convertsEscapedValuesUsingEncodedClassNames() throws Exception {
        ParentWithEscapedValues parent = new ParentWithEscapedValues();
        ParsedNode rootNode = new ParsedNode(null, "parent");
        rootNode.addChild(null, "text", "liquibase!{java.lang.String}");
        rootNode.addChild(null, "createdOn", "2024-01-02!{java.sql.Date}");

        parent.load(rootNode, null);

        assertThat(parent.getText()).isEqualTo("liquibase");
        assertThat(parent.getCreatedOn()).isEqualTo(Date.valueOf("2024-01-02"));
    }

    private static ParsedNode childNodeWithValue(String value) throws Exception {
        ParsedNode childNode = new ParsedNode(null, "child");
        childNode.addChild(null, "value", value);
        return childNode;
    }

    public static class ParentWithChildren extends NamedSerializable {
        private final List<ChildSerializable> children = new ArrayList<>();

        @Override
        public String getSerializedObjectName() {
            return "parent";
        }

        public List<ChildSerializable> getChildren() {
            return children;
        }
    }

    public static class ParentWithChild extends NamedSerializable {
        private ChildSerializable child;

        @Override
        public String getSerializedObjectName() {
            return "parent";
        }

        public ChildSerializable getChild() {
            return child;
        }
    }

    public static class ParentWithEscapedValues extends NamedSerializable {
        private String text;
        private Date createdOn;

        @Override
        public String getSerializedObjectName() {
            return "parent";
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public Date getCreatedOn() {
            return createdOn;
        }

        public void setCreatedOn(Date createdOn) {
            this.createdOn = createdOn;
        }
    }

    public static class ChildSerializable extends NamedSerializable {
        private String value;

        @Override
        public String getSerializedObjectName() {
            return "child";
        }

        @Override
        public SerializationType getSerializableFieldType(String field) {
            return SerializationType.NAMED_FIELD;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    public abstract static class NamedSerializable extends AbstractLiquibaseSerializable {
        @Override
        public String getSerializedObjectNamespace() {
            return LiquibaseSerializable.GENERIC_CHANGELOG_EXTENSION_NAMESPACE;
        }
    }
}

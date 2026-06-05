/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.change.AbstractChange;
import liquibase.change.ChangeParameterMetaData;
import liquibase.change.DatabaseChange;
import liquibase.database.Database;
import liquibase.exception.ValidationErrors;
import liquibase.parser.core.ParsedNode;
import liquibase.resource.ResourceAccessor;
import liquibase.serializer.AbstractLiquibaseSerializable;
import liquibase.statement.SqlStatement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AbstractChangeTest {

    @Test
    void loadCreatesNestedSerializableParametersFromWrappedAndDirectNodes() throws Exception {
        SerializableParameterChange change = new SerializableParameterChange();
        ParsedNode root = new ParsedNode(null, "serializableParameterChange");
        ParsedNode wrappedChildren = new ParsedNode(null, "children");
        wrappedChildren.addChild(serializableNode("wrapped-alpha"));
        root.addChild(wrappedChildren);
        root.addChild(serializableNode("direct-beta"));
        root.addChild(new ParsedNode(null, "single").addChild(null, "value", "single-gamma"));

        change.load(root, (ResourceAccessor) null);

        assertEquals(2, change.getChildren().size());
        assertEquals("wrapped-alpha", change.getChildren().get(0).getValue());
        assertEquals("direct-beta", change.getChildren().get(1).getValue());
        assertNotNull(change.getSingle());
        assertEquals("single-gamma", change.getSingle().getValue());
    }

    @Test
    void createChangeParameterMetadataUsesIsPrefixedReadMethodFallback() {
        SerializableParameterChange change = new SerializableParameterChange();

        ChangeParameterMetaData metadata = change.createMetadataFor("special");

        assertEquals("special", metadata.getParameterName());
        assertEquals(String.class, metadata.getDataTypeClass());
    }

    private static ParsedNode serializableNode(String value) throws Exception {
        return new ParsedNode(null, "testSerializable").addChild(null, "value", value);
    }

    @DatabaseChange(
            name = "serializableParameterChange",
            description = "Exercises AbstractChange loading",
            priority = 1
    )
    public static class SerializableParameterChange extends AbstractChange {
        private List<TestSerializable> children = new ArrayList<>();
        private TestSerializable single;
        private String special = "fallback";

        public ChangeParameterMetaData createMetadataFor(String parameterName) {
            return createChangeParameterMetadata(parameterName);
        }

        public List<TestSerializable> getChildren() {
            return children;
        }

        public void setChildren(List<TestSerializable> children) {
            this.children = children;
        }

        public TestSerializable getSingle() {
            return single;
        }

        public void setSingle(TestSerializable single) {
            this.single = single;
        }

        public String isSpecial() {
            return special;
        }

        public void setSpecial(String special) {
            this.special = special;
        }

        @Override
        public SqlStatement[] generateStatements(Database database) {
            return new SqlStatement[0];
        }

        @Override
        public String getConfirmationMessage() {
            return "loaded serializable parameters";
        }

        @Override
        public ValidationErrors validate(Database database) {
            return new ValidationErrors(this);
        }
    }

    public static class TestSerializable extends AbstractLiquibaseSerializable {
        private String value;

        public TestSerializable() {
        }

        @Override
        public String getSerializedObjectName() {
            return "testSerializable";
        }

        @Override
        public String getSerializedObjectNamespace() {
            return STANDARD_CHANGELOG_NAMESPACE;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}

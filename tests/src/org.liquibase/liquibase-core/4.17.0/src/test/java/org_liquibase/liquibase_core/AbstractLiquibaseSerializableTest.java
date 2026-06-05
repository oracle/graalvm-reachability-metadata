/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.parser.core.ParsedNode;
import liquibase.resource.ResourceAccessor;
import liquibase.serializer.AbstractLiquibaseSerializable;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AbstractLiquibaseSerializableTest {

    @Test
    void loadCreatesNestedSerializableFieldsAndCollectionElements() throws Exception {
        ParentSerializable parent = new ParentSerializable();
        ParsedNode parentNode = new ParsedNode(null, "parent");
        ParsedNode collectionNode = new ParsedNode(null, "items");
        collectionNode.addChild(childNode("alpha"));
        parentNode.addChild(collectionNode);
        parentNode.addChild(childNode("beta"));
        parentNode.addChild(new ParsedNode(null, "directChild").addChild(null, "value", "gamma"));

        parent.load(parentNode, (ResourceAccessor) null);

        assertEquals(2, parent.getItems().size());
        assertEquals("alpha", parent.getItems().get(0).getValue());
        assertEquals("beta", parent.getItems().get(1).getValue());
        assertNotNull(parent.getDirectChild());
        assertEquals("gamma", parent.getDirectChild().getValue());
    }

    @Test
    void loadConvertsEscapedDateAndStringConstructedValues() throws Exception {
        ParentSerializable parent = new ParentSerializable();
        ParsedNode parentNode = new ParsedNode(null, "parent");
        parentNode.addChild(null, "dateValue", "2020-01-02T03:04:05!{java.util.Date}");
        parentNode.addChild(null, "numberValue", "123456789!{java.math.BigInteger}");

        parent.load(parentNode, (ResourceAccessor) null);

        assertInstanceOf(Date.class, parent.getDateValue());
        assertEquals(new BigInteger("123456789"), parent.getNumberValue());
    }

    private static ParsedNode childNode(String value) throws Exception {
        return new ParsedNode(null, "child").addChild(null, "value", value);
    }

    public static class ParentSerializable extends TestLiquibaseSerializable {
        private final List<ChildSerializable> items = new ArrayList<>();
        private ChildSerializable directChild;
        private Date dateValue;
        private BigInteger numberValue;

        @Override
        public String getSerializedObjectName() {
            return "parent";
        }

        public List<ChildSerializable> getItems() {
            return items;
        }

        public ChildSerializable getDirectChild() {
            return directChild;
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

    public static class ChildSerializable extends TestLiquibaseSerializable {
        private String value;

        @Override
        public String getSerializedObjectName() {
            return "child";
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    public abstract static class TestLiquibaseSerializable extends AbstractLiquibaseSerializable {

        @Override
        public String getSerializedObjectNamespace() {
            return STANDARD_CHANGELOG_NAMESPACE;
        }
    }
}

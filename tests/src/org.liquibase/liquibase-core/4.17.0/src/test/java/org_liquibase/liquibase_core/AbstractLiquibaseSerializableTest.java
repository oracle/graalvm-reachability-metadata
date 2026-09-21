/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.change.ColumnConfig;
import liquibase.change.core.CreateTableChange;
import liquibase.parser.core.ParsedNode;
import liquibase.serializer.AbstractLiquibaseSerializable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.math.BigInteger;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractLiquibaseSerializableTest {

    @Test
    void loadsCollectionFieldFromWrapperNode() throws Exception {
        CreateTableChange change = new CreateTableChange();
        ParsedNode root = new ParsedNode(null, "createTable")
                .addChild(new ParsedNode(null, "columns")
                        .addChild(columnNode("id", "int")));

        change.load(root, null);

        assertThat(change.getColumns())
                .singleElement()
                .satisfies(column -> {
                    assertThat(column.getName()).isEqualTo("id");
                    assertThat(column.getType()).isEqualTo("int");
                });
    }

    @Test
    void loadsCollectionFieldFromElementNode() throws Exception {
        CreateTableChange change = new CreateTableChange();
        ParsedNode root = new ParsedNode(null, "createTable")
                .addChild(columnNode("username", "varchar(32)"));

        change.load(root, null);

        assertThat(change.getColumns())
                .singleElement()
                .satisfies(column -> {
                    assertThat(column.getName()).isEqualTo("username");
                    assertThat(column.getType()).isEqualTo("varchar(32)");
                });
    }

    @Test
    void loadsCollectionFieldDeclaredBySerializableFieldName() throws Exception {
        CollectionHolder holder = new CollectionHolder();
        ParsedNode root = new ParsedNode(null, "holder")
                .addChild(new ParsedNode(null, "children")
                        .addChild(childNode("wrapped-child")));

        holder.load(root, null);

        assertThat(holder.getChildren())
                .singleElement()
                .extracting(SerializableChild::getValue)
                .isEqualTo("wrapped-child");
    }

    @Test
    void loadsCollectionFieldDeclaredByElementName() throws Exception {
        CollectionHolder holder = new CollectionHolder();
        ParsedNode root = new ParsedNode(null, "holder")
                .addChild(childNode("direct-child"));

        holder.load(root, null);

        assertThat(holder.getChildren())
                .singleElement()
                .extracting(SerializableChild::getValue)
                .isEqualTo("direct-child");
    }

    @Test
    void loadsConcreteSerializableField() throws Exception {
        ColumnHolder holder = new ColumnHolder();
        ParsedNode root = new ParsedNode(null, "holder")
                .addChild(new ParsedNode(null, "columnConfig")
                        .addChild(null, "name", "created_at")
                        .addChild(null, "type", "timestamp"));

        holder.load(root, null);

        assertThat(holder.getColumnConfig().getName()).isEqualTo("created_at");
        assertThat(holder.getColumnConfig().getType()).isEqualTo("timestamp");
    }

    @Test
    void convertsEscapedDatesAndStringConstructedValues() {
        EscapedValueAccessor accessor = new EscapedValueAccessor();

        Object date = accessor.convert("2024-01-02!{java.sql.Date}");
        Object integer = accessor.convert("12345!{java.math.BigInteger}");

        assertThat(date).isEqualTo(Date.valueOf("2024-01-02"));
        assertThat(integer).isEqualTo(new BigInteger("12345"));
    }

    private static ParsedNode columnNode(String name, String type) throws Exception {
        return new ParsedNode(null, "column")
                .addChild(null, "name", name)
                .addChild(null, "type", type);
    }

    private static ParsedNode childNode(String value) throws Exception {
        return new ParsedNode(null, "serializableChild")
                .addChild(null, "value", value);
    }

    public static final class CollectionHolder extends AbstractLiquibaseSerializable {
        private final List<SerializableChild> children = new ArrayList<>();

        @Override
        public String getSerializedObjectName() {
            return "holder";
        }

        @Override
        public Set<String> getSerializableFields() {
            return Collections.singleton("children");
        }

        @Override
        public Object getSerializableFieldValue(String field) {
            return children;
        }

        @Override
        protected Class getSerializableFieldDataTypeClass(String field) {
            return List.class;
        }

        @Override
        protected Type[] getSerializableFieldDataTypeClassParameters(String field) {
            return new Type[] {SerializableChild.class};
        }

        @Override
        public String getSerializedObjectNamespace() {
            return STANDARD_CHANGELOG_NAMESPACE;
        }

        private List<SerializableChild> getChildren() {
            return children;
        }
    }

    public static final class SerializableChild extends AbstractLiquibaseSerializable {
        private String value;

        @Override
        public String getSerializedObjectName() {
            return "serializableChild";
        }

        @Override
        public Set<String> getSerializableFields() {
            return Collections.singleton("value");
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

    private static final class ColumnHolder extends AbstractLiquibaseSerializable {
        private ColumnConfig columnConfig;

        @Override
        public String getSerializedObjectName() {
            return "holder";
        }

        @Override
        public Set<String> getSerializableFields() {
            return Collections.singleton("columnConfig");
        }

        @Override
        public Object getSerializableFieldValue(String field) {
            return columnConfig;
        }

        @Override
        protected Class getSerializableFieldDataTypeClass(String field) {
            return ColumnConfig.class;
        }

        @Override
        protected void setSerializableFieldValue(String field, Object value) {
            columnConfig = (ColumnConfig) value;
        }

        @Override
        public String getSerializedObjectNamespace() {
            return STANDARD_CHANGELOG_NAMESPACE;
        }

        private ColumnConfig getColumnConfig() {
            return columnConfig;
        }
    }

    private static final class EscapedValueAccessor extends AbstractLiquibaseSerializable {
        @Override
        public String getSerializedObjectName() {
            return "escapedValueAccessor";
        }

        @Override
        public String getSerializedObjectNamespace() {
            return STANDARD_CHANGELOG_NAMESPACE;
        }

        private Object convert(Object value) {
            return convertEscaped(value);
        }
    }
}

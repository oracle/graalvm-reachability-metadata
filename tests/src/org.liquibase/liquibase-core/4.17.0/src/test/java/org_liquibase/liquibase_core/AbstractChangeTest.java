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
import liquibase.change.DatabaseChangeProperty;
import liquibase.database.Database;
import liquibase.parser.core.ParsedNode;
import liquibase.statement.SqlStatement;
import liquibase.structure.core.DataType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractChangeTest {

    @Test
    void loadInstantiatesSerializableCollectionElementsAndSingleSerializableProperty() throws Exception {
        SerializableParameterChange change = new SerializableParameterChange();
        ParsedNode parsedNode = new ParsedNode(null, "serializableParameterChange")
                .addChild(new ParsedNode(null, "dataTypes")
                        .addChild(new ParsedNode(null, "dataType")
                                .addChild(null, "typeName", "varchar")))
                .addChild(new ParsedNode(null, "dataType")
                        .addChild(null, "typeName", "integer"))
                .addChild(new ParsedNode(null, "snapshotType")
                        .addChild(null, "typeName", "boolean"));

        change.load(parsedNode, null);

        assertThat(change.getDataTypes())
                .extracting(DataType::getTypeName)
                .containsExactly("varchar", "integer");
        assertThat(change.getSnapshotType().getTypeName()).isEqualTo("boolean");
    }

    @Test
    void createChangeParameterMetadataFallsBackToIsPrefixedReadMethodForWriteOnlyDescriptor() {
        FallbackReadMethodChange change = new FallbackReadMethodChange();
        change.setFallbackValue("loaded through is-method");

        ChangeParameterMetaData metadata = change.metadataFor("fallbackValue");

        assertThat(metadata.getParameterName()).isEqualTo("fallbackValue");
        assertThat(metadata.getDataTypeClass()).isEqualTo(String.class);
        assertThat(metadata.getCurrentValue(change)).isEqualTo("loaded through is-method");
    }

    @DatabaseChange(
            name = "serializableParameterChange",
            description = "Change used to exercise AbstractChange serializable loading",
            priority = 1
    )
    public static class SerializableParameterChange extends EmptyStatementChange {

        private List<DataType> dataTypes = new ArrayList<>();
        private DataType snapshotType;

        @DatabaseChangeProperty(requiredForDatabase = "none", supportsDatabase = "all")
        public List<DataType> getDataTypes() {
            return dataTypes;
        }

        public void setDataTypes(List<DataType> dataTypes) {
            this.dataTypes = dataTypes;
        }

        @DatabaseChangeProperty(requiredForDatabase = "none", supportsDatabase = "all")
        public DataType getSnapshotType() {
            return snapshotType;
        }

        public void setSnapshotType(DataType snapshotType) {
            this.snapshotType = snapshotType;
        }
    }

    @DatabaseChange(
            name = "fallbackReadMethodChange",
            description = "Change used to exercise AbstractChange parameter metadata creation",
            priority = 1
    )
    public static class FallbackReadMethodChange extends EmptyStatementChange {

        private String fallbackValue;

        ChangeParameterMetaData metadataFor(String parameterName) {
            return createChangeParameterMetadata(parameterName);
        }

        @DatabaseChangeProperty(requiredForDatabase = "none", supportsDatabase = "all")
        public String isFallbackValue() {
            return fallbackValue;
        }

        public void setFallbackValue(String fallbackValue) {
            this.fallbackValue = fallbackValue;
        }
    }

    public abstract static class EmptyStatementChange extends AbstractChange {

        @Override
        public String getConfirmationMessage() {
            return "OK";
        }

        @Override
        public SqlStatement[] generateStatements(Database database) {
            return new SqlStatement[0];
        }
    }
}

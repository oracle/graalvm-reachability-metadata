/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.change.AbstractChange;
import liquibase.change.ChangeParameterMetaData;
import liquibase.database.Database;
import liquibase.serializer.LiquibaseSerializable;
import liquibase.statement.SqlStatement;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class ChangeParameterMetaDataTest {

    @Test
    void getSupportedDatabasesComputesSupportByInstantiatingChangeWithPublicNoArgConstructor() {
        MetadataChange change = new MetadataChange();
        ChangeParameterMetaData metadata = new ChangeParameterMetaData(
                change,
                "supportedValue",
                "Supported Value",
                null,
                null,
                null,
                String.class,
                new String[] {ChangeParameterMetaData.NONE},
                new String[] {ChangeParameterMetaData.COMPUTE},
                null,
                LiquibaseSerializable.SerializationType.NAMED_FIELD
        );

        Set<String> supportedDatabases = metadata.getSupportedDatabases();

        assertThat(supportedDatabases).containsExactly(ChangeParameterMetaData.ALL);
    }

    @Test
    void getCurrentValueFallsBackToIsPrefixedReadMethodForWriteOnlyDescriptor() {
        MetadataChange change = new MetadataChange();
        change.setFallbackValue("loaded through is-method");
        ChangeParameterMetaData metadata = new ChangeParameterMetaData(
                change,
                "fallbackValue",
                "Fallback Value",
                null,
                null,
                null,
                String.class,
                new String[] {ChangeParameterMetaData.NONE},
                new String[] {ChangeParameterMetaData.ALL},
                null,
                LiquibaseSerializable.SerializationType.NAMED_FIELD
        );

        Object currentValue = metadata.getCurrentValue(change);

        assertThat(currentValue).isEqualTo("loaded through is-method");
    }

    public static class MetadataChange extends AbstractChange {

        private String fallbackValue;
        private String supportedValue;

        public String isFallbackValue() {
            return fallbackValue;
        }

        public void setFallbackValue(String fallbackValue) {
            this.fallbackValue = fallbackValue;
        }

        public String getSupportedValue() {
            return supportedValue;
        }

        public void setSupportedValue(String supportedValue) {
            this.supportedValue = supportedValue;
        }

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

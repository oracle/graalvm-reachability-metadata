/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.change.ChangeParameterMetaData;
import liquibase.change.core.CreateSequenceChange;
import liquibase.change.core.CreateTableChange;
import liquibase.serializer.LiquibaseSerializable;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ChangeParameterMetaDataTest {

    @Test
    void computesSupportedDatabasesByInstantiatingTheChange() {
        ChangeParameterMetaData metadata = new CreateTableChange()
                .createChangeMetaData()
                .getParameters()
                .get("tableName");

        Set<String> supportedDatabases = metadata.getSupportedDatabases();

        assertTrue(supportedDatabases.contains(ChangeParameterMetaData.ALL));
    }

    @Test
    void readsValueWithIsPrefixedFallbackMethod() {
        CreateSequenceChange change = new CreateSequenceChange();
        change.setOrdered(Boolean.TRUE);
        ChangeParameterMetaData metadata = new ChangeParameterMetaData(
                change,
                "ordered",
                "Ordered",
                "Property exposed through an is-prefixed Boolean reader",
                null,
                null,
                Boolean.class,
                new String[]{ChangeParameterMetaData.NONE},
                new String[]{ChangeParameterMetaData.NONE},
                null,
                LiquibaseSerializable.SerializationType.NAMED_FIELD
        );

        assertEquals(Boolean.TRUE, metadata.getCurrentValue(change));
    }
}

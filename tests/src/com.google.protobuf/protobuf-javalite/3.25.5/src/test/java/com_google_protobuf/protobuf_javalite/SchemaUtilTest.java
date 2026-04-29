/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import com.google.protobuf.MapEntryLite;
import com.google.protobuf.MessageLite;
import com.google.protobuf.SchemaUtilCoverageSupport;
import com.google.protobuf.Value;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SchemaUtilTest {
    @Test
    void resolvesFullUnknownFieldSchemaWhenAvailableOnTheClasspath() {
        Object fullSchema = SchemaUtilCoverageSupport.fullUnknownFieldSchema();

        assertThat(fullSchema).isNotNull();
        assertThat(fullSchema.getClass().getName())
                .isEqualTo("com.google.protobuf.UnknownFieldSetSchema");
    }

    @Test
    void resolvesGeneratedMapDefaultEntryHolder() {
        try {
            Object defaultEntry = SchemaUtilCoverageSupport.structFieldsDefaultEntry();

            assertThat(defaultEntry).isInstanceOf(MapEntryLite.class);
            @SuppressWarnings("unchecked")
            MapEntryLite<String, Value> fieldsEntry = (MapEntryLite<String, Value>) defaultEntry;
            MessageLite.Builder valueBuilder = Value.newBuilder().setNumberValue(42);
            Value valueMessage = (Value) valueBuilder.build();
            int messageSize = fieldsEntry.computeMessageSize(
                    1, "number", valueMessage);
            assertThat(messageSize).isPositive();
        } catch (RuntimeException exception) {
            assertThat(exception)
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("fields")
                    .hasMessageContaining("com.google.protobuf.Struct");
        }
    }
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import com.google.protobuf.MapEntryLite;
import com.google.protobuf.SchemaUtilCoverageSupport;
import com.google.protobuf.Value;
import com.google.protobuf.WireFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UnsafeUtilInnerAndroid32MemoryAccessorTest {
    @Test
    void mapDefaultEntryLookupReadsStaticHolderField() {
        Object defaultEntry = SchemaUtilCoverageSupport.mapDefaultEntry(MapDefaultEntryMessage.class, "fields");

        assertThat(defaultEntry).isSameAs(MapDefaultEntryMessage.FieldsDefaultEntryHolder.defaultEntry);
        @SuppressWarnings("unchecked")
        MapEntryLite<String, Value> fieldsEntry = (MapEntryLite<String, Value>) defaultEntry;
        int messageSize = fieldsEntry.computeMessageSize(1, "active", Value.getDefaultInstance());
        assertThat(messageSize).isPositive();
    }

    public static final class MapDefaultEntryMessage {
        private MapDefaultEntryMessage() {
        }

        public static final class FieldsDefaultEntryHolder {
            public static final MapEntryLite<String, Value> defaultEntry = MapEntryLite.newDefaultInstance(
                    WireFormat.FieldType.STRING,
                    "",
                    WireFormat.FieldType.MESSAGE,
                    Value.getDefaultInstance());

            private FieldsDefaultEntryHolder() {
            }
        }
    }
}

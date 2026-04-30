/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.protobuf.MapEntryLite;
import com.google.protobuf.SchemaUtilAccess;
import com.google.protobuf.Value;
import com.google.protobuf.WireFormat;
import org.junit.jupiter.api.Test;

public class UnsafeUtilInnerAndroid64MemoryAccessorTest {
    @Test
    public void mapDefaultEntryLookupReadsStaticFieldThroughAndroid64Accessor() {
        @SuppressWarnings("unchecked")
        MapEntryLite<String, Value> defaultEntry =
                (MapEntryLite<String, Value>)
                        SchemaUtilAccess.mapDefaultEntry(MapEntryContainer.class, "fields");

        assertNotNull(defaultEntry);
        assertEquals("", defaultEntry.getKey());
        assertEquals(Value.getDefaultInstance(), defaultEntry.getValue());
    }

    public static final class MapEntryContainer {
        private MapEntryContainer() {
        }

        public static final class FieldsDefaultEntryHolder {
            public static final MapEntryLite<String, Value> defaultEntry =
                    MapEntryLite.newDefaultInstance(
                            WireFormat.FieldType.STRING,
                            "",
                            WireFormat.FieldType.MESSAGE,
                            Value.getDefaultInstance());

            private FieldsDefaultEntryHolder() {
            }
        }
    }
}

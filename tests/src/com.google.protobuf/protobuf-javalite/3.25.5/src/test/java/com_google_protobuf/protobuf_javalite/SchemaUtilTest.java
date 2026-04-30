/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.protobuf.MapEntryLite;
import com.google.protobuf.SchemaUtilAccess;
import com.google.protobuf.WireFormat;
import org.junit.jupiter.api.Test;

public class SchemaUtilTest {
    @Test
    public void optionalFullUnknownFieldSchemaIsLoadedWhenAvailable() {
        Object fullSchema = SchemaUtilAccess.unknownFieldSetFullSchema();

        assertNotNull(fullSchema);
    }

    @Test
    public void mapDefaultEntryHolderIsResolvedFromMessageClassAndFieldName() {
        assertDoesNotThrow(() -> SchemaUtilAccess.mapDefaultEntry(MapDefaultEntryMessage.class, "labels"));
    }

    private static final class MapDefaultEntryMessage {
        private interface LabelsDefaultEntryHolder {
            MapEntryLite<String, Integer> defaultEntry =
                    MapEntryLite.newDefaultInstance(
                            WireFormat.FieldType.STRING, "", WireFormat.FieldType.INT32, 0);
        }
    }
}

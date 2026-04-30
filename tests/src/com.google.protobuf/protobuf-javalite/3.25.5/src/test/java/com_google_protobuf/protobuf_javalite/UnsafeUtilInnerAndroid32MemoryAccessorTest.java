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
import com.google.protobuf.UnsafeUtilAndroid32MemoryAccessorAccess;
import com.google.protobuf.Value;
import org.junit.jupiter.api.Test;

public class UnsafeUtilInnerAndroid32MemoryAccessorTest {
    @Test
    public void mapDefaultEntryLookupReadsStaticFieldThroughAndroid32Accessor() {
        MapEntryLite<String, Value> defaultEntry =
                UnsafeUtilAndroid32MemoryAccessorAccess.loadMapDefaultEntry();

        assertNotNull(defaultEntry);
        assertEquals("", defaultEntry.getKey());
        assertEquals(Value.getDefaultInstance(), defaultEntry.getValue());
    }
}

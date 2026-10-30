/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jffi;

import com.kenai.jffi.Array;
import com.kenai.jffi.Struct;
import com.kenai.jffi.Type;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ForeignTest {
    @Test
    void resolvesNativeTypesAndComputesAggregateLayouts() {
        assertEquals(1, Type.SINT8.size());
        assertEquals(2, Type.SINT16.size());
        assertEquals(4, Type.SINT32.size());
        assertEquals(8, Type.SINT64.size());

        Struct pair = Struct.newStruct(Type.SINT32, Type.SINT32);
        assertEquals(8, pair.size());
        assertEquals(Type.SINT32.alignment(), pair.alignment());

        Array triplet = Array.newArray(pair, 3);
        assertEquals(24, triplet.size());
        assertEquals(pair.alignment(), triplet.alignment());

        Struct padded = Struct.newStruct(Type.SINT8, Type.SINT32);
        int alignment = Type.SINT32.alignment();
        int integerOffset = ((Type.SINT8.size() + alignment - 1) / alignment) * alignment;
        assertEquals(integerOffset + Type.SINT32.size(), padded.size());
        assertEquals(alignment, padded.alignment());
    }
}

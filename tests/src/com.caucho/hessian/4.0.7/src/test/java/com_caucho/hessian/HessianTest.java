/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HessianTest {
    @Test
    void preservesNestedValuesAndNulls() throws Exception {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("items", Arrays.asList("parcel", null, 23));
        original.put("enabled", true);
        original.put("optional", null);
        assertEquals(original, HessianStreams.roundTrip(original));
    }
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import java.util.ArrayList;
import java.util.List;

import org.hsqldb.lib.InOutUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InOutUtilTest {
    @Test
    void roundTripsACollectionUsingLibrarySerialization() throws Exception {
        ArrayList<String> values = new ArrayList<>(List.of("red", "green", "blue"));
        byte[] encoded = InOutUtil.serialize(values);
        assertTrue(encoded.length > 0);
        Object restored = InOutUtil.deserialize(encoded);
        assertEquals(values, restored);
        assertNotSame(values, restored);
    }
}

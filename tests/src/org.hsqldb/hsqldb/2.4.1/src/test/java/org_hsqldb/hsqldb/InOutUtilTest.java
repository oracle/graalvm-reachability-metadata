/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;

import org.hsqldb.lib.InOutUtil;
import org.junit.jupiter.api.Test;

public class InOutUtilTest {
    @Test
    public void serializesAndDeserializesSerializableObjects() throws Exception {
        String payload = "HSQLDB serialized payload";

        byte[] serialized = InOutUtil.serialize(payload);
        Serializable deserialized = InOutUtil.deserialize(serialized);

        assertTrue(serialized.length > 0);
        assertEquals(payload, deserialized);
    }
}

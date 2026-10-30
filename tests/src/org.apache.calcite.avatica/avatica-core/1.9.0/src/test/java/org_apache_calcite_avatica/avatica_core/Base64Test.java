/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite_avatica.avatica_core;

import org.apache.calcite.avatica.util.Base64;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class Base64Test {
    @Test
    void roundTripsSerializablePayloadWithAndWithoutCompression() throws Exception {
        ArrayList<String> payload = new ArrayList<>(List.of("warehouse", "sales", "quarterly report"));
        String plain = Base64.encodeObject(payload);
        String compressed = Base64.encodeObject(payload, Base64.GZIP | Base64.DO_BREAK_LINES);
        assertNotEquals(plain, compressed);
        assertEquals(payload, Base64.decodeToObject(plain));
        assertEquals(payload, Base64.decodeToObject(compressed));
    }
}

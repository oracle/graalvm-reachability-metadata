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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class Base64Anonymous1Test {
    @Test
    void deserializesUsingTheExistingApplicationClassLoader() throws Exception {
        ArrayList<String> payload = new ArrayList<>(List.of("catalog", "schema"));
        String encoded = Base64.encodeObject(payload, Base64.GZIP);
        Object decoded = Base64.decodeToObject(encoded, Base64.NO_OPTIONS, Base64Anonymous1Test.class.getClassLoader());
        assertInstanceOf(ArrayList.class, decoded);
        assertEquals(payload, decoded);
    }
}

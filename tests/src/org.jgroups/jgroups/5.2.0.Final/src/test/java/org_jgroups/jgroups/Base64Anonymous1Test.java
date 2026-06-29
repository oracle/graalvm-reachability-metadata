/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.util.Base64;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class Base64Anonymous1Test {
    @Test
    void decodesSerializedObjectWithSuppliedClassLoader() throws Exception {
        Map<String, Serializable> payload = new LinkedHashMap<>();
        payload.put("cluster", "loopback");
        payload.put("sequence", 5);
        ClassLoader classLoader = Base64Anonymous1Test.class.getClassLoader();

        String encoded = Base64.encodeObject((Serializable) payload);
        Object decoded = Base64.decodeToObject(encoded, Base64.NO_OPTIONS, classLoader);

        assertThat(decoded)
                .isInstanceOf(LinkedHashMap.class)
                .isEqualTo(payload);
    }
}

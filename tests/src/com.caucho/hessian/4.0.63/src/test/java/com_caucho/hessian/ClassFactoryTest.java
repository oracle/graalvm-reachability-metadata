/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.SerializerFactory;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class ClassFactoryTest {
    @Test
    void resolvesAllowedSerializedType() throws Exception {
        SerializerFactory factory = new SerializerFactory();
        factory.getClassFactory().setWhitelist(true);
        factory.getClassFactory().allow("java.util.*");
        assertEquals(LinkedHashMap.class, factory.loadSerializedClass("java.util.LinkedHashMap"));
        LinkedHashMap<String, String> original = new LinkedHashMap<>();
        original.put("role", "leader");
        assertEquals(original, SerializationSupport.roundTrip(original, factory, true));
    }
}

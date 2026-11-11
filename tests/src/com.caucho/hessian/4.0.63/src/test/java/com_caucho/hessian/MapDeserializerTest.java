/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import java.util.LinkedHashMap;
import com.caucho.hessian.io.SerializerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Timeout(120)
public class MapDeserializerTest {
    @Test
    void restoresConcreteMap() throws Exception {
        LinkedHashMap<String, Integer> values = new LinkedHashMap<>();
        values.put("term", 17);
        values.put("index", 123);
        for (boolean hessian2 : new boolean[] {false, true}) {
            Object restored = SerializationSupport.roundTrip(values, new SerializerFactory(), hessian2);
            assertInstanceOf(LinkedHashMap.class, restored);
            assertEquals(values, restored);
        }
    }
}

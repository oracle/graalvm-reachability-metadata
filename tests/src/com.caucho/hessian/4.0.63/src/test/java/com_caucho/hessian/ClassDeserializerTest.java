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
public class ClassDeserializerTest {
    @Test
    void restoresClassValue() throws Exception {
        assertEquals(LinkedHashMap.class, SerializationSupport.roundTrip(LinkedHashMap.class));
    }

    @Test
    void restoresClassValueWithoutConfiguredLoader() throws Exception {
        for (boolean hessian2 : new boolean[] {false, true}) {
            assertEquals(LinkedHashMap.class, SerializationSupport.roundTrip(LinkedHashMap.class,
                    new SerializerFactory(null), hessian2));
        }
    }
}

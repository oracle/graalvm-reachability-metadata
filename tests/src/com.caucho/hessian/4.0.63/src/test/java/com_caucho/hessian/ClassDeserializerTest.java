/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

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
}

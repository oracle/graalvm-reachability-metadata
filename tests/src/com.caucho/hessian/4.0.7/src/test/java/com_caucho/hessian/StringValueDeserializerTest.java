/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.SerializerFactory;
import com.caucho.hessian.io.StringValueDeserializer;
import com.caucho.hessian.io.StringValueSerializer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StringValueDeserializerTest {
    @Test
    void restoresValueUsingPublicStringConstructor() throws Exception {
        BigDecimal original = new BigDecimal("103.2500");
        for (boolean hessian2 : new boolean[] {false, true}) {
            SerializerFactory factory = HessianStreams.codecs(BigDecimal.class,
                    new StringValueSerializer(), new StringValueDeserializer(BigDecimal.class));
            BigDecimal restored = HessianStreams.roundTrip(original, factory, hessian2);
            assertEquals(original, restored);
            assertEquals(4, restored.scale());
        }
    }
}

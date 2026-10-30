/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.ClassDeserializer;
import com.caucho.hessian.io.SerializerFactory;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertSame;

public class ClassDeserializerTest {
    @Test
    void resolvesClassValuesWithExplicitAndDefaultLoader() throws Exception {
        for (ClassLoader loader : new ClassLoader[] {getClass().getClassLoader(), null}) {
            for (boolean hessian2 : new boolean[] {false, true}) {
                SerializerFactory factory = HessianStreams.codecs(Class.class, null, new ClassDeserializer(loader));
                assertSame(LinkedHashMap.class, HessianStreams.roundTrip(LinkedHashMap.class, factory, hessian2));
                assertSame(int.class, HessianStreams.roundTrip(int.class, factory, hessian2));
            }
        }
    }
}

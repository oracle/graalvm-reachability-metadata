/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.SerializerFactory;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AnnotationDeserializerTest {
    @Test
    @SuppressWarnings("checkstyle:annotationAccess")
    void roundTripsAnnotationWithSeveralMemberTypes() throws Exception {
        Policy original = Annotated.class.getAnnotation(Policy.class);
        for (boolean hessian2 : new boolean[] {false, true}) {
            Policy restored = HessianStreams.roundTrip(original, new SerializerFactory(), hessian2);
            assertEquals(Policy.class, restored.annotationType());
            assertEquals("shipping", restored.name());
            assertEquals(3, restored.retries());
            assertArrayEquals(new String[] {"east", "west"}, restored.regions());
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Policy {
        String name();
        int retries();
        String[] regions();
    }

    @Policy(name = "shipping", retries = 3, regions = {"east", "west"})
    private static class Annotated {
    }
}

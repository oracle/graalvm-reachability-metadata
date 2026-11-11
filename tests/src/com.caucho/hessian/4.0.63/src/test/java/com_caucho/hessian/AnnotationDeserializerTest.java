/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.SerializerFactory;
import java.lang.annotation.Annotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class AnnotationDeserializerTest {
    @Test
    void roundTripsAnnotationInBothProtocols() throws Exception {
        for (boolean hessian2 : new boolean[] {false, true}) {
            Tag restored = (Tag) SerializationSupport.roundTrip(new TagValue(), new SerializerFactory(), hessian2);
            assertEquals("snapshot", restored.value());
            assertEquals(Tag.class, restored.annotationType());
        }
    }

    public @interface Tag {
        String value();
    }

    public static class TagValue implements Tag {
        @Override
        public String value() {
            return "snapshot";
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Tag.class;
        }
    }
}

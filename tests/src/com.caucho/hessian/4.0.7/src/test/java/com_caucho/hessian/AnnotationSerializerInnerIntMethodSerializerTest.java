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

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AnnotationSerializerInnerIntMethodSerializerTest {
    @Test
    @SuppressWarnings("checkstyle:annotationAccess")
    void serializesAnnotationMemberInBothWireFormats() throws Exception {
        Setting original = Annotated.class.getAnnotation(Setting.class);
        for (boolean hessian2 : new boolean[] {false, true}) {
            Setting restored = HessianStreams.roundTrip(original, new SerializerFactory(), hessian2);
            assertEquals(23, restored.value());
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Setting {
        int value();
    }

    @Setting(23)
    private static class Annotated {
    }
}

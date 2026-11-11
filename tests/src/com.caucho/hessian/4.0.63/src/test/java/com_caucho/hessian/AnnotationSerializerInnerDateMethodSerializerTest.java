/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.SerializerFactory;
import java.lang.annotation.Annotation;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class AnnotationSerializerInnerDateMethodSerializerTest {
    @Test
    void serializesDateValuedAnnotationInterface() throws Exception {
        for (boolean hessian2 : new boolean[] {false, true}) {
            for (Date date : new Date[] {new Date(1700000000123L), null}) {
                Dated restored = (Dated) SerializationSupport.roundTrip(new DatedValue(date),
                        new SerializerFactory(), hessian2);
                assertEquals(date, restored.createdAt());
                assertEquals(Dated.class, restored.annotationType());
            }
        }
    }

    public interface Dated extends Annotation {
        Date createdAt();
    }

    public static class DatedValue implements Dated {
        private final Date date;

        public DatedValue(Date date) {
            this.date = date;
        }

        @Override
        public Date createdAt() {
            return date;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Dated.class;
        }
    }
}

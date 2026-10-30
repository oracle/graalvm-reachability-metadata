/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.SerializerFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

public class EnumDeserializerTest {
    @Test
    void roundTripsEnumValueInBothWireFormats() throws Exception {
        for (boolean hessian2 : new boolean[] {false, true}) {
            assertSame(Status.SHIPPED, HessianStreams.roundTrip(Status.SHIPPED, new SerializerFactory(), hessian2));
        }
    }

    public enum Status {
        QUEUED, SHIPPED
    }
}

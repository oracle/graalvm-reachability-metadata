/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import org.junit.jupiter.api.Test;

import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WriteReplaceSerializerTest {
    @Test
    void replacesApplicationObjectWithTransportValue() throws Exception {
        assertEquals("order:dispatch", HessianStreams.roundTrip((Object) new Order("dispatch")));
    }

    public static class Order implements Serializable {
        private final String status;

        public Order(String status) {
            this.status = status;
        }

        private Object writeReplace() {
            return "order:" + status;
        }
    }
}

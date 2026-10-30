/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.AbstractHessianOutput;
import com.caucho.hessian.io.AbstractSerializer;
import com.caucho.hessian.io.SerializerFactory;
import com.caucho.hessian.io.WriteReplaceSerializer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WriteReplaceSerializerTest {
    @Test
    void replacesApplicationObjectWithTransportValue() throws Exception {
        assertEquals("order:dispatch", HessianStreams.roundTrip((Object) new Order("dispatch")));
    }

    @Test
    void explicitlySelectsReplacementInsteadOfConventionNamedCodec() throws Exception {
        for (boolean hessian2 : new boolean[] {false, true}) {
            ConventionalOrder order = new ConventionalOrder("dispatch");
            Object orders = new ArrayList<>(List.of(order));
            assertEquals(List.of("codec:dispatch"), HessianStreams.roundTrip(orders, new SerializerFactory(), hessian2));

            WriteReplaceSerializer serializer = new WriteReplaceSerializer(ConventionalOrder.class,
                    getClass().getClassLoader(), new ConventionalOrderHessianSerializer());
            SerializerFactory factory = HessianStreams.codecs(ConventionalOrder.class, serializer, null);
            assertEquals(List.of("replacement:dispatch"), HessianStreams.roundTrip(orders, factory, hessian2));
        }
    }

    public static class ConventionalOrder implements Serializable {
        private final String status;

        public ConventionalOrder(String status) {
            this.status = status;
        }

        private Object writeReplace() {
            return "replacement:" + status;
        }
    }

    public static class ConventionalOrderHessianSerializer extends AbstractSerializer {
        @Override
        public void writeObject(Object value, AbstractHessianOutput output) throws IOException {
            output.writeString("codec:" + ((ConventionalOrder) value).status);
        }
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

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.AbstractHessianInput;
import com.caucho.hessian.io.AbstractHessianOutput;
import com.caucho.hessian.io.AbstractMapDeserializer;
import com.caucho.hessian.io.AbstractSerializer;
import com.caucho.hessian.io.Deserializer;
import com.caucho.hessian.io.HessianProtocolException;
import com.caucho.hessian.io.SerializerFactory;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Locale;
import javax.management.ObjectName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class SerializerFactoryTest {
    @Test
    void discoversBundledCodecsForStandardValues() throws Exception {
        for (Object value : new Object[] {new BigDecimal("12345.6700"), new File("snapshot.bin"),
                new ObjectName("consistency:type=Snapshot,name=primary"), Locale.CANADA_FRENCH}) {
            for (boolean hessian2 : new boolean[] {false, true}) {
                assertEquals(value, SerializationSupport.roundTrip(value, new SerializerFactory(), hessian2));
            }
        }
    }

    @Test
    void customFirstFactoryDiscoversConventionDeserializer() throws Exception {
        for (boolean hessian2 : new boolean[] {false, true}) {
            SerializerFactory factory = new ConventionFirstFactory();
            Ticket restored = (Ticket) SerializationSupport.roundTrip(new Ticket("dispatch-42"), factory, hessian2);
            assertEquals("dispatch-42", restored.reference);
        }
    }

    public static class ConventionFirstFactory extends SerializerFactory {
        @Override
        protected Deserializer loadDeserializer(Class type) throws HessianProtocolException {
            Deserializer custom = getCustomDeserializer(type);
            return custom != null ? custom : super.loadDeserializer(type);
        }
    }

    public static class Ticket {
        private final String reference;

        public Ticket(String reference) {
            this.reference = reference;
        }
    }

    public static class TicketHessianSerializer extends AbstractSerializer {
        @Override
        public void writeObject(Object object, AbstractHessianOutput output) throws IOException {
            output.writeMapBegin(Ticket.class.getName());
            output.writeString("ticket-reference");
            output.writeString(((Ticket) object).reference);
            output.writeMapEnd();
        }
    }

    public static class TicketHessianDeserializer extends AbstractMapDeserializer {
        @Override
        public Class<?> getType() {
            return Ticket.class;
        }

        @Override
        public Object readMap(AbstractHessianInput input) throws IOException {
            String reference = null;
            while (!input.isEnd()) {
                String key = input.readString();
                String value = input.readString();
                if ("ticket-reference".equals(key)) {
                    reference = value;
                }
            }
            input.readMapEnd();
            return new Ticket(reference);
        }
    }
}

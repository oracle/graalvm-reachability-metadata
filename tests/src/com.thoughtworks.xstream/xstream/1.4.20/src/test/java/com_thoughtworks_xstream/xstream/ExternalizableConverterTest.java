/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.List;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.reflection.ExternalizableConverter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExternalizableConverterTest {
    @Test
    void roundTripsExternalizableThroughDefaultConstructor() {
        XStream xstream = newXStream();
        PurchaseOrder order = new PurchaseOrder("PO-42", 7, List.of("created", "packed"));

        String xml = xstream.toXML(order);
        Object restored = xstream.fromXML(xml);

        assertThat(xml).contains("PO-42", "created", "packed");
        assertThat(restored).isEqualTo(order);
        assertThat(((PurchaseOrder)restored).isReadExternalCalled()).isTrue();
    }

    @SuppressWarnings("deprecation")
    @Test
    void deprecatedMapperConstructorStillRecognizesExternalizableTypes() {
        XStream xstream = newXStream();
        ExternalizableConverter converter = new ExternalizableConverter(xstream.getMapper());

        assertThat(converter.canConvert(PurchaseOrder.class)).isTrue();
        assertThat(converter.canConvert(String.class)).isFalse();
    }

    private static XStream newXStream() {
        XStream xstream = new XStream();
        xstream.allowTypes(new Class[]{PurchaseOrder.class});
        xstream.alias("purchase-order", PurchaseOrder.class);
        return xstream;
    }

    public static final class PurchaseOrder implements Externalizable {
        private String id;
        private int quantity;
        private List<String> events;
        private boolean readExternalCalled;

        public PurchaseOrder() {
            events = new ArrayList<>();
        }

        PurchaseOrder(String id, int quantity, List<String> events) {
            this.id = id;
            this.quantity = quantity;
            this.events = new ArrayList<>(events);
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeUTF(id);
            out.writeInt(quantity);
            out.writeObject(events);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            id = in.readUTF();
            quantity = in.readInt();
            events = (List<String>)in.readObject();
            readExternalCalled = true;
        }

        boolean isReadExternalCalled() {
            return readExternalCalled;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PurchaseOrder)) {
                return false;
            }
            PurchaseOrder that = (PurchaseOrder)other;
            return quantity == that.quantity && id.equals(that.id) && events.equals(that.events);
        }

        @Override
        public int hashCode() {
            int result = id.hashCode();
            result = 31 * result + quantity;
            result = 31 * result + events.hashCode();
            return result;
        }
    }
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.BeanSerializerFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class BeanDeserializerTest {
    @Test
    void reconstructsBeanUsingConstructorAndSetters() throws Exception {
        Address original = new Address(99);
        original.setCity("Riverside");
        original.setPostalCode(8042);
        Address restored = HessianStreams.roundTrip(original, new BeanSerializerFactory(), false);
        assertNotSame(original, restored);
        assertEquals("Riverside", restored.getCity());
        assertEquals(8042, restored.getPostalCode());
        assertEquals(0, restored.constructorValue);
    }

    @Test
    void resolvesDeserializedBean() throws Exception {
        ResolvedBean original = new ResolvedBean();
        original.setText("ready");
        assertEquals("resolved:ready", HessianStreams.roundTrip((Object) original, new BeanSerializerFactory(), false));
    }

    public static class Address {
        private final transient int constructorValue;
        private String city;
        private int postalCode;

        public Address(int constructorValue) {
            this.constructorValue = constructorValue;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public int getPostalCode() {
            return postalCode;
        }

        public void setPostalCode(int postalCode) {
            this.postalCode = postalCode;
        }
    }

    public static class ResolvedBean {
        private String text;

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public Object readResolve() {
            return "resolved:" + text;
        }
    }
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.SerializerFactory;
import com.caucho.hessian.io.UnsafeDeserializer;
import org.junit.jupiter.api.Test;

import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UnsafeDeserializerTest {
    @Test
    void restoresObjectWithoutRunningConstructorAndAppliesReadResolve() throws Exception {
        Parcel original = new Parcel("fragile", 4);
        for (boolean hessian2 : new boolean[] {false, true}) {
            SerializerFactory factory = HessianStreams.codecs(Parcel.class, null, new UnsafeDeserializer(Parcel.class));
            Parcel restored = HessianStreams.roundTrip(original, factory, hessian2);
            assertEquals("fragile", restored.label);
            assertEquals(4, restored.quantity);
            assertFalse(restored.constructed);
            assertTrue(restored.resolved);
        }
    }

    public static class Parcel implements Serializable {
        private String label;
        private int quantity;
        private transient boolean constructed;
        private transient boolean resolved;

        public Parcel(String label, int quantity) {
            this.label = label;
            this.quantity = quantity;
            constructed = true;
        }

        private Object readResolve() {
            resolved = true;
            return this;
        }
    }
}

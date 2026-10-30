/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.JavaDeserializer;
import com.caucho.hessian.io.SerializerFactory;
import com.caucho.hessian.io.UnsafeSerializer;
import org.junit.jupiter.api.Test;

import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class UnsafeSerializerTest {
    @Test
    void serializesPrivateFieldsAndOmitsTransientState() throws Exception {
        Parcel original = new Parcel();
        original.label = "express";
        original.weight = 12.5;
        original.localNote = "not on wire";
        for (boolean hessian2 : new boolean[] {false, true}) {
            SerializerFactory factory = HessianStreams.codecs(Parcel.class,
                    new UnsafeSerializer(Parcel.class), new JavaDeserializer(Parcel.class));
            Parcel restored = HessianStreams.roundTrip(original, factory, hessian2);
            assertEquals("express", restored.label);
            assertEquals(12.5, restored.weight);
            assertNull(restored.localNote);
        }
    }

    public static class Parcel implements Serializable {
        private String label;
        private double weight;
        private transient String localNote;
    }
}

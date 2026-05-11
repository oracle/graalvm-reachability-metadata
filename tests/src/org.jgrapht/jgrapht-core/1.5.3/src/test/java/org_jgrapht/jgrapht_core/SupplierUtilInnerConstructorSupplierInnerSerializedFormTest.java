/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgrapht.jgrapht_core;

import org.jgrapht.util.SupplierUtil;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

public class SupplierUtilInnerConstructorSupplierInnerSerializedFormTest {
    @Test
    void deserializesConstructorSupplierThroughSerializedForm() throws Exception {
        Supplier<SerializableSuppliedValue> originalSupplier =
            SupplierUtil.createSupplier(SerializableSuppliedValue.class);

        Supplier<SerializableSuppliedValue> deserializedSupplier = roundTrip(originalSupplier);

        SerializableSuppliedValue suppliedValue = deserializedSupplier.get();
        assertThat(deserializedSupplier).isNotSameAs(originalSupplier);
        assertThat(suppliedValue.value()).isEqualTo("deserialized constructor supplier");
    }

    @SuppressWarnings("unchecked")
    private static Supplier<SerializableSuppliedValue> roundTrip(
        Supplier<SerializableSuppliedValue> supplier)
        throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(supplier);
        }

        try (ObjectInputStream input = new ObjectInputStream(
            new ByteArrayInputStream(bytes.toByteArray()))) {
            return (Supplier<SerializableSuppliedValue>) input.readObject();
        }
    }

    public static final class SerializableSuppliedValue implements Serializable {
        private final String value;

        public SerializableSuppliedValue() {
            this.value = "deserialized constructor supplier";
        }

        String value() {
            return value;
        }
    }
}

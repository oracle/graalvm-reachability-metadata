/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

final class SerializationTestSupport {
    private SerializationTestSupport() {
    }

    static <T extends Serializable> T roundTrip(T value) throws IOException, ClassNotFoundException {
        final byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
            serialized = bytes.toByteArray();
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            @SuppressWarnings("unchecked")
            final T restored = (T) input.readObject();
            return restored;
        }
    }
}

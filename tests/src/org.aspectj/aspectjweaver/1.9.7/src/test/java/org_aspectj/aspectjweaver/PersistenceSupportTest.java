/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

import org.aspectj.weaver.CompressingDataOutputStream;
import org.aspectj.weaver.PersistenceSupport;
import org.junit.jupiter.api.Test;

public class PersistenceSupportTest {
    @Test
    void writesSerializablePayloadThroughObjectStream() throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressingDataOutputStream output = new CompressingDataOutputStream(bytes, null);
        String payload = "aspectj-persistence-support";

        PersistenceSupport.write(output, payload);
        output.flush();

        Object restored = readObject(bytes);

        assertThat(restored).isEqualTo(payload);
    }

    private static Object readObject(ByteArrayOutputStream bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return input.readObject();
        }
    }
}

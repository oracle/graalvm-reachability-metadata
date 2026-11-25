/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import org.aspectj.weaver.CompressingDataOutputStream;
import org.aspectj.weaver.PersistenceSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PersistenceSupportTest {
    @Test
    void serializableObjectsAreWrittenToTheCompressingStream() throws IOException, ClassNotFoundException {
        Serializable value = "persisted aspectj state";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (CompressingDataOutputStream output = new CompressingDataOutputStream(bytes, null)) {
            PersistenceSupport.write(output, value);
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertThat(input.readObject()).isEqualTo(value);
        }
    }
}

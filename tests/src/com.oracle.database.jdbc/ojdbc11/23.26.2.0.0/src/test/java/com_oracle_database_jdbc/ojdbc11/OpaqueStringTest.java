/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import oracle.jdbc.internal.OpaqueString;
import org.junit.jupiter.api.Test;

public class OpaqueStringTest {
    @Test
    void retainsItsValueAcrossJavaSerialization() throws Exception {
        OpaqueString original = OpaqueString.newOpaqueString("sensitive-value");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }

        OpaqueString restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (OpaqueString) input.readObject();
        }

        assertThat(restored.get()).isEqualTo("sensitive-value");
        assertThat(restored).isEqualTo(original);
    }
}

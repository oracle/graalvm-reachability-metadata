/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import static org.assertj.core.api.Assertions.assertThat;

import com.mchange.v2.c3p0.impl.DbAuth;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class DbAuthTest {
    @Test
    void serializesAndDeserializesCredentials() throws Exception {
        DbAuth original = new DbAuth("database-user", "database-password");

        DbAuth restored = roundTrip(original);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.getUser()).isEqualTo("database-user");
        assertThat(restored.getPassword()).isEqualTo("database-password");
        assertThat(restored.getMaskedUserString()).isEqualTo("da******");
    }

    @Test
    void preservesNullCredentialsDuringSerialization() throws Exception {
        DbAuth original = new DbAuth(null, null);

        DbAuth restored = roundTrip(original);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.getUser()).isNull();
        assertThat(restored.getPassword()).isNull();
        assertThat(restored.getMaskedUserString()).isEqualTo("null");
    }

    private static DbAuth roundTrip(DbAuth original) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(original);
        }

        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (DbAuth) in.readObject();
        }
    }
}

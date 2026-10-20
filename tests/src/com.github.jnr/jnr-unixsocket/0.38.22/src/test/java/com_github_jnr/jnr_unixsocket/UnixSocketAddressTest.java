/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_unixsocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import jnr.unixsocket.UnixSocketAddress;
import org.junit.jupiter.api.Test;

public class UnixSocketAddressTest {
    @Test
    void preservesTheSocketPathAcrossSerialization() throws Exception {
        UnixSocketAddress original = new UnixSocketAddress("/tmp/jnr-unixsocket-round-trip.sock");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }

        UnixSocketAddress restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = assertInstanceOf(UnixSocketAddress.class, input.readObject());
        }

        assertNotSame(original, restored);
        assertEquals(original, restored);
        assertEquals(original.path(), restored.path());
        assertEquals(original.humanReadablePath(), restored.humanReadablePath());
    }
}

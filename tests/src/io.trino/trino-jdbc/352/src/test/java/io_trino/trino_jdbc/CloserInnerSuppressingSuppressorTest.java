/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.io.Closer;
import java.io.IOException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CloserInnerSuppressingSuppressorTest {
    @Test
    void preservesPrimaryAndSuppressedCloseExceptions() {
        IOException first = new IOException("first close");
        IOException second = new IOException("second close");
        Closer closer = Closer.create();
        closer.register(() -> {
            throw second;
        });
        closer.register(() -> {
            throw first;
        });
        assertSame(first, assertThrows(IOException.class, closer::close));
        assertArrayEquals(new Throwable[] {second}, first.getSuppressed());
    }
}

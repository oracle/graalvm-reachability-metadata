/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.base.Throwables;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ThrowablesTest {
    @Test
    void exposesStackFramesAndCausalChain() {
        IOException root = new IOException("read interrupted");
        IllegalStateException failure = new IllegalStateException("request failed", root);
        List<StackTraceElement> frames = Throwables.lazyStackTrace(failure);

        assertEquals(Arrays.asList(failure.getStackTrace()), frames);
        assertThrows(UnsupportedOperationException.class, () -> frames.add(frames.get(0)));
        assertEquals(List.of(failure, root), Throwables.getCausalChain(failure));
        assertSame(root, Throwables.getRootCause(failure));
    }
}

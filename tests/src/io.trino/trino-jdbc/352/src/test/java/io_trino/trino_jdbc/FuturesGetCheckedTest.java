/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.util.concurrent.Futures;
import java.io.IOException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FuturesGetCheckedTest {
    @Test
    void translatesFailedFutureToRequestedCheckedException() {
        IOException cause = new IOException("unavailable");
        IOException failure = assertThrows(IOException.class,
                () -> Futures.getChecked(Futures.immediateFailedFuture(cause), IOException.class));
        assertSame(cause, failure.getCause());
    }
}

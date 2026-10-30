/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.util.concurrent.Futures;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FuturesGetCheckedInnerGetCheckedTypeValidatorHolderTest {
    @Test
    void validatesExceptionTypeBeforeReturningResult() throws Exception {
        assertEquals("ready", Futures.getChecked(Futures.immediateFuture("ready"), IOException.class, 10, TimeUnit.SECONDS));
    }
}

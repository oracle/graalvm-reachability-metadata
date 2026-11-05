/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.common.util.concurrent.Futures;
import com.couchbase.client.core.deps.com.google.common.util.concurrent.MoreExecutors;
import com.couchbase.client.core.deps.com.google.common.util.concurrent.SettableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AbstractFutureInnerUnsafeAtomicHelperAnonymous1Test {
    @Test
    void exercisesPublicApi() throws Exception {
        SettableFuture<Integer> source = SettableFuture.create();
        var transformed = Futures.transform(source, value -> value * 2, MoreExecutors.directExecutor());
        assertTrue(source.set(21));
        assertEquals(42, transformed.get(10, TimeUnit.SECONDS));
        assertFalse(source.set(99));
    }
}

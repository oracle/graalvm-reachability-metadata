/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty_websocket.jetty_websocket_jetty_api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jetty.websocket.api.Callback;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CallbackInnerCompletableTest {
    @Test
    void startsEachComposedOperationOnlyAfterItsPredecessorSucceeds() throws Exception {
        List<String> operations = new ArrayList<>();
        Callback.Completable first = Callback.Completable.with(callback -> operations.add("first"));
        Callback.Completable second = first.compose(callback -> operations.add("second"));
        Callback.Completable third = second.compose(callback -> {
            operations.add("third");
            callback.succeed();
        });

        assertEquals(List.of("first"), operations);
        assertFalse(second.isDone());
        first.succeed();
        assertEquals(List.of("first", "second"), operations);
        assertFalse(third.isDone());
        second.succeed();

        assertNull(third.get(20, TimeUnit.SECONDS));
        assertEquals(List.of("first", "second", "third"), operations);
    }

    @Test
    void propagatesFailureWithoutStartingTheNextOperation() {
        IOException failure = new IOException("write failed");
        Callback.Completable first = Callback.Completable.with(callback -> callback.fail(failure));
        AtomicBoolean invoked = new AtomicBoolean();
        Callback.Completable next = first.compose(callback -> {
            invoked.set(true);
            callback.succeed();
        });

        ExecutionException exception = assertThrows(ExecutionException.class,
                () -> next.get(20, TimeUnit.SECONDS));
        assertSame(failure, exception.getCause());
        assertFalse(invoked.get());
    }
}

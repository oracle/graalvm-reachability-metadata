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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.websocket.api.Callback;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CallbackTest {
    @Test
    void invokesSuccessBeforeNestedCompletion() {
        List<String> events = new ArrayList<>();
        Callback delegate = Callback.from(() -> events.add("sent"), failure -> {
            throw new AssertionError(failure);
        });
        Callback callback = Callback.from(delegate, () -> events.add("completed"));

        callback.succeed();

        assertEquals(List.of("sent", "completed"), events);
    }

    @Test
    void passesOriginalFailureBeforeNestedCompletion() {
        List<Object> events = new ArrayList<>();
        IOException failure = new IOException("send rejected");
        Callback delegate = Callback.from(() -> events.add("sent"), events::add);
        Callback callback = Callback.from(delegate, () -> events.add("completed"));

        callback.fail(failure);

        assertEquals(List.of(failure, "completed"), events);
    }

    @Test
    void adaptsSuccessfulFutureCompletion() throws Exception {
        Callback.Completable result = new Callback.Completable();
        CompletableFuture<Void> operation = new CompletableFuture<>();
        operation.whenComplete(result.asBiConsumer());

        operation.complete(null);

        assertNull(result.get(20, TimeUnit.SECONDS));
    }

    @Test
    void adaptsExceptionalFutureCompletion() {
        Callback.Completable result = new Callback.Completable();
        CompletableFuture<Void> operation = new CompletableFuture<>();
        operation.whenComplete(result.asBiConsumer());
        IOException failure = new IOException("connection closed");

        operation.completeExceptionally(failure);

        ExecutionException exception = assertThrows(ExecutionException.class,
                () -> result.get(20, TimeUnit.SECONDS));
        assertSame(failure, exception.getCause());
    }
}

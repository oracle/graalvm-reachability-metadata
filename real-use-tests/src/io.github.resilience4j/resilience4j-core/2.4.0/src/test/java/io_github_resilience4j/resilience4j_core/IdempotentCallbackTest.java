/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_core;

import io.github.resilience4j.core.functions.OnceConsumer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Delivers an idempotent callback through Resilience4j's once-consumer subsystem.
 *
 * A checkout request may be completed by both a timeout path and a remote response. The application
 * wraps the response in a once-only consumer so repeated completion notifications are sent once.
 */
class IdempotentCallbackTest {

    @Test
    void sendsOnlyOneNotificationWhenCompletionPathsRepeat() {
        CheckoutResult result = new CheckoutResult("order-42", "confirmed");
        OnceConsumer<CheckoutResult> completion = OnceConsumer.of(result);
        List<String> notifications = new ArrayList<>();

        completion.applyOnce(value -> notifications.add(value.orderId() + ":" + value.status()));
        completion.applyOnce(value -> notifications.add(value.orderId() + ":duplicate"));

        assertThat(notifications).containsExactly("order-42:confirmed");
    }

    private record CheckoutResult(String orderId, String status) {
    }
}

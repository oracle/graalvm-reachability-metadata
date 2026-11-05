/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_reactivex_rxjava3.rxjava;

import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.subjects.PublishSubject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** RxJavaPlugins routes undeliverable errors to the application's error handler. */
public class RxJavaPluginsErrorReportingTest {
    @Test
    @Timeout(60)
    public void undeliverableErrorsReachApplicationErrorHandler() {
        List<String> errorMessages = new ArrayList<>();
        RxJavaPlugins.setErrorHandler(error -> errorMessages.add(error.getMessage()));
        try {
            PublishSubject<String> events = PublishSubject.create();
            events.subscribe(value -> { }, error -> { });
            events.onComplete();
            events.onError(new IllegalStateException("connection lost"));

            assertEquals(List.of("connection lost"), errorMessages);
        } finally {
            RxJavaPlugins.reset();
        }
    }
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_reactor.micronaut_reactor;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class ReactorContextPropagationTest {
    @Test
    void propagatesMicronautContextAcrossReactorScheduler() {
        try (ApplicationContext context = ApplicationContext.run(
                Map.of(
                        "reactor.enable-automatic-context-propagation", false,
                        "reactor.enable-schedule-hook-context-propagation", true))) {
            ContextValue value = new ContextValue("request-value");
            PropagatedContext propagatedContext = PropagatedContext.empty().plus(value);

            try (PropagatedContext.Scope ignored = propagatedContext.propagate()) {
                String propagatedValue = Mono.fromCallable(
                                () -> PropagatedContext.get().get(ContextValue.class).value())
                        .subscribeOn(Schedulers.boundedElastic())
                        .block(Duration.ofSeconds(10));

                assertThat(propagatedValue).isEqualTo("request-value");
            }
        }
    }

    private record ContextValue(String value) implements PropagatedContextElement {
    }
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_core_reactive;

import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReactorPropagationTest {
    @Test
    void carriesContextAlongAReactorPipeline() {
        RequestContext request = new RequestContext("request-b");
        PropagatedContext propagated = PropagatedContext.empty().plus(request);
        Mono<String> pipeline = Mono.deferContextual(view -> {
            assertSame(propagated, ReactorPropagation.findPropagatedContext(view).orElseThrow());
            assertEquals("retained", view.get("existing"));
            return Mono.just(ReactorPropagation.findContextElement(view, RequestContext.class)
                    .orElseThrow().value());
        });

        assertEquals("request-b", pipeline.contextWrite(context -> ReactorPropagation.addPropagatedContext(
                context.put("existing", "retained"), propagated)).block(Duration.ofSeconds(10)));
        assertFalse(PropagatedContext.exists());
    }

    @Test
    void addsElementsWithoutMutatingExistingContext() {
        Context original = Context.of("existing", "retained");
        RequestContext first = new RequestContext("first");
        RequestContext second = new RequestContext("second");
        Context oneElement = ReactorPropagation.addContextElement(original, first);
        Context twoElements = ReactorPropagation.addContextElement(oneElement, second);

        assertTrue(ReactorPropagation.findPropagatedContext(original).isEmpty());
        assertTrue(ReactorPropagation.findContextElement(original, RequestContext.class).isEmpty());
        assertEquals(List.of(first), ReactorPropagation.findAllContextElements(oneElement, RequestContext.class).toList());
        assertEquals(2, ReactorPropagation.findAllContextElements(twoElements, RequestContext.class).count());
        assertTrue(ReactorPropagation.findAllContextElements(twoElements, RequestContext.class).toList()
                .containsAll(List.of(first, second)));
        assertEquals("retained", twoElements.get("existing"));
    }

    private record RequestContext(String value) implements PropagatedContextElement {
    }
}

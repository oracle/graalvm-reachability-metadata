/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_core_reactive;

import io.micronaut.core.type.Argument;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReactiveStreamsTypeInformationProviderTest {
    @Test
    void argumentDiscoversReactivePublisherTypes() {
        assertTrue(Argument.of(Publisher.class, String.class).isReactive());
        assertTrue(Argument.of(Flux.class, String.class).isReactive());
        assertTrue(Argument.of(Mono.class, String.class).isReactive());
        assertFalse(Argument.of(Mono.class, String.class).isSpecifiedSingle());
        assertFalse(Argument.of(Flux.class, String.class).isSpecifiedSingle());
        assertFalse(Argument.STRING.isReactive());
        assertFalse(Argument.STRING.isCompletable());
    }

    @Test
    void resolvesPayloadAndVoidPublisherSemantics() {
        assertEquals(String.class, Argument.of(Publisher.class, String.class).getWrappedType().getType());
        assertTrue(Argument.of(Mono.class, Void.class).isVoid());
        assertFalse(Argument.of(Mono.class, String.class).isVoid());
    }
}

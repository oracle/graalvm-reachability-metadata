/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_projectreactor.reactor_core;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

public class MonoTest {

    @Test
    void resolvesAValueFromEachSubscribersContext() {
        Mono<String> greeting = Mono.deferContextual(context -> Mono.just("Hello, " + context.get("name")));

        assertThat(greeting.contextWrite(context -> context.put("name", "Ada"))
                .block(Duration.ofSeconds(10))).isEqualTo("Hello, Ada");
        assertThat(greeting.contextWrite(context -> context.put("name", "Grace"))
                .block(Duration.ofSeconds(10))).isEqualTo("Hello, Grace");
    }
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_projectreactor.reactor_core;

import java.time.Duration;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

public class MonoFlatMapInnerFlatMapMainTest {

    @Test
    void subscribesToTheMappedDeferredMono() {
        String value = Mono.defer(() -> Mono.just("reactor"))
                .flatMap(text -> Mono.defer(() -> Mono.just(text.toUpperCase(Locale.ROOT))))
                .block(Duration.ofSeconds(10));

        assertThat(value).isEqualTo("REACTOR");
    }
}

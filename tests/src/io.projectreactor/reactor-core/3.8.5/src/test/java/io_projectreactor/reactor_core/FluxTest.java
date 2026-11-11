/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_projectreactor.reactor_core;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

public class FluxTest {

    @Test
    void transformsAndFiltersValuesUsingSubscriberContext() {
        List<String> values = Flux.deferContextual(context -> Flux.just("apple", "pear", "apricot")
                        .filter(value -> value.startsWith("a"))
                        .map(value -> context.get("prefix") + value))
                .contextWrite(context -> context.put("prefix", "fruit:"))
                .collectList()
                .block(Duration.ofSeconds(10));

        assertThat(values).containsExactly("fruit:apple", "fruit:apricot");
    }
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_projectreactor.reactor_core;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

public class MonoIgnoreThenInnerThenIgnoreMainTest {

    @Test
    void completesEarlierStagesBeforeEmittingTheLastValue() {
        List<String> stages = new ArrayList<>();
        String result = Mono.fromRunnable(() -> stages.add("prepare"))
                .then(Mono.fromRunnable(() -> stages.add("save")))
                .then(Mono.defer(() -> {
                    stages.add("reply");
                    return Mono.just("saved");
                }))
                .block(Duration.ofSeconds(10));

        assertThat(result).isEqualTo("saved");
        assertThat(stages).containsExactly("prepare", "save", "reply");
    }
}

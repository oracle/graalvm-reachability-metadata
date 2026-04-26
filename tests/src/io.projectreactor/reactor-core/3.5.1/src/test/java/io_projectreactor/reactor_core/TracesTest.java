/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_projectreactor.reactor_core;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class TracesTest {

    @Test
    void forcedCheckpointAddsAssemblyTraceToFailingFlux() {
        Throwable failure = catchThrowable(() -> Flux.error(new IllegalStateException("boom"))
                .checkpoint("dynamic-access-traces", true)
                .blockLast());

        assertThat(failure)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
        assertThat(failure.getSuppressed())
                .isNotEmpty()
                .anySatisfy(suppressed -> assertThat(suppressed.getMessage())
                        .contains("Assembly trace from producer")
                        .contains("dynamic-access-traces")
                        .contains("TracesTest"));
    }
}

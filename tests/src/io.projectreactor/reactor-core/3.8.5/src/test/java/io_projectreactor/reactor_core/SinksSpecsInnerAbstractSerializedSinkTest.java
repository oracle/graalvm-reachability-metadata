/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_projectreactor.reactor_core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;

import static org.assertj.core.api.Assertions.assertThat;

public class SinksSpecsInnerAbstractSerializedSinkTest {

    @Test
    void deliversASingleReplyToAllSubscribersAndRejectsFurtherValues() throws Exception {
        Sinks.One<String> reply = Sinks.one();
        CompletableFuture<String> first = reply.asMono().toFuture();
        CompletableFuture<String> second = reply.asMono().toFuture();
        try {
            assertThat(reply.tryEmitValue("accepted")).isEqualTo(Sinks.EmitResult.OK);

            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo("accepted");
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo("accepted");
            assertThat(reply.tryEmitValue("ignored")).isEqualTo(Sinks.EmitResult.FAIL_TERMINATED);
        } finally {
            first.cancel(true);
            second.cancel(true);
        }
    }
}

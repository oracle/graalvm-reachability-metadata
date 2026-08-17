/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import java.time.Duration;
import java.util.Map;

import org.apache.kafka.streams.kstream.EmitStrategy;
import org.apache.kafka.streams.kstream.internals.suppress.BufferConfigInternal;
import org.apache.kafka.streams.kstream.internals.suppress.BufferFullStrategy;
import org.apache.kafka.streams.kstream.internals.suppress.EagerBufferConfigImpl;
import org.apache.kafka.streams.kstream.internals.suppress.FinalResultsSuppressionBuilder;
import org.apache.kafka.streams.kstream.internals.suppress.StrictBufferConfigImpl;
import org.apache.kafka.streams.kstream.internals.suppress.SuppressedInternal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SuppressionPolicyCoverageTest {
    @Test
    void strictAndEagerBufferPoliciesRetainBoundsAndLoggingConfiguration() {
        Map<String, String> logging = Map.of("cleanup.policy", "compact");
        StrictBufferConfigImpl strict = new StrictBufferConfigImpl(
                10, 20, BufferFullStrategy.SHUT_DOWN, logging);
        assertThat(strict.maxRecords()).isEqualTo(10);
        assertThat(strict.maxBytes()).isEqualTo(20);
        assertThat(strict.bufferFullStrategy()).isEqualTo(BufferFullStrategy.SHUT_DOWN);
        assertThat(strict.isLoggingEnabled()).isTrue();
        assertThat(strict.getLogConfig()).isEqualTo(logging);

        StrictBufferConfigImpl resized = (StrictBufferConfigImpl) strict.withMaxRecords(30).withMaxBytes(40);
        assertThat(resized.maxRecords()).isEqualTo(30);
        assertThat(resized.maxBytes()).isEqualTo(40);
        assertThat(((StrictBufferConfigImpl) resized.withLoggingDisabled()).isLoggingEnabled()).isFalse();
        assertThat(((StrictBufferConfigImpl) resized.withLoggingEnabled(logging)).getLogConfig()).isEqualTo(logging);
        assertThat(strict).isEqualTo(new StrictBufferConfigImpl(10, 20, BufferFullStrategy.SHUT_DOWN, logging));
        assertThat(strict.hashCode()).isEqualTo(
                new StrictBufferConfigImpl(10, 20, BufferFullStrategy.SHUT_DOWN, logging).hashCode());
        assertThat(strict.toString()).contains("maxKeys=10", "maxBytes=20");

        EagerBufferConfigImpl eager = new EagerBufferConfigImpl(5, 6, logging);
        assertThat(eager.maxRecords()).isEqualTo(5);
        assertThat(eager.maxBytes()).isEqualTo(6);
        assertThat(eager.bufferFullStrategy()).isEqualTo(BufferFullStrategy.EMIT);
        assertThat(eager.isLoggingEnabled()).isTrue();
        assertThat(eager.getLogConfig()).isEqualTo(logging);
        assertThat(((EagerBufferConfigImpl) eager.withMaxRecords(7)).maxRecords()).isEqualTo(7);
        assertThat(((EagerBufferConfigImpl) eager.withMaxBytes(8)).maxBytes()).isEqualTo(8);
        assertThat(((EagerBufferConfigImpl) eager.withLoggingDisabled()).isLoggingEnabled()).isFalse();
        assertThat(((EagerBufferConfigImpl) eager.withLoggingEnabled(logging)).getLogConfig()).isEqualTo(logging);
        assertThat(eager).isEqualTo(new EagerBufferConfigImpl(5, 6, logging));
        assertThat(eager.hashCode()).isEqualTo(new EagerBufferConfigImpl(5, 6, logging).hashCode());
        assertThat(eager.toString()).contains("maxRecords=5", "maxBytes=6");
    }

    @Test
    void genericBufferPolicyCanChooseEachFullBufferStrategy() {
        BufferConfigInternal<?> bounded = new EagerBufferConfigImpl(1, 2, Map.of());
        assertThat(((EagerBufferConfigImpl) bounded.emitEarlyWhenFull()).bufferFullStrategy())
                .isEqualTo(BufferFullStrategy.EMIT);
        assertThat(((StrictBufferConfigImpl) bounded.shutDownWhenFull()).bufferFullStrategy())
                .isEqualTo(BufferFullStrategy.SHUT_DOWN);
        assertThat(((StrictBufferConfigImpl) bounded.withNoBound()).maxBytes()).isEqualTo(Long.MAX_VALUE);
        assertThat(BufferFullStrategy.valueOf("EMIT")).isEqualTo(BufferFullStrategy.EMIT);
        assertThat(BufferFullStrategy.values()).containsExactly(BufferFullStrategy.EMIT, BufferFullStrategy.SHUT_DOWN);
    }

    @Test
    void finalResultsBuilderProducesNamedWindowEndSuppression() {
        StrictBufferConfigImpl buffer = new StrictBufferConfigImpl();
        FinalResultsSuppressionBuilder<?> builder =
                new FinalResultsSuppressionBuilder<>("final-results", buffer);
        SuppressedInternal<?> suppression = builder.buildFinalResultsSuppression(Duration.ofSeconds(3));

        assertThat(builder.name()).isEqualTo("final-results");
        assertThat(builder.withName("renamed")).isNotEqualTo(builder);
        assertThat(builder).isEqualTo(new FinalResultsSuppressionBuilder<>("final-results", buffer));
        assertThat(builder.hashCode()).isEqualTo(
                new FinalResultsSuppressionBuilder<>("final-results", buffer).hashCode());
        assertThat(builder.toString()).contains("final-results");
        assertThat(suppression.toString()).contains("final-results");
        assertThat(suppression).isEqualTo(suppression);
        assertThat(suppression.hashCode()).isNotZero();
        assertThat(suppression.withName("other")).isNotEqualTo(suppression);
    }


    @Test
    void emitStrategiesRoundTripThroughTheirPublicTypeFactory() {
        assertThat(EmitStrategy.StrategyType.values()).containsExactly(
                EmitStrategy.StrategyType.ON_WINDOW_UPDATE, EmitStrategy.StrategyType.ON_WINDOW_CLOSE);
        assertThat(EmitStrategy.StrategyType.valueOf("ON_WINDOW_CLOSE"))
                .isEqualTo(EmitStrategy.StrategyType.ON_WINDOW_CLOSE);
        assertThat(EmitStrategy.StrategyType.forType(EmitStrategy.StrategyType.ON_WINDOW_UPDATE)
                .type()).isEqualTo(EmitStrategy.StrategyType.ON_WINDOW_UPDATE);
        assertThat(EmitStrategy.StrategyType.forType(EmitStrategy.StrategyType.ON_WINDOW_CLOSE)
                .type()).isEqualTo(EmitStrategy.StrategyType.ON_WINDOW_CLOSE);
    }
}

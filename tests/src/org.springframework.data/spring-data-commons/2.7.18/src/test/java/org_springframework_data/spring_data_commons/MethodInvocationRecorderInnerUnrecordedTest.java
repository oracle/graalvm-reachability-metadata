/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.springframework.data.util.MethodInvocationRecorder;
import org.springframework.data.util.MethodInvocationRecorder.Recorded;

public class MethodInvocationRecorderInnerUnrecordedTest {

    @Test
    void recordsPrimitivePropertiesUsingTheirBoxedDefaultValues() {
        Recorded<Metrics> recorder = MethodInvocationRecorder.forProxyOf(Metrics.class);
        Recorded<Integer> count = recorder.record((Function<Metrics, Integer>) value -> {
            int result = value.getCount();
            assertThat(result).isZero();
            return result;
        });
        assertThat(count.getPropertyPath()).contains("count");

        Recorded<Boolean> active = recorder.record((Function<Metrics, Boolean>) value -> {
            boolean result = value.isActive();
            assertThat(result).isFalse();
            return result;
        });
        assertThat(active.getPropertyPath()).contains("active");
    }

    public interface Metrics {

        int getCount();

        boolean isActive();
    }
}

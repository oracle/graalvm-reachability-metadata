/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Period;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaEightUtilTest {
    @Test
    void mockDefaultAnswerReturnsEmptyJavaEightValues() {
        JavaEightValues values = Mockito.mock(JavaEightValues.class);

        assertThat(values.optional()).isEqualTo(Optional.empty());
        assertThat(values.optionalInt()).isEqualTo(OptionalInt.empty());
        assertThat(values.optionalLong()).isEqualTo(OptionalLong.empty());
        assertThat(values.optionalDouble()).isEqualTo(OptionalDouble.empty());
        assertThat(values.stream()).isEmpty();
        assertThat(values.intStream().toArray()).isEmpty();
        assertThat(values.longStream().toArray()).isEmpty();
        assertThat(values.doubleStream().toArray()).isEmpty();
        assertThat(values.duration()).isEqualTo(Duration.ZERO);
        assertThat(values.period()).isEqualTo(Period.ZERO);
    }

    interface JavaEightValues {
        Optional<String> optional();

        OptionalInt optionalInt();

        OptionalLong optionalLong();

        OptionalDouble optionalDouble();

        Stream<String> stream();

        IntStream intStream();

        LongStream longStream();

        DoubleStream doubleStream();

        Duration duration();

        Period period();
    }
}

/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

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

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaEightUtilTest {
    @Test
    void returnsJavaEightEmptyValuesForUnstubbedInterfaceMethods() {
        JavaEightReturnTypes service = Mockito.mock(JavaEightReturnTypes.class);

        assertThat(service.optional()).isEmpty();
        assertThat(service.optionalDouble()).isEqualTo(OptionalDouble.empty());
        assertThat(service.optionalInt()).isEqualTo(OptionalInt.empty());
        assertThat(service.optionalLong()).isEqualTo(OptionalLong.empty());
        assertThat(service.duration()).isEqualTo(Duration.ZERO);
        assertThat(service.period()).isEqualTo(Period.ZERO);

        try (Stream<String> strings = service.stream();
                DoubleStream doubles = service.doubleStream();
                IntStream integers = service.intStream();
                LongStream longs = service.longStream()) {
            assertThat(strings).isEmpty();
            assertThat(doubles.count()).isZero();
            assertThat(integers.count()).isZero();
            assertThat(longs.count()).isZero();
        }
    }

    public interface JavaEightReturnTypes {
        Optional<String> optional();

        OptionalDouble optionalDouble();

        OptionalInt optionalInt();

        OptionalLong optionalLong();

        Stream<String> stream();

        DoubleStream doubleStream();

        IntStream intStream();

        LongStream longStream();

        Duration duration();

        Period period();
    }
}

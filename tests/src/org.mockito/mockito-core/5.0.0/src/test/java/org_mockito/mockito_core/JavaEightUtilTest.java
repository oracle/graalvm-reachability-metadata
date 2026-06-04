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
    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Test
    void defaultAnswerReturnsEmptyJavaEightValuesForUnstubbedMethods() {
        JavaEightDefaultValues service = Mockito.mock(JavaEightDefaultValues.class);

        assertThat(service.optional()).isEmpty();
        assertThat(service.optionalDouble()).isEmpty();
        assertThat(service.optionalInt()).isEmpty();
        assertThat(service.optionalLong()).isEmpty();
        assertThat(service.duration()).isEqualTo(Duration.ZERO);
        assertThat(service.period()).isEqualTo(Period.ZERO);

        try (Stream<String> stream = service.stream();
                DoubleStream doubleStream = service.doubleStream();
                IntStream intStream = service.intStream();
                LongStream longStream = service.longStream()) {
            assertThat(stream).isEmpty();
            assertThat(doubleStream).isEmpty();
            assertThat(intStream).isEmpty();
            assertThat(longStream).isEmpty();
        }
    }

    private interface JavaEightDefaultValues {
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

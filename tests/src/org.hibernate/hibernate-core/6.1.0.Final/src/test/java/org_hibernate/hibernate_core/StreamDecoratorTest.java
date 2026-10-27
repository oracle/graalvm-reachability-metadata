/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.query.spi.StreamDecorator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class StreamDecoratorTest {

    @Test
    public void appliesJdkNineStreamOperationsAndClosesTheDelegate() {
        AtomicBoolean closed = new AtomicBoolean();

        try (Stream<Integer> stream = new StreamDecorator<>(
                Stream.of(1, 2, 3, 4),
                () -> closed.set(true)
        )) {
            List<Integer> values = stream.dropWhile(value -> value < 2)
                    .takeWhile(value -> value < 4)
                    .toList();
            assertThat(values).containsExactly(2, 3);
        }

        assertThat(closed).isTrue();
    }
}

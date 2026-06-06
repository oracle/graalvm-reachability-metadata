/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.builder.Visitor;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class VisitorsTest {
    @Test
    void resolvesVisitorGenericArrayType() {
        GenericArrayVisitor visitor = new GenericArrayVisitor();
        List<String>[] values = listArray(List.of("first"), List.of("second"));

        assertThat(visitor.getType()).isEqualTo(List[].class);
        assertThat(visitor.canVisit(List.of(), values)).isTrue();

        visitor.visit(values);

        assertThat(visitor.visited()).isSameAs(values);
        assertThat(visitor.visited()[0]).containsExactly("first");
        assertThat(visitor.visited()[1]).containsExactly("second");
    }

    @SafeVarargs
    private static List<String>[] listArray(List<String>... values) {
        return values;
    }

    public static final class GenericArrayVisitor implements Visitor<List<String>[]> {
        private final AtomicReference<List<String>[]> visited = new AtomicReference<>();

        @Override
        public void visit(List<String>[] element) {
            visited.set(element);
        }

        List<String>[] visited() {
            return visited.get();
        }
    }
}

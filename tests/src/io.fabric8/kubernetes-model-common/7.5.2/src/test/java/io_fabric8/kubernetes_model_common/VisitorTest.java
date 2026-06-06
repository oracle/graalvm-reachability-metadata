/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_model_common;

import static org.assertj.core.api.Assertions.assertThat;

import io.fabric8.kubernetes.api.builder.Visitor;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class VisitorTest {
    @Test
    void canVisitUsesVisitMethodWhenGenericTypeCannotBeResolved() {
        GenericVisitor<String> visitor = new GenericVisitor<>();

        Boolean canVisit = visitor.canVisit(Collections.emptyList(), "resource");

        assertThat(canVisit).isTrue();
    }

    @Test
    void genericVisitorStillVisitsElementsThroughDefaultPathOverload() {
        GenericVisitor<String> visitor = new GenericVisitor<>();

        visitor.visit(Collections.emptyList(), "resource");

        assertThat(visitor.getVisited()).isEqualTo("resource");
    }

    private static final class GenericVisitor<T> implements Visitor<T> {
        private final AtomicReference<T> visited = new AtomicReference<>();

        @Override
        public void visit(T element) {
            visited.set(element);
        }

        T getVisited() {
            return visited.get();
        }
    }
}
